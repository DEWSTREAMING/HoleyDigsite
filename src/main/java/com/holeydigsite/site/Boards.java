package com.holeydigsite.site;

import com.holeydigsite.HoleyDigsite;
import com.holeydigsite.contract.ContractBoardBlock;
import com.holeydigsite.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Contract Boards in a site's template. A new build gets them with the structure; an existing store (any older version) gets
 * the board placed into air at its template spot on load. Anything that is not air at the spot is left alone and reported.
 */
public final class Boards {
    private Boards() {}

    public enum State { PLACED, THERE, BLOCKED }

    public record Spot(BlockPos pos, State state) {}

    /** Places missing boards of the site's template into air and returns what became of each spot. */
    public static List<Spot> ensure(ServerLevel level, SiteData.Site site, SiteDef def) {
        List<Spot> spots = new ArrayList<>();
        if (def.structure() == null) {
            return spots;
        }
        Optional<StructureTemplate> template = level.getStructureManager().get(def.structure());
        if (template.isEmpty()) {
            return spots;
        }
        for (StructureTemplate.StructureBlockInfo info : template.get().filterBlocks(site.anchor(), new StructurePlaceSettings(),
                ModBlocks.CONTRACT_BOARD.get())) {
            BlockPos pos = info.pos();
            if (!site.zone().isInside(pos)) {
                continue;
            }
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() instanceof ContractBoardBlock) {
                spots.add(new Spot(pos, State.THERE));
            } else if (now.isAir()) {
                level.setBlock(pos, info.state(), Block.UPDATE_ALL);
                spots.add(new Spot(pos, State.PLACED));
            } else {
                spots.add(new Spot(pos, State.BLOCKED));
            }
        }
        return spots;
    }

    /** Tells everyone (and players who log in soon) that a board spot is taken. */
    public static void announceBlocked(ServerLevel level, List<Spot> spots) {
        for (Spot s : spots) {
            if (s.state() != State.BLOCKED) {
                continue;
            }
            HoleyDigsite.LOGGER.warn("No room for the Contract Board at {}", s.pos());
            Component msg = Component.translatable("chat.holeydigsite.board.blocked").withStyle(ChatFormatting.GOLD);
            SiteData.Announcement a = SiteData.get(level).announce(msg, level.getGameTime());
            if (level.getServer() != null) {
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    p.sendSystemMessage(msg);
                    p.getPersistentData().putInt(SiteEvents.SEEN, Math.max(p.getPersistentData().getInt(SiteEvents.SEEN), a.serial()));
                }
            }
            return;
        }
    }
}
