package com.holeydigsite.tier;

import com.holeydigsite.block.LargePanBlock;
import com.holeydigsite.block.PanBlock;
import com.holeydigsite.belt.ClumpBeltBlock;
import com.holeydigsite.belt.DownInclineBeltBlock;
import com.holeydigsite.belt.InclineBeltBlock;
import com.holeydigsite.drill.MiningDrillBlock;
import com.holeydigsite.forge.CastingMoldBlock;
import com.holeydigsite.forge.CrucibleBlock;
import com.holeydigsite.forge.ForgeBlock;
import com.holeydigsite.forge.HotForgeBlock;
import com.holeydigsite.forge.LargeCrucibleBlock;
import com.holeydigsite.forge.MoldBlock;
import com.holeydigsite.item.BrushItem;
import com.holeydigsite.item.PanBlockItem;
import com.holeydigsite.sluice.LongSluiceBlock;
import com.holeydigsite.sluice.SluiceBoxBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Which Java class a tier's {@code kind} builds. THE place to touch when a family gets a new behaviour class; a new tier of an
 * existing kind needs nothing here. Stats come from the tier's {@code stats} object, never from constants.
 */
final class TierKinds {
    private TierKinds() {}

    static Block createBlock(TierDef d) {
        BlockBehaviour.Properties p = d.blockProperties(lightWhen(d.family()));
        return switch (d.family()) {
            case "mining_drill" -> new MiningDrillBlock(p, d.stat("cycle_ticks"));
            case "sluice_box" -> {
                SluiceBoxBlock.Stats stats = new SluiceBoxBlock.Stats(d.stat("cycle_ticks"), d.stat("max_queue"), d.stat("tray_slots"));
                yield "long".equals(d.kind()) ? new LongSluiceBlock(p, stats) : new SluiceBoxBlock(p, stats);
            }
            case "pan" -> switch (d.kind()) {
                case "large" -> new LargePanBlock(p);
                default -> new PanBlock(p);
            };
            case "clump_belt" -> new ClumpBeltBlock(p, d.stat("speed"));
            case "incline_belt" -> new InclineBeltBlock(p, d.stat("speed"));
            case "down_incline_belt" -> new DownInclineBeltBlock(p, d.stat("speed"));
            case "crucible" -> switch (d.kind()) {
                case "large" -> new LargeCrucibleBlock(p, d.stat("capacity"), d.stat("ticks_per_gram"));
                default -> new CrucibleBlock(p, d.stat("capacity"), d.stat("ticks_per_gram"));
            };
            case "forge" -> switch (d.kind()) {
                case "hot" -> new HotForgeBlock(p, d.stat("heat"));
                default -> new ForgeBlock(p, d.stat("heat"));
            };
            case "mold" -> switch (d.kind()) {
                case "casting" -> new CastingMoldBlock(p, d.stat("cool_ticks"), d.statArray("targets", null));
                default -> new MoldBlock(p, d.stat("cool_ticks"), d.statArray("targets", null));
            };
            default -> throw new IllegalStateException("tier family '" + d.family() + "' has no block kind in TierKinds");
        };
    }

    static Item createItem(TierDef d, Block block) {
        Item.Properties p = d.itemProperties();
        return switch (d.family()) {
            case "brush" -> new BrushItem(p, d.stat("cycle_ticks"));
            case "pan" -> new PanBlockItem((PanBlock) block, p);
            default -> new BlockItem(block, p);
        };
    }

    /** The "active" block states of a family that emit the tier's {@code light} level. */
    private static Predicate<BlockState> lightWhen(String family) {
        return switch (family) {
            case "forge" -> s -> s.getValue(ForgeBlock.LIT);
            case "crucible" -> s -> s.getValue(CrucibleBlock.FILL) > 0;
            case "mold" -> s -> s.getValue(MoldBlock.LEVEL) > 0 && !s.getValue(MoldBlock.SOLID);
            default -> null;
        };
    }

}
