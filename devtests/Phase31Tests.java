package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.crate.CrateBlock;
import com.holeydigsite.crate.CrateDefs;
import com.holeydigsite.crate.CrateFinds;
import com.holeydigsite.crate.CrateRolls;
import com.holeydigsite.event.EventIsland;
import com.holeydigsite.event.EventIslandData;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.skin.SkinBenchBlockEntity;
import com.holeydigsite.skin.SkinBenchNetwork;
import com.holeydigsite.skin.SkinDefs;
import com.holeydigsite.skin.SkinKitItem;
import com.holeydigsite.skin.SkinWear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Outback collection: seven skins with every wear and texture, the Black Opal's animation files, the Outback Crate's
 * contents and odds, the Fossicker title, locked finds on the event island only, kits on a Derrick drill and a long sluice.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase31Tests {
    private static final String[] NEW_SKINS = {"outback", "outback_dusty", "outback_ochre", "opal_calm", "opal", "opal_boulder", "black_opal"};
    private static final String[] WEARS = {"workshop_clean", "field_worn", "pit_scarred", "cave_battered"};

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        LedgerData.get(p).resetAll();
        LedgerData.get(p).setCratesOn(true);
        return p;
    }

    private static File skinDir() {
        File base = new File("src/main/resources/assets/holeydigsite/textures/block/skin");
        if (!base.exists()) base = new File("../src/main/resources/assets/holeydigsite/textures/block/skin");
        return base;
    }

    private static File[] pngs(File dir, boolean maps) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".png") && (maps ? n.endsWith("_s.png") : !n.endsWith("_s.png") && !n.endsWith("_n.png")));
        return files == null ? new File[0] : files;
    }

    // ------------------------------------------------------------------------------------------------ skins

    @GameTest(template = "empty")
    public static void sevenSkinsExistForBothFamiliesWithEveryWearAndTexture(GameTestHelper h) {
        Map<String, String> rarity = Map.of("outback", "common", "outback_dusty", "common", "outback_ochre", "rare", "opal_calm", "rare",
                "opal", "epic", "opal_boulder", "epic", "black_opal", "legendary");
        File base = skinDir();
        check(h, base.isDirectory(), "skin texture folder found");
        int defaults = pngs(new File("src/main/resources/assets/holeydigsite/textures/block/skin/rustic"), false).length;
        for (String id : NEW_SKINS) {
            SkinDefs.SkinDef def = SkinDefs.skin(id);
            check(h, def != null, id + " is a skin");
            check(h, def.rarity().equals(rarity.get(id)), id + " rarity " + def.rarity());
            check(h, def.appliesTo(SkinDefs.DRILL) && def.appliesTo(SkinDefs.SLUICE), id + " fits both families");
            check(h, !def.nameKey().isEmpty() && !SkinDefs.displayName(id).getString().isEmpty(), id + " has a name");
            File fresh = new File(base, id);
            check(h, pngs(fresh, false).length == 54, id + " fresh has 54 textures, got " + pngs(fresh, false).length);
            for (String wear : WEARS) {
                int n = pngs(new File(fresh, wear), false).length;
                check(h, n == 54, id + "/" + wear + " has 54 textures, got " + n);
            }
            check(h, pngs(fresh, true).length == 54, id + " has 54 specular maps");
        }
        check(h, defaults == 0 || defaults >= 54, "the default skin folder is read from the project");
        check(h, SkinDefs.skins().size() >= 12, "twelve skins in all");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blackOpalFilesAreAnimatedWithMcmeta(GameTestHelper h) throws Exception {
        File base = new File(skinDir(), "black_opal");
        List<File> dirs = new java.util.ArrayList<>();
        dirs.add(base);
        for (String wear : WEARS) dirs.add(new File(base, wear));
        for (File dir : dirs) {
            for (boolean maps : new boolean[]{false, true}) {
                for (File png : pngs(dir, maps)) {
                    File meta = new File(png.getPath() + ".mcmeta");
                    check(h, meta.exists(), png.getName() + " has a .mcmeta in " + dir.getName());
                    JsonObject o = JsonParser.parseReader(new FileReader(meta)).getAsJsonObject();
                    check(h, o.has("animation"), png.getName() + " mcmeta animates");
                    var img = javax.imageio.ImageIO.read(png);
                    check(h, img.getWidth() == 16 && img.getHeight() % 16 == 0 && img.getHeight() >= 16 * 8, png.getName() + " is a strip of 16x16 frames: " + img.getHeight());
                }
            }
        }
        // the other new skins stay still
        check(h, !new File(skinDir(), "opal/sluice_wood.png.mcmeta").exists(), "opal does not animate");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outbackKitsApplyAndComeOffOnADerrickDrillAndALongSluice(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos abs = h.absolutePos(pos);
        h.setBlock(pos, ModBlocks.SKIN_BENCH.get().defaultBlockState());
        SkinBenchBlockEntity bench = (SkinBenchBlockEntity) h.getBlockEntity(pos);
        ServerPlayer p = player(h);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
        for (String skin : NEW_SKINS) {
            for (boolean drill : new boolean[]{true, false}) {
                String family = drill ? SkinDefs.DRILL : SkinDefs.SLUICE;
                bench.takeItem();
                bench.setItem(drill ? new ItemStack(ModItems.MINING_DRILL_T3.get()) : new ItemStack(ModBlocks.SLUICE_BOX_T2.get().asItem()));
                p.getInventory().clearContent();
                p.getInventory().add(SkinKitItem.of(skin, family, "field_worn", 1));
                check(h, SkinBenchNetwork.handleApply(p, new SkinBenchNetwork.ApplySkinPayload(abs, skin, "field_worn")), skin + " applies on " + family);
                check(h, skin.equals(bench.getItem().get(ModDataComponents.SKIN.get())) && "field_worn".equals(SkinWear.of(bench.getItem())), skin + " is on the machine");
                check(h, countKits(p) == 0, "the kit was used");
                check(h, SkinBenchNetwork.handleApply(p, SkinBenchNetwork.ApplySkinPayload.remove(abs)), skin + " comes off");
                check(h, !bench.getItem().has(ModDataComponents.SKIN.get()) && countKits(p) == 1, skin + " is back as a kit");
            }
        }
        h.succeed();
    }

    private static int countKits(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (SkinKitItem.isKit(s)) n += s.getCount();
        return n;
    }

    @GameTest(template = "empty")
    public static void blackOpalScrapsLikeAnyLegendary(GameTestHelper h) {
        int[] expected = {40, 30, 20, 15, 10};
        String[] wears = {SkinWear.FRESH, "workshop_clean", "field_worn", "pit_scarred", "cave_battered"};
        for (int i = 0; i < wears.length; i++) {
            int v = SkinKitItem.scrapValue(SkinKitItem.of("black_opal", SkinDefs.DRILL, wears[i], 1));
            check(h, v == expected[i], "black opal " + wears[i] + " scraps for " + expected[i] + ", got " + v);
        }
        check(h, SkinKitItem.scrapValue(SkinKitItem.of("opal", SkinDefs.SLUICE, "field_worn", 1)) == 8, "an epic opal at field worn scraps for 8");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the crate

    @GameTest(template = "empty")
    public static void outbackCrateHoldsTheCollectionAt80_16_3_1(GameTestHelper h) {
        CrateDefs.CrateTier t = CrateDefs.tier("outback_crate");
        check(h, t != null && !t.old() && t.look().equals("outback"), "outback crate is a collection");
        check(h, Math.abs(t.percent("common") - 80) < 1e-9 && Math.abs(t.percent("rare") - 16) < 1e-9
                && Math.abs(t.percent("epic") - 3) < 1e-9 && Math.abs(t.percent("legendary") - 1) < 1e-9, "odds 80 / 16 / 3 / 1");
        check(h, ids(t, "common").equals(Set.of("outback", "outback_dusty", "holeydigsite:corrugated_iron_sheet", "holeydigsite:southern_cross_banner")), "common " + ids(t, "common"));
        check(h, ids(t, "rare").equals(Set.of("outback_ochre", "opal_calm", "fossicker", "holeydigsite:little_windmill", "holeydigsite:ute_ore_cart")), "rare " + ids(t, "rare"));
        check(h, ids(t, "epic").equals(Set.of("opal", "opal_boulder", "holeydigsite:welcome_stranger_stand")), "epic " + ids(t, "epic"));
        check(h, ids(t, "legendary").equals(Set.of("black_opal")), "legendary is the Black Opal");
        for (CrateDefs.Entry e : t.pool("common")) {
            if (e.id().endsWith("corrugated_iron_sheet")) check(h, e.amount() == 4, "four sheets as before");
        }
        h.succeed();
    }

    private static Set<String> ids(CrateDefs.CrateTier t, String rarity) {
        Set<String> out = new HashSet<>();
        for (CrateDefs.Entry e : t.pool(rarity)) out.add(e.id());
        return out;
    }

    @GameTest(template = "empty")
    public static void outbackCrateRollsOnlyItsOwnContents(GameTestHelper h) {
        CrateDefs.CrateTier t = CrateDefs.tier("outback_crate");
        Set<String> own = new HashSet<>();
        for (String r : CrateDefs.RARITIES) own.addAll(ids(t, r));
        RandomSource random = RandomSource.create(3131L);
        int n = 20000, legendary = 0;
        for (int i = 0; i < n; i++) {
            CrateRolls.Roll roll = CrateRolls.roll(t, random);
            check(h, own.contains(roll.entry().id()), "rolled something that is not in the crate: " + roll.entry().id());
            if (roll.rarity().equals("legendary")) {
                legendary++;
                check(h, roll.entry().id().equals("black_opal") && roll.entry().kind() == CrateDefs.Kind.SKIN, "the legendary is the Black Opal skin");
            }
        }
        check(h, legendary > n * 0.006 && legendary < n * 0.014, "about 1 in 100 are legendary: " + legendary);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fossickerIsGivenAndDuplicatesAsATitle(GameTestHelper h) {
        ServerPlayer p = player(h);
        CrateRolls.Roll roll = new CrateRolls.Roll("outback_crate", "rare", "rare", new CrateDefs.Entry(CrateDefs.Kind.TITLE, "fossicker", 1, 1), null);
        CrateRolls.Outcome first = CrateRolls.grant(p, roll);
        check(h, first.result() == CrateRolls.Result.NEW, "first Fossicker is a new title, got " + first.result());
        check(h, LedgerData.get(p).hasTitle("fossicker"), "the player has the title");
        CrateRolls.Outcome second = CrateRolls.grant(p, roll);
        check(h, second.result() == CrateRolls.Result.DUPLICATE && second.scrap() == 3, "a second one gives 3 Skin Scrap, got " + second.scrap());
        check(h, com.holeydigsite.ledger.LedgerDefs.crateTitles().contains("fossicker"), "listed as a crate title");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lockedOutbackCratesAreFoundOnTheEventIslandOnly(GameTestHelper h) {
        check(h, EventIsland.layout() != null, "the island layout is loaded");
        ServerPlayer p = player(h);
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        var outback = BuiltInRegistries.ITEM.get(HoleyDigsite.id("outback_crate"));
        var prospector = CrateDefs.tier("prospector_crate");
        BlockPos origin = at.offset(0, -1, 10);
        EventIslandData data = EventIslandData.get(h.getLevel());
        try {
            // the Prospector Crate would win every roll at the test rate, so leave it out of the finds here
            CrateDefs.setTierForTest(new CrateDefs.CrateTier(prospector.id(), prospector.name(), prospector.look(), false, prospector.odds(), Map.of(),
                    "", prospector.pools()));
            CrateFinds.setTestOneIn(1);
            int outside = 0;
            for (String source : CrateDefs.FIND_SOURCES) {
                if (CrateFinds.roll(h.getLevel(), at, source, p).getItem() == outback) outside++;
            }
            check(h, outside == 0, "no Outback Crate found away from the event island");
            data.put(new EventIslandData.Island(origin, "outback"));
            check(h, EventIsland.islandAt(h.getLevel(), at) != null, "the test spot is on the island");
            for (String source : CrateDefs.FIND_SOURCES) {
                ItemStack found = CrateFinds.roll(h.getLevel(), at, source, p);
                check(h, found.getItem() == outback && CrateBlock.isLocked(found), "a locked Outback Crate from the " + source + " on the island");
            }
            // the switch off finds nothing, even there
            LedgerData.get(p).setCratesOn(false);
            for (String source : CrateDefs.FIND_SOURCES) {
                check(h, CrateFinds.roll(h.getLevel(), at, source, p).isEmpty(), "switch off: nothing from the " + source);
            }
        } finally {
            CrateFinds.setTestOneIn(0);
            data.remove(origin);
            CrateDefs.reloadBuiltin();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void boughtOutbackCratesComeUnlockedAndCutAsAnyCrate(GameTestHelper h) throws Exception {
        ItemStack bought = new ItemStack(ModItems.OUTBACK_CRATE.get());
        check(h, !CrateBlock.isLocked(bought), "a crate from the shop is not locked");
        File shop = new File("src/main/resources/data/holeydigsite/holeydigsite/shop_items/outback_crate.json");
        if (!shop.exists()) shop = new File("../src/main/resources/data/holeydigsite/holeydigsite/shop_items/outback_crate.json");
        JsonObject o = JsonParser.parseReader(new FileReader(shop)).getAsJsonObject();
        check(h, !o.has("components") && o.get("chips").getAsInt() == 90, "bought with 90 Opal Chips, no lock component");
        // a locked one needs cutters, and the padlock state exists on the block
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, ModBlocks.OUTBACK_CRATE.get().defaultBlockState().setValue(CrateBlock.LOCKED, true));
        ServerPlayer p = player(h);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(pos)),
                net.minecraft.core.Direction.UP, h.absolutePos(pos), false);
        var st = h.getBlockState(pos);
        st.useWithoutItem(h.getLevel(), p, hit);
        check(h, !h.getBlockState(pos).getValue(CrateBlock.OPEN), "a locked Outback Crate does not open by hand");
        ItemStack cutters = new ItemStack(ModItems.BOLT_CUTTERS.get(), 2);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, cutters);
        st.useItemOn(cutters, h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        check(h, h.getBlockState(pos).getValue(CrateBlock.OPEN) && cutters.getCount() == 1, "cutters open it and one pair is used");
        h.succeed();
    }
}
