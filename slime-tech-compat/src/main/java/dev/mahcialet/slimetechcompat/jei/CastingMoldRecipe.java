package dev.mahcialet.slimetechcompat.jei;
/** Display only; does not register a server recipe or change the crafting result. */
public record CastingMoldRecipe() {
    public static final CastingMoldRecipe INSTANCE = new CastingMoldRecipe();
}
