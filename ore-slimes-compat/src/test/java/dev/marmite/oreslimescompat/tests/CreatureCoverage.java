package dev.marmite.oreslimescompat.tests;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import dev.marmite.oreslimescompat.SpawnerConfig;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Controlled habitat coverage, using the ordinary server spawn scheduler and caps. */
final class CreatureCoverage {
    private record Habitat(String name, ResourceKey<Biome> biome, Block floor, Set<String> targets) {}
    private record Attempt(String type, long gameTime) {}
    private static Set<String> vanilla(String... names) {
        Set<String> result = new TreeSet<>();
        for (String name : names) result.add("minecraft:" + name);
        return result;
    }
    private static final List<Habitat> HABITATS = List.of(
            new Habitat("savanna_plateau", Biomes.SAVANNA_PLATEAU, Blocks.GRASS_BLOCK,
                    vanilla("sheep", "pig", "chicken", "cow", "donkey", "armadillo", "llama", "wolf")),
            new Habitat("plains", Biomes.PLAINS, Blocks.GRASS_BLOCK, vanilla("horse")),
            new Habitat("taiga", Biomes.TAIGA, Blocks.GRASS_BLOCK, vanilla("fox", "rabbit")),
            new Habitat("bamboo_jungle", Biomes.BAMBOO_JUNGLE, Blocks.GRASS_BLOCK, vanilla("panda", "parrot", "ocelot")),
            new Habitat("swamp", Biomes.SWAMP, Blocks.GRASS_BLOCK, vanilla("frog")),
            new Habitat("frozen_ocean", Biomes.FROZEN_OCEAN, Blocks.ICE, vanilla("polar_bear")),
            new Habitat("snowy_slopes", Biomes.SNOWY_SLOPES, Blocks.GRASS_BLOCK, vanilla("goat")),
            new Habitat("desert", Biomes.DESERT, Blocks.SAND, vanilla("camel")),
            new Habitat("mushroom_fields", Biomes.MUSHROOM_FIELDS, Blocks.MYCELIUM, vanilla("mooshroom")),
            new Habitat("beach", Biomes.BEACH, Blocks.SAND, vanilla("turtle")),
            new Habitat("nether_wastes", Biomes.NETHER_WASTES, Blocks.LAVA, vanilla("strider")));
    private static final Set<String> SPECIAL = vanilla("allay", "bee", "cat", "happy_ghast", "mule",
            "skeleton_horse", "sniffer", "tadpole", "trader_llama", "wandering_trader");
    private List<Habitat> habitats = HABITATS;
    private final String focus = System.getProperty("compat.coverageTarget", "");
    private final boolean compact = Boolean.getBoolean("compat.compactCoverage");
    private final boolean normalCoverage = Boolean.getBoolean("compat.normalCoverage");
    private final int surfaceY = normalCoverage ? 199 : compact ? -63 : 64;
    private final int maxWaves = Integer.getInteger("compat.coverageMaxWaves", 2000);
    private final Gson gson = new Gson();
    private final Set<String> targets = new TreeSet<>();
    private final Set<String> oreTargets = new TreeSet<>();
    private final Set<String> observed = new TreeSet<>();
    private final Map<UUID, Attempt> attempts = new HashMap<>();
    private final Set<UUID> joined = new HashSet<>();
    private ServerLevel level;
    private BufferedWriter output;
    private int habitat = -1;
    private int warmup;
    private int wave;
    private int totalTicks;
    private boolean measuring;
    private int lastSpawnableChunks;
    private int lastNearbyPlayers;
    private net.minecraft.server.level.ServerPlayer player;

