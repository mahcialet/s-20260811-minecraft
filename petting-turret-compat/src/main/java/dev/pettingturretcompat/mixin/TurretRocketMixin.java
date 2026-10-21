package dev.pettingturretcompat.mixin;

import dev.pettingturretcompat.PettingProtection;
import liedge.ltxindustries.entity.TurretRocketEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TurretRocketEntity.class, remap = false)
public abstract class TurretRocketMixin {
    // LTXI 1.1.3 uses this for direct hits and splash, including when the
    // launching turret has been removed. Recheck at impact after flight time.
    @Inject(method = "hurtTarget", at = @At("HEAD"), cancellable = true, require = 1)
    private void pettingTurretCompat$recheckImpact(ServerLevel level, Entity target,
            LivingEntity owner, Vec3 origin, boolean direct, CallbackInfo ci) {
        if (PettingProtection.isProtected(target)) ci.cancel();
    }
}
