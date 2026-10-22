package dev.mahcialet.slimetechcompat.tests;
import dev.mahcialet.slimetechcompat.CompatConfig;

import dev.wizardjw.slimetech.SlimeTech;
import dev.wizardjw.slimetech.blockentity.*;
import java.nio.file.*;
import java.lang.reflect.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real dedicated-server tests. No API stubs or synthetic Mixin weaver. */
@Mod("slime_tech_compat_tests")
public final class RuntimeTests {
    private static final boolean BASELINE = Boolean.getBoolean("compat.baseline");
    private static int assertions;
    public RuntimeTests() { NeoForge.EVENT_BUS.addListener(this::started); }
    private void started(ServerStartedEvent event) {
        try {
            run(event.getServer().overworld());
            Files.writeString(Path.of("compat-test-result.txt"), "PASS assertions=" + assertions);
            System.out.println("SLIME_COMPAT_RUNTIME_PASS baseline=" + BASELINE + " assertions=" + assertions);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new AssertionError("SLIME_COMPAT_RUNTIME_FAILED", failure);
        } finally { event.getServer().halt(false); }
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        assertions++; System.out.println("CHECK " + name);
    }
    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("slime_tech", name));
    }
    private static void run(ServerLevel level) throws Exception {
        if (Boolean.getBoolean("compat.packaged")) check(
                dev.mahcialet.slimetechcompat.SlimeTechCompat.class.getProtectionDomain().getCodeSource().getLocation().toString().contains(".jar"),
                "ENV-03 actual packaged patch JAR loaded");
        if (Boolean.getBoolean("compat.migration")) { migration(level); return; }
        furnace(level);
        casting(level);
        // Force all original three Mixin targets to load, even in the baseline.
        var g = new GrinderBlockEntity(new BlockPos(8, 100, 8), block("grinder").defaultBlockState());
        check(g.getEnergyStored() == (!BASELINE && CompatConfig.get().fixGrinderEnergy() ? 0 : 2000), "Grinder new energy honors optional policy");
        grinder(level);
        mining(level);
        tank(level);
        pump(level);
        pumpSources(level);
        if (!BASELINE) assertions += SafetyRuntimeChecks.run(level);
        if (BASELINE) migrationFixture(level);
    }
    private static void migrationFixture(ServerLevel level) {
        var p = new BlockPos(10, 100, 10);
        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(p, block("pump").defaultBlockState());
        var pump = (PumpBlockEntity) level.getBlockEntity(p);
        pump.getContainerData().set(0, 1007); pump.getContainerData().set(1, 1234); pump.getContainerData().set(2, 777); pump.setChanged();
        var t = new BlockPos(11, 100, 10);
        level.setBlockAndUpdate(t, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(t, block("fluid_tank").defaultBlockState());
        var tank = (FluidTankBlockEntity) level.getBlockEntity(t);
        check(tank.insertFluid(Direction.UP, 1, 4321, false) == 4321, "MIGRATION write original Tank fixture");
        tank.setChanged();
    }
    private static void migration(ServerLevel level) {
        var pump = (PumpBlockEntity) level.getBlockEntity(new BlockPos(10, 100, 10));
        var tank = (FluidTankBlockEntity) level.getBlockEntity(new BlockPos(11, 100, 10));
        check(pump != null && tank != null, "MIGRATION existing world blocks loaded");
        check(dev.mahcialet.slimetechcompat.safety.PumpSafety.quarantined(pump), "MIGRATION old dynamic Pump held");
        var saved = pump.saveCustomOnly(level.registryAccess());
        check(saved.getInt("SlimeTechCompatUnresolvedCode").orElse(0) == 1007, "MIGRATION original code survives world restart");
        check(pump.getFluidAmount() == 1234 && pump.getEnergyStored() == 777, "MIGRATION original amount and energy survive world restart");
        check(tank.getFluidAmount() == 4321 && tank.getFluidTypeCode() == 1, "MIGRATION original water survives world restart");
        pump.setChanged(); tank.setChanged();
    }
    private static void furnace(ServerLevel level) {
        var storage = FurnaceCondensedSlimeStorage.get(level);
        BlockPos pos = new BlockPos(8, 100, 8);
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
        for (int initial : new int[]{7000, 7500, 7999, 8000}) {
            for (int count : new int[]{1, 2, 64}) {
                storage.extract(pos.asLong(), 8000); storage.insert(pos.asLong(), initial);
                furnace.setItem(1, ItemStack.EMPTY);
                furnace.setItem(2, new ItemStack(SlimeTech.DRIED_SLIME.get(), count));
                FurnaceCondensedSlimeCompat.processFurnaceOutput(level, furnace);
                int consumed = count - furnace.getItem(2).getCount();
                int gained = storage.getAmount(pos.asLong()) - initial;
                if (BASELINE && initial > 7000 && initial < 8000) {
                    check(consumed == 0 && gained == 8000 - initial, "ST-022 baseline reproduced at " + initial + " count=" + count);
                } else {
                    check(gained == consumed * 1000, "ST-022 conservation at " + initial + " count=" + count);
                }
                int after = storage.getAmount(pos.asLong());
                FurnaceCondensedSlimeCompat.processFurnaceOutput(level, furnace);
                check(storage.getAmount(pos.asLong()) == after, "ST-022 repeated tick no further conversion");
            }
        }
        for (int initial : new int[]{7000, 7500, 7999, 8000}) {
            for (int count : new int[]{1, 2, 64}) for (int buckets : new int[]{1, 2}) {
                storage.extract(pos.asLong(), 8000); storage.insert(pos.asLong(), initial);
                furnace.setItem(1, new ItemStack(Items.BUCKET, buckets));
                furnace.setItem(2, new ItemStack(SlimeTech.DRIED_SLIME.get(), count));
                FurnaceCondensedSlimeCompat.processFurnaceOutput(level, furnace);
                int consumed = count - furnace.getItem(2).getCount();
                int filled = furnace.getItem(1).is(SlimeTech.CONDENSED_SLIME_BUCKET.get()) ? furnace.getItem(1).getCount() : 0;
                int totalBuckets = furnace.getItem(1).getCount();
                check(totalBuckets == buckets, "ST-022 bucket stacks preserved");
                if (!BASELINE) {
                    check(totalBuckets == buckets, "ST-022 bucket item conservation at " + initial + "/" + count + "/" + buckets);
                    check(initial + consumed * 1000 == storage.getAmount(pos.asLong()) + filled * 1000, "ST-022 bucket fluid conservation at " + initial + "/" + count + "/" + buckets);
                }
            }
        }
        storage.extract(pos.asLong(), 8000); storage.insert(pos.asLong(), 8000);
        check(FurnaceCondensedSlimeCompat.extract(level, pos, 500) == 500 && storage.getAmount(pos.asLong()) == 7500,
                "ST-022 partial extraction reaches 7500 via original fluid API");
        storage.extract(pos.asLong(), 8000);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
    private static void casting(ServerLevel level) {
        var pos = new BlockPos(8, 100, 8);
        var state = block("casting_mold").defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var mold = (CastingMoldBlockEntity) level.getBlockEntity(pos);
        check(mold.insertFluid(Direction.UP, SlimeTechFluidType.CONDENSED_SLIME, 1000, true) == 1000 && mold.getFluidAmount() == 0, "ST-002 casting simulation");
        check(mold.insertFluid(Direction.UP, SlimeTechFluidType.CONDENSED_SLIME, 1000, false) == 1000, "ST-002 casting 1000mB");
        for (int n=0; n<199; n++) CastingMoldBlockEntity.serverTick(level, pos, state, mold);
        check(!mold.isReady(), "ST-002 casting not ready at 199 ticks");
        CastingMoldBlockEntity.serverTick(level, pos, state, mold);
        check(mold.getCoolingTicks() == 0, "ST-002 cooldown reaches zero at 200 ticks");
        CastingMoldBlockEntity.serverTick(level, pos, state, mold);
        var output = mold.removeReadyOutput();
        check(output.is(SlimeTech.GEL_INGOT.get()) && output.getCount() == 1, "ST-002 casting 1 ingot on tick after 200-tick cooldown");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
    private static void grinder(ServerLevel level) {
        boolean enabled = !BASELINE && CompatConfig.get().fixGrinderEnergy();
        for (int energy : new int[]{0, 14, 15, 119, 120, 2000}) {
            var pos = new BlockPos(8, 100, 8);
            var state = block("grinder").defaultBlockState();
            var g = new GrinderBlockEntity(pos, state); g.setLevel(level);
            g.getContainerData().set(0, energy); g.setItem(0, new ItemStack(Items.COBBLESTONE));
            for (int n=0; n<200; n++) GrinderBlockEntity.serverTick(level, pos, state, g);
            boolean completes = energy >= (enabled ? 120 : 15);
            check(!g.getItem(1).isEmpty() == completes, "ST-008 completion FE=" + energy + " enabled=" + enabled);
            check(g.getEnergyStored() == (completes ? Math.max(0, energy - 120) : energy), "ST-008 completion cost FE=" + energy);
            CompoundTag tag = g.saveCustomOnly(level.registryAccess());
            g.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            check(g.getEnergyStored() == (completes ? Math.max(0, energy - 120) : energy), "ST-008 saved energy preserved");
        }
    }
    private static void mining(ServerLevel level) {
        for (String name : new String[]{"casting_mold", "generator", "grinder", "destructor", "mixer", "pump", "energy_converter", "energy_cell", "reactor_core"}) {
            var state = block(name).defaultBlockState();
            check(state.is(BlockTags.MINEABLE_WITH_PICKAXE) != BASELINE, "ST-001 tag " + name);
            if (!BASELINE) {
                var drops = Block.getDrops(state, level, new BlockPos(8, 100, 8), null, null, new ItemStack(Items.DIAMOND_PICKAXE));
                check(drops.size() == 1 && drops.getFirst().getCount() == 1 && drops.getFirst().is(block(name).asItem()), "ST-001 one body loot " + name);
                check(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), "ST-001 appropriate pickaxe " + name);
            }
        }
    }
    private static void pumpSources(ServerLevel level) {
        BlockPos pos = new BlockPos(8, 100, 8);
        var state = block("pump").defaultBlockState();
        var p = new PumpBlockEntity(pos, state); p.setLevel(level);
        var data = p.getContainerData();
        for (Direction side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
        data.set(0, 2); data.set(1, 1000); data.set(2, 100);
        PumpBlockEntity.serverTick(level, pos, state, p);
        check(data.get(0) == (BASELINE ? 0 : 2) && data.get(1) == 1000, "ST-006 removed source identity");
        data.set(0, 2); data.set(1, 1000); data.set(2, 100);
        level.setBlockAndUpdate(pos.below(), Blocks.WATER.defaultBlockState());
        PumpBlockEntity.serverTick(level, pos, state, p);
        check(data.get(0) == (BASELINE ? 1 : 2), "ST-006 changed source identity");
        check(data.get(1) == (BASELINE ? 1100 : 1000), "ST-006 no water added to lava buffer");
        data.set(0, 0); data.set(1, 0); data.set(2, 100);
        PumpBlockEntity.serverTick(level, pos, state, p);
        check(data.get(0) == 1 && data.get(1) == 100 && data.get(2) == 90, "ST-007 water normal source and cost");
        level.setBlockAndUpdate(pos.below(), Blocks.LAVA.defaultBlockState());
        data.set(0, 0); data.set(1, 0); data.set(2, 100);
        PumpBlockEntity.serverTick(level, pos, state, p);
        boolean blocked = !BASELINE && CompatConfig.get().blockNonWaterWorldSources();
        check(data.get(1) == (blocked ? 0 : 100) && data.get(2) == (blocked ? 100 : 90), "ST-007 lava source optional policy");
        check(level.getFluidState(pos.below()).isSource(), "ST-007 source remains per original semantics");
        level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
    }
    private static void tank(ServerLevel level) throws Exception {
        BlockPos p = new BlockPos(8, 100, 8);
        level.setBlockAndUpdate(p, block("fluid_tank").defaultBlockState());
        var tank = (FluidTankBlockEntity) level.getBlockEntity(p);
        check(tank != null, "Tank registered");
        check(tank.insertFluid(Direction.UP, 1, 1000, true) == 1000 && tank.getFluidAmount() == 0, "Tank simulation preserves empty state");
        check(tank.insertFluid(Direction.UP, 1, 1000, false) == 1000, "Tank normal water insertion");
        check(tank.extractFluid(Direction.DOWN, 1, 500, true) == 500 && tank.getFluidAmount() == 1000, "Tank simulate extraction");
        check(tank.extractFluid(Direction.DOWN, 1, 500, false) == 500 && tank.getFluidAmount() == 500, "Tank normal extraction");
        var saved = tank.saveCustomOnly(level.registryAccess());
        tank.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        check(tank.getFluidAmount() == 500, "Tank save reload");
        if (!BASELINE) check(block("grinder").defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "ST-001 Grinder pickaxe tag loaded");
        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
    }
    private static void pump(ServerLevel level) throws Exception {
        var p = new PumpBlockEntity(new BlockPos(8, 100, 8), block("pump").defaultBlockState());
        p.setLevel(level);
        p.getContainerData().set(0, 1); p.getContainerData().set(1, 1000);
        for (int n=0; n<2; n++) {
            CompoundTag tag = p.saveCustomOnly(level.registryAccess());
            p.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            check(p.getContainerData().get(0) == 1 && p.getContainerData().get(1) == 1000, "Pump builtin identity roundtrip " + n);
            if (!BASELINE) check(tag.getString("SlimeTechCompatFluidId").orElse("").equals("minecraft:water"), "Pump stable ID persisted " + n);
        }
    }
}
