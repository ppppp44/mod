package dev.mobminers.item;

import dev.mobminers.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;

/** One-item container for the complete serialized data of a supported mob. */
public final class CapturedMobItem extends Item {
    private static final String ENTITY_DATA_KEY = "MobMinersEntityData";

    public CapturedMobItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createFor(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(
                ProblemReporter.DISCARDING,
                entity.registryAccess()
        );
        entity.saveWithoutId(output);

        CompoundTag entityData = output.buildResult();
        entityData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());

        CompoundTag itemData = new CompoundTag();
        itemData.put(ENTITY_DATA_KEY, entityData);

        ItemStack captured = new ItemStack(ModItems.CAPTURED_MOB);
        CustomData.set(DataComponents.CUSTOM_DATA, captured, itemData);
        return captured;
    }

    public static InteractionResult place(
            ItemStack stack,
            ServerLevel level,
            Player player,
            BlockHitResult hitResult
    ) {
        BlockPos clickedPos = hitResult.getBlockPos();
        Direction face = hitResult.getDirection();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (!clickedState.isFaceSturdy(level, clickedPos, face)) {
            player.displayClientMessage(Component.literal("Place the mob against a solid block face."), true);
            return InteractionResult.FAIL;
        }

        BlockPos spawnPos = clickedPos.relative(face);
        if (!level.isInWorldBounds(spawnPos) || !level.hasChunkAt(spawnPos)) {
            player.displayClientMessage(Component.literal("That position is outside the loaded world."), true);
            return InteractionResult.FAIL;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            player.displayClientMessage(Component.literal("This captured mob has no valid entity data."), true);
            return InteractionResult.FAIL;
        }

        CompoundTag entityData = customData.copyTag().getCompound(ENTITY_DATA_KEY).orElse(null);
        if (entityData == null || entityData.isEmpty()) {
            player.displayClientMessage(Component.literal("This captured mob has no valid entity data."), true);
            return InteractionResult.FAIL;
        }

        Entity restored;
        try {
            var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entityData);
            restored = EntityType.loadEntityRecursive(
                    input,
                    level,
                    EntitySpawnReason.LOAD,
                    EntityProcessor.NOP
            );
        } catch (RuntimeException exception) {
            player.displayClientMessage(Component.literal("The captured entity data could not be read."), true);
            return InteractionResult.FAIL;
        }

        if (!(restored instanceof Mob mob) || !ModEvents.isSupportedMob(mob)) {
            if (restored != null) {
                restored.discard();
            }
            player.displayClientMessage(Component.literal("The captured entity could not be restored."), true);
            return InteractionResult.FAIL;
        }

        mob.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                mob.getYRot(),
                mob.getXRot()
        );

        if (!level.noCollision(mob)) {
            mob.discard();
            player.displayClientMessage(Component.literal("There is not enough room to place this mob."), true);
            return InteractionResult.FAIL;
        }

        if (!level.addFreshEntity(mob)) {
            mob.discard();
            player.displayClientMessage(Component.literal("The mob could not be placed here."), true);
            return InteractionResult.FAIL;
        }

        stack.shrink(1);
        player.displayClientMessage(Component.literal("Mob placed."), true);
        return InteractionResult.SUCCESS;
    }
}