    CreatureCoverage() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::joined);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::finalized);
    }

    private void started(ServerStartedEvent event) {
        try {
            level = event.getServer().overworld();
            output = Files.newBufferedWriter(Path.of("creature-coverage.jsonl"));
            if (maxWaves < 1 || maxWaves > 2000) throw new IllegalArgumentException("coverageMaxWaves must be 1..2000");
            if (normalCoverage && (compact || focus.isEmpty())) {
                throw new IllegalArgumentException("Normal-generator controlled coverage requires one focused Ore type and a raised floor");
            }
            if (normalCoverage && (!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)
                    || SpawnerConfig.BACKUP_MODE.get() != SpawnerConfig.BackupSpawnerMode.DISABLED
                    || SpawnerConfig.STANDARD_WEIGHT_SCALE.get() != 0.01)) {
                throw new IllegalStateException("Normal-generator controlled coverage requires NoiseBasedChunkGenerator, DISABLED backup and 0.01 standard weight scale");
            }
            if (compact && (focus.isEmpty() || focus.equals("minecraft:ocelot") || focus.equals("minecraft:strider"))) {
                throw new IllegalArgumentException("Low-floor follow-up requires a target without the ocelot/nether height fixture");
            }
            if (!SpawnerConfig.ENABLED.get() || SpawnerConfig.INTERVAL.get() != 1200
                    || SpawnerConfig.MAX_NEARBY.get() != 3 || SpawnerConfig.CHANCE.get() != 0.35
                    || SpawnerConfig.ATTEMPTS.get() != 4 || SpawnerConfig.COUNT_RADIUS.get() != 72
                    || SpawnerConfig.MIN_DISTANCE.get() != 18 || SpawnerConfig.MAX_DISTANCE.get() != 52
                    || SpawnerConfig.VANILLA.get()) throw new IllegalStateException("Coverage requires default compat settings");
            for (var type : BuiltInRegistries.ENTITY_TYPE) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                if (id.startsWith("oreslimes:") && type.getCategory() == MobCategory.CREATURE) oreTargets.add(id);
            }
            HABITATS.forEach(h -> targets.addAll(h.targets()));
            if (normalCoverage && !oreTargets.contains(focus)) throw new IllegalArgumentException("Normal-generator controlled coverage target must be one registered Ore Slime type");
            targets.addAll(oreTargets);
            if (oreTargets.size() != 13 || targets.size() != 34) throw new IllegalStateException("Unexpected creature registry: " + targets);
            Set<String> registered = new TreeSet<>();
            for (var type : BuiltInRegistries.ENTITY_TYPE) if (type.getCategory() == MobCategory.CREATURE) {
                registered.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            }
            Set<String> classified = new TreeSet<>(targets); classified.addAll(SPECIAL);
            if (!classified.equals(registered)) throw new IllegalStateException("Unclassified CREATURE registry: " + registered);
            if (!focus.isEmpty()) {
                if (!targets.contains(focus)) throw new IllegalArgumentException("Not a natural-spawn target: " + focus);
                Habitat source = HABITATS.stream().filter(h -> h.targets().contains(focus))
                        .findFirst().orElse(HABITATS.getFirst());
                habitats = List.of(new Habitat(source.name(), source.biome(), source.floor(), Set.of(focus)));
                targets.retainAll(Set.of(focus));
            }

            player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("CreatureCoverage".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "CreatureCoverage"));
            player.setPos(530.5, surfaceY + 1, 530.5);
            level.addNewPlayer(player);
            level.getChunkSource().move(player);
            write(Map.of("record", "inventory", "targets", targets, "specialRoutesExcluded", SPECIAL,
                    "maxWavesPerHabitat", maxWaves, "seed", level.getSeed(),
                    "limitation", "Controlled habitats; Strider uses a lava platform above the Nether roof. Wave-boundary removals release mob caps."));
            if (normalCoverage) write(Map.of("record", "normalCoverageFixture", "normalCoverage", true,
                    "generator", level.getChunkSource().getGenerator().getClass().getName(),
                    "backupSpawnerMode", SpawnerConfig.BACKUP_MODE.get().name(),
                    "standardSpawnWeightScale", SpawnerConfig.STANDARD_WEIGHT_SCALE.get(),
                    "surfaceY", surfaceY,
                    "scope", "Raised artificial grass habitat on a normal generator; ordinary tick spawning with population resets between waves. This proves a standard spawn path, not sustained natural-terrain abundance."));
            nextHabitat();
            event.getServer().setWeatherParameters(10_000_000, 0, false, false);
            event.getServer().tickRateManager().requestGameToSprint(10_000_000);
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }

    private void finalized(FinalizeSpawnEvent event) {
        if (!measuring || event.getEntity().level() != level || event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        if (targets.contains(type)) attempts.put(event.getEntity().getUUID(), new Attempt(type, level.getGameTime()));
    }

    private void joined(EntityJoinLevelEvent event) {
        if (!measuring || event.getLevel() != level || event.loadedFromDisk() || event.isCanceled()) return;
        if (attempts.containsKey(event.getEntity().getUUID())) joined.add(event.getEntity().getUUID());
    }

    private void tick(ServerTickEvent.Post event) {
        if (level == null) return;
        try {
            if (++totalTicks >= 9_999_000) throw new IllegalStateException("Coverage tick timeout");
            holdDaylight();
            var state = level.getChunkSource().getLastSpawnState();
            lastSpawnableChunks = state == null ? 0 : state.getSpawnableChunkCount();
            lastNearbyPlayers = level.getChunkSource().chunkMap.getPlayersCloseForSpawning(new ChunkPos(33, 33)).size();
            if (!measuring) {
                if (++warmup >= 600 && lastSpawnableChunks > 0 && lastNearbyPlayers > 0 && level.getGameTime() % 400 == 1) {
                    clearMobs();
                    measuring = true;
                    wave = 1;
                    write(Map.of("record", "ready", "habitat", habitats.get(habitat).name(),
                            "spawnableChunks", lastSpawnableChunks, "nearbyPlayers", lastNearbyPlayers, "gameTime", level.getGameTime()));
                } else if (warmup >= 2400) throw new IllegalStateException("Natural spawning not ready");
                return;
            }
            if (lastSpawnableChunks <= 0 || lastNearbyPlayers <= 0) throw new IllegalStateException("Natural spawning became inactive");
            int orePresent = 0;
            for (var entity : level.getAllEntities()) {
                if (oreTargets.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) orePresent++;
            }
            for (UUID id : joined) {
                var entity = level.getEntity(id);
                Attempt attempt = attempts.get(id);
                if (entity == null || !entity.isAlive() || attempt == null
                        || level.getGameTime() <= attempt.gameTime()) continue;
                if (observed.add(attempt.type())) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("record", "verified"); record.put("type", attempt.type()); record.put("uuid", id.toString());
                    record.put("habitat", habitats.get(habitat).name()); record.put("wave", wave);
                    record.put("spawnGameTime", attempt.gameTime()); record.put("verifiedGameTime", level.getGameTime());
                    record.put("biome", level.getBiome(entity.blockPosition()).unwrapKey().orElseThrow().toString());
                    record.put("position", List.of(entity.getX(), entity.getY(), entity.getZ()));
                    record.put("orePresent", orePresent); record.put("spawnableChunks", lastSpawnableChunks);
                    record.put("nearbyPlayers", lastNearbyPlayers); write(record);
                }
            }
            attempts.entrySet().removeIf(entry -> level.getGameTime() > entry.getValue().gameTime());
            joined.removeIf(id -> !attempts.containsKey(id));
            boolean covered = observed.containsAll(habitats.get(habitat).targets())
                    && (habitat != 0 || !focus.isEmpty() || observed.containsAll(oreTargets));
            if (level.getGameTime() % 400 == 1 && (covered || wave >= maxWaves)) {
                write(Map.of("record", "habitatSummary", "habitat", habitats.get(habitat).name(), "waves", wave,
                        "observed", new TreeSet<>(observed), "fixtureTargetsObserved", covered));
                nextHabitat();
            } else if (level.getGameTime() % 400 == 1) {
                if (wave % 25 == 0) {
                    Set<String> missing = new TreeSet<>(habitats.get(habitat).targets());
                    if (habitat == 0 && focus.isEmpty()) missing.addAll(oreTargets);
                    missing.removeAll(observed);
                    write(Map.of("record", "progress", "habitat", habitats.get(habitat).name(),
                            "wave", wave, "missing", missing, "spawnableChunks", lastSpawnableChunks,
                            "nearbyPlayers", lastNearbyPlayers, "gameTime", level.getGameTime()));
                }
                clearMobs();
                wave++;
            }
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }

    private void nextHabitat() throws Exception {
        measuring = false;
        clearMobs();
        if (++habitat >= habitats.size()) { finish(null); return; }
        Habitat h = habitats.get(habitat);
        boolean nether = h.biome().equals(Biomes.NETHER_WASTES);
        if (nether) {
            var server = level.getServer();
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            level = java.util.Objects.requireNonNull(server.getLevel(Level.NETHER));
            player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("NetherCoverage".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "NetherCoverage"));
            player.setPos(530.5, 200, 530.5);
            level.addNewPlayer(player);
            level.getChunkSource().move(player);
        }
        var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(h.biome());
        for (int x = 24; x <= 42; x++) for (int z = 24; z <= 42; z++) {
            var chunk = level.getChunk(x, z);
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, level.getChunkSource().randomState().sampler());
            chunk.markUnsaved();
        }
        for (int x = 386; x <= 674; x++) for (int z = 386; z <= 674; z++) {
            int floorY = nether ? 199 : surfaceY;
            if (nether) level.setBlock(new BlockPos(x, floorY - 1, z), Blocks.NETHERRACK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, floorY, z), h.floor().defaultBlockState(), 2);
            // Cold-biome precipitation or earlier habitat updates must not obstruct the next floor.
            level.setBlock(new BlockPos(x, floorY + 1, z), Blocks.AIR.defaultBlockState(), 2);
        }
        holdDaylight();
        warmup = 0;
        write(Map.of("record", "habitatStart", "habitat", h.name(), "targets", h.targets(), "gameTime", level.getGameTime()));
    }

    private void clearMobs() {
        var mobs = new ArrayList<Mob>();
        for (var entity : level.getAllEntities()) if (entity instanceof Mob mob) mobs.add(mob);
        mobs.forEach(Mob::discard);
        joined.clear(); attempts.clear();
    }

    private void holdDaylight() {
        var clock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        level.getServer().clockManager().setTotalTicks(clock, 6000);
    }

    private void write(Object record) throws Exception {
        output.write(gson.toJson(record)); output.newLine(); output.flush();
    }

    private void finish(Exception failure) throws Exception {
        Set<String> missing = new TreeSet<>(targets); missing.removeAll(observed);
        boolean complete = failure == null && missing.isEmpty() && !targets.isEmpty();
        Map<String, Object> result = new HashMap<>();
        result.put("targets", targets); result.put("focusedTarget", focus);
        result.put("overworldFloorY", surfaceY);
        if (normalCoverage) {
            result.put("normalCoverage", true);
            result.put("generator", level.getChunkSource().getGenerator().getClass().getName());
            result.put("backupSpawnerMode", SpawnerConfig.BACKUP_MODE.get().name());
            result.put("standardSpawnWeightScale", SpawnerConfig.STANDARD_WEIGHT_SCALE.get());
        }
        result.put("complete", complete); result.put("observed", observed); result.put("missing", missing);
        result.put("specialRoutesExcluded", SPECIAL); result.put("ticks", totalTicks);
        result.put("failure", failure == null ? "" : failure.toString());
        result.put("scope", "Controlled habitat natural-spawn coverage; caps released between waves; Strider uses a lava platform above the Nether roof.");
        if (normalCoverage) result.put("scope", "Raised artificial grass habitat on a normal generator; ordinary tick spawning with population resets between waves. This proves a standard spawn path, not sustained natural-terrain abundance.");
        Files.writeString(Path.of("creature-coverage-result.json"), gson.toJson(result));
        write(result); output.close();
        Files.writeString(Path.of("statistics-result.txt"), complete ? "COMPLETE\n" : "FAIL missing=" + missing + "\n");
        var server = level.getServer(); level = null; server.halt(false);
    }

    private void fail(MinecraftServer server, Exception failure) {
        failure.printStackTrace();
        try { finish(failure); }
        catch (Exception writeFailure) { writeFailure.printStackTrace(); level = null; server.halt(false); }
    }
}
