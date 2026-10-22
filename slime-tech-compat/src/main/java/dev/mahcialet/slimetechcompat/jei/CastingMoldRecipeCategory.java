package dev.mahcialet.slimetechcompat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CastingMoldRecipeCategory implements IRecipeCategory<CastingMoldRecipe> {
    public static final IRecipeType<CastingMoldRecipe> TYPE =
            IRecipeType.create("slime_tech_compat", "casting_mold", CastingMoldRecipe.class);
    private final IDrawable icon;
    public CastingMoldRecipeCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(Ingredients.item("slime_tech:casting_mold"));
    }
    @Override public IRecipeType<CastingMoldRecipe> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.slime_tech_compat.casting_mold"); }
    @Override public int getWidth() { return 116; }
    @Override public int getHeight() { return 48; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public void setRecipe(IRecipeLayoutBuilder b, CastingMoldRecipe recipe, IFocusGroup focuses) {
        b.addInputSlot(20, 7).setFluidRenderer(1000L, true, 16, 32)
                .add(Ingredients.fluid("slime_tech:condensed_slime"), 1000L);
        b.addOutputSlot(82, 15).setOutputSlotBackground().add(Ingredients.item("slime_tech:gel_ingot"));
    }
    @Override public Identifier getIdentifier(CastingMoldRecipe recipe) {
        return Identifier.fromNamespaceAndPath("slime_tech_compat", "gel_ingot_casting");
    }
}
