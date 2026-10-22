package dev.marmite.oreslimescompat;

import com.jeremiah.oreslimes.registry.ModEntities;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/** Adjusts selection weights after spawn entries have been added and removed. */
public final class OreSlimeWeightModifier implements BiomeModifier {
    public static final MapCodec<OreSlimeWeightModifier> CODEC = MapCodec.unit(new OreSlimeWeightModifier());

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY) return;
        double scale = SpawnerConfig.STANDARD_WEIGHT_SCALE.get();
        if (scale == 1.0) return;
        var spawns = builder.getMobSpawnSettings().getSpawner(MobCategory.CREATURE);
        var entries = List.copyOf(spawns.getList());
        spawns.removeIf(entry -> true);
        for (var entry : entries) {
            boolean oreSlime = ModEntities.ALL.stream().anyMatch(type -> type.get() == entry.value().type());
            int weight = oreSlime && entry.weight() > 0
                    ? Math.max(1, (int) Math.round(entry.weight() * scale)) : entry.weight();
            spawns.add(new Weighted<>(entry.value(), weight));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
