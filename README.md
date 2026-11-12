# MobMiners

MobMiners is a server-authoritative Fabric mod for Minecraft Java 26.3. Capture a cow, pig, sheep, or villager, carry it underground, equip it with a pickaxe, bind it to a chest, and let it mine nearby blocks. The miner does not require food, coins, or a fuel item.

## Required toolchain

- Minecraft Java Edition **26.3**
- **Java 25 JDK** (Minecraft 26.3 requires Java 25)
- Fabric Loader **0.19.5**
- Fabric API **0.160.7+26.3**
- Fabric Loom **1.17.9**
- Gradle **9.6.0**

Minecraft 26.3 uses unobfuscated game classes. This project therefore uses the class names and package names exposed by the 26.3 game rather than declaring a Yarn mappings artifact. The Loom plugin ID is `net.fabricmc.fabric-loom`.

## Build

Install a JDK 25 and Gradle 9.6.0, open a terminal in the project directory, and run:

```sh
gradle --version
gradle build
```

The mod jar is written to `build/libs/mobminers-1.0.0.jar`.

The repository includes `gradle/wrapper/gradle-wrapper.properties` with the pinned Gradle distribution URL. Wrapper launcher scripts and the wrapper JAR are not bundled in this source archive; use an installed Gradle 9.6.0 executable or generate the wrapper scripts with `gradle wrapper --gradle-version 9.6.0`.

## How to use it

1. Craft a **Mining Overseer Wand** from one stick and one iron ingot.
2. Hold the wand, sneak, and right-click a vanilla chest. The wand remembers the chest's position and dimension.
3. Right-click a cow, pig, sheep, or villager with that wand to bind the mob to the chest.
4. Right-click the mob with a pickaxe to transfer one pickaxe into its main hand.
5. Sneak-right-click the mob with an empty main hand to capture it. The **Captured Mob** item stores its entity data in the `minecraft:custom_data` component.
6. Right-click the solid face of a block with the Captured Mob item to restore and place it.

A miner becomes active only when it has both a pickaxe in its main hand and a bound-chest attachment. It is made persistent when a pickaxe is equipped or a chest is assigned.

## Mining and item handling

- The scan volume is 5 blocks wide, 3 blocks high, and 5 blocks deep, at the miner's feet and above. No block below its feet is considered.
- Bedrock, fluids, air, chest blocks, ender chests, barrels, block-entity blocks, and the bound chest position are excluded.
- Obsidian is only considered when the miner is holding a diamond or netherite pickaxe.
- Mining progress is updated server-side and crack stages are broadcast through the level's block-breaking progress API.
- Block drops are calculated with the held pickaxe, so Fortune and Silk Touch enchantments participate in the normal loot calculation.
- Each successfully broken block costs one pickaxe durability point. When the pickaxe breaks, the miner stops until another pickaxe is equipped.
- Drops are inserted through Fabric Transfer API's `ItemStorage.SIDED` using a transaction. If the destination is in another dimension, unloaded, broken, or unable to accept the full stack, the remaining items are spawned at the miner's feet.
- Miners never target their assigned chest, and the goal does not force-load chunks.

## Project layout

```text
MobMiners/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── LICENSE
└── src/main/
    ├── java/dev/mobminers/
    │   ├── MobMiners.java
    │   ├── ai/MobMineBlockGoal.java
    │   ├── attachment/BoundChestData.java
    │   ├── event/ModEvents.java
    │   ├── item/CapturedMobItem.java
    │   ├── item/ModItems.java
    │   ├── item/OverseerWandItem.java
    │   └── mixin/MobEntityMixin.java
    └── resources/
        ├── fabric.mod.json
        ├── mobminers.mixins.json
        ├── assets/mobminers/items/
        ├── assets/mobminers/lang/en_us.json
        ├── assets/mobminers/models/item/
        ├── assets/mobminers/textures/item/
        └── data/mobminers/recipe/
```

## Verification status

The source and resource files were generated and structurally checked (JSON parsing, required-file checks, archive inspection). This environment does not have a Java 25 JDK, Gradle 9.6.0, or the cached Minecraft/Fabric build artifacts, and it cannot download those build dependencies. Consequently, a real Gradle compile and in-game integration test could not be executed here. Treat this as a complete source project for a Java 25 / Fabric 26.3 build, not as a verified release binary.
