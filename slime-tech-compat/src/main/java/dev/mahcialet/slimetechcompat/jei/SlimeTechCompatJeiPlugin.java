package dev.mahcialet.slimetechcompat.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class SlimeTechCompatJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("slime_tech_compat", "jei");
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CastingMoldRecipeCategory(gui), new MixerRecipeCategory(gui));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CastingMoldRecipeCategory.TYPE, List.of(CastingMoldRecipe.INSTANCE));
        ArrayList<MixerRecipe> mixers = new ArrayList<>();
        mixers.add(new MixerRecipe(false));
        // Ore Slimes is not a declared dependency of the base mod. Do not publish an AIR recipe.
        if (!Ingredients.item("oreslimes:slimy_coal").isEmpty() && !Ingredients.item("oreslimes:slimy_copper").isEmpty()) {
            mixers.add(new MixerRecipe(true));
        }
        registration.addRecipes(MixerRecipeCategory.TYPE, mixers);
        info(registration, new String[]{"slime_tech:gel_ingot", "slime_tech:casting_mold"}, "casting", "bootstrap", "pipes");
        info(registration, new String[]{"minecraft:slime_block", "minecraft:furnace", "slime_tech:condensed_slime_bucket", "slime_tech:dried_slime"}, "bootstrap");
        info(registration, new String[]{"slime_tech:basic_fluid_pipe", "slime_tech:reinforced_fluid_pipe"}, "pipes");
        info(registration, new String[]{"slime_tech:mixer"}, "mixer_clay", "mixer_radioactive");
        info(registration, new String[]{"slime_tech:fluid_tank"}, "tank_guard");
        info(registration, new String[]{"slime_tech:pump"}, "pump_safety");
        info(registration, new String[]{"slime_tech:grinder"}, "grinder");
        info(registration, new String[]{"slime_tech:energy_converter"}, "converter_placeholder");
        info(registration, new String[]{"slime_tech:generator"}, "drain_placeholder");
    }
    private static void info(IRecipeRegistration registration, String[] ids, String... keys) {
        ArrayList<ItemStack> stacks = new ArrayList<>();
        for (String id : ids) { ItemStack stack = Ingredients.item(id); if (!stack.isEmpty()) stacks.add(stack); }
        if (stacks.isEmpty()) return;
        Component[] lines = new Component[keys.length];
        for (int i = 0; i < keys.length; i++) lines[i] = Component.translatable("jei.slime_tech_compat.info." + keys[i]);
        registration.addItemStackInfo(stacks, lines);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(CastingMoldRecipeCategory.TYPE, Ingredients.item("slime_tech:casting_mold"));
        registration.addCraftingStation(MixerRecipeCategory.TYPE, Ingredients.item("slime_tech:mixer"));
    }
}
