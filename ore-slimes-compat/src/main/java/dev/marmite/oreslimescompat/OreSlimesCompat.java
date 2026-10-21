package dev.marmite.oreslimescompat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod("ore_slimes_compat")
public final class OreSlimesCompat {
    public OreSlimesCompat(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SpawnerConfig.SPEC);
    }
}
