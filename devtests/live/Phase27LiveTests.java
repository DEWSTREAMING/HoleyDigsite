package com.holeydigsite.devtest;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.plot.PlotData;
import com.holeydigsite.plot.PlotLock;
import com.holeydigsite.registry.ModBlocks;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.shop.MoneyPanBlock;
import com.holeydigsite.team.TeamAccess;
import com.mojang.authlib.GameProfile;
import de.melanx.skyblockbuilder.data.SkyblockSavedData;
import de.melanx.skyblockbuilder.data.Team;
import de.melanx.skyblockbuilder.util.WorldUtil;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import io.netty.channel.embedded.EmbeddedChannel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Live check with the real team mods (run with -PteamsRuntime and forceSkyblockCheck; not part of the normal suite).
 * Bots log in through the real player list, so Skyblock Builder's and FTB Teams' own login code runs; parties are
 * made, joined, left, kicked and disbanded through FTB Teams' own party code.
 */
@SuppressWarnings("removal")
@GameTestHolder(HoleyDigsite.MODID)
@PrefixGameTestTemplate(false)
public class Phase27LiveTests {
    /** Dedicated dev server only (GameTest servers have no profile cache, which Skyblock Builder needs): start the test. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = HoleyDigsite.MODID)
    public static final class Starter {
        @net.neoforged.bus.api.SubscribeEvent
        public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
            if (e.getServer().isDedicatedServer()) {
                e.getServer().getCommands().performPrefixedCommand(e.getServer().createCommandSourceStack(), "test runall");
            }
        }
    }

    private static void check(GameTestHelper h, boolean cond, String msg) {
        if (!cond) {
            HoleyDigsite.LOGGER.error("LIVE27 FAILED: {}", msg);
            h.getLevel().getServer().halt(false);
            h.fail(msg);
        }
        HoleyDigsite.LOGGER.info("LIVE27 ok: {}", msg);
    }

    private static ServerPlayer bot(MinecraftServer server, GameProfile profile) {
        ServerLevel level = server.overworld();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        // a bot has no client to agree on mod channels with: let it "hear" every play payload
        try {
            java.lang.reflect.Field f = net.neoforged.neoforge.network.registration.NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
            f.setAccessible(true);
            java.util.Map<?, ?> all = (java.util.Map<?, ?>) f.get(null);
            java.util.Map<?, ?> play = (java.util.Map<?, ?>) all.get(net.minecraft.network.ConnectionProtocol.PLAY);
            for (Object id : play.keySet()) {
                net.neoforged.neoforge.network.registration.ChannelAttributes.getOrCreateAdHocChannels(c).add((net.minecraft.resources.ResourceLocation) id);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        server.getPlayerList().placeNewPlayer(c, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static SkyblockSavedData sbb(MinecraftServer s) {
        return SkyblockSavedData.get(WorldUtil.getConfiguredLevel(s));
    }

    private static UUID islandOf(MinecraftServer s, UUID p) {
        Team t = sbb(s).getTeamFromPlayer(p);
        if (t != null) return t.getId();
        return sbb(s).getSpawn().hasPlayer(p) ? SkyblockSavedData.SPAWN_ID : null;
    }

    private static PartyTeam partyOf(UUID p) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(p).filter(t -> t.isPartyTeam()).map(t -> (PartyTeam) t).orElse(null);
    }

    private static boolean near(ServerPlayer p, UUID island, MinecraftServer s) {
        BlockPos c = sbb(s).getTeam(island).getIsland().getCenter();
        return Math.abs(p.getX() - c.getX()) < 200 && Math.abs(p.getZ() - c.getZ()) < 200;
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void partiesAndIslandsLive(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        ServerPlayer alice = bot(s, new GameProfile(UUID.randomUUID(), "Alice"));
        h.runAfterDelay(10, () -> {
            ServerPlayer bob = bot(s, new GameProfile(UUID.randomUUID(), "Bob"));
            h.runAfterDelay(10, () -> {
                ServerPlayer carol = bot(s, new GameProfile(UUID.randomUUID(), "Carol"));
                h.runAfterDelay(10, () -> step2(h, s, alice, bob, carol));
            });
        });
    }

    private static void step2(GameTestHelper h, MinecraftServer s, ServerPlayer alice, ServerPlayer bob, ServerPlayer carol) {
        UUID spawn = SkyblockSavedData.SPAWN_ID;
        check(h, spawn.equals(islandOf(s, alice.getUUID())), "Alice (first) is on the spawn island");
        UUID bobOwn = islandOf(s, bob.getUUID()), carolOwn = islandOf(s, carol.getUUID());
        check(h, bobOwn != null && !bobOwn.equals(spawn) && carolOwn != null && !carolOwn.equals(spawn) && !bobOwn.equals(carolOwn),
                "Bob and Carol got islands of their own: " + bobOwn + " " + carolOwn);
        check(h, near(bob, bobOwn, s) && near(carol, carolOwn, s), "Bob and Carol are standing on them");
        try {
            FTBTeamsAPI.api().getManager().createPartyTeam(alice, "Diggers", "", Color4I.rgb(0xC8A040));
            PartyTeam party = partyOf(alice.getUUID());
            check(h, party != null && spawn.equals(islandOf(s, alice.getUUID())), "Alice made a party and stays on her island");
            party.invite(alice, List.of(bob.getGameProfile()));
            party.join(bob);
        } catch (Exception e) {
            h.fail("party: " + e);
        }
        h.runAfterDelay(5, () -> step3(h, s, alice, bob, carol, bobOwn));
    }

    private static void step3(GameTestHelper h, MinecraftServer s, ServerPlayer alice, ServerPlayer bob, ServerPlayer carol, UUID bobOwn) {
        UUID spawn = SkyblockSavedData.SPAWN_ID;
        PartyTeam party = partyOf(alice.getUUID());
        check(h, party != null && party.getMembers().contains(bob.getUUID()), "Bob is in Alice's FTB party");
        check(h, spawn.equals(islandOf(s, bob.getUUID())) && sbb(s).getSpawn().hasPlayer(bob.getUUID()), "Bob is in Alice's Skyblock Builder team");
        check(h, near(bob, spawn, s), "Bob was sent to Alice's island");
        check(h, sbb(s).getTeam(bobOwn) != null, "Bob's old island is still there");
        // a bought plot on Alice's island
        ServerLevel level = WorldUtil.getConfiguredLevel(s);
        BlockPos c = sbb(s).getSpawn().getIsland().getCenter();
        BlockPos board = c.offset(30, 0, 30), in = c.offset(33, 0, 33);
        PlotData d = PlotData.get(level);
        d.register("live27", board, new BoundingBox(board.getX(), board.getY() - 2, board.getZ(), board.getX() + 6, board.getY() + 4, board.getZ() + 6));
        d.setOwned("live27", board, true, alice.getGameProfile().getName(), alice.getUUID());
        level.setBlock(in, Blocks.DIRT.defaultBlockState(), 3);
        check(h, TeamAccess.get().islandTeamAt(level, in) != null && spawn.equals(TeamAccess.get().islandTeamAt(level, in)), "the plot is on the spawn team's island");
        check(h, PlotLock.access(alice, level, in) == PlotLock.Access.OK && PlotLock.access(bob, level, in) == PlotLock.Access.OK, "Alice and Bob may use the plot");
        check(h, PlotLock.access(carol, level, in) == PlotLock.Access.OTHER_TEAM, "Carol may not");
        carol.gameMode.destroyBlock(in);
        check(h, level.getBlockState(in).is(Blocks.DIRT), "Carol's break is refused");
        bob.gameMode.destroyBlock(in);
        check(h, level.getBlockState(in).isAir(), "Bob breaks it");
        BlockPos pan = c.offset(36, 0, 30);
        level.setBlock(pan, ModBlocks.MONEY_PAN.get().defaultBlockState(), 3);
        MoneyPanBlock.depositHeld(level, pan, carol, new ItemStack(ModItems.COPPER_COIN.get(), 7));
        check(h, MoneyPanBlock.withdraw(level, pan, carol) == 0, "Carol can pay in but not take");
        check(h, MoneyPanBlock.withdraw(level, pan, bob) == 7, "Bob takes the coins");
        d.remove("live27", board);
        try {
            party.leave(bob.getUUID());
        } catch (Exception e) {
            h.fail("leave: " + e);
        }
        h.runAfterDelay(5, () -> step4(h, s, alice, bob, carol, bobOwn));
    }

    private static void step4(GameTestHelper h, MinecraftServer s, ServerPlayer alice, ServerPlayer bob, ServerPlayer carol, UUID bobOwn) {
        UUID spawn = SkyblockSavedData.SPAWN_ID;
        UUID bobNow = islandOf(s, bob.getUUID());
        check(h, partyOf(bob.getUUID()) == null && bobNow != null && !bobNow.equals(spawn), "Bob left: own island " + bobNow);
        check(h, near(bob, bobNow, s), "Bob was sent there");
        check(h, spawn.equals(islandOf(s, alice.getUUID())), "Alice keeps her island");
        PartyTeam party = partyOf(alice.getUUID());
        try {
            party.invite(alice, List.of(carol.getGameProfile()));
            party.join(carol);
        } catch (Exception e) {
            h.fail("carol join: " + e);
        }
        h.runAfterDelay(5, () -> {
            check(h, spawn.equals(islandOf(s, carol.getUUID())), "Carol joined: on Alice's island");
            try {
                party.kick(alice.createCommandSourceStack(), List.of(carol.getGameProfile()));
            } catch (Exception e) {
                h.fail("kick: " + e);
            }
            h.runAfterDelay(5, () -> step5(h, s, alice, bob, carol));
        });
    }

    private static void step5(GameTestHelper h, MinecraftServer s, ServerPlayer alice, ServerPlayer bob, ServerPlayer carol) {
        UUID spawn = SkyblockSavedData.SPAWN_ID;
        UUID carolNow = islandOf(s, carol.getUUID());
        check(h, partyOf(carol.getUUID()) == null && carolNow != null && !carolNow.equals(spawn), "Carol kicked: own island");
        // Bob rejoins, goes offline, is kicked while away, logs back in
        PartyTeam party = partyOf(alice.getUUID());
        GameProfile bobProfile = bob.getGameProfile();
        try {
            party.invite(alice, List.of(bobProfile));
            party.join(bob);
        } catch (Exception e) {
            h.fail("bob rejoin: " + e);
        }
        h.runAfterDelay(5, () -> {
            check(h, spawn.equals(islandOf(s, bob.getUUID())), "Bob rejoined");
            s.getPlayerList().remove(bob);
            try {
                party.kick(alice.createCommandSourceStack(), List.of(bobProfile));
            } catch (Exception e) {
                h.fail("offline kick: " + e);
            }
            UUID offlineIsland = islandOf(s, bobProfile.getId());
            check(h, offlineIsland != null && !offlineIsland.equals(spawn), "offline kick: island changed while away");
            ServerPlayer bob2 = bot(s, bobProfile);
            h.runAfterDelay(5, () -> {
                check(h, near(bob2, offlineIsland, s), "Bob is sent to his island on login");
                try {
                    party.invite(alice, List.of(carol.getGameProfile()));
                    party.join(carol);
                } catch (Exception e) {
                    h.fail("carol rejoin: " + e);
                }
                h.runAfterDelay(5, () -> {
                    try {
                        party.forceDisband(s.createCommandSourceStack());
                    } catch (Exception e) {
                        h.fail("disband: " + e);
                    }
                    h.runAfterDelay(5, () -> {
                        UUID c = islandOf(s, carol.getUUID());
                        check(h, partyOf(alice.getUUID()) == null && spawn.equals(islandOf(s, alice.getUUID())), "disband: Alice keeps her island");
                        check(h, c != null && !c.equals(spawn), "disband: Carol has her own island");
                        h.succeed();
                        HoleyDigsite.LOGGER.info("LIVE27 DONE: all checks passed");
                        s.halt(false);
                    });
                });
            });
        });
    }
}
