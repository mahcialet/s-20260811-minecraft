package dev.marmite.oreslimescompat.tests;

import com.jeremiah.oreslimes.entity.OreSlimeEntity;
import com.jeremiah.oreslimes.world.OreSlimeWorldSpawner;
import com.jeremiah.oreslimes.world.OreSlimeSpawnRules;
import com.mojang.authlib.GameProfile;
import dev.marmite.oreslimescompat.SpawnerConfig;
import dev.marmite.oreslimescompat.OreSlimeWeightModifier;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Runs the actual transformed dependency spawner in a disposable dedicated server. */
@Mod("ore_slimes_compat_tests")
public final class CompatRuntimeTests {
    private static final AABB AREA = new AABB(400, 180, 400, 700, 230, 700);
    private static int assertions;

    public CompatRuntimeTests(net.neoforged.bus.api.IEventBus bus) {
        EqualWeightExperiment.register(bus);
        if (Boolean.getBoolean("compat.phase2")) {
            new Phase2Statistics();
            return;
        }
        if (Boolean.getBoolean("compat.creatureCoverage")) {
            new CreatureCoverage();
            return;
        }
        if (Boolean.getBoolean("compat.statistics")) {
            new CompatStatistics();
            return;
        }
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        try {
            run(event.getServer().overworld());
            Files.writeString(Path.of("compat-test-result.txt"), "PASS assertions=" + assertions + "\n");
            System.out.println("COMPAT_RUNTIME_TESTS_PASS assertions=" + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("compat-test-result.txt"), "FAIL " + failure + "\n"); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            throw new AssertionError("COMPAT_RUNTIME_TESTS_FAILED", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void run(ServerLevel level) throws Exception {
        check(SpawnerConfig.INTERVAL.get() == 1200, "default interval");
        check(SpawnerConfig.MAX_NEARBY.get() == 3, "default nearby cap");
        check(SpawnerConfig.CHANCE.get() == 0.35, "default chance");
        check(!SpawnerConfig.VANILLA.get(), "default vanilla injection disabled");
        check(SpawnerConfig.BACKUP_MODE.get() == SpawnerConfig.BackupSpawnerMode.DISABLED,
                "default policy uses standard spawning only");
        standardSpawnWeights(level);
        clear(level);
        // Elevated platform avoids existing terrain, caves, and world-generation mobs.
        for (int x = 440; x <= 620; x++) for (int z = 440; z <= 620; z++) {
            level.setBlock(new BlockPos(x, 199, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "OreTestOne"));
        player.setPos(530.5, 200, 530.5);
        level.addNewPlayer(player);
        level.getRandom().setSeed(384731L);
        census(level);
        spawnPolicies(level, player);
        standardChunkRules(level);
        SpawnerConfig.BACKUP_MODE.set(SpawnerConfig.BackupSpawnerMode.ALL_WORLDS);
        counter(0);
        for (int tick = 0; tick < 120000; tick++) tick(level);
        check(oreCount(level) > 0 && oreCount(level) <= 3, "default chance and attempts retain ore spawning over 100 intervals");
        clear(level);
        SpawnerConfig.CHANCE.set(1.0);
        SpawnerConfig.ATTEMPTS.set(32);
        counter(0);
        for (int tick = 0; tick < 1199; tick++) tick(level);
        check(oreCount(level) == 0, "no early spawn before configured interval");
        tick(level);
        check(oreCount(level) <= 1, "one successful addition per pass");
        for (int pass = 0; pass < 100; pass++) pass(level);
        check(oreCount(level) == 3, "single player reaches and respects cap over 100 passes");
        check(vanillaCount(level) == 0, "vanilla injection remains off");
        clear(level);
        SpawnerConfig.ENABLED.set(false);
        for (int pass = 0; pass < 20; pass++) pass(level);
        check(oreCount(level) == 0, "disabled spawner adds no ore slimes");
        var calmSlime = EntityType.SLIME.create(level, EntitySpawnReason.COMMAND);
        calmSlime.setSize(1, true);
        calmSlime.setPos(532, 200, 530);
        calmSlime.setTarget(player);
        level.addFreshEntity(calmSlime);
        counter(9);
        tick(level);
        check(calmSlime.getTarget() == null, "disabled spawning retains ten-tick vanilla slime calming");
        calmSlime.discard();
        SpawnerConfig.ENABLED.set(true);
        SpawnerConfig.CHANCE.set(0.0);
        for (int pass = 0; pass < 20; pass++) pass(level);
        check(oreCount(level) == 0, "zero chance adds no ore slimes");
        SpawnerConfig.CHANCE.set(1.0);
        int[] cancelled = {0};
        java.util.function.Consumer<EntityJoinLevelEvent> cancelOre = event -> {
            if (event.getEntity() instanceof OreSlimeEntity) { cancelled[0]++; event.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(cancelOre);
        try {
            for (int pass = 0; pass < 20; pass++) pass(level);
            check(cancelled[0] > 3, "cancelled joins do not consume a successful-addition cap");
            check(oreCount(level) == 0, "join cancellation remains respected");
        } finally { NeoForge.EVENT_BUS.unregister(cancelOre); }
        SpawnerConfig.MAX_NEARBY.set(0);
        for (int pass = 0; pass < 20; pass++) pass(level);
        check(oreCount(level) == 0, "zero nearby cap adds no ore slimes");
        SpawnerConfig.MAX_NEARBY.set(3);
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "OreTestTwo"));
        other.setPos(550.5, 200, 530.5);
        level.addNewPlayer(other);
        for (int pass = 0; pass < 100; pass++) {
            int before = oreCount(level);
            pass(level);
            check(oreCount(level) - before <= 1, "overlapping players share one pass");
        }
        check(oreCount(level) == 3, "overlapping players retain shared cap");
        clear(level);
        other.setPos(630.5, 200, 530.5);
        for (int pass = 0; pass < 100; pass++) {
            int before = oreCount(level);
            pass(level);
            check(oreCount(level) - before <= 1, "players 100 blocks apart share one pass");
        }
        check(oreCount(level) == 3, "100-block player spacing retains shared cap");
        other.setPos(550.5, 200, 530.5);
        clear(level);
        // Use Minecraft's own census with ten real CREATURE entities, not a copied cap formula.
        for (int i = 0; i < 10; i++) {
            var cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
            cow.setPos(520 + i, 200, 520);
            level.addFreshEntity(cow);
        }
        census(level);
        check(level.getChunkSource().getLastSpawnState().getMobCategoryCounts().getInt(MobCategory.CREATURE) >= 10,
                "real census counts passive creatures");
        for (int pass = 0; pass < 20; pass++) pass(level);
        check(oreCount(level) == 0, "full CREATURE census blocks backup spawner");
        clear(level);
        census(level);
        SpawnerConfig.VANILLA.set(true);
        SpawnerConfig.VANILLA_CHANCE.set(1.0);
        SpawnerConfig.MAX_NEARBY.set(64);
        for (int pass = 0; pass < 10; pass++) pass(level);
        check(vanillaCount(level) > 0, "explicit vanilla injection option works");
        clear(level);
        SpawnerConfig.VANILLA.set(false);
        for (int pass = 0; pass < 10; pass++) pass(level);
        check(vanillaCount(level) == 0, "vanilla injection option suppresses forced slimes");
        clear(level);
        census(level);
        SpawnerConfig.MAX_NEARBY.set(3);
        for (int i = 0; i < 100; i++) pass(level);
        check(oreCount(level) == 3, "ore slimes remain present for natural spawn tests");
        naturalPassiveSpawning(level);
        for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, AREA,
                mob -> !(mob instanceof OreSlimeEntity))) mob.discard();
        SpawnerConfig.ENABLED.set(false);
        naturalPassiveSpawning(level);
        naturalVanillaSlime(level);
    }

    private static void standardSpawnWeights(ServerLevel level) throws Exception {
        check(SpawnerConfig.STANDARD_WEIGHT_SCALE.get() == 0.01, "default standard weight scale");
        var plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var original = plains.value().modifiableBiomeInfo().getOriginalBiomeInfo();
        var baseline = net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(original);
        var expectedOre = new java.util.LinkedHashMap<EntityType<?>, net.minecraft.util.random.Weighted<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData>>();
        try (var stream = CompatRuntimeTests.class.getClassLoader().getResourceAsStream(
                "data/oreslimes/neoforge/biome_modifier/add_ore_slime_spawns.json")) {
            check(stream != null, "upstream spawn registration resource is available");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var value : json.getAsJsonArray("spawners")) {
                var entry = value.getAsJsonObject();
                var key = net.minecraft.resources.Identifier.parse(entry.get("type").getAsString());
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(key);
                check(type != null, "upstream spawn type is registered: " + key);
                var data = new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(type,
                        entry.get("minCount").getAsInt(), entry.get("maxCount").getAsInt());
                var weighted = new net.minecraft.util.random.Weighted<>(data, entry.get("weight").getAsInt());
                baseline.getMobSpawnSettings().getSpawner(type.getCategory()).add(weighted);
                if (key.getNamespace().equals("oreslimes")) expectedOre.put(type, weighted);
            }
        }
        check(expectedOre.size() == 13, "upstream resource supplies all thirteen Ore types");
        var live = plains.value().getMobSettings();
        int total = 0;
        for (var entry : expectedOre.entrySet()) {
            var matches = live.getMobs(MobCategory.CREATURE).unwrap().stream()
                    .filter(weighted -> weighted.value().type() == entry.getKey()).toList();
            check(matches.size() == 1, "one live CREATURE entry for " + entry.getKey());
            var actual = matches.getFirst();
            check(entry.getKey().getCategory() == MobCategory.CREATURE, "Ore type retains CREATURE category");
            check(actual.value().equals(entry.getValue().value()), "Ore type and group bounds match upstream");
            check(actual.weight() == Math.max(1, (int) Math.round(entry.getValue().weight() * 0.01)),
                    "live Ore weight matches configured scale");
            total += actual.weight();
        }
        check(total == 88, "default rounded Ore weights total eighty-eight");
        var originalSettings = baseline.build().mobSpawnSettings();
        for (var category : MobCategory.values()) {
            var expectedOthers = originalSettings.getMobs(category).unwrap().stream()
                    .filter(entry -> !expectedOre.containsKey(entry.value().type())).toList();
            var actualOthers = live.getMobs(category).unwrap().stream()
                    .filter(entry -> !expectedOre.containsKey(entry.value().type())).toList();
            check(actualOthers.equals(expectedOthers), "non-Ore entries retain weight, group bounds and order in " + category);
        }
        check(live.getMobs(MobCategory.MONSTER).unwrap().stream()
                        .anyMatch(entry -> entry.value().type() == EntityType.SLIME && entry.weight() == 500
                                && entry.value().minCount() == 2 && entry.value().maxCount() == 4),
                "upstream vanilla Slime addition retains its MONSTER weight and group bounds");
        double savedScale = SpawnerConfig.STANDARD_WEIGHT_SCALE.get();
        var modifier = new OreSlimeWeightModifier();
        try {
            SpawnerConfig.STANDARD_WEIGHT_SCALE.set(1.0);
            var restored = net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(baseline.build());
            modifier.modify(plains, net.neoforged.neoforge.common.world.BiomeModifier.Phase.MODIFY, restored);
            for (var category : MobCategory.values()) {
                check(restored.build().mobSpawnSettings().getMobs(category).unwrap().equals(originalSettings.getMobs(category).unwrap()),
                        "scale one preserves full upstream entries in " + category);
            }
            SpawnerConfig.STANDARD_WEIGHT_SCALE.set(0.01);
            var fresh = net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(baseline.build());
            modifier.modify(plains, net.neoforged.neoforge.common.world.BiomeModifier.Phase.ADD, fresh);
            check(fresh.build().mobSpawnSettings().getMobs(MobCategory.CREATURE).unwrap()
                    .equals(originalSettings.getMobs(MobCategory.CREATURE).unwrap()), "weight modifier waits until MODIFY phase");
            modifier.modify(plains, net.neoforged.neoforge.common.world.BiomeModifier.Phase.MODIFY, fresh);
            check(fresh.build().mobSpawnSettings().getMobs(MobCategory.CREATURE).unwrap()
                    .equals(live.getMobs(MobCategory.CREATURE).unwrap()), "fresh builder reproduces startup registry weights");
            var creatures = fresh.getMobSpawnSettings().getSpawner(MobCategory.CREATURE);
            creatures.removeIf(entry -> true);
            var ore = expectedOre.keySet().iterator().next();
            creatures.add(new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(ore, 1, 2), 0);
            // Use an existing registered type: EntityType construction requires an unfrozen registry.
            var foreignData = new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(EntityType.COW, 2, 7);
            creatures.add(foreignData, 31);
            modifier.modify(plains, net.neoforged.neoforge.common.world.BiomeModifier.Phase.MODIFY, fresh);
            check(creatures.getList().getFirst().weight() == 0, "explicit zero Ore weight remains disabled");
            check(creatures.getList().getLast().equals(new net.minecraft.util.random.Weighted<>(foreignData, 31)),
                    "non-Ore CREATURE entry retains its custom weight and group bounds");
        } finally {
            SpawnerConfig.STANDARD_WEIGHT_SCALE.set(savedScale);
        }
        check(plains.value().getMobSettings() == live, "isolated builder probes leave the live registry unchanged");
    }

    private static void standardChunkRules(ServerLevel level) {
        BlockPos slime = null, ordinary = null;
        for (int x = 448; x <= 608; x += 16) for (int z = 448; z <= 608; z += 16) {
            var candidate = new BlockPos(x + 8, 200, z + 8);
            if (OreSlimeSpawnRules.isInSlimeChunk(level, candidate)) slime = candidate;
            else ordinary = candidate;
        }
        check(slime != null && ordinary != null, "fixture includes slime and non-slime chunks on the same valid floor");
        check(OreSlimeSpawnRules.canNaturallySpawn(level, EntitySpawnReason.NATURAL, slime, level.getRandom()),
                "actual upstream natural predicate accepts a valid floor in a slime chunk");
        check(!OreSlimeSpawnRules.canNaturallySpawn(level, EntitySpawnReason.NATURAL, ordinary, level.getRandom()),
                "actual upstream natural predicate rejects a non-slime chunk");
        check(!OreSlimeSpawnRules.canSpawnFromBackupSpawner(level, ordinary),
                "optional backup predicate also rejects a non-slime chunk");
    }

    private static void spawnPolicies(ServerLevel level, net.minecraft.server.level.ServerPlayer player) throws Exception {
        SpawnerConfig.CHANCE.set(1.0);
        SpawnerConfig.ATTEMPTS.set(32);
        SpawnerConfig.VANILLA.set(true);
        SpawnerConfig.VANILLA_CHANCE.set(1.0);
        for (var mode : java.util.List.of(SpawnerConfig.BackupSpawnerMode.DISABLED,
                SpawnerConfig.BackupSpawnerMode.SUPERFLAT_ONLY)) {
            SpawnerConfig.BACKUP_MODE.set(mode);
            check(!SpawnerConfig.allowsBackup(level), mode + " blocks backup in normal Overworld");
            for (int pass = 0; pass < 20; pass++) pass(level);
            check(oreCount(level) == 0, mode + " blocks Ore Slime additions");
            check(vanillaCount(level) == 0, mode + " blocks vanilla slime injection");
            var slime = EntityType.SLIME.create(level, EntitySpawnReason.COMMAND);
            slime.setSize(1, true);
            slime.setPos(532, 200, 530);
            slime.setTarget(player);
            level.addFreshEntity(slime);
            counter(9);
            tick(level);
            check(slime.getTarget() == null, mode + " retains ten-tick calming");
            slime.discard();
        }
        var nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        check(nether != null && !SpawnerConfig.allowsBackup(nether), "SUPERFLAT_ONLY rejects Nether noise generator");
        SpawnerConfig.BACKUP_MODE.set(SpawnerConfig.BackupSpawnerMode.ALL_WORLDS);
        check(SpawnerConfig.allowsBackup(level), "ALL_WORLDS restores normal-world backup eligibility");
        check(SpawnerConfig.allowsBackup(nether), "ALL_WORLDS retains upstream dimension eligibility checks");
        SpawnerConfig.ENABLED.set(false);
        check(!SpawnerConfig.allowsBackup(level), "Phase 1 enabled switch remains authoritative");
        SpawnerConfig.ENABLED.set(true);
        SpawnerConfig.CHANCE.set(0.35);
        SpawnerConfig.ATTEMPTS.set(4);
        SpawnerConfig.VANILLA.set(false);
        SpawnerConfig.VANILLA_CHANCE.set(0.02);
        level.getRandom().setSeed(384731L);
    }

    private static void naturalVanillaSlime(ServerLevel level) {
        int chunkX = 32, chunkZ = 32;
        while (!OreSlimeSpawnRules.isInSlimeChunk(level, new BlockPos(chunkX * 16, 10, chunkZ * 16))) chunkX++;
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        AABB cave = new AABB(x0, 8, z0, x0 + 16, 18, z0 + 16);
        for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, cave)) mob.discard();
        for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
            for (int y = 9; y <= 17; y++) level.setBlock(new BlockPos(x, y, z),
                    (y == 9 || y == 17 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        }
        for (var player : level.players()) player.setPos(x0 + 8, 45, z0 + 8);
        SpawnerConfig.ENABLED.set(false);
        check(level.getBiome(new BlockPos(x0 + 8, 10, z0 + 8)).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap().stream().anyMatch(entry -> entry.value().type() == EntityType.SLIME),
                "natural MONSTER biome list includes vanilla slime");
        for (int attempt = 0; attempt < 2000; attempt++) {
            for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, cave,
                    mob -> mob.getType() != EntityType.SLIME)) mob.discard();
            NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, new BlockPos(x0 + 8, 10, z0 + 8));
            if (!level.getEntitiesOfClass(Slime.class, cave, slime -> !(slime instanceof OreSlimeEntity)).isEmpty()) break;
        }
        check(!level.getEntitiesOfClass(Slime.class, cave, slime -> !(slime instanceof OreSlimeEntity)).isEmpty(),
                "vanilla slime natural path works with backup spawner and vanilla injection disabled");
    }

    private static void naturalPassiveSpawning(ServerLevel level) {
        // Exercise the vanilla natural-spawner selection, placement and permission path.
        // Biomes are real registry entries: no spawn-list substitution or forced entity creation.
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var biome : java.util.List.of(Biomes.DESERT, Biomes.PLAINS)) {
            for (int x = 27; x <= 38; x++) for (int z = 27; z <= 38; z++) {
                level.getChunk(x, z).fillBiomesFromNoise((qx, qy, qz, sampler) -> biomes.getOrThrow(biome),
                        level.getChunkSource().randomState().sampler());
            }
            for (int attempt = 0; attempt < 2000; attempt++) {
                NaturalSpawner.spawnCategoryForPosition(MobCategory.CREATURE, level, new BlockPos(584, 200, 584));
                if (biome == Biomes.DESERT && countType(level, EntityType.RABBIT) > 0) break;
                if (biome == Biomes.PLAINS && countType(level, EntityType.COW) > 0
                        && countType(level, EntityType.SHEEP) > 0 && countType(level, EntityType.PIG) > 0) break;
            }
            if (biome == Biomes.DESERT) check(countType(level, EntityType.RABBIT) > 0, "rabbit naturally spawns with ore slimes present");
        }
        check(countType(level, EntityType.COW) > 0, "cow naturally spawns with ore slimes present");
        check(countType(level, EntityType.SHEEP) > 0, "sheep naturally spawns with ore slimes present");
        check(countType(level, EntityType.PIG) > 0, "pig naturally spawns with ore slimes present");
    }

    private static int countType(ServerLevel level, EntityType<?> type) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, AREA, mob -> mob.getType() == type).size();
    }

    private static void census(ServerLevel level) throws Exception {
        var state = NaturalSpawner.createState(289, level.getAllEntities(),
                (position, consumer) -> consumer.accept(level.getChunk((int) position, (int) (position >> 32))),
                new LocalMobCapCalculator(level.getChunkSource().chunkMap));
        Field field = ServerChunkCache.class.getDeclaredField("lastSpawnState");
        field.setAccessible(true);
        field.set(level.getChunkSource(), state);
    }

    private static void counter(int value) throws Exception {
        Field field = OreSlimeWorldSpawner.class.getDeclaredField("tickCounter");
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static void tick(ServerLevel level) {
        OreSlimeWorldSpawner.onServerTick(new ServerTickEvent.Post(() -> true, level.getServer()));
    }

    private static void pass(ServerLevel level) throws Exception {
        counter(SpawnerConfig.INTERVAL.get() - 1);
        tick(level);
    }

    private static int oreCount(ServerLevel level) { return level.getEntitiesOfClass(OreSlimeEntity.class, AREA).size(); }
    private static int vanillaCount(ServerLevel level) {
        return level.getEntitiesOfClass(Slime.class, AREA, slime -> !(slime instanceof OreSlimeEntity)).size();
    }
    private static void clear(ServerLevel level) {
        for (Entity entity : new ArrayList<>(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, AREA))) entity.discard();
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
