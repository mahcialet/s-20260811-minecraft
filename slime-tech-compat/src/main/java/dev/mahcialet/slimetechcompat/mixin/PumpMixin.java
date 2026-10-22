package dev.mahcialet.slimetechcompat.mixin;

import dev.mahcialet.slimetechcompat.safety.PumpSafety;
import dev.wizardjw.slimetech.blockentity.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PumpBlockEntity.class, remap = false)
public abstract class PumpMixin {
    @Inject(method = "loadMachineData(Lnet/minecraft/world/level/storage/ValueInput;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$loadIdentity(ValueInput input, CallbackInfo ci) {
        PumpSafety.load((PumpBlockEntity)(Object)this, input);
    }
    @Inject(method = "saveMachineData(Lnet/minecraft/world/level/storage/ValueOutput;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$saveIdentity(ValueOutput output, CallbackInfo ci) {
        PumpSafety.save((PumpBlockEntity)(Object)this, output);
    }
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Ldev/wizardjw/slimetech/blockentity/PumpBlockEntity;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private static void slimeTechCompat$guardTick(Level level, BlockPos pos, BlockState state,
                                                 PumpBlockEntity pump, CallbackInfo ci) {
        if (PumpSafety.quarantined(pump)) ci.cancel();
    }
    @Inject(method = "canExtractFluid(Lnet/minecraft/core/Direction;I)Z",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$guardExtraction(Direction side, int type, CallbackInfoReturnable<Boolean> cir) {
        if (PumpSafety.quarantined((PumpBlockEntity)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method = "canInsertEnergy(Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$guardEnergyConnection(Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (PumpSafety.quarantined((PumpBlockEntity)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method = "insertEnergy(Lnet/minecraft/core/Direction;IZ)I", at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$guardEnergyInput(Direction side, int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        if (PumpSafety.quarantined((PumpBlockEntity)(Object)this)) cir.setReturnValue(0);
    }
    @Inject(method = "isActivelyUsingEnergy()Z", at = @At("HEAD"), cancellable = true,
            require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$guardDemand(CallbackInfoReturnable<Boolean> cir) {
        if (PumpSafety.quarantined((PumpBlockEntity)(Object)this)) cir.setReturnValue(false);
    }
    @Redirect(method = "serverTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/SimpleContainerData;set(II)V", ordinal = 0),
            require = 1, expect = 1, allow = 1)
    private static void slimeTechCompat$keepBufferedIdentity(SimpleContainerData data, int slot, int value) {
        PumpSafety.setDetectedType(data, slot, value);
    }
    @Redirect(method = "serverTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/SimpleContainerData;set(II)V", ordinal = 3),
            require = 1, expect = 1, allow = 1)
    private static void slimeTechCompat$rejectSourceRetyping(SimpleContainerData data, int slot, int value) {
        // The fourth set() otherwise overwrites the buffered type BEFORE its mismatch check.
        PumpSafety.setDetectedType(data, slot, value);
    }
    @Redirect(method = {"findSourceRoute", "findSourceThroughPipeNetwork"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;isSource()Z"),
            require = 2, expect = 2, allow = 2)
    private static boolean slimeTechCompat$sourcePolicy(FluidState source) {
        return PumpSafety.allowWorldSource(source);
    }
}
