package dev.mahcialet.slimetechcompat.mixin;

import dev.wizardjw.slimetech.blockentity.FurnaceCondensedSlimeCompat;
import dev.wizardjw.slimetech.blockentity.FurnaceCondensedSlimeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the original conversion ratio and storage extraction granularity. */
@Mixin(value = FurnaceCondensedSlimeCompat.class, remap = false)
public abstract class FurnaceMixin {
    @Redirect(method = "processFurnaceOutput", at = @At(value = "INVOKE",
            target = "Ldev/wizardjw/slimetech/blockentity/FurnaceCondensedSlimeStorage;insert(JI)I"),
            require = 1, expect = 1, allow = 1)
    private static int slimeTechCompat$wholeItems(FurnaceCondensedSlimeStorage storage, long position, int requested) {
        int available = Math.max(0, 8000 - storage.getAmount(position));
        int whole = Math.min(available / 1000, Math.max(0, requested) / 1000);
        return storage.insert(position, whole * 1000);
    }

}
