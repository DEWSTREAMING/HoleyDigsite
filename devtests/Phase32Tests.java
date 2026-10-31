package com.holeydigsite.devtest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.guide.FieldGuideDefs;
import com.holeydigsite.guide.FieldGuideManager;
import com.holeydigsite.ledger.LedgerData;
import com.holeydigsite.ledger.LedgerManager;
import com.holeydigsite.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Tests for the Field Guide pages, translations, clip/still textures, starter kit and existing-player
 * distribution, first-join auto-open, and opening from the item and Ledger button.
 */
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase32Tests {
    private static final Gson GSON = new Gson();

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) h.fail(msg);
    }

    private static ServerPlayer makeServerPlayer(GameTestHelper h, String name) {
        var level = h.getLevel();
        var server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer sp = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        sp.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
        return sp;
    }

    private static int countGuides(ServerPlayer sp) {
        int total = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack st = sp.getInventory().getItem(i);
            if (st.is(ModItems.FIELD_GUIDE.get())) {
                total += st.getCount();
            }
        }
        return total;
    }

    @GameTest(template = "empty_long", batch = "phase32")
    public static void field_guide_pages_lang_and_textures(GameTestHelper h) {
        FieldGuideDefs.reload();
        List<FieldGuideDefs.Page> pages = FieldGuideDefs.pages();
        check(h, pages.size() == 8, "Expected 8 Field Guide pages, got " + pages.size());

        List<String> expectedIds = List.of(
                "dig_and_pan", "sell", "buy", "first_line",
                "belts", "metal", "land_plots", "ledger"
        );
        for (int i = 0; i < expectedIds.size(); i++) {
            FieldGuideDefs.Page p = pages.get(i);
            check(h, p.id().equals(expectedIds.get(i)),
                    "Page " + i + " expected id " + expectedIds.get(i) + " but got " + p.id());
            check(h, p.order() == i + 1,
                    "Page " + p.id() + " expected order " + (i + 1) + " but got " + p.order());
            check(h, p.textKeys().size() >= 2 && p.textKeys().size() <= 4,
                    "Page " + p.id() + " should have 2-4 text lines, got " + p.textKeys().size());
        }

        // Load en_us.json and verify all UI and page lang keys exist
        JsonObject lang;
        try (InputStream in = Phase32Tests.class.getClassLoader()
                .getResourceAsStream("assets/holeydigsite/lang/en_us.json")) {
            check(h, in != null, "Missing assets/holeydigsite/lang/en_us.json");
            lang = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            h.fail("Failed reading en_us.json: " + e.getMessage());
            return;
        }

        for (String uiKey : List.of(
                "item.holeydigsite.field_guide",
                "tooltip.holeydigsite.field_guide",
                "screen.holeydigsite.field_guide.title",
                "screen.holeydigsite.field_guide.prev",
                "screen.holeydigsite.field_guide.next",
                "screen.holeydigsite.field_guide.page",
                "screen.holeydigsite.ledger.field_guide",
                "chat.holeydigsite.field_guide.first_join"
        )) {
            check(h, lang.has(uiKey) && !lang.get(uiKey).getAsString().isBlank(),
                    "Missing or blank lang key: " + uiKey);
        }

        for (FieldGuideDefs.Page p : pages) {
            check(h, lang.has(p.titleKey()) && !lang.get(p.titleKey()).getAsString().isBlank(),
                    "Missing title lang key for " + p.id() + ": " + p.titleKey());
            for (String tk : p.textKeys()) {
                check(h, lang.has(tk) && !lang.get(tk).getAsString().isBlank(),
                        "Missing text lang key for " + p.id() + ": " + tk);
            }

            String texPath = "assets/holeydigsite/textures/gui/field_guide/" + p.clip() + ".png";
            try (InputStream texIn = Phase32Tests.class.getClassLoader().getResourceAsStream(texPath)) {
                check(h, texIn != null, "Missing clip/still texture for " + p.id() + ": " + texPath);
                BufferedImage img = ImageIO.read(texIn);
                check(h, img != null, "Unreadable PNG for " + p.id() + ": " + texPath);
                check(h, img.getWidth() <= 4096 && img.getHeight() <= 4096,
                        "Sheet exceeds 4096x4096 for " + p.id() + ": " + img.getWidth() + "x" + img.getHeight());
                check(h, img.getWidth() == p.sheetWidth() && img.getHeight() == p.sheetHeight(),
                        "Sheet size mismatch for " + p.id() + ": image is " + img.getWidth() + "x" + img.getHeight()
                                + " but page expects " + p.sheetWidth() + "x" + p.sheetHeight());
            } catch (Exception e) {
                h.fail("Failed reading texture " + texPath + ": " + e.getMessage());
                return;
            }
        }

        // Verify screen dimensions fit 1280x720 at GUI scale 3 (426x240)
        check(h, FieldGuideDefs.WIN_W <= 426 && FieldGuideDefs.WIN_H <= 240,
                "FieldGuideScreen must fit 426x240, got " + FieldGuideDefs.WIN_W + "x" + FieldGuideDefs.WIN_H);

        // Verify frame stepping for 1-frame still vs multi-frame clip with end pause
        FieldGuideDefs.Page stillPage = pages.get(0);
        check(h, FieldGuideDefs.frameAt(stillPage, 0L) == 0, "Still at 0ms should be frame 0");
        check(h, FieldGuideDefs.frameAt(stillPage, 5500L) == 0, "Still at 5500ms should be frame 0");

        FieldGuideDefs.Page animPage = new FieldGuideDefs.Page(
                "test_anim", 1, "t", List.of("l"), "c", 10, 10, 4, 320, 180);
        check(h, FieldGuideDefs.frameAt(animPage, 0L) == 0, "Anim at 0ms should be frame 0");
        check(h, FieldGuideDefs.frameAt(animPage, 450L) == 4, "Anim at 450ms should be frame 4");
        check(h, FieldGuideDefs.frameAt(animPage, 950L) == 9, "Anim at 950ms should be frame 9");
        check(h, FieldGuideDefs.frameAt(animPage, 1500L) == 9, "Anim during 1s end pause should hold frame 9");
        check(h, FieldGuideDefs.frameAt(animPage, 2000L) == 0, "Anim after 1s end pause should loop to frame 0");

        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase32")
    public static void field_guide_first_join_and_existing_player(GameTestHelper h) {
        // 1. Brand new player joining for the first time
        ServerPlayer newPlayer = makeServerPlayer(h, "GuideNewbie");
        FieldGuideManager.resetOpenCount(newPlayer);
        LedgerData newData = LedgerData.get(newPlayer);
        check(h, !newData.starterGiven(), "Fresh player should not have starterGiven");
        check(h, !newData.starterFieldGuideGiven(), "Fresh player should not have starterFieldGuideGiven");
        check(h, !newData.fieldGuideOpened(), "Fresh player should not have fieldGuideOpened");

        LedgerManager.handlePlayerLogin(newPlayer);
        check(h, newData.starterGiven(), "First join should set starterGiven");
        check(h, newData.starterFieldGuideGiven(), "First join should set starterFieldGuideGiven");
        check(h, newData.fieldGuideOpened(), "First join should set fieldGuideOpened");
        check(h, countGuides(newPlayer) == 1, "First join should give 1 Field Guide, got " + countGuides(newPlayer));
        check(h, FieldGuideManager.openCount(newPlayer) == 1,
                "First join should auto-open Field Guide once, got " + FieldGuideManager.openCount(newPlayer));

        // 2. Second join of the same player (even if they tossed their Field Guide)
        newPlayer.getInventory().clearContent();
        LedgerManager.handlePlayerLogin(newPlayer);
        check(h, countGuides(newPlayer) == 0,
                "Second join must not give a second Field Guide, got " + countGuides(newPlayer));
        check(h, FieldGuideManager.openCount(newPlayer) == 1,
                "Second join must not auto-open Field Guide again, got " + FieldGuideManager.openCount(newPlayer));

        // 3. Existing player from an older world (starterGiven=true, starterFieldGuideGiven=false)
        ServerPlayer oldPlayer = makeServerPlayer(h, "GuideVeteran");
        FieldGuideManager.resetOpenCount(oldPlayer);
        LedgerData oldData = LedgerData.get(oldPlayer);
        oldData.setStarterGiven(true);
        oldData.setStarterBedGiven(true);
        oldData.setStarterFieldGuideGiven(false);

        LedgerManager.handlePlayerLogin(oldPlayer);
        check(h, oldData.starterFieldGuideGiven(), "Existing player login should set starterFieldGuideGiven");
        check(h, countGuides(oldPlayer) == 1,
                "Existing player should get 1 Field Guide once, got " + countGuides(oldPlayer));
        check(h, FieldGuideManager.openCount(oldPlayer) == 0,
                "Existing player login should not auto-pop first-join screen");

        // Second login of that existing player after clearing inventory does not give another one
        oldPlayer.getInventory().clearContent();
        LedgerManager.handlePlayerLogin(oldPlayer);
        check(h, countGuides(oldPlayer) == 0, "Existing player should not get a second Field Guide on later login");

        // 4. NBT round-trip and copyFrom preserve flags
        CompoundTag saved = newData.serializeNBT(h.getLevel().registryAccess());
        LedgerData loaded = new LedgerData();
        loaded.deserializeNBT(h.getLevel().registryAccess(), saved);
        check(h, loaded.starterFieldGuideGiven(), "NBT should preserve starterFieldGuideGiven");
        check(h, loaded.fieldGuideOpened(), "NBT should preserve fieldGuideOpened");

        LedgerData copied = new LedgerData();
        copied.copyFrom(loaded);
        check(h, copied.starterFieldGuideGiven(), "copyFrom should preserve starterFieldGuideGiven");
        check(h, copied.fieldGuideOpened(), "copyFrom should preserve fieldGuideOpened");

        h.succeed();
    }

    @GameTest(template = "empty_long", batch = "phase32")
    public static void field_guide_open_from_item_and_ledger(GameTestHelper h) {
        ServerPlayer sp = makeServerPlayer(h, "GuideReader");
        FieldGuideManager.resetOpenCount(sp);

        ItemStack guideStack = new ItemStack(ModItems.FIELD_GUIDE.get());
        sp.setItemInHand(InteractionHand.MAIN_HAND, guideStack);

        InteractionResultHolder<ItemStack> res = ModItems.FIELD_GUIDE.get()
                .use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        check(h, res.getResult().consumesAction(), "Field Guide right-click should consume action");
        check(h, FieldGuideManager.openCount(sp) == 1,
                "Right-clicking Field Guide item should open screen on server, count=" + FieldGuideManager.openCount(sp));

        // Opening via the Ledger button's server handler
        FieldGuideManager.openFromLedger(sp);
        check(h, FieldGuideManager.openCount(sp) == 2,
                "Ledger Field Guide button should open screen on server, count=" + FieldGuideManager.openCount(sp));

        // Client page memory per player UUID
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        check(h, FieldGuideDefs.lastPageFor(p1) == 0, "Default page should be 0");
        FieldGuideDefs.setLastPageFor(p1, 5);
        FieldGuideDefs.setLastPageFor(p2, 2);
        check(h, FieldGuideDefs.lastPageFor(p1) == 5, "Player 1 last page should be 5");
        check(h, FieldGuideDefs.lastPageFor(p2) == 2, "Player 2 last page should be 2");

        h.succeed();
    }
}
