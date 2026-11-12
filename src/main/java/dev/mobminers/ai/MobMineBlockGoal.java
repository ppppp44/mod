package dev.mobminers.ai;

import dev.mobminers.MobMiners;
import dev.mobminers.attachment.BoundChestData;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/** Autonomous mining goal. World mutation and storage transfers are server-side only. */
public final class MobMineBlockGoal extends Goal {
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int VERTICAL_SCAN = 3;
    private static final double MINING_REACH_SQUARED = 20.25D;
    private static final int PATH_RETRY_TICKS = 10;
    private static final int PATH_TIMEOUT_TICKS = 120;

    private final Mob mob;
    private BlockPos targetPos;
    private float breakProgress;
    private int pathTicks;
    private int swingTicks;

    public MobMineBlockGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ServerLevel level = serverLevel();
        if (level == null || !hasMiningSetup()) {
            return false;
        }
        targetPos = findTarget(level);
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        ServerLevel level = serverLevel();
        return level != null
                && hasMiningSetup()
                && targetPos != null
                && isValidTarget(level, targetPos)
                && pathTicks <= PATH_TIMEOUT_TICKS;
    }

    @Override
    public void start() {
        breakProgress = 0.0F;
        pathTicks = 0;
        swingTicks = 0;
    }

    @Override
    public void stop() {
        ServerLevel level = serverLevel();
        if (level != null && targetPos != null) {
            level.destroyBlockProgress(mob.getId(), targetPos, -1);
        }
        mob.getNavigation().stop();
        targetPos = null;
        breakProgress = 0.0F;
        pathTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ServerLevel level = serverLevel();
        if (level == null || targetPos == null || !hasMiningSetup()) {
            stop();
            return;
        }

        if (!isValidTarget(level, targetPos)) {
            selectNextTarget(level);
            if (targetPos == null) {
                stop();
                return;
            }
        }

        BlockState state = level.getBlockState(targetPos);
        Vec3 center = Vec3.atCenterOf(targetPos);
        mob.getLookControl().setLookAt(center.x, center.y, center.z, 30.0F, 30.0F);

        if (mob.distanceToSqr(center) > MINING_REACH_SQUARED) {
            pathTicks++;
            if (pathTicks % PATH_RETRY_TICKS == 1) {
                mob.getNavigation().moveTo(
                        targetPos.getX() + 0.5D,
                        targetPos.getY(),
                        targetPos.getZ() + 0.5D,
                        1.0D
                );
            }
            if (pathTicks > PATH_TIMEOUT_TICKS) {
                selectNextTarget(level);
            }
            return;
        }

        pathTicks = 0;
        mob.getNavigation().stop();
        swingTicks++;
        if (swingTicks % 6 == 1) {
            mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        }

        ItemStack tool = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        float hardness = state.getDestroySpeed(level, targetPos);
        if (hardness < 0.0F) {
            selectNextTarget(level);
            return;
        }

        float miningSpeed = Math.max(1.0F, tool.getDestroySpeed(state));
        boolean correctTool = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        if (hardness == 0.0F) {
            breakProgress = 1.0F;
        } else {
            breakProgress += miningSpeed / hardness / (correctTool ? 30.0F : 100.0F);
        }

        int crackStage = Math.min(9, Math.max(0, (int) (breakProgress * 10.0F)));
        level.destroyBlockProgress(mob.getId(), targetPos, crackStage);

        if (breakProgress >= 1.0F) {
            breakTarget(level, state, tool);
        }
    }

    private ServerLevel serverLevel() {
        return mob.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private boolean hasMiningSetup() {
        ItemStack tool = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        return !tool.isEmpty()
                && tool.is(ItemTags.PICKAXES)
                && mob.getAttached(BoundChestData.TYPE) != null;
    }

    private void selectNextTarget(ServerLevel level) {
        if (targetPos != null) {
            level.destroyBlockProgress(mob.getId(), targetPos, -1);
        }
        targetPos = findTarget(level);
        breakProgress = 0.0F;
        pathTicks = 0;
    }

    /** Searches a 5x3x5 volume at and above the miner's feet, never the supporting layer. */
    private BlockPos findTarget(ServerLevel level) {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dy = 0; dy < VERTICAL_SCAN; dy++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isValidTarget(level, candidate)) {
                        continue;
                    }

                    double distance = mob.distanceToSqr(
                            candidate.getX() + 0.5D,
                            candidate.getY() + 0.5D,
                            candidate.getZ() + 0.5D
                    );
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isValidTarget(ServerLevel level, BlockPos pos) {
        BlockPos feet = mob.blockPosition();
        if (pos.getY() < feet.getY()
                || pos.equals(feet)
                || !level.isInWorldBounds(pos)
                || !level.hasChunkAt(pos)) {
            return false;
        }

        BoundChestData bound = mob.getAttached(BoundChestData.TYPE);
        if (bound != null
                && bound.dimension().equals(level.dimension())
                && bound.pos().equals(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || !state.getFluidState().isEmpty()
                || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        if (state.is(Blocks.BEDROCK)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.BARREL)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null || state.hasBlockEntity()) {
            return false;
        }

        if (state.is(Blocks.OBSIDIAN)) {
            ItemStack tool = mob.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!tool.is(Items.DIAMOND_PICKAXE) && !tool.is(Items.NETHERITE_PICKAXE)) {
                return false;
            }
        }

        return true;
    }

    private void breakTarget(ServerLevel level, BlockState state, ItemStack tool) {
        BlockPos brokenPos = targetPos;
        BlockEntity blockEntity = level.getBlockEntity(brokenPos);
        List<ItemStack> drops = Block.getDrops(state, level, brokenPos, blockEntity, mob, tool);

        boolean destroyed = level.destroyBlock(brokenPos, false, mob);
        level.destroyBlockProgress(mob.getId(), brokenPos, -1);

        if (destroyed) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    depositOrDrop(level, drop);
                }
            }

            tool.hurtAndBreak(1, mob, EquipmentSlot.MAINHAND);
            if (tool.isEmpty() || tool.isBroken()) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                targetPos = null;
                breakProgress = 0.0F;
                mob.getNavigation().stop();
                return;
            }
        }

        selectNextTarget(level);
    }

    /** Transactionally inserts as much as possible, spilling only the unaccepted remainder. */
    private void depositOrDrop(ServerLevel level, ItemStack stack) {
        BoundChestData bound = mob.getAttached(BoundChestData.TYPE);
        if (bound != null
                && bound.dimension().equals(level.dimension())
                && level.hasChunkAt(bound.pos())
                && level.getBlockEntity(bound.pos()) instanceof ChestBlockEntity) {
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, bound.pos(), (Direction) null);
            if (storage != null) {
                long inserted = 0L;
                try (Transaction transaction = Transaction.openOuter()) {
                    inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    if (inserted > 0L) {
                        transaction.commit();
                    }
                } catch (RuntimeException exception) {
                    MobMiners.LOGGER.warn(
                            "Could not insert miner drops into bound chest at {} in {}",
                            bound.pos(),
                            level.dimension().location(),
                            exception
                    );
                    inserted = 0L;
                }

                int accepted = (int) Math.min(stack.getCount(), Math.max(0L, inserted));
                if (accepted > 0) {
                    stack.shrink(accepted);
                }
            }
        }

        if (!stack.isEmpty()) {
            ItemEntity spill = new ItemEntity(
                    level,
                    mob.getX(),
                    mob.getY() + 0.25D,
                    mob.getZ(),
                    stack.copy()
            );
            spill.setDefaultPickUpDelay();
            if (!level.addFreshEntity(spill)) {
                Block.popResource(level, mob.blockPosition(), stack.copy());
            }
        }
    }
}
