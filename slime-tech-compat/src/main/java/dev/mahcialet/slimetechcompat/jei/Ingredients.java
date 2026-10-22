package dev.mahcialet.slimetechcompat.jei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/** Resolve public registry IDs; no reflection into the original mod's JEI or client classes. */
final class Ingredients {
    private Ingredients() { }
    static ItemStack item(String id) {
        Identifier key = Identifier.parse(id);
        // A defaulted registry otherwise turns a missing optional ingredient into AIR.
        if (!BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }
    static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
    }
}
