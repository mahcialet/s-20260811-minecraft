package dev.marmite.oreslimescompat.tests;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Measurement-only weights; never packaged in the production mod. */
public final class EqualWeightExperiment implements BiomeModifier {
    private static final MapCodec<EqualWeightExperiment> CODEC = MapCodec.unit(new EqualWeightExperiment());
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "ore_slimes_compat_tests");

    static void register(IEventBus bus) {
        SERIALIZERS.register("equal_weights", () -> CODEC);
        SERIALIZERS.register(bus);
    }

    private static String variant() {
        return Boolean.getBoolean("compat.phase2") ? System.getProperty("compat.phase2.weightScale", "original") : "original";
    }

    private static boolean ore(net.minecraft.world.entity.EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("oreslimes");
    }

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        String variant = variant();
        if (phase != Phase.AFTER_EVERYTHING || !(variant.equals("equal-all") || variant.equals("equal-ore"))) return;
        var spawns = builder.getMobSpawnSettings().getSpawner(MobCategory.CREATURE);
        var entries = List.copyOf(spawns.getList());
        spawns.removeIf(entry -> true);
        for (var entry : entries) {
            int weight = entry.weight();
            if (weight > 0) {
                if (variant.equals("equal-all")) weight = 1;
                else if (ore(entry.value().type())) weight = 7;
            }
            spawns.add(new Weighted<>(entry.value(), weight));
        }
    }

    static void verify(ServerLevel level) {
        String variant = variant();
        if (!(variant.equals("equal-all") || variant.equals("equal-ore"))) return;
        for (var biome : level.registryAccess().lookupOrThrow(Registries.BIOME)) {
            for (var entry : biome.getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
                if (entry.weight() > 0 && (variant.equals("equal-all") || ore(entry.value().type()))
                        && entry.weight() != (variant.equals("equal-all") ? 1 : 7)) {
                    throw new IllegalStateException("Experimental weight was not applied: " + entry);
                }
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
