package dev.mahcialet.slimetechcompat.tests;

import dev.mahcialet.slimetechcompat.safety.PumpSafety;
import dev.mahcialet.slimetechcompat.safety.TankAccess;
import dev.mahcialet.slimetechcompat.safety.TankSafety;
import dev.wizardjw.slimetech.blockentity.FluidTankBlockEntity;
import dev.wizardjw.slimetech.blockentity.PumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Executed inside the real NeoForge server after Sponge Mixin transformation. */
public final class SafetyRuntimeChecks {
    private static int assertions;
    private static final BlockPos A = new BlockPos(8, 100, 8);
    private static final BlockPos B = A.east();
    private static final BlockPos P = new BlockPos(8, 100, 10);
    private SafetyRuntimeChecks() { }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        assertions++;
        System.out.println("CHECK SAFETY " + name);
    }
    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("slime_tech", name));
    }
    private static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        entity.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }
    private static CompoundTag tankTag(int amount, String id) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("LocalFluidAmount", amount);
        tag.putString("FluidId", id);
        return tag;
    }
    private static CompoundTag pumpTag(int amount, int code) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FluidAmount", amount);
        tag.putInt("FluidType", code);
        tag.putInt("Energy", 1234);
        return tag;
    }
    public static int run(ServerLevel level) throws Exception {
        assertions = 0;
        try {
            tanks(level);
            pumps(level);
            return assertions;
        } finally {
            for (BlockPos pos : new BlockPos[] {A, B, P}) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }
    private static void tanks(ServerLevel level) {
        level.setBlockAndUpdate(A, block("fluid_tank").defaultBlockState());
        level.setBlockAndUpdate(B, block("fluid_tank").defaultBlockState());
        FluidTankBlockEntity a = (FluidTankBlockEntity) level.getBlockEntity(A);
        FluidTankBlockEntity b = (FluidTankBlockEntity) level.getBlockEntity(B);
        check(a instanceof TankAccess && b instanceof TankAccess, "real Tank accessor Mixin applied");
        load(level, a, tankTag(1000, "minecraft:water"));
        load(level, b, tankTag(2000, "minecraft:lava"));
        check(TankSafety.blocked(a) && TankSafety.blocked(b), "mixed water/lava network held");
        a.refreshNetworkData();
        b.refreshNetworkData();
        check(a.getContainerData().get(0) == 1000 && a.getContainerData().get(1) == 1 && a.getContainerData().get(2) == 0,
                "held Tank GUI slots retain local water amount and identity");
        check(b.getContainerData().get(0) == 2000 && b.getContainerData().get(1) == 2 && b.getContainerData().get(2) == 0,
                "held Tank GUI slots retain local lava amount and identity");
        CompoundTag beforeA = a.saveCustomOnly(level.registryAccess());
        CompoundTag beforeB = b.saveCustomOnly(level.registryAccess());
        var mixedHandler = level.getCapability(Capabilities.Fluid.BLOCK, A, Direction.UP);
        check(mixedHandler != null, "actual NeoForge fluid capability registered");
        for (boolean commit : new boolean[] {false, true}) {
            try (var transaction = Transaction.openRoot()) {
                check(mixedHandler.extract(FluidResource.of(Fluids.WATER), 100, transaction) == 0,
                        "mixed capability extraction refused commit=" + commit);
                check(mixedHandler.insert(FluidResource.of(Fluids.WATER), 100, transaction) == 0,
                        "mixed capability insertion refused commit=" + commit);
                if (commit) transaction.commit();
            }
            check(beforeA.equals(a.saveCustomOnly(level.registryAccess())) && beforeB.equals(b.saveCustomOnly(level.registryAccess())),
                    "mixed capability transaction preserves per-ID saved data commit=" + commit);
        }
        for (boolean simulate : new boolean[] {true, true, false}) {
            check(a.extractFluid(Direction.UP, 1, 1000, simulate) == 0, "mixed extraction blocked simulate=" + simulate);
            check(a.insertFluid(Direction.UP, 1, 1000, simulate) == 0, "mixed insertion blocked simulate=" + simulate);
        }
        check(beforeA.equals(a.saveCustomOnly(level.registryAccess())) && beforeB.equals(b.saveCustomOnly(level.registryAccess())),
                "mixed transfer and simulation preserve per-ID local saves");
        load(level, a, beforeA);
        load(level, b, beforeB);
        check(((TankAccess)a).slimeTechCompat$getLocalAmount() == 1000 && ((TankAccess)b).slimeTechCompat$getLocalAmount() == 2000,
                "mixed saved amounts retained after reload");
        level.setBlockAndUpdate(B, Blocks.AIR.defaultBlockState());
        check(!TankSafety.blocked(a), "mixed network recovers after separation");
        check(a.extractFluid(Direction.UP, 1, 100, true) == 100 && a.getFluidAmount() == 1000, "normal simulation preserves quantity");
        check(a.extractFluid(Direction.UP, 1, 100, false) == 100 && a.getFluidAmount() == 900, "normal extraction regression");
        var handler = level.getCapability(Capabilities.Fluid.BLOCK, A, Direction.UP);
        check(handler != null, "normal Tank capability available");
        try (var transaction = Transaction.openRoot()) {
            check(handler.extract(FluidResource.of(Fluids.WATER), 100, transaction) == 100, "capability extract simulation");
        }
        check(a.getFluidAmount() == 900, "aborted capability extraction preserves amount");
        try (var transaction = Transaction.openRoot()) {
            check(handler.insert(FluidResource.of(Fluids.WATER), 100, transaction) == 100, "capability insert simulation");
        }
        check(a.getFluidAmount() == 900, "aborted capability insertion preserves amount");
        try (var transaction = Transaction.openRoot()) {
            check(handler.extract(FluidResource.of(Fluids.WATER), 100, transaction) == 100, "capability extract before commit");
            transaction.commit();
        }
        check(a.getFluidAmount() == 800, "committed capability extraction changes amount once");
        try (var transaction = Transaction.openRoot()) {
            check(handler.insert(FluidResource.of(Fluids.WATER), 100, transaction) == 100, "capability insert before commit");
            transaction.commit();
        }
        check(a.getFluidAmount() == 900, "committed capability insertion changes amount once");
        level.setBlockAndUpdate(B, block("fluid_tank").defaultBlockState());
        b = (FluidTankBlockEntity) level.getBlockEntity(B);
        load(level, b, tankTag(500, "minecraft:water"));
        var otherHandler = level.getCapability(Capabilities.Fluid.BLOCK, B, Direction.DOWN);
        check(otherHandler != null, "second network member capability available");
        var networkBeforeA = a.saveCustomOnly(level.registryAccess());
        var networkBeforeB = b.saveCustomOnly(level.registryAccess());
        try (var outer = Transaction.openRoot()) {
            check(handler.extract(FluidResource.of(Fluids.WATER), 1000, outer) == 1000, "transaction spans two tank local buffers");
            try (var inner = Transaction.open(outer)) {
                check(otherHandler.insert(FluidResource.of(Fluids.WATER), 200, inner) == 200, "nested transaction uses another network capability");
                inner.commit();
            }
        }
        check(networkBeforeA.equals(a.saveCustomOnly(level.registryAccess())) && networkBeforeB.equals(b.saveCustomOnly(level.registryAccess())),
                "outer abort reverts committed child and both local tank identities");
        for (var member : new FluidTankBlockEntity[] {a, b}) {
            check(member.getContainerData().get(0) == 1400 && member.getContainerData().get(1) == 1
                    && member.getContainerData().get(2) == 16000, "outer abort restores each member GUI aggregate without a later tick");
        }
        try (var outer = Transaction.openRoot()) {
            check(handler.extract(FluidResource.of(Fluids.WATER), 100, outer) == 100, "outer transaction extraction");
            try (var inner = Transaction.open(outer)) {
                check(otherHandler.extract(FluidResource.of(Fluids.WATER), 100, inner) == 100, "inner transaction extraction before abort");
            }
            outer.commit();
        }
        check(a.getFluidAmount() == 1300, "inner abort followed by outer commit removes only outer amount");
        for (var member : new FluidTankBlockEntity[] {a, b}) {
            check(member.getContainerData().get(0) == 1300 && member.getContainerData().get(1) == 1
                    && member.getContainerData().get(2) == 16000, "inner abort preserves outer transaction aggregate in each GUI");
        }
        level.setBlockAndUpdate(B, Blocks.AIR.defaultBlockState());
        for (int invalid : new int[] {-1, 8001}) {
            load(level, a, tankTag(invalid, "minecraft:water"));
            check(TankSafety.blocked(a), "invalid local amount held " + invalid);
            for (int i = 0; i < 2; i++) {
                CompoundTag saved = a.saveCustomOnly(level.registryAccess());
                check(saved.getInt("LocalFluidAmount").orElse(0) == invalid, "invalid amount saved verbatim " + invalid + " round " + i);
                load(level, a, saved);
            }
        }
        for (String id : new String[] {"", "absent_fluid_mod:missing"}) {
            CompoundTag input = tankTag(456, id);
            input.putInt("FluidType", 1017);
            load(level, a, input);
            for (int i = 0; i < 2; i++) {
                check(TankSafety.blocked(a), "unidentified Tank held " + id + " round " + i);
                CompoundTag saved = a.saveCustomOnly(level.registryAccess());
                check(saved.getInt("LocalFluidAmount").orElse(0) == 456 && saved.getString("FluidId").orElse("lost").equals(id),
                        "unidentified Tank amount and ID retained " + i);
                if (id.isEmpty()) check(saved.getInt("SlimeTechCompatUnresolvedCode").orElse(0) == 1017, "legacy Tank code retained " + i);
                load(level, a, saved);
            }
        }
        load(level, a, tankTag(0, "absent_fluid_mod:missing"));
        check(!TankSafety.blocked(a), "empty stale-ID Tank permitted");
    }
    private static void pumps(ServerLevel level) {
        level.setBlockAndUpdate(P, block("pump").defaultBlockState());
        PumpBlockEntity pump = (PumpBlockEntity) level.getBlockEntity(P);
        CompoundTag legacy = pumpTag(777, 1019);
        load(level, pump, legacy);
        for (int i = 0; i < 3; i++) {
            check(PumpSafety.quarantined(pump), "legacy dynamic Pump held round " + i);
            check(pump.extractFluid(Direction.UP, 1019, 100, false) == 0, "legacy Pump extraction blocked " + i);
            CompoundTag saved = pump.saveCustomOnly(level.registryAccess());
            check(saved.getInt("SlimeTechCompatUnresolvedCode").orElse(0) == 1019, "legacy Pump diagnostic retained " + i);
            check(saved.getInt("FluidAmount").orElse(0) == 777 && saved.getInt("Energy").orElse(0) == 1234, "legacy Pump quantity and energy retained " + i);
            load(level, pump, saved);
        }
        CompoundTag missing = pumpTag(333, 1023);
        missing.putInt(PumpSafety.SCHEMA, 1);
        missing.putString(PumpSafety.FLUID_ID, "absent_fluid_mod:missing");
        load(level, pump, missing);
        for (int i = 0; i < 2; i++) {
            check(PumpSafety.quarantined(pump), "missing fluid Pump held " + i);
            CompoundTag saved = pump.saveCustomOnly(level.registryAccess());
            check(saved.getString(PumpSafety.FLUID_ID).orElse("").equals("absent_fluid_mod:missing") && saved.getInt("FluidAmount").orElse(0) == 333,
                    "missing fluid ID and amount retained " + i);
            load(level, pump, saved);
        }
        for (String id : new String[] {"minecraft:water", "minecraft:lava"}) {
            CompoundTag known = pumpTag(888, 1024);
            known.putInt(PumpSafety.SCHEMA, 1);
            known.putString(PumpSafety.FLUID_ID, id);
            load(level, pump, known);
            check(!PumpSafety.quarantined(pump), "schema1 ID resolves independently of stale dynamic number " + id);
            CompoundTag saved = pump.saveCustomOnly(level.registryAccess());
            load(level, pump, saved);
            check(!PumpSafety.quarantined(pump) && pump.getFluidAmount() == 888 && pump.getEnergyStored() == 1234,
                    "schema1 normal roundtrip " + id);
            var capability = level.getCapability(Capabilities.Fluid.BLOCK, P, Direction.UP);
            check(capability != null, "Pump fluid capability exists " + id);
            var fluid = FluidResource.of(id.equals("minecraft:water") ? Fluids.WATER : Fluids.LAVA);
            try (var transaction = Transaction.openRoot()) {
                check(capability.extract(fluid, 888, transaction) == 888, "Pump full drain before abort " + id);
            }
            check(pump.getFluidAmount() == 888 && pump.getFluidTypeCode() == (id.equals("minecraft:water") ? 1 : 2),
                    "Pump abort restores both amount and type after emptying " + id);
            try (var transaction = Transaction.openRoot()) {
                check(capability.extract(fluid, 100, transaction) == 100, "Pump committed extraction " + id);
                transaction.commit();
            }
            check(pump.getFluidAmount() == 788, "Pump commit drains once " + id);
        }
        CompoundTag future = pumpTag(246, 1007);
        future.putInt(PumpSafety.SCHEMA, 99);
        future.putString(PumpSafety.FLUID_ID, "future:fluid");
        CompoundTag opaque = new CompoundTag();
        opaque.putString("futureField", "must survive downgrade");
        opaque.putIntArray("payload", new int[] {17, 23, -999});
        future.put("FutureOpaqueData", opaque);
        load(level, pump, future);
        for (int i = 0; i < 3; i++) {
            check(PumpSafety.quarantined(pump), "future schema held " + i);
            check(!pump.canInsertEnergy(Direction.UP) && pump.insertEnergy(Direction.UP, 100, true) == 0
                    && pump.insertEnergy(Direction.UP, 100, false) == 0, "future held Pump refuses incoming energy " + i);
            PumpBlockEntity.serverTick(level, P, pump.getBlockState(), pump);
            CompoundTag saved = pump.saveCustomOnly(level.registryAccess());
            check(saved.getInt(PumpSafety.SCHEMA).orElse(0) == 99 && saved.getInt("FluidType").orElse(0) == 1007,
                    "future schema and original runtime number retained " + i);
            check(opaque.equals(saved.get("FutureOpaqueData")), "future unknown nested data retained " + i);
            check(saved.getInt("FluidAmount").orElse(0) == 246 && saved.getInt("Energy").orElse(0) == 1234,
                    "future held tick preserves amount and energy " + i);
            load(level, pump, saved);
        }
        CompoundTag collision = pumpTag(123, 1);
        collision.putString(PumpSafety.SCHEMA, "foreign-format");
        load(level, pump, collision);
        check(PumpSafety.quarantined(pump), "schema key type collision held");
        check(pump.saveCustomOnly(level.registryAccess()).getString(PumpSafety.SCHEMA).orElse("").equals("foreign-format"),
                "schema key type collision retained verbatim");
    }
}
