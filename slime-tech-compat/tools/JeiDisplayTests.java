/* MIT. Registration/layout tests with explicit JEI fixtures, NOT the actual JEI runtime. */
import dev.mahcialet.slimetechcompat.jei.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.*;
import mezz.jei.api.helpers.*;
import mezz.jei.api.recipe.category.*;
import mezz.jei.api.recipe.types.*;
import mezz.jei.api.registration.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.*;
import net.minecraft.network.chat.*;
import java.util.*;

public final class JeiDisplayTests {
    static int passed;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    @SuppressWarnings("unchecked") static <T> BuiltInRegistries.FixtureRegistry<T> registry(net.minecraft.core.DefaultedRegistry<T> r){return (BuiltInRegistries.FixtureRegistry<T>)r;}
    static final BuiltInRegistries.FixtureRegistry<Item> ITEMS=registry(BuiltInRegistries.ITEM);
    static final BuiltInRegistries.FixtureRegistry<Fluid> FLUIDS=registry(BuiltInRegistries.FLUID);
    static Item item(String id){return ITEMS.getValue(Identifier.parse(id));}
    static Fluid fluid(String id){return FLUIDS.getValue(Identifier.parse(id));}
    static void register(){
        for(String id:List.of("gel_ingot","casting_mold","condensed_slime_bucket","dried_slime","basic_fluid_pipe","reinforced_fluid_pipe","mixer","fluid_tank","pump","grinder","energy_converter","generator","dust"))
            ITEMS.entries.put(Identifier.parse("slime_tech:"+id),new Item());
        ITEMS.entries.put(Identifier.parse("minecraft:slime_ball"),Items.SLIME_BALL);
        ITEMS.entries.put(Identifier.parse("minecraft:clay"),Items.CLAY);
        FLUIDS.entries.put(Identifier.parse("minecraft:water"),Fluids.WATER);
        FLUIDS.entries.put(Identifier.parse("slime_tech:condensed_slime"),new Fluid());
        FLUIDS.entries.put(Identifier.parse("slime_tech:radioactive_slime"),new Fluid());
    }
    static final class Registration implements IRecipeRegistration,IRecipeCategoryRegistration,IRecipeCatalystRegistration {
        final Map<IRecipeType<?>,List<?>> recipes=new HashMap<>();
        final List<IRecipeCategory<?>> categories=new ArrayList<>();
        final Map<IRecipeType<?>,List<ItemStack>> stations=new HashMap<>();
        final List<String> info=new ArrayList<>();
        public <T> void addRecipes(IRecipeType<T> t,List<T> r){recipes.put(t,List.copyOf(r));}
        public void addItemStackInfo(List<ItemStack> s,Component... c){if(s.isEmpty()||s.stream().anyMatch(ItemStack::isEmpty))throw new AssertionError("Empty info page ingredient");for(var x:c)info.add(((MutableComponent)x).key);}
        public IJeiHelpers getJeiHelpers(){return () -> s -> {if(s.isEmpty())throw new AssertionError("AIR category icon");return new IDrawable(){};};}
        public void addRecipeCategories(IRecipeCategory<?>... c){categories.addAll(List.of(c));}
        public void addCraftingStation(IRecipeType<?> t,ItemStack... s){stations.put(t,List.of(s));}
    }
    static final class Slot implements IRecipeSlotBuilder {
        final boolean input;final int x,y;ItemStack item;Fluid fluid;long amount;
        Slot(boolean input,int x,int y){this.input=input;this.x=x;this.y=y;}
        public Slot setFluidRenderer(long c,boolean s,int w,int h){return this;}
        public Slot setOutputSlotBackground(){return this;}public Slot setStandardSlotBackground(){return this;}
        public Slot add(ItemStack s){item=s;return this;}public Slot add(Fluid f,long a){fluid=f;amount=a;return this;}
    }
    static final class Layout implements IRecipeLayoutBuilder {
        final List<Slot> slots=new ArrayList<>();
        public Slot addInputSlot(int x,int y){var s=new Slot(true,x,y);slots.add(s);return s;}
        public Slot addOutputSlot(int x,int y){var s=new Slot(false,x,y);slots.add(s);return s;}
        long countItem(Item i){return slots.stream().filter(s->s.input&&s.item!=null&&s.item.getItem()==i).count();}
        boolean hasFluid(boolean input,Fluid f,long amount){return slots.stream().anyMatch(s->s.input==input&&s.fluid==f&&s.amount==amount);}
        boolean hasOutput(Item i,int count){return slots.stream().anyMatch(s->!s.input&&s.item!=null&&s.item.getItem()==i&&s.item.getCount()==count);}
    }
    public static void main(String[] args){
        register();var plugin=new SlimeTechCompatJeiPlugin();var reg=new Registration();
        plugin.registerCategories(reg);plugin.registerRecipes(reg);plugin.registerRecipeCatalysts(reg);
        check(reg.categories.size()==2,"casting and mixer categories registered");
        check(reg.recipes.get(CastingMoldRecipeCategory.TYPE).size()==1,"exactly one casting display");
        check(reg.recipes.get(MixerRecipeCategory.TYPE).equals(List.of(new MixerRecipe(false))),"without Ore Slimes only clay recipe is present");
        check(reg.stations.get(CastingMoldRecipeCategory.TYPE).getFirst().getItem()==item("slime_tech:casting_mold"),"casting crafting station lookup");
        check(reg.stations.get(MixerRecipeCategory.TYPE).getFirst().getItem()==item("slime_tech:mixer"),"mixer crafting station lookup");
        check(reg.info.size()==10&&new HashSet<>(reg.info).size()==10,"ten unique documentation paragraphs registered");
        ITEMS.entries.put(Identifier.parse("oreslimes:slimy_coal"),new Item());var partial=new Registration();plugin.registerRecipes(partial);
        check(partial.recipes.get(MixerRecipeCategory.TYPE).size()==1,"missing copper suppresses radioactive display");
        ITEMS.entries.put(Identifier.parse("oreslimes:slimy_copper"),new Item());var full=new Registration();plugin.registerRecipes(full);
        check(full.recipes.get(MixerRecipeCategory.TYPE).equals(List.of(new MixerRecipe(false),new MixerRecipe(true))),"both Ore Slimes inputs enable second recipe");
        var gui=reg.getJeiHelpers().getGuiHelper();
        var casting=new CastingMoldRecipeCategory(gui);var c=new Layout();casting.setRecipe(c,CastingMoldRecipe.INSTANCE,null);
        check(c.hasFluid(true,fluid("slime_tech:condensed_slime"),1000),"casting input is 1000 mB condensed slime");
        check(c.hasOutput(item("slime_tech:gel_ingot"),1),"Gel Ingot output is indexed for R lookup");
        check(casting.getIdentifier(CastingMoldRecipe.INSTANCE).toString().equals("slime_tech_compat:gel_ingot_casting"),"0.2 casting recipe identifier preserved");
        var mixer=new MixerRecipeCategory(gui);var clay=new Layout();mixer.setRecipe(clay,new MixerRecipe(false),null);
        check(clay.countItem(Items.SLIME_BALL)==1&&clay.slots.get(4).item.getItem()==Items.SLIME_BALL,"clay has one slime ball in center");
        check(clay.countItem(item("slime_tech:dust"))==1&&clay.slots.stream().filter(s->s.input&&s.item!=null).count()==2,"clay has exactly two filled item slots");
        check(clay.hasFluid(true,Fluids.WATER,250)&&clay.hasOutput(Items.CLAY,1),"clay fluid quantity and output");
        var r=new Layout();mixer.setRecipe(r,new MixerRecipe(true),null);
        check(r.countItem(item("oreslimes:slimy_coal"))==6&&r.countItem(item("oreslimes:slimy_copper"))==3,"radioactive uses six coal slots and three copper slots");
        check(r.slots.subList(3,6).stream().allMatch(s->s.item.getItem()==item("oreslimes:slimy_copper")),"copper in middle row");
        check(r.hasFluid(true,fluid("slime_tech:condensed_slime"),1000)&&r.hasFluid(false,fluid("slime_tech:radioactive_slime"),1000),"radioactive fluid conversion display is one bucket each");
        check(r.slots.stream().filter(s->s.item!=null).allMatch(s->s.item.getCount()==1),"one unit consumed per occupied slot");
        System.out.println("JEI_DISPLAY_TESTS="+passed);
    }
}
