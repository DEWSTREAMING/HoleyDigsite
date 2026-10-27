package com.holeydigsite.devtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.economy.Selling;
import com.holeydigsite.item.BlueprintItem;
import com.holeydigsite.item.CoinItem;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.shop.MoneyPanBlockEntity;
import com.holeydigsite.shop.PadBlockEntity;
import com.holeydigsite.shop.ShopButtonBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The Platinum Coin, Ruby Crown and Mint Bar: values, exchange, payouts, Money Pan, shop and money text. */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase234Tests {
    private static final BlockPos BTN = new BlockPos(4, 1, 2);
    private static final BlockPos SELL = new BlockPos(3, 1, 2);
    private static final BlockPos BUY = new BlockPos(5, 1, 2);
    private static final BlockPos PAN = new BlockPos(4, 1, 3);

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static void shop(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        h.setBlock(BTN, ModBlocks.SHOP_BUTTON.get());
        h.setBlock(SELL, ModBlocks.SELL_PAD.get());
        h.setBlock(BUY, ModBlocks.BUY_PAD.get());
        h.setBlock(PAN, ModBlocks.MONEY_PAN.get());
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        return p;
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Right-clicks a block with a stack in the main hand. */
    private static void use(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(false);
        BlockPos abs = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static void deposit(GameTestHelper h, ServerPlayer p, ItemStack coins) {
        MoneyPanBlock.depositHeld(h.getLevel(), h.absolutePos(PAN), p, coins);
    }

    private static void withdraw(GameTestHelper h, ServerPlayer p) {
        MoneyPanBlock.withdraw(h.getLevel(), h.absolutePos(PAN), p);
    }

    private static MoneyPanBlockEntity pan(GameTestHelper h) {
        return (MoneyPanBlockEntity) h.getBlockEntity(PAN);
    }

    private static JsonObject lang;

    /** The English text of a money component, read from the lang file (a server has no mod lang). */
    private static String english(Component c) {
        try {
            if (lang == null) {
                try (InputStream in = HoleyDigsite.class.getResourceAsStream("/assets/holeydigsite/lang/en_us.json")) {
                    lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        List<String> out = new ArrayList<>();
        for (Component sib : c.getSiblings()) {
            if (sib.getContents() instanceof TranslatableContents t) {
                out.add(String.format(lang.get(t.getKey()).getAsString().replace("%s", "%1$s"), t.getArgs()));
            }
        }
        return String.join(" ", out);
    }

    private static final String[][] LADDER = {
            {"copper_coin", "silver_coin"}, {"silver_coin", "gold_coin"}, {"gold_coin", "platinum_coin"},
            {"platinum_coin", "ruby_crown"}, {"ruby_crown", "mint_bar"}};

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(HoleyDigsite.MODID, id));
    }

    @GameTest(template = "empty")
    public static void coinValuesFollowTheLadder(GameTestHelper h) {
        check(h, ((CoinItem) ModItems.PLATINUM_COIN.get()).getValue() == 512, "platinum 512");
        check(h, ((CoinItem) ModItems.RUBY_CROWN.get()).getValue() == 4096, "crown 4096");
        check(h, ((CoinItem) ModItems.MINT_BAR.get()).getValue() == 32768, "bar 32768");
        for (String[] step : LADDER) {
            check(h, ((CoinItem) item(step[1])).getValue() == 8 * ((CoinItem) item(step[0])).getValue(), step[1] + " is 8 " + step[0]);
        }
        check(h, ModItems.MINT_BAR.get().getDefaultInstance().getMaxStackSize() == 64, "stacks to 64");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void newCoinsExchangeBothWays(GameTestHelper h) {
        var level = h.getLevel();
        for (String[] step : LADDER) {
            Item lo = item(step[0]), hi = item(step[1]);
            List<ItemStack> eight = new ArrayList<>();
            for (int i = 0; i < 8; i++) eight.add(new ItemStack(lo));
            eight.add(ItemStack.EMPTY);
            CraftingInput up = CraftingInput.of(3, 3, eight);
            var upRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, up, level);
            check(h, upRecipe.isPresent(), "8 " + step[0] + " craft something");
            ItemStack upOut = upRecipe.get().value().assemble(up, level.registryAccess());
            check(h, upOut.is(hi) && upOut.getCount() == 1, "8 " + step[0] + " -> 1 " + step[1]);
            CraftingInput down = CraftingInput.of(1, 1, List.of(new ItemStack(hi)));
            var downRecipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, down, level);
            check(h, downRecipe.isPresent(), "1 " + step[1] + " crafts something");
            ItemStack downOut = downRecipe.get().value().assemble(down, level.registryAccess());
            check(h, downOut.is(lo) && downOut.getCount() == 8, "1 " + step[1] + " -> 8 " + step[0]);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void payoutsUseTheBiggestCoins(GameTestHelper h) {
        List<ItemStack> sand = Selling.coins(288_000);
        check(h, sand.size() == 4, "4 stacks, got " + sand.size());
        check(h, sand.get(0).is(ModItems.MINT_BAR.get()) && sand.get(0).getCount() == 8, "8 bars");
        check(h, sand.get(1).is(ModItems.RUBY_CROWN.get()) && sand.get(1).getCount() == 6, "6 crowns");
        check(h, sand.get(2).is(ModItems.PLATINUM_COIN.get()) && sand.get(2).getCount() == 2, "2 platinum");
        check(h, sand.get(3).is(ModItems.GOLD_COIN.get()) && sand.get(3).getCount() == 4, "4 gold");
        check(h, Selling.coins(0).isEmpty(), "nothing for nothing");
        long odd = 3 * 32768L + 5 * 4096 + 7 * 512 + 3 * 64 + 5 * 8 + 6;
        long sum = 0;
        for (ItemStack s : Selling.coins(odd)) sum += MoneyPanBlock.coinValue(s);
        check(h, sum == odd, "paying " + odd + " adds up, got " + sum);
        List<ItemStack> many = Selling.coins(70 * 32768L);
        check(h, many.size() == 2 && many.get(0).getCount() == 64 && many.get(1).getCount() == 6, "bars in stacks of 64");
        check(h, Selling.split(160)[0] == 2, "the old three-way split still works");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void moneyPanTakesAndPaysTheNewCoins(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        deposit(h, p, new ItemStack(ModItems.MINT_BAR.get(), 2));
        check(h, pan(h).balance() == 65_536, "2 bars, got " + pan(h).balance());
        deposit(h, p, new ItemStack(ModItems.RUBY_CROWN.get(), 3));
        deposit(h, p, new ItemStack(ModItems.PLATINUM_COIN.get(), 5));
        check(h, pan(h).balance() == 65_536 + 3 * 4096 + 5 * 512, "bars, crowns, platinum, got " + pan(h).balance());
        withdraw(h, p);
        check(h, count(p, ModItems.MINT_BAR.get()) == 2 && count(p, ModItems.RUBY_CROWN.get()) == 3
                && count(p, ModItems.PLATINUM_COIN.get()) == 5 && count(p, ModItems.GOLD_COIN.get()) == 0, "withdrawn as 2/3/5");
        check(h, pan(h).balance() == 0, "balance 0");
        p.getInventory().clearContent();
        pan(h).deposit(288_000);
        withdraw(h, p);
        check(h, count(p, ModItems.MINT_BAR.get()) == 8 && count(p, ModItems.RUBY_CROWN.get()) == 6 && count(p, ModItems.PLATINUM_COIN.get()) == 2
                && count(p, ModItems.GOLD_COIN.get()) == 4, "Sand Dunes deed price paid out in big coins");
        // right-click with a bar in hand and sneak deposit of the whole inventory work through the block too
        p.getInventory().clearContent();
        use(h, p, PAN, new ItemStack(ModItems.RUBY_CROWN.get(), 2));
        check(h, pan(h).balance() == 8192, "right click with crowns, got " + pan(h).balance());
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void mintBarBuysAndGivesChange(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        deposit(h, p, new ItemStack(ModItems.MINT_BAR.get()));
        ((PadBlockEntity) h.getBlockEntity(BUY)).insert(BlueprintItem.of(ModItems.MINING_DRILL.get(), 1));
        BlockState st = h.getBlockState(BTN);
        if (st.hasProperty(ShopButtonBlock.PRESSED) && st.getValue(ShopButtonBlock.PRESSED)) h.setBlock(BTN, st.setValue(ShopButtonBlock.PRESSED, false));
        use(h, p, BTN, ItemStack.EMPTY);
        check(h, count(p, ModItems.MINING_DRILL.get()) == 1, "the drill was bought");
        check(h, pan(h).balance() == 32_768 - 2048, "change stays in the pan, got " + pan(h).balance());
        p.getInventory().clearContent();
        withdraw(h, p);
        check(h, count(p, ModItems.RUBY_CROWN.get()) == 7 && count(p, ModItems.PLATINUM_COIN.get()) == 4
                && count(p, ModItems.MINT_BAR.get()) == 0, "change comes out as 7 crowns 4 platinum");
        h.succeed();
    }

    @GameTest(template = "empty_long")
    public static void sellPadRefusesEveryCoin(GameTestHelper h) {
        shop(h);
        ServerPlayer p = player(h);
        var reg = h.getLevel().registryAccess();
        for (Item coin : List.of(ModItems.COPPER_COIN.get(), ModItems.SILVER_COIN.get(), ModItems.GOLD_COIN.get(),
                ModItems.PLATINUM_COIN.get(), ModItems.RUBY_CROWN.get(), ModItems.MINT_BAR.get())) {
            check(h, !Selling.isSellable(new ItemStack(coin), reg), coin + " is not sellable");
            use(h, p, SELL, new ItemStack(coin, 3));
            check(h, ((PadBlockEntity) h.getBlockEntity(SELL)).isEmpty() && p.getMainHandItem().getCount() == 3, coin + " refused, kept in hand");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void moneyTextShowsTheBigCoins(GameTestHelper h) {
        check(h, english(Selling.format(288_000)).equals("8 bars 6 crowns 2 platinum 4 gold (4,500 gold)"), "288000: " + english(Selling.format(288_000)));
        check(h, english(Selling.format(32_768)).equals("1 bar (512 gold)"), "one bar: " + english(Selling.format(32_768)));
        check(h, english(Selling.format(4096 + 64)).equals("1 crown 1 gold (65 gold)"), "crown: " + english(Selling.format(4096 + 64)));
        check(h, english(Selling.format(2 * 512 + 8)).equals("2 platinum 1 silver (16 gold)"), "platinum: " + english(Selling.format(2 * 512 + 8)));
        check(h, english(Selling.format(511)).equals("7 gold 7 silver 7 copper"), "no total below platinum: " + english(Selling.format(511)));
        check(h, english(Selling.format(0)).equals("0 copper"), "zero");
        check(h, english(Selling.format(70)).equals("1 gold 6 copper"), "small amounts as before: " + english(Selling.format(70)));
        long worst = 7 * 32768L + 7 * 4096 + 7 * 512 + 7 * 64 + 7 * 8 + 7;
        check(h, english(Selling.format(worst, 52)).equals("7 bars 7 crowns 7 platinum 7 gold 7 silver 7 copper"),
                "total dropped when it does not fit: " + english(Selling.format(worst, 52)));
        check(h, english(Selling.format(288_000, 46)).equals("8 bars 6 crowns 2 platinum 4 gold (4,500 gold)"), "kept when it fits");
        check(h, Selling.textLength(worst, false) == english(Selling.format(worst, 10)).length(), "length estimate matches the text");
        h.succeed();
    }
}
