package dev.pettingturretcompat.mixin;

import net.minecraft.world.entity.Mob;
import net.yigitguven.petting.procedures.GoldenWheatRightclickedProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GoldenWheatRightclickedProcedure.class, remap = false)
public interface PettingTameInvoker {
    @Invoker("isCustomPet")
    static boolean pettingTurretCompat$isCustomPet(Mob mob) {
        throw new AssertionError("Required Petting tame invoker was not applied");
    }
}
