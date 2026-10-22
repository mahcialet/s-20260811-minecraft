package dev.mahcialet.slimetechcompat.mixin;

import dev.mahcialet.slimetechcompat.safety.TankAccess;
import dev.mahcialet.slimetechcompat.safety.TankSafety;
import dev.wizardjw.slimetech.blockentity.FluidTankBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.wizardjw.slimetech.blockentity.SlimeTechFluidType;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class FluidTankMixin implements TankAccess {
    @Shadow private int localAmount;
    @Shadow private String storedFluidId;
    @Unique private int slimeTechCompat$unresolvedLegacyCode;

    @Inject(method = "loadMachineData(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("HEAD"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$preserveRawContents(ValueInput input, CallbackInfo ci) {
        localAmount = input.getIntOr("LocalFluidAmount", input.getIntOr("FluidAmount", 0));
        storedFluidId = input.getStringOr("FluidId", "");
        slimeTechCompat$unresolvedLegacyCode = input.getIntOr("SlimeTechCompatUnresolvedCode", input.getIntOr("FluidType", 0));
        if (localAmount > 0 && storedFluidId.isBlank()
                && slimeTechCompat$unresolvedLegacyCode >= 1 && slimeTechCompat$unresolvedLegacyCode <= 4) {
            storedFluidId = SlimeTechFluidType.toIdentifierString(slimeTechCompat$unresolvedLegacyCode);
        }
        ((FluidTankBlockEntity)(Object)this).refreshNetworkData();
        ci.cancel();
    }

    @Inject(method = "saveMachineData(Lnet/minecraft/world/level/storage/ValueOutput;)V", at = @At("TAIL"),
            require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$preserveLegacyDiagnostic(ValueOutput output, CallbackInfo ci) {
        if (localAmount != 0 && storedFluidId.isBlank() && slimeTechCompat$unresolvedLegacyCode != 0)
            output.putInt("SlimeTechCompatUnresolvedCode", slimeTechCompat$unresolvedLegacyCode);
    }
    @Accessor("localAmount")
    public abstract int slimeTechCompat$getLocalAmount();
    @Accessor("storedFluidId")
    public abstract String slimeTechCompat$getStoredFluidId();
    @Accessor("localAmount")
    public abstract void slimeTechCompat$setLocalAmount(int amount);
    @Accessor("storedFluidId")
    public abstract void slimeTechCompat$setStoredFluidId(String id);

    @Inject(method = {"canExtractFluid(Lnet/minecraft/core/Direction;I)Z", "canInsertFluid(Lnet/minecraft/core/Direction;I)Z"},
            at = @At("HEAD"), cancellable = true, require = 2, expect = 2, allow = 2)
    private void slimeTechCompat$guardTransfer(Direction side, int type, CallbackInfoReturnable<Boolean> cir) {
        if (TankSafety.blocked((FluidTankBlockEntity)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method = {"getFluidType()I", "getFluidAmount()I", "getFluidCapacity()I"},
            at = @At("HEAD"), cancellable = true, require = 3, expect = 3, allow = 3)
    private void slimeTechCompat$hideUnsafeAggregate(CallbackInfoReturnable<Integer> cir) {
        if (TankSafety.blocked((FluidTankBlockEntity)(Object)this)) cir.setReturnValue(0);
    }
    @Inject(method = "refreshNetworkData()V", at = @At("HEAD"), cancellable = true,
            require = 1, expect = 1, allow = 1)
    private void slimeTechCompat$guardRefresh(CallbackInfo ci) {
        FluidTankBlockEntity self = (FluidTankBlockEntity)(Object)this;
        if (TankSafety.blocked(self)) {
            ContainerData data = self.getContainerData();
            // Show this block's retained contents, while zero capacity marks transfers unavailable.
            data.set(0, localAmount);
            data.set(1, SlimeTechFluidType.fromIdentifierString(storedFluidId)); data.set(2, 0);
            ci.cancel();
        }
    }
}
