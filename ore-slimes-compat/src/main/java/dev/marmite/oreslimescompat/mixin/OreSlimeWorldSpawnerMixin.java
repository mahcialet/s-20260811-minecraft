package dev.marmite.oreslimescompat.mixin;

import com.jeremiah.oreslimes.entity.OreSlimeEntity;
import com.jeremiah.oreslimes.world.OreSlimeWorldSpawner;
import com.mojang.logging.LogUtils;
import dev.marmite.oreslimescompat.SpawnerConfig;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OreSlimeWorldSpawner.class, remap = false)
public abstract class OreSlimeWorldSpawnerMixin {
    @Shadow private static int tickCounter;
    @Shadow private static void calmNearbyVanillaSlimes(ServerLevel level, ServerPlayer player) { throw new AssertionError(); }
    @Shadow private static boolean trySpawnOreSlimeNearPlayer(ServerLevel level, ServerPlayer player, RandomSource random) { throw new AssertionError(); }
    @Shadow private static void trySpawnVanillaSlimeNearPlayer(ServerLevel level, ServerPlayer player, RandomSource random) { throw new AssertionError(); }
    @Unique private static boolean osc$added;

    // Replace only orchestration; upstream species, size, spawn rules and temperament remain intact.
    @Inject(method = "onServerTick", at = @At("HEAD"), cancellable = true)
    private static void osc$tick(ServerTickEvent.Post event, CallbackInfo ci) {
        ci.cancel();
        tickCounter++;
        boolean calm = Math.floorMod(tickCounter, 10) == 0;
        boolean spawn = SpawnerConfig.ENABLED.get()
                && Math.floorMod(tickCounter, SpawnerConfig.INTERVAL.get()) == 0;
        if (!calm && !spawn) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var coverage = new ArrayList<Vec3>();
            int addedThisPass = 0;
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                if (calm) calmNearbyVanillaSlimes(level, player);
                if (!spawn) continue;
                double separation = 2.0 * SpawnerConfig.countRadius();
                boolean covered = coverage.stream().anyMatch(p -> {
                    double dx = p.x - player.getX(), dz = p.z - player.getZ();
                    return dx * dx + dz * dz <= separation * separation;
                });
                if (covered) { osc$debug("Ore Slime spawn skipped: overlapping player coverage"); continue; }
                // Reserve before the probability roll, so extra players do not multiply the chance.
                coverage.add(player.position());
                int nearby = level.getEntitiesOfClass(OreSlimeEntity.class,
                        player.getBoundingBox().inflate(SpawnerConfig.countRadius())).size();
                if (nearby >= SpawnerConfig.MAX_NEARBY.get()) {
                    osc$debug("Ore Slime spawn skipped: nearby=" + nearby + " max=" + SpawnerConfig.MAX_NEARBY.get());
                    continue;
                }
                var state = level.getChunkSource().getLastSpawnState();
                if (state == null) { osc$debug("Ore Slime spawn skipped: no natural spawn census"); continue; }
                int cap = MobCategory.CREATURE.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / 289;
                if (state.getMobCategoryCounts().getInt(MobCategory.CREATURE) + addedThisPass >= cap) {
                    osc$debug("Ore Slime spawn skipped: CREATURE cap reached");
                    continue;
                }
                RandomSource random = level.getRandom();
                if (random.nextDouble() >= SpawnerConfig.CHANCE.get()) {
                    osc$debug("Ore Slime spawn skipped: chance failed");
                    continue;
                }
                for (int attempt = 0; attempt < SpawnerConfig.ATTEMPTS.get(); attempt++) {
                    osc$added = false;
                    trySpawnOreSlimeNearPlayer(level, player, random);
                    if (osc$added) { addedThisPass++; break; }
                }
                if (!SpawnerConfig.VANILLA.get()) {
                    osc$debug("Vanilla slime injection disabled");
                } else if (level.getDifficulty().getId() != 0
                        && random.nextDouble() < SpawnerConfig.VANILLA_CHANCE.get()) {
                    trySpawnVanillaSlimeNearPlayer(level, player, random);
                }
            }
        }
    }

    @Inject(method = "findSurfaceSpawnPos", at = @At("HEAD"), cancellable = true)
    private static void osc$position(ServerLevel level, ServerPlayer player, RandomSource random,
                                     CallbackInfoReturnable<BlockPos> cir) {
        double min = SpawnerConfig.MIN_DISTANCE.get(), max = SpawnerConfig.maxDistance();
        double radius = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
        double angle = random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * radius);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * radius);
        // Do not synchronously load terrain just to try a spawn.
        if (!level.hasChunk(x >> 4, z >> 4)) { cir.setReturnValue(null); return; }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        cir.setReturnValue(y <= level.getMinY() || y >= level.getMaxY() ? null : new BlockPos(x, y, z));
    }

    @Redirect(method = "trySpawnOreSlimeNearPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean osc$add(ServerLevel level, Entity entity) {
        osc$added = level.addFreshEntity(entity);
        if (osc$added) osc$debug("Ore Slime spawned at " + entity.blockPosition().toShortString());
        return osc$added;
    }

    @Unique private static void osc$debug(String message) {
        if (SpawnerConfig.DEBUG.get()) LogUtils.getLogger().debug(message);
    }
}
