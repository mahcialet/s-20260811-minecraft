package dev.marmite.oreslimescompat.tests;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sustained population observation: ordinary scheduler, real players and census, no repeated resets. */
final class Phase2Statistics {
    private static final int DURATION = 72000;
    private record Attempt(String type, long tick, boolean orePresent) {}
    private final String profile = System.getProperty("compat.phase2.profile", "phase2");
    private final String terrain = System.getProperty("compat.phase2.terrain", "normal");
    private final String weightVariant = System.getProperty("compat.phase2.weightScale", "original");
    private final int players = Integer.getInteger("compat.phase2.players", 1);
    private final int trial = Integer.getInteger("compat.phase2.trial", 0);
    private int readyTicks;
    private int readyChunks;
    private int loadedChunks;
    private final Map<UUID, String> spawnReasons = new java.util.concurrent.ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final Map<UUID, Attempt> natural = new HashMap<>();
    private final Set<UUID> joined = new HashSet<>();
    private final Map<String, Integer> verified = new TreeMap<>();
    private final Map<String, Integer> origins = new TreeMap<>();
    private final List<Map<String, Object>> checkpoints = new ArrayList<>();
    private ServerLevel level;
    private ServerPlayer observer;
    private BufferedWriter output;
    private int warmup;
    private int elapsed;
    private int activeSamples;
    private boolean measuring;

