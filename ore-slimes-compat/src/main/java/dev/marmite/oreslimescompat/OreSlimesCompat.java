package dev.marmite.oreslimescompat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.mojang.serialization.MapCodec;

@Mod("ore_slimes_compat")
public final class OreSlimesCompat {
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "ore_slimes_compat");
    static {
        BIOME_MODIFIERS.register("scale_ore_slime_weights", () -> OreSlimeWeightModifier.CODEC);
    }

    public OreSlimesCompat(IEventBus bus, ModContainer container) {
        BIOME_MODIFIERS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SpawnerConfig.SPEC);
    }
}
