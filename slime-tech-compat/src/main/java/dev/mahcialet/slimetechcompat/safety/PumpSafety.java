package dev.mahcialet.slimetechcompat.safety;

import dev.mahcialet.slimetechcompat.CompatConfig;
import dev.wizardjw.slimetech.blockentity.PumpBlockEntity;
import dev.wizardjw.slimetech.blockentity.SlimeTechFluidType;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import com.mojang.serialization.MapCodec;

public final class PumpSafety {
    public static final String SCHEMA = "SlimeTechCompatSchema";
    public static final String FLUID_ID = "SlimeTechCompatFluidId";
    public static final String UNRESOLVED = "SlimeTechCompatUnresolvedCode";
    private static final Map<PumpBlockEntity, PumpIdentityPolicy.Result> QUARANTINE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final MapCodec<CompoundTag> RAW_DATA = MapCodec.assumeMapUnsafe(CompoundTag.CODEC);
    private static final Map<PumpBlockEntity, CompoundTag> UNSUPPORTED = Collections.synchronizedMap(new WeakHashMap<>());
    private PumpSafety() { }
    public static void load(PumpBlockEntity pump, ValueInput input) {
        ContainerData data = pump.getContainerData();
        CompoundTag raw = input.read(RAW_DATA).orElseThrow(() -> new IllegalStateException("Cannot preserve Pump save data"));
        int schema = input.getIntOr(SCHEMA, 0);
        if ((raw.contains(SCHEMA) && input.getInt(SCHEMA).isEmpty()) || schema < 0 || schema > 1
                || (raw.contains(FLUID_ID) && input.getString(FLUID_ID).isEmpty())
                || (raw.contains(UNRESOLVED) && input.getInt(UNRESOLVED).isEmpty())) {
            // A newer format must survive a downgrade without interpretation or key loss.
            UNSUPPORTED.put(pump, raw.copy());
            QUARANTINE.put(pump, new PumpIdentityPolicy.Result(0, input.getStringOr(FLUID_ID, ""), data.get(0), true));
            data.set(0, 0);
            System.getLogger("SlimeTechCompat").log(System.Logger.Level.WARNING,
                    "Pump at " + pump.getBlockPos() + " is held: unsupported save schema " + schema + "; original save data retained.");
            return;
        }
        UNSUPPORTED.remove(pump);
        PumpIdentityPolicy.Result result = PumpIdentityPolicy.restore(
                data.get(1), data.get(0), input.getStringOr(FLUID_ID, ""), input.getIntOr(UNRESOLVED, 0),
                SlimeTechFluidType::fromIdentifierString, SlimeTechFluidType::toIdentifierString);
        data.set(0, result.runtimeCode());
        if (result.quarantined()) {
            QUARANTINE.put(pump, result);
            if (pump.getLevel() == null || !pump.getLevel().isClientSide()) {
                System.getLogger("SlimeTechCompat").log(System.Logger.Level.WARNING,
                        "Quarantined Pump at " + pump.getBlockPos() + ": amount=" + data.get(1)
                        + ", unresolved code=" + result.unresolvedCode() + ", saved ID=" + result.stableId()
                        + ". No contents were deleted or guessed. Restore the fluid mod or recover from a backup.");
            }
        } else {
            QUARANTINE.remove(pump);
        }
    }
    public static void save(PumpBlockEntity pump, ValueOutput output) {
        CompoundTag raw = UNSUPPORTED.get(pump);
        if (raw != null) {
            output.store(RAW_DATA, raw);
            return;
        }
        PumpIdentityPolicy.Result q = QUARANTINE.get(pump);
        String id = q != null ? q.stableId()
                : (pump.getFluidAmount() > 0 ? SlimeTechFluidType.toIdentifierString(pump.getFluidTypeCode()) : "");
        int unknown = q != null ? q.unresolvedCode()
                : (pump.getFluidAmount() > 0 && id.isBlank() ? pump.getFluidTypeCode() : 0);
        output.putInt(SCHEMA, 1);
        output.putString(FLUID_ID, id);
        output.putInt(UNRESOLVED, unknown);
        // Original FluidAmount/Energy fields remain intact. Quarantined runtime code stays zero.
    }
    public static boolean quarantined(PumpBlockEntity pump) { return QUARANTINE.containsKey(pump); }
    public static void setDetectedType(net.minecraft.world.inventory.SimpleContainerData data, int slot, int value) {
        if (slot == 0 && data.get(1) > 0 && data.get(0) != value) return;
        data.set(slot, value);
    }
    public static boolean allowWorldSource(FluidState source) {
        return source.isSource() && (!CompatConfig.get().blockNonWaterWorldSources()
                || source.getType() == Fluids.WATER);
    }
}
