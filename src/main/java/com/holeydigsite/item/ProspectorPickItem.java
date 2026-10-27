package com.holeydigsite.item;

import com.holeydigsite.registry.ModDataComponents;
import com.holeydigsite.registry.ModItems;
import com.holeydigsite.tier.DirtTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Prospector's Pick from the Deep Dig kiosk. Right-click the ground to find the nearest buried rock within
 * {@link #RANGE} blocks, showing particles at the rock, a sound, and an action-bar line.
 * With a Sand Head it finds Sandrock, with a Gravel Head Gravelrock, with no head Deep/Abyssal Dirtrock.
 */
public class ProspectorPickItem extends Item {
    public static final int RANGE = 12;
    public static final int COOLDOWN_TICKS = 20;

    public ProspectorPickItem(Properties properties) {
        super(properties);
    }

    /** A single scan result containing the position, rock kind, and offset from the scan origin. */
    public record Find(BlockPos pos, int kind, int dx, int dy, int dz) {
        public double distance() {
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** The head item fitted on this pick, or null if none. */
    @Nullable
    public static Item getHead(ItemStack stack) {
        return stack.get(ModDataComponents.PICK_HEAD.get());
    }

    /** Sets or removes the head item fitted on this pick. */
    public static void setHead(ItemStack stack, @Nullable Item head) {
        if (head == null) {
            stack.remove(ModDataComponents.PICK_HEAD.get());
        } else {
            stack.set(ModDataComponents.PICK_HEAD.get(), head);
        }
    }

    /** True if the stack is a valid pick head. */
    public static boolean isHead(ItemStack stack) {
        return stack.getItem() instanceof PickHeadItem;
    }

    /** Gives an item stack to the player inventory or drops it at their feet if full. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Fits a head stack onto the pick, returning any old head to the player. */
    public static boolean fitHead(Player player, ItemStack pick, ItemStack headStack) {
        if (!isHead(headStack)) {
            return false;
        }
        Item oldHead = getHead(pick);
        Item newHead = headStack.getItem();
        setHead(pick, newHead);
        headStack.shrink(1);
        if (oldHead != null) {
            giveOrDrop(player, new ItemStack(oldHead));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** Detaches the fitted head from the pick and returns it to the player. */
    public static boolean detachHead(Player player, ItemStack pick) {
        Item oldHead = getHead(pick);
        if (oldHead == null) {
            return false;
        }
        setHead(pick, null);
        giveOrDrop(player, new ItemStack(oldHead));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** Finds the nearest rock within {@link #RANGE} blocks of the origin for the default (no head) pick. */
    @Nullable
    public static Find scan(Level level, BlockPos origin) {
        return scan(level, origin, null, RANGE);
    }

    /** Finds the nearest rock within {@link #RANGE} blocks of the origin for a pick with the given head. */
    @Nullable
    public static Find scan(Level level, BlockPos origin, @Nullable Item head) {
        return scan(level, origin, head, RANGE);
    }

    /** Finds the nearest matching rock within {@code range} blocks of the origin. */
    @Nullable
    public static Find scan(Level level, BlockPos origin, @Nullable Item head, int range) {
        Find best = null;
        long bestSq = (long) range * range + 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    long sq = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (sq >= bestSq) {
                        continue;
                    }
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    int kind = matchRock(s, head);
                    if (kind != 0) {
                        bestSq = sq;
                        best = new Find(p.immutable(), kind, dx, dy, dz);
                    }
                }
            }
        }
        return best;
    }

    private static int matchRock(BlockState s, @Nullable Item head) {
        if (head instanceof PickHeadItem ph) {
            if (ph.rockKind() == DirtTier.SAND) {
                return s.is(DirtTier.ROCK_SAND) ? DirtTier.SAND : 0;
            }
            if (ph.rockKind() == DirtTier.GRAVEL) {
                return s.is(DirtTier.ROCK_GRAVEL) ? DirtTier.GRAVEL : 0;
            }
        }
        return s.is(DirtTier.ROCK_TIER_3) ? 3 : s.is(DirtTier.ROCK_TIER_2) ? 2 : 0;
    }

    private static final String[] COMPASS = {"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"};

    /** Builds direction strings like down-east or north-west from language entries. */
    public static Component direction(Find f) {
        int horizontal = Math.max(Math.abs(f.dx()), Math.abs(f.dz()));
        String vertical = f.dy() <= -2 ? "down" : f.dy() >= 2 ? "up" : null;
        String compass = null;
        if (horizontal >= 2) {
            // 8 sectors; +x = east, -z = north
            int sector = Math.floorMod((int) Math.round(Math.atan2(f.dx(), -f.dz()) / (Math.PI / 4)), 8);
            compass = COMPASS[sector];
        }
        if (vertical == null && compass == null) {
            return Component.translatable("message.holeydigsite.prospect.dir.here");
        }
        if (vertical == null) {
            return Component.translatable("message.holeydigsite.prospect.dir." + compass);
        }
        if (compass == null) {
            return Component.translatable("message.holeydigsite.prospect.dir." + vertical);
        }
        return Component.translatable("message.holeydigsite.prospect.dir.combined",
                Component.translatable("message.holeydigsite.prospect.dir." + vertical),
                Component.translatable("message.holeydigsite.prospect.dir." + compass));
    }

    /** The action-bar line for a scan result or a message indicating nothing is nearby. Public for tests. */
    public static Component message(@Nullable Find f) {
        if (f == null) {
            return Component.translatable("message.holeydigsite.prospect.none").withStyle(ChatFormatting.YELLOW);
        }
        ChatFormatting color = switch (f.kind()) {
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            case 4 -> ChatFormatting.AQUA;
            case 5 -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GOLD;
        };
        return Component.translatable("message.holeydigsite.prospect.found",
                Component.translatable("message.holeydigsite.prospect.rock." + f.kind()),
                Math.max(1, (int) Math.round(f.distance())), direction(f))
                .withStyle(color);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack pick = context.getItemInHand();
        InteractionHand otherHand = context.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (isHead(otherStack)) {
            if (fitHead(player, pick, otherStack)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (player.isShiftKeyDown()) {
            if (detachHead(player, pick)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel sl) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            Find f = scan(level, context.getClickedPos(), getHead(pick), RANGE);
            player.displayClientMessage(message(f), true);
            if (f != null) {
                double x = f.pos().getX() + 0.5, y = f.pos().getY() + 0.5, z = f.pos().getZ() + 0.5;
                sl.sendParticles(ParticleTypes.GLOW, x, y, z, 12, 0.4, 0.4, 0.4, 0.02);
                sl.sendParticles(ParticleTypes.WAX_ON, x, y, z, 6, 0.4, 0.4, 0.4, 0.0);
                sl.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F,
                        f.kind() == 3 ? 1.6F : 1.2F);
            } else {
                sl.playSound(null, context.getClickedPos(), SoundEvents.GRAVEL_HIT, SoundSource.PLAYERS, 0.6F, 0.8F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pick = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        if (isHead(otherStack)) {
            if (fitHead(player, pick, otherStack)) {
                return InteractionResultHolder.sidedSuccess(pick, level.isClientSide);
            }
        }
        if (player.isShiftKeyDown()) {
            if (detachHead(player, pick)) {
                return InteractionResultHolder.sidedSuccess(pick, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(pick);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (stack.getCount() != 1) return false;
        if (action == ClickAction.SECONDARY && slot.allowModification(player) && isHead(other)) {
            Item oldHead = getHead(stack);
            Item newHead = other.getItem();
            setHead(stack, newHead);
            if (other.getCount() == 1 && oldHead != null) {
                access.set(new ItemStack(oldHead));
            } else {
                other.shrink(1);
                if (oldHead != null) {
                    giveOrDrop(player, new ItemStack(oldHead));
                }
            }
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY) return false;
        ItemStack slotStack = slot.getItem();
        if (isHead(slotStack)) {
            Item oldHead = getHead(stack);
            Item newHead = slotStack.getItem();
            setHead(stack, newHead);
            slot.remove(1);
            if (oldHead != null) {
                giveOrDrop(player, new ItemStack(oldHead));
            }
            player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.holeydigsite.prospectors_pick").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.holeydigsite.prospectors_pick.how", RANGE).withStyle(ChatFormatting.DARK_GRAY));
        Item head = getHead(stack);
        if (head != null) {
            tooltip.add(Component.translatable("tooltip.holeydigsite.prospectors_pick.head", head.getName(new ItemStack(head)))
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
