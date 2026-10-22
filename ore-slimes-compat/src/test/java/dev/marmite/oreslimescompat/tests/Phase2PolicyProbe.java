package dev.marmite.oreslimescompat.tests;

import dev.marmite.oreslimescompat.SpawnerConfig;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/** Loaded only in compat profiles, so the upstream-only mod set never resolves compat classes. */
final class Phase2PolicyProbe {
    static Map<String, Object> verify(ServerLevel overworld, String profile, String terrain, String weightVariant) {
        var original = SpawnerConfig.BACKUP_MODE.get();
        var expected = profile.equals("phase1") ? SpawnerConfig.BackupSpawnerMode.ALL_WORLDS
                : SpawnerConfig.BackupSpawnerMode.DISABLED;
        if (original != expected || !SpawnerConfig.ENABLED.get()
                || SpawnerConfig.INTERVAL.get() != 1200 || SpawnerConfig.MAX_NEARBY.get() != 3
                || SpawnerConfig.CHANCE.get() != 0.35 || SpawnerConfig.ATTEMPTS.get() != 4
                || SpawnerConfig.VANILLA.get() || SpawnerConfig.VANILLA_CHANCE.get() != 0.02
                || SpawnerConfig.COUNT_RADIUS.get() != 72 || SpawnerConfig.MIN_DISTANCE.get() != 18
                || SpawnerConfig.MAX_DISTANCE.get() != 52
                || SpawnerConfig.STANDARD_WEIGHT_SCALE.get() != (weightVariant.equals("balanced") ? 0.01 : 1.0)) {
            throw new IllegalStateException("Phase 2 comparison config differs from the declared " + profile + " profile");
        }
        boolean flat = overworld.getChunkSource().getGenerator() instanceof FlatLevelSource;
        if (flat != terrain.equals("flat")) throw new IllegalStateException("Actual Overworld generator differs from requested terrain");
        Map<String, Object> result = new TreeMap<>();
        result.put("backupSpawnerMode", original.name());
        result.put("standardSpawnWeightScale", SpawnerConfig.STANDARD_WEIGHT_SCALE.get());
        result.put("enabled", SpawnerConfig.ENABLED.get());
        result.put("intervalTicks", SpawnerConfig.INTERVAL.get());
        result.put("maxNearby", SpawnerConfig.MAX_NEARBY.get());
        result.put("spawnChance", SpawnerConfig.CHANCE.get());
        result.put("searchAttempts", SpawnerConfig.ATTEMPTS.get());
        result.put("spawnVanillaSlimes", SpawnerConfig.VANILLA.get());
        result.put("vanillaSlimeChance", SpawnerConfig.VANILLA_CHANCE.get());
        result.put("countRadius", SpawnerConfig.COUNT_RADIUS.get());
        result.put("minSpawnDistance", SpawnerConfig.MIN_DISTANCE.get());
        result.put("maxSpawnDistance", SpawnerConfig.MAX_DISTANCE.get());
        Map<String, Object> dimensions = new TreeMap<>();
        try {
            for (var level : overworld.getServer().getAllLevels()) {
                Map<String, Object> modes = new TreeMap<>();
                boolean dimensionFlat = level.getChunkSource().getGenerator() instanceof FlatLevelSource;
                modes.put("generator", level.getChunkSource().getGenerator().getClass().getName());
                for (var mode : SpawnerConfig.BackupSpawnerMode.values()) {
                    SpawnerConfig.BACKUP_MODE.set(mode);
                    boolean expectedAllowed = switch (mode) {
                        case DISABLED -> false;
                        case SUPERFLAT_ONLY -> dimensionFlat;
                        case ALL_WORLDS -> true;
                    };
                    boolean actual = SpawnerConfig.allowsBackup(level);
                    if (actual != expectedAllowed) throw new IllegalStateException("Policy mismatch for " + level.dimension() + " / " + mode);
                    modes.put(mode.name(), actual);
                }
                dimensions.put(level.dimension().identifier().toString(), modes);
            }
        } finally {
            SpawnerConfig.BACKUP_MODE.set(original);
        }
        result.put("policyProbe", dimensions);
        return result;
    }
    private Phase2PolicyProbe() {}
}
