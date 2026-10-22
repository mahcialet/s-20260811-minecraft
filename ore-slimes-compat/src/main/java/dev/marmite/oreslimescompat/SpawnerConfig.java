package dev.marmite.oreslimescompat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpawnerConfig {
    public static final ModConfigSpec SPEC;
    public enum BackupSpawnerMode { DISABLED, SUPERFLAT_ONLY, ALL_WORLDS }
    public static final ModConfigSpec.EnumValue<BackupSpawnerMode> BACKUP_MODE;
    public static final ModConfigSpec.DoubleValue STANDARD_WEIGHT_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLED, VANILLA, DEBUG;
    public static final ModConfigSpec.IntValue INTERVAL, MAX_NEARBY, ATTEMPTS, COUNT_RADIUS, MIN_DISTANCE, MAX_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHANCE, VANILLA_CHANCE;
    static {
        var b = new ModConfigSpec.Builder();
        STANDARD_WEIGHT_SCALE = b.comment("Scale Ore Slime CREATURE biome spawn weights at server startup. Positive weights round to at least 1; 1.0 preserves upstream weights. Restart to apply changes.")
                .defineInRange("standardSpawnWeightScale", 0.01, 0.001, 1.0);
        BACKUP_MODE = b.comment("Backup spawn policy for both Ore Slimes and vanilla slime injection. SUPERFLAT_ONLY uses each dimension's actual chunk generator.")
                .defineEnum("backupSpawnerMode", BackupSpawnerMode.DISABLED);
        b.push("oreSlimeSpawner");
        ENABLED = b.comment("Enable backup spawning in dimensions allowed by backupSpawnerMode.").define("enabled", true);
        INTERVAL = b.defineInRange("intervalTicks", 1200, 20, 72000);
        MAX_NEARBY = b.defineInRange("maxNearby", 3, 0, 64);
        CHANCE = b.defineInRange("spawnChance", 0.35, 0.0, 1.0);
        ATTEMPTS = b.defineInRange("searchAttempts", 4, 1, 32);
        COUNT_RADIUS = b.comment("AABB radius; effective radius is at least maxSpawnDistance.").defineInRange("countRadius", 72, 16, 256);
        MIN_DISTANCE = b.defineInRange("minSpawnDistance", 18, 1, 128);
        MAX_DISTANCE = b.comment("Horizontal radial distance; clamped up to minSpawnDistance.").defineInRange("maxSpawnDistance", 52, 1, 128);
        VANILLA = b.comment("Allow vanilla slime injection, also gated by enabled and ore pass.").define("spawnVanillaSlimes", false);
        VANILLA_CHANCE = b.defineInRange("vanillaSlimeChance", 0.02, 0.0, 1.0);
        b.pop();
        DEBUG = b.comment("Emit spawner diagnostics at DEBUG level only.").define("debugSpawner", false);
        SPEC = b.build();
    }
    public static int maxDistance() { return Math.max(MIN_DISTANCE.get(), MAX_DISTANCE.get()); }
    public static int countRadius() { return Math.max(COUNT_RADIUS.get(), maxDistance()); }
    public static boolean allowsBackup(ServerLevel level) {
        return ENABLED.get() && switch (BACKUP_MODE.get()) {
            case DISABLED -> false;
            case SUPERFLAT_ONLY -> level.getChunkSource().getGenerator() instanceof FlatLevelSource;
            case ALL_WORLDS -> true;
        };
    }
    private SpawnerConfig() {}
}
