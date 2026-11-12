package dev.mobminers;

import dev.mobminers.attachment.BoundChestData;
import dev.mobminers.event.ModEvents;
import dev.mobminers.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobMiners implements ModInitializer {
    public static final String MOD_ID = "mobminers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MobMiners() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        BoundChestData.register();
        ModItems.register();
        ModEvents.register();
        LOGGER.info("MobMiners initialized for Minecraft 26.3");
    }
}
