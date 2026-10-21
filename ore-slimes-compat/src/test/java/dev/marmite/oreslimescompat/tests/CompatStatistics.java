package dev.marmite.oreslimescompat.tests;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import dev.marmite.oreslimescompat.SpawnerConfig;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Observes normal server ticks; never invokes spawn helpers or replaces the mob census. */
final class CompatStatistics {
    private static final int DURATION = 24000;
    private static final boolean OBSERVE_COWS = Boolean.getBoolean("compat.observeCows");
    private static final int MAX_COW_ROUNDS = 10;
    private static final String[] PHASES = OBSERVE_COWS ? new String[]{"cows-defaults-one-player"}
            : new String[]{"defaults-one-player", "defaults-two-players", "disabled-two-players"};
    private final Gson gson = new Gson();
    private final Map<String, Integer> joins = new TreeMap<>();
    private final Map<String, Integer> finalized = new TreeMap<>();
    private final ArrayList<Map<String, Object>> additions = new ArrayList<>();
    private final HashSet<UUID> naturalCowAttempts = new HashSet<>();
    private final HashSet<UUID> naturalCowJoins = new HashSet<>();
    private final HashSet<UUID> verifiedCows = new HashSet<>();
    private int round;
    private BufferedWriter output;
    private ServerLevel level;
    private int phase = -1;
    private int ticks;
    private int activeSamples;

