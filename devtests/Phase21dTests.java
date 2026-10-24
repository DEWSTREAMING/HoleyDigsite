package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.sellpad.SellPadBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import com.holeydigsite.shop.ShopDisplayBlock;
import com.holeydigsite.shop.ShopDisplayBlockEntity;
import com.holeydigsite.shop.custom.CustomBlueprint;
import com.holeydigsite.shop.custom.CustomEntry;
import com.holeydigsite.shop.custom.CustomShops;
import com.holeydigsite.shop.custom.ShopSetupNetwork;
import com.holeydigsite.site.SiteData;
import com.holeydigsite.site.SiteDef;
import com.holeydigsite.site.SiteDefs;
import com.holeydigsite.site.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 21d (sandbox only): operator-made shop displays - set by the setup screen's save packet and by command, permission
 * checks (incl. a forged packet), copied template, blueprint -> Buy Pad -> button delivers the exact item and charges the
 * price, cleared / changed entries refuse old blueprints, buy-back only at that store's Sell Pad, entries survive a store
 * rebuild, and a catalogue slot can't be overwritten.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase21dTests {
    private static void check(GameTestHelper h, boolean ok, String msg) {
        if (!ok) {
            h.fail(msg);
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getAbilities().instabuild = false;
        p.getInventory().clearContent();
        return p;
    }

    private static void op(ServerPlayer p, boolean on) {
        var ops = p.server.getPlayerList().getOps();
        if (on) {
            ops.add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 2, false));
        } else {
            ops.remove(p.getGameProfile());
        }
    }

    private static ServerPlayer opPlayer(GameTestHelper h) {
        ServerPlayer p = player(h);
        op(p, true);
        return p;
    }

    private static ItemStack namedSword() {
        ItemStack s = new ItemStack(Items.IRON_SWORD);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Shop Special"));
        return s;
    }

    private static ShopDisplayBlockEntity display(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.SHOP_DISPLAY.get());
        return (ShopDisplayBlockEntity) h.getBlockEntity(rel);
    }

    private static int count(ServerPlayer p, ItemStack like) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (ItemStack.isSameItemSameComponents(s, like)) n += s.getCount();
        return n;
    }

    private static void near(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos a = h.absolutePos(rel);
        p.setPos(a.getX() + 0.5, a.getY(), a.getZ() + 2.5);
    }

    // ------------------------------------------------------------------------------------------------ set / permissions

    @GameTest(template = "empty")
    public static void opSetsAnEntryNonOpCannotAndTemplateIsACopy(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 2, 1);
        ShopDisplayBlockEntity d = display(h, rel);
        BlockPos abs = h.absolutePos(rel);
        ServerLevel l = h.getLevel();
        ServerPlayer op = opPlayer(h);
        ServerPlayer plain = player(h);
        try {
            near(h, op, rel);
            near(h, plain, rel);
            ItemStack held = namedSword();
            op.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);

            // a forged save packet from a non-op is refused
            var forged = new ShopSetupNetwork.SavePayload(abs, false, namedSword(), 1, 10, 0);
            check(h, ShopSetupNetwork.handleSave(plain, forged) != CustomShops.Result.OK && d.custom() == null, "non-op's forged save is refused");
            check(h, CustomShops.get(l).entry(CustomShops.idOf(l, d)) == null, "nothing saved by the forged packet");

            // the op saves (the screen's packet): template copy, count, price, buy-back
            var save = new ShopSetupNetwork.SavePayload(abs, false, held.copy(), 4, 1536, 64);
            check(h, ShopSetupNetwork.handleSave(op, save) == CustomShops.Result.OK, "op saves");
            CustomEntry e = d.custom();
            check(h, e != null && e.count() == 4 && e.price() == 1536 && e.buyBack() == 64, "entry values saved: " + e);
            check(h, ItemStack.isSameItemSameComponents(e.template(), held) && e.template().getCount() == 1, "template is a copy with its components");
            check(h, held.getCount() == 1 && !op.getMainHandItem().isEmpty(), "the op keeps the item");
            check(h, CustomShops.get(l).entry(CustomShops.idOf(l, d)) != null, "entry is in the world data");

            // bad values
            check(h, ShopSetupNetwork.handleSave(op, new ShopSetupNetwork.SavePayload(abs, false, held.copy(), 0, 10, 0)) == CustomShops.Result.BAD_VALUES, "count 0 refused");
            check(h, ShopSetupNetwork.handleSave(op, new ShopSetupNetwork.SavePayload(abs, false, held.copy(), 1, 0, 0)) == CustomShops.Result.BAD_VALUES, "price 0 refused");
            check(h, ShopSetupNetwork.handleSave(op, new ShopSetupNetwork.SavePayload(abs, false, held.copy(), 65, 5, 0)) == CustomShops.Result.BAD_VALUES, "count 65 refused");
            check(h, d.custom().count() == 4, "refused saves change nothing");

            // non-op clear is refused too; op clear works
            ShopSetupNetwork.handleSave(plain, new ShopSetupNetwork.SavePayload(abs, true, ItemStack.EMPTY, 1, 0, 0));
            check(h, d.custom() != null, "non-op can't clear");
            ShopSetupNetwork.handleSave(op, new ShopSetupNetwork.SavePayload(abs, true, ItemStack.EMPTY, 1, 0, 0));
            check(h, d.custom() == null && CustomShops.get(l).entry(CustomShops.idOf(l, d)) == null, "op clears");

            // a catalogue display can't be made custom
            BlockPos rel2 = new BlockPos(3, 2, 1);
            ShopDisplayBlockEntity cat = display(h, rel2);
            cat.setItem(ModItems.PAN.get());
            check(h, CustomShops.set(l, h.absolutePos(rel2), held, 1, 5, 0) == CustomShops.Result.CATALOGUE_ITEM, "catalogue display can't be overwritten");
            check(h, ShopSetupNetwork.handleSave(op, new ShopSetupNetwork.SavePayload(h.absolutePos(rel2), false, held.copy(), 1, 5, 0)) == CustomShops.Result.CATALOGUE_ITEM && cat.custom() == null, "also not by packet");
            h.succeed();
        } finally {
            CustomShops.clear(l, abs);
            op(op, false);
        }
    }

    @GameTest(template = "empty")
    public static void commandSetClearInfoList(GameTestHelper h) {
        BlockPos rel = new BlockPos(1, 2, 1);
        ShopDisplayBlockEntity d = display(h, rel);
        BlockPos abs = h.absolutePos(rel);
        ServerLevel l = h.getLevel();
        ServerPlayer p = player(h);
        try {
            p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 3.5);
            p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(abs));
            var server = l.getServer();
            var src = p.createCommandSourceStack().withPermission(2);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, namedSword());
            server.getCommands().performPrefixedCommand(src, "holeydigsite display set 1536 3 256");
            CustomEntry e = d.custom();
            check(h, e != null && e.price() == 1536 && e.count() == 3 && e.buyBack() == 256, "command set: " + e);
            check(h, ItemStack.isSameItemSameComponents(e.template(), namedSword()), "command template = the held stack");
            server.getCommands().performPrefixedCommand(src, "holeydigsite display set 99");
            check(h, d.custom().count() == 1 && d.custom().buyBack() == 0 && d.custom().price() == 99, "count defaults to 1, no buy-back");
            check(h, CustomShops.get(l).ids().contains(CustomShops.idOf(l, d)), "listed");
            server.getCommands().performPrefixedCommand(src, "holeydigsite display info");
            server.getCommands().performPrefixedCommand(src, "holeydigsite display list");
            server.getCommands().performPrefixedCommand(src, "holeydigsite display clear");
            check(h, d.custom() == null && CustomShops.get(l).entry(CustomShops.idOf(l, d)) == null, "command clear");
            // without permission the command does nothing
            var weak = p.createCommandSourceStack().withPermission(0);
            server.getCommands().performPrefixedCommand(weak, "holeydigsite display set 5");
            check(h, d.custom() == null, "permission 0 can't use the command");
            h.succeed();
        } finally {
            CustomShops.clear(l, abs);
        }
    }

    // ------------------------------------------------------------------------------------------------ buying

    @GameTest(template = "empty")
    public static void blueprintBuyPadButtonDeliversTheExactItemAndStaleBlueprintsAreRefused(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerLevel l = h.getLevel();
        BlockPos dispRel = new BlockPos(1, 1, 1);
        ShopDisplayBlockEntity d = display(h, dispRel);
        BlockPos btn = new BlockPos(4, 1, 2), buy = new BlockPos(5, 1, 2), pan = new BlockPos(4, 1, 3);
        h.setBlock(btn, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(buy, ModBlocks.BUY_PAD.get());
        h.setBlock(pan, ModBlocks.MONEY_PAN.get());
        ServerPlayer p = player(h);
        try {
            check(h, CustomShops.set(l, h.absolutePos(dispRel), namedSword(), 2, 100, 0) == CustomShops.Result.OK, "entry set");
            ShopDisplayBlock.interact(l, h.absolutePos(dispRel), p, ItemStack.EMPTY);
            ItemStack bp = p.getInventory().items.stream().filter(s -> CustomBlueprint.of(s) != null).findFirst().orElse(ItemStack.EMPTY);
            check(h, !bp.isEmpty() && bp.getCount() == 1, "the custom display hands out a blueprint");
            check(h, bp.getHoverName().getString().length() > 0, "blueprint has a name");
            check(h, ModBlocks.BUY_PAD.get().accepts(bp, l, h.absolutePos(buy)), "the Buy Pad accepts it");

            PadBlockEntity pad = (PadBlockEntity) h.getBlockEntity(buy);
            pad.insert(bp.copy());
            MoneyPanBlockEntity money = (MoneyPanBlockEntity) h.getBlockEntity(pan);
            money.deposit(150);
            p.getInventory().clearContent();
            ShopButtonBlock.press(l, h.absolutePos(btn), p);
            check(h, count(p, namedSword()) == 2, "2 exact swords (with the custom name) delivered: " + count(p, namedSword()));
            check(h, money.balance() == 50, "the price was charged: " + money.balance());
            check(h, pad.isEmpty(), "the blueprint was used up");

            // a changed or cleared entry refuses old blueprints, nothing is taken
            ItemStack old = bp.copy();
            check(h, CustomShops.set(l, h.absolutePos(dispRel), namedSword(), 2, 120, 0) == CustomShops.Result.OK, "entry changed");
            check(h, !ModBlocks.BUY_PAD.get().accepts(old, l, h.absolutePos(buy)), "old blueprint refused after a change");
            ItemStack fresh = CustomBlueprint.stackFor(CustomShops.idOf(l, d), d.custom(), 1);
            pad.insert(fresh.copy());
            CustomShops.clear(l, h.absolutePos(dispRel));
            money.deposit(1000);
            p.getInventory().clearContent();
            ShopButtonBlock.press(l, h.absolutePos(btn), p);
            check(h, count(p, namedSword()) == 0 && money.balance() == 1050, "stale blueprint on the pad: nothing bought, nothing charged");
            check(h, !pad.isEmpty(), "the blueprint is still on the pad (nothing taken)");
            check(h, !ModBlocks.BUY_PAD.get().accepts(fresh, l, h.absolutePos(buy)), "blueprint of a cleared entry refused");
            h.succeed();
        } finally {
            CustomShops.clear(l, h.absolutePos(dispRel));
        }
    }

    // ------------------------------------------------------------------------------------------------ stores: buy-back, rebuild, catalogue

    private static BoundingBox big(BlockPos a) {
        return new BoundingBox(a.getX() - 4, a.getY() - 2, a.getZ() - 4, a.getX() + 40, a.getY() + 34, a.getZ() + 40);
    }

    private static void prepare(ServerLevel l, BlockPos a) {
        Sites.clear(l, big(a));
        for (ItemEntity e : l.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(48))) e.discard();
    }

    private static void teardown(ServerLevel l, BlockPos a, java.util.List<String> ids) {
        SiteDefs.clearOverrides();
        SiteData data = SiteData.get(l);
        for (SiteData.Site s : new ArrayList<>(data.all())) {
            if (s.zone().isInside(a) || s.anchor().equals(a)) data.remove(s.key());
        }
        Sites.clear(l, big(a));
    }

    private static ShopDisplayBlockEntity slot(ServerLevel l, BoundingBox z, int n) {
        for (BlockPos p : BlockPos.betweenClosed(z.minX(), z.minY(), z.minZ(), z.maxX(), z.maxY(), z.maxZ())) {
            if (l.getBlockEntity(p) instanceof ShopDisplayBlockEntity d && d.slot() == n) return d;
        }
        return null;
    }

    private static PadBlockEntity sellPad(ServerLevel l, BoundingBox z) {
        for (BlockPos p : BlockPos.betweenClosed(z.minX(), z.minY(), z.minZ(), z.maxX(), z.maxY(), z.maxZ())) {
            if (l.getBlockEntity(p) instanceof PadBlockEntity pad && pad.getBlockState().is(ModBlocks.SELL_PAD.get())) return pad;
        }
        return null;
    }

    /** Buy-back is paid at that store's Sell Pad only; it survives a rebuild; a catalogue slot can't be overwritten. */
    @GameTest(template = "empty", batch = "p21d_store", timeoutTicks = 300)
    public static void buyBackRebuildAndCatalogueSlot(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        BlockPos a = new BlockPos(26000, 100, 20000);
        BlockPos other = new BlockPos(26100, 100, 20000);
        ResourceLocation home = HoleyDigsite.id("home");
        try {
            prepare(l, a);
            prepare(l, other);
            SiteDef def = SiteDefs.get(home);
            Map<Integer, ResourceLocation> layout = new HashMap<>(SiteDefs.layout(def.layout()));
            ResourceLocation deed = layout.remove(33);
            check(h, deed != null, "slot 33 is a catalogue slot in the shipped layout");
            SiteDefs.overrideLayout(def.layout(), layout);       // slot 33 is empty in this test world
            BoundingBox zone = Sites.build(l, def, a, null, false).site().zone();
            BoundingBox zoneB = Sites.build(l, def, other, null, false).site().zone();

            ShopDisplayBlockEntity free = slot(l, zone, 33);
            check(h, free != null && free.item() == null, "an empty display in the store");
            ShopDisplayBlockEntity catalogue = slot(l, zone, 1);
            check(h, catalogue.item() != null, "slot 1 shows a catalogue item");
            check(h, CustomShops.set(l, catalogue.getBlockPos(), namedSword(), 1, 5, 0) == CustomShops.Result.CATALOGUE_ITEM, "catalogue slot can't be overwritten");

            // a diamond the store buys back at 500, and a mod item whose normal value this store overrides
            CustomShops.set(l, free.getBlockPos(), new ItemStack(Items.DIAMOND), 1, 2000, 500);
            String id = CustomShops.idOf(l, free);
            check(h, id.contains(SiteData.keyOf(home, a)) && id.endsWith("/33"), "entry is keyed by site + slot: " + id);
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 3);
            PadBlockEntity padA = sellPad(l, zone), padB = sellPad(l, zoneB);
            check(h, CustomShops.stackValue(l, padA.getBlockPos(), diamonds) == 1500, "this store pays 500 each");
            check(h, CustomShops.stackValue(l, padB.getBlockPos(), diamonds) == 0, "the other store pays nothing for it");
            check(h, CustomShops.stackValue(l, h.absolutePos(new BlockPos(1, 2, 1)), diamonds) == 0, "a pad outside any store neither");
            SellPadBlock sellBlock = (SellPadBlock) ModBlocks.SELL_PAD.get();
            check(h, sellBlock.accepts(diamonds, l, padA.getBlockPos()) && !sellBlock.accepts(diamonds, l, padB.getBlockPos()), "only this store's Sell Pad accepts it");

            // the real button: sells diamonds at this store, deposits into its Money Pan
            ServerPlayer p = player(h);
            BlockPos padPos = padA.getBlockPos();
            BlockPos btnPos = null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (l.getBlockState(padPos.relative(dir)).is(ModBlocks.SHOP_BUTTON.get())) btnPos = padPos.relative(dir);
            }
            check(h, btnPos != null, "the Sell Pad has its Shop Button");
            MoneyPanBlockEntity money = null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (l.getBlockEntity(btnPos.relative(dir)) instanceof MoneyPanBlockEntity m) money = m;
            }
            long before = money.balance();
            padA.insert(diamonds.copy());
            ShopButtonBlock.press(l, btnPos, p);
            check(h, money.balance() == before + 1500, "button paid the buy-back price: " + (money.balance() - before));

            // survives a rebuild (version bump), keeps its revision
            int rev = CustomShops.find(l, id).rev();
            SiteDefs.override(def.withVersion(def.version() + 1));
            Sites.checkAll(l);
            ShopDisplayBlockEntity after = slot(l, zone, 33);
            check(h, after != null && after.custom() != null && after.custom().rev() == rev && after.custom().buyBack() == 500, "the entry is back on the rebuilt display");
            check(h, CustomShops.find(l, id) != null && CustomShops.find(l, id).rev() == rev, "world data untouched by the rebuild");

            // the layout later fills slot 33 with a catalogue item: the catalogue wins
            SiteDefs.clearOverrides();
            Sites.checkAll(l);
            ShopDisplayBlockEntity filled = slot(l, zone, 33);
            check(h, filled.item() != null && filled.custom() == null && CustomShops.find(l, id) == null, "a catalogue item replaces the custom entry");
            h.succeed();
        } finally {
            teardown(l, a, java.util.List.of());
            teardown(l, other, java.util.List.of());
        }
    }
}
