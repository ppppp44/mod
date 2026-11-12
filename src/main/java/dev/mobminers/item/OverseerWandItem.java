package dev.mobminers.item;

import dev.mobminers.attachment.BoundChestData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Stores a dimension-qualified chest destination on the wand itself. */
public final class OverseerWandItem extends Item {
    private static final String DIMENSION_KEY = "MobMinersDimension";
    private static final String X_KEY = "MobMinersChestX";
    private static final String Y_KEY = "MobMinersChestY";
    private static final String Z_KEY = "MobMinersChestZ";

    public OverseerWandItem(Properties properties) {
        super(properties);
    }

    public static void bind(ItemStack wand, ServerLevel level, BlockPos chestPos) {
        CustomData current = wand.get(DataComponents.CUSTOM_DATA);
        CompoundTag data = current == null ? new CompoundTag() : current.copyTag();
        data.putString(DIMENSION_KEY, level.dimension().location().toString());
        data.putInt(X_KEY, chestPos.getX());
        data.putInt(Y_KEY, chestPos.getY());
        data.putInt(Z_KEY, chestPos.getZ());
        CustomData.set(DataComponents.CUSTOM_DATA, wand, data);
    }

    public static Optional<BoundChestData> getBoundChest(ItemStack wand) {
        CustomData customData = wand.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }

        CompoundTag data = customData.copyTag();
        String dimensionName = data.getStringOr(DIMENSION_KEY, "");
        if (dimensionName.isBlank()
                || data.getInt(X_KEY).isEmpty()
                || data.getInt(Y_KEY).isEmpty()
                || data.getInt(Z_KEY).isEmpty()) {
            return Optional.empty();
        }

        Identifier dimensionId;
        try {
            dimensionId = Identifier.parse(dimensionName);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos pos = new BlockPos(
                data.getIntOr(X_KEY, 0),
                data.getIntOr(Y_KEY, 0),
                data.getIntOr(Z_KEY, 0)
        );
        return Optional.of(new BoundChestData(dimension, pos));
    }
}