    CompatStatistics() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::joined);
        NeoForge.EVENT_BUS.addListener(this::finalized);
    }

    private void started(ServerStartedEvent event) {
        try {
            level = event.getServer().overworld();
            if (Boolean.getBoolean("compat.creatureInventory")) {
                Map<String, Object> inventory = new TreeMap<>();
                for (var type : BuiltInRegistries.ENTITY_TYPE) if (type.getCategory() == MobCategory.CREATURE) {
                    var biomes = new ArrayList<String>();
                    level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().forEach(biome -> {
                        for (var category : MobCategory.values()) {
                            if (biome.value().getMobSettings().getMobs(category).unwrap().stream()
                                    .anyMatch(entry -> entry.value().type() == type)) {
                                biomes.add(category.name() + " @ " + biome.key());
                            }
                        }
                    });
                    inventory.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), biomes);
                }
                Files.writeString(Path.of("creature-inventory.json"), gson.toJson(inventory));
                Files.writeString(Path.of("statistics-result.txt"), "COMPLETE\n");
                level = null;
                event.getServer().halt(false);
                return;
            }
            output = Files.newBufferedWriter(Path.of("statistics.jsonl"));
            if (SpawnerConfig.INTERVAL.get() != 1200 || SpawnerConfig.MAX_NEARBY.get() != 3
                    || SpawnerConfig.CHANCE.get() != 0.35 || SpawnerConfig.VANILLA.get()
                    || SpawnerConfig.ATTEMPTS.get() != 4 || SpawnerConfig.COUNT_RADIUS.get() != 72
                    || SpawnerConfig.MIN_DISTANCE.get() != 18 || SpawnerConfig.MAX_DISTANCE.get() != 52) {
                throw new IllegalStateException("Statistics require default config; reset run-statistics/config");
            }
            addPlayer("StatisticsOne", 530.5);
            event.getServer().tickRateManager().requestGameToSprint(
                    DURATION * (OBSERVE_COWS ? MAX_COW_ROUNDS : PHASES.length) + 2400);
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }

    private void addPlayer(String name, double x) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
        player.setPos(x, 65, 530.5);
        level.addNewPlayer(player);
        level.getChunkSource().move(player);
    }

    private void joined(EntityJoinLevelEvent event) {
        if (phase < 0 || event.getLevel() != level || event.loadedFromDisk() || event.isCanceled()
                || !(event.getEntity() instanceof Mob)) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        joins.merge(type, 1, Integer::sum);
        var entity = event.getEntity();
        if (naturalCowAttempts.remove(entity.getUUID())) naturalCowJoins.add(entity.getUUID());
        additions.add(Map.of("type", type, "gameTime", level.getGameTime(),
                "uuid", entity.getUUID().toString(), "naturalCow", naturalCowJoins.contains(entity.getUUID()),
                "x", entity.getX(), "y", entity.getY(), "z", entity.getZ()));
    }

    private void finalized(FinalizeSpawnEvent event) {
        if (phase < 0 || event.getEntity().level() != level) return;
        String key = event.getSpawnType() + ":" + BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        finalized.merge(key, 1, Integer::sum);
        if (event.getEntity().getType() == EntityType.COW && event.getSpawnType() == EntitySpawnReason.NATURAL) {
            naturalCowAttempts.add(event.getEntity().getUUID());
        }
    }

    private void tick(ServerTickEvent.Post event) {
        if (level == null) return;
        try {
            ticks++;
            var state = level.getChunkSource().getLastSpawnState();
            if (phase < 0) {
                if (ticks >= 600 && state != null && state.getSpawnableChunkCount() > 0) {
                    beginPhase(0);
                } else if (ticks >= 2400) throw new IllegalStateException("No active natural-spawn census after warmup");
                return;
            }
            if (ticks % 200 == 0) {
                if (state == null || state.getSpawnableChunkCount() <= 0) {
                    throw new IllegalStateException("Natural-spawn census inactive");
                }
                activeSamples++;
                for (UUID id : naturalCowJoins) {
                    var cow = level.getEntity(id);
                    if (cow != null && cow.isAlive() && cow.getType() == EntityType.COW) verifiedCows.add(id);
                }
                Map<String, Integer> population = new TreeMap<>();
                for (var entity : level.getAllEntities()) if (entity instanceof Mob) {
                    population.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), 1, Integer::sum);
                }
                write(Map.of("record", "sample", "phase", PHASES[phase], "ticks", ticks,
                        "spawnableChunks", state.getSpawnableChunkCount(),
                        "creatureCensus", state.getMobCategoryCounts().getInt(MobCategory.CREATURE),
                        "population", population, "cumulativeJoins", joins, "joinEvents", additions,
                        "verifiedNaturalCows", verifiedCows.stream().map(UUID::toString).sorted().toList()));
                additions.clear();
            }
            if (ticks >= DURATION) {
                write(Map.of("record", "summary", "phase", PHASES[phase], "ticks", ticks,
                        "activeSamples", activeSamples, "joins", joins, "finalizeAttempts", finalized,
                        "round", round, "verifiedNaturalCows", verifiedCows.stream().map(UUID::toString).sorted().toList()));
                System.out.println("COMPAT_STATISTICS " + PHASES[phase] + " joins=" + joins);
                if (OBSERVE_COWS && verifiedCows.isEmpty()) {
                    if (round >= MAX_COW_ROUNDS) throw new IllegalStateException("No natural cow observed in " + round + " rounds");
                    beginPhase(0);
                } else if (phase < PHASES.length - 1) beginPhase(phase + 1);
                else {
                    output.close();
                    Files.writeString(Path.of("statistics-result.txt"), "COMPLETE\n");
                    level = null;
                    event.getServer().halt(false);
                }
            }
        } catch (Exception failure) { fail(event.getServer(), failure); }
    }

    private void beginPhase(int next) throws Exception {
        // Reset subjects only between scenarios; no removals during the measurement window.
        var mobs = new ArrayList<Mob>();
        for (var entity : level.getAllEntities()) if (entity instanceof Mob mob) mobs.add(mob);
        mobs.forEach(Mob::discard);
        if (next == 1) addPlayer("StatisticsTwo", 550.5);
        SpawnerConfig.ENABLED.set(next != 2);
        phase = next;
        round++;
        ticks = 0;
        activeSamples = 0;
        joins.clear();
        finalized.clear();
        additions.clear();
        naturalCowAttempts.clear();
        naturalCowJoins.clear();
        verifiedCows.clear();
        write(Map.of("record", "start", "phase", PHASES[phase], "gameTime", level.getGameTime(),
                "players", level.players().size(), "seed", level.getSeed(), "round", round));
    }

    private void write(Object record) throws Exception {
        output.write(gson.toJson(record));
        output.newLine();
        output.flush();
    }

    private void fail(MinecraftServer server, Exception failure) {
        failure.printStackTrace();
        try {
            if (output != null) output.close();
            Files.writeString(Path.of("statistics-result.txt"), "FAIL " + failure + "\n");
        } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        level = null;
        server.halt(false);
    }
}