    Phase2Statistics() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::finalized);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::joined);
    }

    private void started(ServerStartedEvent event) {
        try {
            if (!Set.of("upstream", "phase1", "phase2").contains(profile)
                    || !Set.of("normal", "flat").contains(terrain) || players < 1 || players > 2
                    || !Set.of("original", "balanced", "equal-all", "equal-ore").contains(weightVariant)
                    || (!profile.equals("phase2") && !weightVariant.equals("original"))) {
                throw new IllegalArgumentException("Invalid Phase 2 observation properties");
            }
            boolean compatLoaded = net.neoforged.fml.ModList.get().isLoaded("ore_slimes_compat");
            if (compatLoaded == profile.equals("upstream")) throw new IllegalStateException("Unexpected compat mod load state: " + compatLoaded);
            level = event.getServer().overworld();
            output = Files.newBufferedWriter(Path.of("phase2.jsonl"));
            if (compatLoaded) write(Map.of("record", "effectiveConfig", "values", Phase2PolicyProbe.verify(level, profile, terrain, weightVariant)));
            EqualWeightExperiment.verify(level);
            recordBiomeSpawns();
            var site = selectSite();
            int y = site.getY();
            if (trial > 0) {
                long expectedSeed = 384731L + (trial - 1L) * 104729L;
                if (trial > 20 || level.getSeed() != expectedSeed) throw new IllegalStateException("Unexpected trial seed");
                var center = new ChunkPos(site.getX() >> 4, site.getZ() >> 4);
                for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
                    level.getChunk(center.x() + dx, center.z() + dz);
                }
            }
            write(Map.of("record", "siteSelection", "position", List.of(site.getX(), site.getY(), site.getZ()),
                    "method", terrain.equals("flat") ? "Fixed flat coordinate 530,530 on dry generated floor"
                            : "Closest taiga from 530,64,530, radius8192 horizontalStep32 verticalStep64; first dry taiga surface in deterministic radius0..128 step4 rings"));
            for (int n = 0; n < players; n++) {
                String name = "Phase2Observer" + n;
                var player = FakePlayerFactory.get(level, new GameProfile(
                        UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
                player.setPos(site.getX() + 0.5, y, site.getZ() + 0.5);
                level.addNewPlayer(player);
                level.getChunkSource().move(player);
                if (n == 0) observer = player;
            }
            var setup = new TreeMap<String, Object>(Map.of("record", "setup", "profile", profile, "terrain", terrain,
                    "players", players, "seed", level.getSeed(), "observerY", y,
                    "biome", biome(observer.blockPosition()), "durationTicks", DURATION, "compatModLoaded", compatLoaded,
                    "conditions", "Unmodified generated terrain; fixed noon and clear weather; one initial population reset after chunk warmup, no subsequent removal; stationary colocated FakePlayers"));
            setup.put("weightVariant", weightVariant);
            setup.put("trial", trial);
            setup.put("protocol", trial > 0 ? "prepared-v2" : "original-v1");
            write(setup);
            event.getServer().setWeatherParameters(DURATION + 14000, 0, false, false);
            event.getServer().tickRateManager().requestGameToSprint(DURATION + 14000);
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }

    private void recordBiomeSpawns() throws Exception {
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var key : List.of(net.minecraft.world.level.biome.Biomes.TAIGA, net.minecraft.world.level.biome.Biomes.DESERT)) {
            var entries = new ArrayList<Map<String, Object>>();
            int oreWeight = 0;
            int totalWeight = 0;
            int oreEntries = 0;
            for (var entry : registry.getOrThrow(key).value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
                var spawn = entry.value();
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type()).toString();
                entries.add(Map.of("type", type, "weight", entry.weight(), "minCount", spawn.minCount(), "maxCount", spawn.maxCount()));
                totalWeight += entry.weight();
                if (type.startsWith("oreslimes:")) { oreWeight += entry.weight(); oreEntries++; }
            }
            write(Map.of("record", "effectiveBiomeSpawns", "biome", key.identifier().toString(),
                    "category", "CREATURE", "entries", entries, "oreEntryCount", oreEntries,
                    "oreWeightSum", oreWeight, "totalWeightSum", totalWeight, "weightVariant", weightVariant));
        }
    }

    private net.minecraft.core.BlockPos selectSite() {
        if (terrain.equals("flat")) {
            var site = drySurface(530, 530);
            if (site == null) throw new IllegalStateException("Fixed flat observer coordinate has no dry generated floor");
            return site;
        }
        var located = level.findClosestBiome3d(holder -> holder.is(net.minecraft.world.level.biome.Biomes.TAIGA),
                new net.minecraft.core.BlockPos(530, 64, 530), 8192, 32, 64);
        if (located == null) throw new IllegalStateException("No taiga in deterministic search radius");
        var center = located.getFirst();
        for (int radius = 0; radius <= 128; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var site = drySurface(center.getX() + dx, center.getZ() + dz);
                    if (site != null && level.getBiome(site).is(net.minecraft.world.level.biome.Biomes.TAIGA)) return site;
                }
            }
        }
        throw new IllegalStateException("Located taiga has no dry surface within deterministic 128-block search");
    }

    private net.minecraft.core.BlockPos drySurface(int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var site = new net.minecraft.core.BlockPos(x, y, z);
        var floor = site.below();
        if (y <= level.getMinY() || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                || !level.getFluidState(floor).isEmpty()
                || !level.getBlockState(site).getCollisionShape(level, site).isEmpty()
                || !level.getBlockState(site.above()).getCollisionShape(level, site.above()).isEmpty()
                || !level.getFluidState(site).isEmpty() || !level.getFluidState(site.above()).isEmpty()) return null;
        return site;
    }

    private static String id(Mob mob) { return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(); }
    private static boolean relevant(Mob mob) {
        return mob.getType().getCategory() == MobCategory.CREATURE || id(mob).equals("minecraft:slime");
    }
    private String biome(net.minecraft.core.BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
    }
    private boolean slimeChunk(ChunkPos pos) {
        return WorldgenRandom.seedSlimeChunk(pos.x(), pos.z(), level.getSeed(), 987234911L).nextInt(10) == 0;
    }
    private boolean orePresent() {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && mob.isAlive() && id(mob).startsWith("oreslimes:")) return true;
        }
        return false;
    }
    private void finalized(FinalizeSpawnEvent event) {
        if (event.getEntity().level() != level || event.isSpawnCancelled()
                || !relevant(event.getEntity())) return;
        spawnReasons.put(event.getEntity().getUUID(), event.getSpawnType().name());
        if (!measuring || event.getSpawnType() != EntitySpawnReason.NATURAL) return;
        natural.put(event.getEntity().getUUID(), new Attempt(id(event.getEntity()), level.getGameTime(), orePresent()));
    }
    private void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() != level || !(event.getEntity() instanceof Mob mob) || !relevant(mob)) return;
        String reason = spawnReasons.remove(mob.getUUID());
        if (!measuring || event.loadedFromDisk() || event.isCanceled()) return;
        try {
            joined.add(mob.getUUID());
            var spawnCallers = StackWalker.getInstance().walk(frames -> frames
                    .filter(frame -> frame.getClassName().startsWith("com.jeremiah.oreslimes.")
                            || frame.getClassName().contains("BeehiveBlockEntity"))
                    .map(frame -> frame.getClassName() + "#" + frame.getMethodName()).toList());
            boolean backup = spawnCallers.stream().anyMatch(frame -> frame.contains("OreSlimeWorldSpawner#"));
            boolean beeAbility = spawnCallers.stream().anyMatch(frame -> frame.endsWith("OreSlimeEntity#maybeSpawnBee"));
            boolean hive = spawnCallers.stream().anyMatch(frame -> frame.contains("BeehiveBlockEntity"));
            String source = backup ? "backup-direct" : beeAbility ? "ore-slime-ability"
                    : hive ? "hive-release" : natural.containsKey(mob.getUUID()) ? "natural"
                    : "CHUNK_GENERATION".equals(reason) ? "chunk-generation" : "other-or-unclassified";
            origins.merge(source + "/" + id(mob), 1, Integer::sum);
            write(Map.of("record", "join", "type", id(mob), "uuid", mob.getUUID(), "source", source,
                    "gameTime", level.getGameTime(), "elapsedTicks", elapsed, "slimeChunk", slimeChunk(mob.chunkPosition()),
                    "biome", biome(mob.blockPosition()), "position", List.of(mob.getX(), mob.getY(), mob.getZ()),
                    "spawnCallers", spawnCallers));
        } catch (Exception failure) { fail(level.getServer(), failure); }
    }
    private Map<String, Integer> counts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (String key : List.of("oreSlime", "vanillaSlime", "rabbit", "cow", "sheep", "pig", "totalCreature")) counts.put(key, 0);
        for (var entity : level.getAllEntities()) if (entity instanceof Mob mob && mob.isAlive()) {
            String type = id(mob);
            if (mob.getType().getCategory() == MobCategory.CREATURE) counts.merge("totalCreature", 1, Integer::sum);
            if (type.startsWith("oreslimes:")) counts.merge("oreSlime", 1, Integer::sum);
            if (type.equals("minecraft:slime")) counts.merge("vanillaSlime", 1, Integer::sum);
            for (String animal : List.of("rabbit", "cow", "sheep", "pig")) if (type.equals("minecraft:" + animal)) counts.merge(animal, 1, Integer::sum);
        }
        return counts;
    }
    private void tick(ServerTickEvent.Post event) {
        if (level == null) return;
        try {
            var clock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
            level.getServer().clockManager().setTotalTicks(clock, 6000);
            var state = level.getChunkSource().getLastSpawnState();
            int chunks = state == null ? 0 : state.getSpawnableChunkCount();
            if (!measuring) {
                warmup++;
                if (trial > 0) {
                    readyChunks = 0;
                    loadedChunks = 0;
                    var center = observer.chunkPosition();
                    for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                        var pos = new ChunkPos(center.x() + dx, center.z() + dz);
                        if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null) loadedChunks++;
                        if (level.areEntitiesActuallyLoadedAndTicking(pos)) readyChunks++;
                    }
                    readyTicks = chunks == 289 && loadedChunks == 289 && readyChunks == 289 ? readyTicks + 1 : 0;
                }
                if (warmup < 600 || chunks == 0 || (trial > 0 && readyTicks < 200)) {
                    if (warmup > (trial > 0 ? 12000 : 1800)) throw new IllegalStateException("Chunks/entities not ready: " + chunks + "/" + loadedChunks + "/" + readyChunks);
                    return;
                }
                if (trial > 0) write(Map.of("record", "prepared", "protocol", "prepared-v2", "warmupTicks", warmup,
                        "candidateChunks", chunks, "fullChunks", loadedChunks, "entityTickingLoadedChunks", readyChunks,
                        "stableReadyTicks", readyTicks, "pregeneratedRadius", 10));
                var mobs = new ArrayList<Mob>();
                for (var entity : level.getAllEntities()) if (entity instanceof Mob mob) mobs.add(mob);
                mobs.forEach(Mob::discard);
                measuring = true;
                write(Map.of("record", "start", "gameTime", level.getGameTime(), "initialRemoved", mobs.size(),
                        "spawnableChunks", chunks, "nearbyPlayers", level.getChunkSource().chunkMap.getPlayersCloseForSpawning(observer.chunkPosition()).size()));
                return;
            }
            elapsed++;
            for (var iterator = natural.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (level.getGameTime() <= entry.getValue().tick()) continue;
                var entity = level.getEntity(entry.getKey());
                if (joined.remove(entry.getKey()) && entity != null && entity.isAlive()) {
                    var attempt = entry.getValue();
                    verified.merge(attempt.type(), 1, Integer::sum);
                    write(Map.of("record", "verifiedNatural", "type", attempt.type(), "uuid", entry.getKey(),
                            "spawnGameTime", attempt.tick(), "verifiedGameTime", level.getGameTime(),
                            "orePresentAtAttempt", attempt.orePresent(), "slimeChunk", slimeChunk(entity.chunkPosition())));
                }
                iterator.remove();
            }
            if (elapsed % 200 == 0) {
                int nearbyPlayers = level.getChunkSource().chunkMap.getPlayersCloseForSpawning(observer.chunkPosition()).size();
                if (chunks <= 0 || nearbyPlayers != players || (trial > 0 && chunks != 289)) {
                    throw new IllegalStateException("Inactive observation fixture at tick " + elapsed
                            + ": spawnableChunks=" + chunks + ", nearbyPlayers=" + nearbyPlayers + ", expected=" + players);
                }
                activeSamples++;
                Map<String, Object> sample = new TreeMap<>();
                sample.put("record", "sample"); sample.put("elapsedTicks", elapsed); sample.put("counts", counts());
                sample.put("spawnableChunks", chunks);
                sample.put("creatureCensus", state == null ? 0 : state.getMobCategoryCounts().getInt(MobCategory.CREATURE));
                sample.put("nearbyPlayers", nearbyPlayers);
                write(sample);
                if (elapsed == 12000 || elapsed == 36000 || elapsed == DURATION) {
                    checkpoints.add(sample);
                    System.out.println("PHASE2_OBSERVATION " + profile + " " + terrain + " players=" + players + " ticks=" + elapsed + " " + counts());
                }
            }
            if (elapsed >= DURATION) finish();
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }
    private void write(Object record) throws Exception {
        output.write(gson.toJson(record)); output.newLine(); output.flush();
    }
    private void finish() throws Exception {
        var result = new TreeMap<String, Object>();
        result.put("observationComplete", true); result.put("profile", profile); result.put("terrain", terrain);
        result.put("weightVariant", weightVariant);
        result.put("trial", trial);
        result.put("protocol", trial > 0 ? "prepared-v2" : "original-v1");
        result.put("players", players); result.put("seed", level.getSeed()); result.put("elapsedTicks", elapsed);
        result.put("activeSamples", activeSamples); result.put("checkpoints", checkpoints);
        result.put("verifiedNaturalByType", verified); result.put("joinsByOriginAndType", origins);
        result.put("limitations", "One seeded stationary location, fixed noon; counts cover loaded Overworld entities. COMPLETE means the observation duration finished, not that abundance or coexistence passed. Flat biome is an artificial selected habitat; normal terrain is generated unchanged. UUID proof covers successful NATURAL finalize/join/later-tick survival, not every spawn attempt. Backup and OreSlimeEntity.maybeSpawnBee origins identified from the actual joining call stack; unmatched joins remain unclassified.");
        Files.writeString(Path.of("phase2-result.json"), gson.toJson(result));
        Files.writeString(Path.of("phase2-result.txt"), "COMPLETE\n");
        output.close();
        var server = level.getServer(); level = null; server.halt(false);
    }
    private void fail(MinecraftServer server, Exception failure) {
        failure.printStackTrace();
        try { Files.writeString(Path.of("phase2-result.txt"), "FAIL " + failure + "\n"); if (output != null) output.close(); }
        catch (Exception ignored) { failure.addSuppressed(ignored); }
        level = null; server.halt(false);
    }
}
