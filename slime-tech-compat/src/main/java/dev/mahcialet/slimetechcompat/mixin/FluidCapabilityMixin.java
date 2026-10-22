package dev.mahcialet.slimetechcompat.mixin;

import dev.mahcialet.slimetechcompat.safety.TransactionalFluidHandler;
import dev.wizardjw.slimetech.blockentity.FluidTankBlockEntity;
import dev.wizardjw.slimetech.blockentity.PumpBlockEntity;
import dev.wizardjw.slimetech.blockentity.SlimeTechCapabilityAdapters;
import dev.wizardjw.slimetech.blockentity.SlimeTechFluidNode;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SlimeTechCapabilityAdapters.class, remap = false)
public abstract class FluidCapabilityMixin {
    @Inject(method = "fluid(Ldev/wizardjw/slimetech/blockentity/SlimeTechFluidNode;Lnet/minecraft/core/Direction;)Lnet/neoforged/neoforge/transfer/ResourceHandler;",
            at = @At("RETURN"), cancellable = true, require = 1, expect = 1, allow = 1)
    private static void slimeTechCompat$transactionalFluid(SlimeTechFluidNode node, Direction side,
            CallbackInfoReturnable<ResourceHandler<FluidResource>> cir) {
        if (node instanceof FluidTankBlockEntity || node instanceof PumpBlockEntity)
            cir.setReturnValue(new TransactionalFluidHandler(node, cir.getReturnValue()));
    }
}
