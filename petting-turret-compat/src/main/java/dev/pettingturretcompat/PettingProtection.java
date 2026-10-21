package dev.pettingturretcompat;

import dev.pettingturretcompat.mixin.PettingTameInvoker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class PettingProtection {
    private PettingProtection() {}

    public static boolean isProtected(Entity target) {
        // The supplied 4.2.3-beta.3 jar has no PetHelper API. Delegate to its
        // own tame predicate, without resolving or comparing player owners.
        return target instanceof Mob mob && PettingTameInvoker.pettingTurretCompat$isCustomPet(mob);
    }
}
