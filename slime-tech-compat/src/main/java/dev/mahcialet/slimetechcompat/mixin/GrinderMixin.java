package dev.mahcialet.slimetechcompat.mixin;

import dev.mahcialet.slimetechcompat.CompatConfig;
import dev.wizardjw.slimetech.blockentity.GrinderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = GrinderBlockEntity.class, remap = false)
public abstract class GrinderMixin {
    @ModifyConstant(method = {"<init>", "loadMachineData"}, constant = @Constant(intValue = 2000),
            require = 2, expect = 2, allow = 2)
    private int slimeTechCompat$initialEnergy(int original) {
        return CompatConfig.get().fixGrinderEnergy() ? 0 : original;
    }
    @ModifyConstant(method = "serverTick", constant = @Constant(intValue = 15),
            require = 1, expect = 1, allow = 1)
    private static int slimeTechCompat$requireFullCost(int original) {
        // The observed completion cost is 120 FE. Do not invent an unsupported 15 FE/t balance.
        return CompatConfig.get().fixGrinderEnergy() ? 120 : original;
    }
}
