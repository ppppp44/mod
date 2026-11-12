package dev.mobminers.event;

import dev.mobminers.MobMiners;
import dev.mobminers.ai.MobMineBlockGoal;
import dev.mobminers.attachment.BoundChestData;
import dev.mobminers.item.CapturedMobItem;
import dev.mobminers.item.ModItems;
import dev.mobminers.item.OverseerWandItem;
import dev.mobminers.mixin.MobEntityMixin;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Optional;

public final class ModEvents {
    private ModEvents() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register(ModEvents::onUseEntity);
        UseBlockCallback.EVENT.register(ModEvents::onUseBlock);

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && isSupportedMob(mob)) {
                ((MobEntityMixin) (Object) mob).mobminers$getGoalSelector()
                        .addGoal(2, new MobMineBlockGoal(mob));

                if (mob.getAttached(BoundChestData.TYPE) != null
                        || mob.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.PICKAXES)) {
                    mob.setPersistenceRequired();
                }
            }
        });

        MobMiners.LOGGER.info("MobMiners event handlers registered");
    }

    public static boolean isSupportedMob(Entity entity) {
        return entity instanceof Cow || entity instanceof Pig || entity instanceof Sheep || entity instanceof Villager;
    }

    private static InteractionResult onUseEntity(
            Player player,
            Level level,
            InteractionHand hand,
            Entity entity,
            EntityHitResult hitResult
    ) {
        if (hand != InteractionHand.MAIN_HAND
                || !(entity instanceof Mob mob)
                || !isSupportedMob(mob)
                || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            ItemStack captured = CapturedMobItem.createFor(mob);
            player.setItemInHand(InteractionHand.MAIN_HAND, captured);
            mob.discard();
            player.displayClientMessage(
                    Component.literal("Mob captured. Right-click a solid block face to place it."), true
            );
            return InteractionResult.SUCCESS;
        }

        if (held.is(ModItems.MINING_OVERSEER_WAND)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            Optional<BoundChestData> binding = OverseerWandItem.getBoundChest(held);
            if (binding.isEmpty()) {
                player.displayClientMessage(
                        Component.literal("Sneak-right-click a chest with this wand first."), true
                );
                return InteractionResult.FAIL;
            }

            mob.setAttached(BoundChestData.TYPE, binding.get());
            mob.setPersistenceRequired();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.75D,
                        mob.getZ(),
                        8,
                        0.35D,
                        0.35D,
                        0.35D,
                        0.02D
                );
            }
            player.displayClientMessage(Component.literal("Mob bound to the wand's chest."), true);
            return InteractionResult.SUCCESS;
        }

        if (held.is(ItemTags.PICKAXES)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (!mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                player.displayClientMessage(
                        Component.literal("This mob's main hand is already occupied."), true
                );
                return InteractionResult.FAIL;
            }

            ItemStack toolForMob = held.copyWithCount(1);
            mob.setItemSlot(EquipmentSlot.MAINHAND, toolForMob);
            mob.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            mob.setPersistenceRequired();
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            player.displayClientMessage(
                    Component.literal("Pickaxe equipped. Bind this mob to a chest with the Mining Overseer Wand."),
                    true
            );
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult onUseBlock(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (held.is(ModItems.CAPTURED_MOB)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel) {
                return CapturedMobItem.place(held, serverLevel, player, hitResult);
            }
            return InteractionResult.FAIL;
        }

        if (held.is(ModItems.MINING_OVERSEER_WAND) && player.isShiftKeyDown()) {
            BlockPos clickedPos = hitResult.getBlockPos();
            if (!(level.getBlockEntity(clickedPos) instanceof ChestBlockEntity)) {
                return InteractionResult.PASS;
            }

            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (level instanceof ServerLevel serverLevel) {
                OverseerWandItem.bind(held, serverLevel, clickedPos);
                player.displayClientMessage(
                        Component.literal("Mining chest bound at " + clickedPos.toShortString()
                                + ". Right-click a mob to assign it."),
                        true
                );
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }
}
