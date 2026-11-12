package dev.mobminers.item;

import dev.mobminers.MobMiners;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item CAPTURED_MOB = Registry.register(
            BuiltInRegistries.ITEM,
            MobMiners.id("captured_mob"),
            new CapturedMobItem(properties("captured_mob", 1))
    );

    public static final Item MINING_OVERSEER_WAND = Registry.register(
            BuiltInRegistries.ITEM,
            MobMiners.id("mining_overseer_wand"),
            new OverseerWandItem(properties("mining_overseer_wand", 1))
    );

    private ModItems() {
    }

    private static Item.Properties properties(String path, int maxCount) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobMiners.id(path));
        return new Item.Properties().setId(key).stacksTo(maxCount);
    }

    public static void register() {
        MobMiners.LOGGER.debug("Registered MobMiners items: {}, {}", CAPTURED_MOB, MINING_OVERSEER_WAND);
    }
}
