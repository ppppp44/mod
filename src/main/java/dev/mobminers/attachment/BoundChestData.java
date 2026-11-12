package dev.mobminers.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mobminers.MobMiners;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Persistent chest destination assigned to a supported miner. */
public record BoundChestData(ResourceKey<Level> dimension, BlockPos pos) {
    public static final Codec<BoundChestData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(BoundChestData::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(BoundChestData::pos)
    ).apply(instance, BoundChestData::new));

    public static final AttachmentType<BoundChestData> TYPE = AttachmentRegistry.create(
            MobMiners.id("bound_chest"),
            builder -> builder.persistent(CODEC)
    );

    public BoundChestData {
        Objects.requireNonNull(dimension, "dimension");
        pos = Objects.requireNonNull(pos, "pos").immutable();
    }

    public static void register() {
        // Calling this method forces class initialization before any entity is read or written.
        MobMiners.LOGGER.debug("Persistent bound-chest attachment registered");
    }
}
