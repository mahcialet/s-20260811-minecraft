package dev.pettingturretcompat.mixin;

import dev.pettingturretcompat.PettingProtection;
import liedge.ltxindustries.entity.TurretRocketEntity;
import liedge.ltxindustries.entity.UpgradesAwareEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UpgradesAwareEntity.class, remap = false)
public abstract class TurretRocketAreaMixin {
    // LTXI 1.1.3 shares AOE selection with weapons. Only turret rockets opt in.
    @Inject(method = "lambda$getEntitiesInAOE$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void pettingTurretCompat$excludeSplashTarget(LivingEntity owner, Entity excluded,
            Entity candidate, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof TurretRocketEntity && PettingProtection.isProtected(candidate)) {
            cir.setReturnValue(false);
        }
    }
}
