package dev.pettingturretcompat.mixin;

import dev.pettingturretcompat.PettingProtection;
import java.util.UUID;
import liedge.ltxindustries.entity.HomingProjectileEntity;
import liedge.ltxindustries.entity.TurretRocketEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HomingProjectileEntity.class, remap = false)
public abstract class TurretRocketHomingMixin {
    @Shadow private UUID targetUUID;
    @Shadow private Entity targeted;
    // Release a newly tamed target on the next server tick, without changing
    // shared homing behavior for handheld weapons or non-pet targets.
    @Inject(method = "tickServer", at = @At("HEAD"), require = 1)
    private void pettingTurretCompat$releaseTamedTarget(ServerLevel level, LivingEntity owner,
            CallbackInfo ci) {
        if ((Object) this instanceof TurretRocketEntity rocket
                && PettingProtection.isProtected(rocket.getTargetEntity())) {
            // LTXI 1.1.3's setTargetEntity(null) is a no-op. Clear both;
            // otherwise getTargetEntity resolves the entity again from UUID.
            targetUUID = null;
            targeted = null;
        }
    }
}
