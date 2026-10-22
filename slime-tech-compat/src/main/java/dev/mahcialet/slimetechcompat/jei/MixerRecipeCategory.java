package dev.mahcialet.slimetechcompat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MixerRecipeCategory implements IRecipeCategory<MixerRecipe> {
    public static final IRecipeType<MixerRecipe> TYPE =
            IRecipeType.create("slime_tech_compat", "mixer", MixerRecipe.class);
    private final IDrawable icon;
    public MixerRecipeCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(Ingredients.item("slime_tech:mixer"));
    }
    @Override public IRecipeType<MixerRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.slime_tech_compat.mixer"); }
    @Override public int getWidth() { return 164; }
    @Override public int getHeight() { return 64; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public void setRecipe(IRecipeLayoutBuilder b, MixerRecipe recipe, IFocusGroup focuses) {
        for (int slot = 0; slot < 9; slot++) {
            IRecipeSlotBuilder input = b.addInputSlot(4 + slot % 3 * 18, 5 + slot / 3 * 18)
                    .setStandardSlotBackground();
            if (recipe.radioactive()) {
                input.add(Ingredients.item(slot / 3 == 1 ? "oreslimes:slimy_copper" : "oreslimes:slimy_coal"));
            } else if (slot == 4) {
                input.add(Ingredients.item("minecraft:slime_ball"));
            } else if (slot == 1) {
                input.add(Ingredients.item("slime_tech:dust"));
            }
        }
        b.addInputSlot(77, 14).setFluidRenderer(1000L, true, 16, 32).add(
                Ingredients.fluid(recipe.radioactive() ? "slime_tech:condensed_slime" : "minecraft:water"),
                recipe.radioactive() ? 1000L : 250L);
        if (recipe.radioactive()) {
            b.addOutputSlot(137, 14).setFluidRenderer(1000L, true, 16, 32)
                    .add(Ingredients.fluid("slime_tech:radioactive_slime"), 1000L);
        } else {
            b.addOutputSlot(136, 21).setOutputSlotBackground().add(Ingredients.item("minecraft:clay"));
        }
    }
    @Override public Identifier getIdentifier(MixerRecipe recipe) {
        return Identifier.fromNamespaceAndPath("slime_tech_compat", recipe.radioactive() ? "mixer_radioactive" : "mixer_clay");
    }
}
