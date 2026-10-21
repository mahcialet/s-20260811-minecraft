package dev.pettingturretcompat.mixin;

import dev.pettingturretcompat.PettingProtection;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Queue;
import liedge.limacore.network.sync.LimaDataWatcher;
import liedge.ltxindustries.blockentity.turret.TurretBlockEntity;
import liedge.ltxindustries.lib.TurretTargetTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hooks are pinned to LTXI 1.1.3: its turret search lambda and queue lifecycle. */
@Mixin(value = TurretBlockEntity.class, remap = false)
public abstract class TurretBlockEntityMixin {
    @Shadow @Final private Queue<Entity> targetQueue;
    @Shadow private LimaDataWatcher<IntList> queueWatcher;

    @Inject(method = "lambda$tickServer$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void pettingTurretCompat$excludeCandidate(Player owner, TurretTargetTracker tracker,
            Entity candidate, CallbackInfoReturnable<Boolean> cir) {
        if (PettingProtection.isProtected(candidate)) cir.setReturnValue(false);
    }

    @Inject(method = "targetStillValid", at = @At("HEAD"), cancellable = true, require = 1)
    private void pettingTurretCompat$recheckActiveTarget(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (PettingProtection.isProtected(target)) cir.setReturnValue(false);
    }

    @Inject(method = "tickServer", at = @At("HEAD"), require = 1)
    private void pettingTurretCompat$pruneNewlyTamed(ServerLevel level, BlockPos pos,
            BlockState state, CallbackInfo ci) {
        // Arc attacks the entire queue without targetStillValid for each victim.
        // Prune before firing, preserving the tracker's and client's queue state.
        TurretBlockEntity turret = (TurretBlockEntity) (Object) this;
        TurretTargetTracker tracker = TurretTargetTracker.getOrDefault(turret.getOwner());
        boolean changed = targetQueue.removeIf(target -> {
            if (!PettingProtection.isProtected(target)) return false;
            tracker.remove(target);
            return true;
        });
        if (changed) queueWatcher.setChanged(true);
    }
}
