"""Independently authored OFFLINE compilation/test fixtures. Never package these classes.
They model only the binary signatures exercised by the patch. They are not real Minecraft,
NeoForge, Mixin, JEI, or Slime Tech libraries. Use Gradle + the real dependencies for release verification.
"""
from pathlib import Path

def write_stubs(root: Path):
    stubs = {}
    def add(name, body):
        package, cls = name.rsplit('.', 1)
        stubs[name.replace('.', '/')+'.java'] = f'package {package};\n'+body+'\n'
    add('net.minecraft.resources.Identifier', '''public record Identifier(String namespace, String path) {
        public static Identifier fromNamespaceAndPath(String n,String p){return new Identifier(n,p);}
        public static Identifier parse(String s){Identifier i=tryParse(s);if(i==null)throw new IllegalArgumentException(s);return i;}
        public static Identifier tryParse(String s){if(s==null||!s.matches("[a-z0-9_./-]+:[a-z0-9_./-]+"))return null;String[] a=s.split(":",2);return new Identifier(a[0],a[1]);}
        public String getPath(){return path;} public String getNamespace(){return namespace;}
        public String toString(){return namespace+":"+path;}
    }''')
    add('net.minecraft.core.BlockPos', '''public record BlockPos(int x,int y,int z) {
        public BlockPos relative(Direction d){return new BlockPos(x+d.x,y+d.y,z+d.z);}
        public long asLong(){return ((long)x<<40)^((long)z<<16)^y;}
        public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}
    }''')
    add('net.minecraft.core.Direction','''public enum Direction {
        DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
        public final int x,y,z;Direction(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public Direction getOpposite(){return values()[ordinal()^1];}
    }''')
    add('net.minecraft.world.inventory.ContainerData','public interface ContainerData {int get(int i);void set(int i,int v);int getCount();}')
    add('net.minecraft.world.inventory.SimpleContainerData','''public class SimpleContainerData implements ContainerData {
        private final int[] a;public SimpleContainerData(int size){a=new int[size];}
        public int get(int i){return a[i];}public void set(int i,int v){a[i]=v;}public int getCount(){return a.length;}
    }''')
    add('net.minecraft.world.level.block.state.BlockState','public class BlockState { }')
    add('net.minecraft.world.level.block.entity.BlockEntityType','public class BlockEntityType<T> { }')
    add('net.minecraft.world.level.block.entity.BlockEntity','''public class BlockEntity {
        protected final net.minecraft.core.BlockPos worldPosition;protected net.minecraft.world.level.Level level;
        public BlockEntity(BlockEntityType<?> t,net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){worldPosition=p;}
        public net.minecraft.core.BlockPos getBlockPos(){return worldPosition;}
        public net.minecraft.world.level.Level getLevel(){return level;}
        public void setLevel(net.minecraft.world.level.Level l){level=l;}public void setChanged(){}
    }''')
    add('net.minecraft.world.level.Level','''public class Level {
        public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity> entities=new java.util.HashMap<>();
        public final java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.material.FluidState> fluids=new java.util.HashMap<>();
        public final java.util.Set<net.minecraft.core.BlockPos> unloaded=new java.util.HashSet<>();
        public boolean isClientSide(){return false;}
        public boolean hasChunkAt(net.minecraft.core.BlockPos p){return !unloaded.contains(p);}
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos p){return entities.get(p);}
        public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos p){return fluids.getOrDefault(p,new net.minecraft.world.level.material.FluidState(net.minecraft.world.level.material.Fluids.EMPTY,false));}
        public void put(net.minecraft.world.level.block.entity.BlockEntity b){entities.put(b.getBlockPos(),b);b.setLevel(this);}
    }''')
    add('net.minecraft.world.level.storage.ValueInput','public interface ValueInput { int getIntOr(String k,int d); String getStringOr(String k,String d); }')
    add('net.minecraft.world.level.storage.ValueOutput','public interface ValueOutput { void putInt(String k,int v); void putString(String k,String v); }')
    add('net.minecraft.world.level.material.Fluid','public class Fluid { }')
    add('net.minecraft.world.level.material.FlowingFluid','public class FlowingFluid extends Fluid { }')
    add('net.minecraft.world.level.material.Fluids','''public class Fluids {
        public static final Fluid EMPTY=new Fluid();
        public static final FlowingFluid WATER=new FlowingFluid(),LAVA=new FlowingFluid(),FLOWING_WATER=new FlowingFluid(),FLOWING_LAVA=new FlowingFluid();
    }''')
    add('net.minecraft.world.level.material.FluidState','''public class FluidState {
        private final Fluid fluid;private final boolean source;
        public FluidState(Fluid fluid,boolean source){this.fluid=fluid;this.source=source;}
        public Fluid getType(){return fluid;}public boolean isSource(){return source;}public boolean isEmpty(){return fluid==Fluids.EMPTY;}
    }''')
    add('net.minecraft.world.level.ItemLike','public interface ItemLike {net.minecraft.world.item.Item asItem();}')
    add('net.minecraft.world.item.Item','public class Item implements net.minecraft.world.level.ItemLike {public Item asItem(){return this;}}')
    add('net.minecraft.world.item.BucketItem','public class BucketItem extends Item {private final net.minecraft.world.level.material.Fluid content;public BucketItem(net.minecraft.world.level.material.Fluid f){content=f;}}')
    add('net.minecraft.world.item.Items','''public class Items {
        public static final Item AIR=new Item(),BUCKET=new Item(),CLAY=new Item(),SLIME_BALL=new Item();
        public static final Item WATER_BUCKET=new BucketItem(net.minecraft.world.level.material.Fluids.WATER),LAVA_BUCKET=new BucketItem(net.minecraft.world.level.material.Fluids.LAVA);
    }''')
    add('net.minecraft.world.item.ItemStack','''public class ItemStack {
        public static final ItemStack EMPTY=new ItemStack(Items.AIR,0);private final Item item;private int count;
        public ItemStack(net.minecraft.world.level.ItemLike i){this(i,1);}public ItemStack(net.minecraft.world.level.ItemLike i,int n){item=i.asItem();count=n;}
        public boolean isEmpty(){return item==Items.AIR||count<=0;}public Item getItem(){return item;}public int getCount(){return count;}
        public void grow(int n){count+=n;}public void shrink(int n){count-=n;}public void setCount(int n){count=n;}public int getMaxStackSize(){return 64;}
        public ItemStack copy(){return new ItemStack(item,count);}public boolean is(Object i){return item==i;}
        public static boolean isSameItemSameComponents(ItemStack a,ItemStack b){return a.item==b.item;}
    }''')
    add('net.minecraft.world.inventory.AbstractContainerMenu','public abstract class AbstractContainerMenu { }')
    add('net.minecraft.world.entity.player.Inventory','public class Inventory { }')
    add('net.minecraft.world.entity.player.Player','public class Player { }')
    add('net.minecraft.network.chat.Component','''public interface Component {
        static MutableComponent translatable(String key){return new MutableComponent(key);}
    }''')
    add('net.minecraft.network.chat.MutableComponent','public class MutableComponent implements Component {public final String key;public MutableComponent(String k){key=k;}}')
    add('net.minecraft.core.DefaultedRegistry','''public interface DefaultedRegistry<T> extends java.lang.Iterable<T> {
        T getValue(net.minecraft.resources.Identifier id);net.minecraft.resources.Identifier getKey(T value);
    }''')
    add('net.minecraft.core.registries.BuiltInRegistries','''public class BuiltInRegistries {
        public static class FixtureRegistry<T> implements net.minecraft.core.DefaultedRegistry<T> {
            private final T fallback;public final java.util.Map<net.minecraft.resources.Identifier,T> entries=new java.util.LinkedHashMap<>();
            public FixtureRegistry(T fallback){this.fallback=fallback;}
            public T getValue(net.minecraft.resources.Identifier id){return entries.getOrDefault(id,fallback);}
            public net.minecraft.resources.Identifier getKey(T value){return entries.entrySet().stream().filter(e->e.getValue()==value).map(java.util.Map.Entry::getKey).findFirst().orElse(null);}
            public java.util.Iterator<T> iterator(){return entries.values().iterator();}
        }
        public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.item.Item> ITEM=new FixtureRegistry<>(net.minecraft.world.item.Items.AIR);
        public static final net.minecraft.core.DefaultedRegistry<net.minecraft.world.level.material.Fluid> FLUID=new FixtureRegistry<>(net.minecraft.world.level.material.Fluids.EMPTY);
    }''')
    add('net.neoforged.neoforge.registries.DeferredHolder','public class DeferredHolder<R,T extends R> {private final T value;public DeferredHolder(T v){value=v;}public T get(){return value;}}')
    add('net.neoforged.fml.common.Mod','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE) public @interface Mod {String value();}')
    add('net.neoforged.fml.loading.FMLPaths','''public enum FMLPaths {CONFIGDIR;public java.nio.file.Path get(){return java.nio.file.Path.of(System.getProperty("compat.test.configDir","build/offline-config"));}}''')
    # No original implementations below: these declarations exist solely to compile or host test slices.
    add('dev.wizardjw.slimetech.blockentity.SlimeTechMenuBlockEntity','''public abstract class SlimeTechMenuBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
        protected final net.minecraft.world.inventory.SimpleContainerData data;private final net.minecraft.world.item.ItemStack[] items;
        protected SlimeTechMenuBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> t,net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s,int slots,int dataSize,String name){
            super(t,p,s);data=new net.minecraft.world.inventory.SimpleContainerData(dataSize);items=new net.minecraft.world.item.ItemStack[slots];java.util.Arrays.fill(items,net.minecraft.world.item.ItemStack.EMPTY);
        }
        public net.minecraft.world.inventory.ContainerData getContainerData(){return data;}
        protected int getDataValue(int n){return data.get(n);}protected void setDataValue(int i,int n){data.set(i,n);setChanged();}
        public net.minecraft.world.item.ItemStack getItem(int n){return items[n];}public void setItem(int n,net.minecraft.world.item.ItemStack s){items[n]=s;}
        public net.minecraft.world.item.ItemStack removeItem(int n,int a){var old=items[n];var result=old.copy();result.setCount(Math.min(a,old.getCount()));old.shrink(a);return result;}
    }''')
    add('dev.wizardjw.slimetech.SlimeTech','''public class SlimeTech {
        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>,net.minecraft.world.level.block.entity.BlockEntityType<?>>
            FLUID_TANK_BLOCK_ENTITY=new net.neoforged.neoforge.registries.DeferredHolder<>(new net.minecraft.world.level.block.entity.BlockEntityType<>()),
            PUMP_BLOCK_ENTITY=new net.neoforged.neoforge.registries.DeferredHolder<>(new net.minecraft.world.level.block.entity.BlockEntityType<>()),
            GRINDER_BLOCK_ENTITY=new net.neoforged.neoforge.registries.DeferredHolder<>(new net.minecraft.world.level.block.entity.BlockEntityType<>());
    }''')
    for cls,size in [('FluidTankBlockEntity',3),('PumpBlockEntity',5),('GrinderBlockEntity',3)]:
        common=f'''public class {cls} extends SlimeTechMenuBlockEntity {{
            public {cls}(net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s){{super(null,p,s,2,{size},"");}}
            public int getFluidAmount(){{return 0;}}public int getFluidTypeCode(){{return 0;}}public int getFluidCapacity(){{return 0;}}
            public int getEnergyStored(){{return 0;}}public int getProgress(){{return 0;}}
            public boolean canInsertFluid(net.minecraft.core.Direction d,int t){{return false;}}
            public boolean canExtractFluid(net.minecraft.core.Direction d,int t){{return false;}}
            public int insertFluid(net.minecraft.core.Direction d,int t,int a,boolean sim){{return 0;}}
            public int extractFluid(net.minecraft.core.Direction d,int t,int a,boolean sim){{return 0;}}
            public static void serverTick(net.minecraft.world.level.Level l,net.minecraft.core.BlockPos p,net.minecraft.world.level.block.state.BlockState s,{cls} b){{}}
        }}'''
        add('dev.wizardjw.slimetech.blockentity.'+cls,common)
    add('dev.wizardjw.slimetech.blockentity.SlimeTechFluidType','''public final class SlimeTechFluidType {
        private static final java.util.Map<Integer,String> ids=new java.util.HashMap<>();
        private static final java.util.Map<String,Integer> codes=new java.util.HashMap<>();
        private static final java.util.Map<net.minecraft.world.level.material.Fluid,Integer> fluids=new java.util.IdentityHashMap<>();
        public static void fixtureReset(){ids.clear();codes.clear();fluids.clear();fixtureRegister(1,"minecraft:water",net.minecraft.world.level.material.Fluids.WATER);fixtureRegister(2,"minecraft:lava",net.minecraft.world.level.material.Fluids.LAVA);fixtureRegister(3,"slime_tech:condensed_slime",new net.minecraft.world.level.material.Fluid());fixtureRegister(4,"slime_tech:radioactive_slime",new net.minecraft.world.level.material.Fluid());}
        public static void fixtureRegister(int c,String id,net.minecraft.world.level.material.Fluid f){ids.put(c,id);codes.put(id,c);fluids.put(f,c);}
        static{fixtureReset();}
        public static String toIdentifierString(int code){return ids.getOrDefault(code,"");}
        public static int fromIdentifierString(String id){return codes.getOrDefault(id,0);}
        public static int fromFluid(net.minecraft.world.level.material.Fluid f){return fluids.getOrDefault(f,0);}
        public static net.minecraft.world.level.material.Fluid toFluid(int c){return fluids.entrySet().stream().filter(e->e.getValue()==c).map(java.util.Map.Entry::getKey).findFirst().orElse(net.minecraft.world.level.material.Fluids.EMPTY);}
        public static boolean isHot(int c){return c>=2&&c<=4;}
    }''')
    add('dev.wizardjw.slimetech.blockentity.SlimeTechFluidNode','''public interface SlimeTechFluidNode {
        int getFluidTypeCode();int getFluidAmount();int getFluidCapacity();
        boolean canExtractFluid(net.minecraft.core.Direction d,int t);boolean canInsertFluid(net.minecraft.core.Direction d,int t);
        int extractFluid(net.minecraft.core.Direction d,int t,int a,boolean sim);int insertFluid(net.minecraft.core.Direction d,int t,int a,boolean sim);
    }''')
    add('dev.wizardjw.slimetech.blockentity.SlimeTechEnergyNode','public interface SlimeTechEnergyNode { }')
    add('dev.wizardjw.slimetech.blockentity.PowerPriority','public enum PowerPriority {CONSUMER,STORAGE,GENERATOR}')
    add('dev.wizardjw.slimetech.blockentity.TransportConfigBlockEntity','''public class TransportConfigBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
        public TransportConfigBlockEntity(){super(null,new net.minecraft.core.BlockPos(0,0,0),null);}public boolean isDirectionEnabled(net.minecraft.core.Direction d){return true;}
    }''')
    add('dev.wizardjw.slimetech.blockentity.FluidPipeBlockEntity','public class FluidPipeBlockEntity extends TransportConfigBlockEntity {public boolean isHotSafe(){return true;}}')
    add('dev.wizardjw.slimetech.blockentity.SlimeTechTransferLogic','''public class SlimeTechTransferLogic {
        public static SlimeTechFluidNode getFluidNode(net.minecraft.world.level.Level l,net.minecraft.core.BlockPos p,net.minecraft.core.Direction d){return l.getBlockEntity(p) instanceof SlimeTechFluidNode n?n:null;}
        public static int moveFluid(SlimeTechFluidNode a,net.minecraft.core.Direction ad,SlimeTechFluidNode b,net.minecraft.core.Direction bd,int amount){return 0;}
    }''')
    add('dev.wizardjw.slimetech.blockentity.SlimeTechMachineLogic','''public class SlimeTechMachineLogic {
        public static final net.minecraft.world.item.Item INPUT=new net.minecraft.world.item.Item(),OUTPUT=new net.minecraft.world.item.Item();
        public static net.minecraft.world.item.ItemStack grindResult(net.minecraft.world.item.ItemStack s){return s.is(INPUT)&&!s.isEmpty()?new net.minecraft.world.item.ItemStack(OUTPUT):net.minecraft.world.item.ItemStack.EMPTY;}
    }''')
    # Mixin annotation ABI. Only attributes explicitly used by this patch are modeled.
    add('org.spongepowered.asm.mixin.Mixin','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Mixin {Class<?>[] value();boolean remap() default true;}')
    add('org.spongepowered.asm.mixin.gen.Accessor','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Accessor {String value();}')
    add('org.spongepowered.asm.mixin.injection.At','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface At {String value();String target() default "";int ordinal() default -1;}')
    add('org.spongepowered.asm.mixin.injection.Constant','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Constant {int intValue();}')
    add('org.spongepowered.asm.mixin.injection.Inject','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Inject {String[] method();At[] at();boolean cancellable() default false;int require() default -1;int expect() default 1;int allow() default -1;}')
    add('org.spongepowered.asm.mixin.injection.Redirect','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Redirect {String[] method();At at();int require() default -1;int expect() default 1;int allow() default -1;}')
    add('org.spongepowered.asm.mixin.injection.ModifyConstant','@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ModifyConstant {String[] method();Constant[] constant();int require() default -1;int expect() default 1;int allow() default -1;}')
    add('org.spongepowered.asm.mixin.injection.callback.CallbackInfo','''public class CallbackInfo {private boolean cancelled;public CallbackInfo(String id,boolean c){}public void cancel(){cancelled=true;}public boolean isCancelled(){return cancelled;}}''')
    add('org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable','''public class CallbackInfoReturnable<T> extends CallbackInfo {
        private T value;public CallbackInfoReturnable(String id,boolean c){super(id,c);}public void setReturnValue(T v){value=v;cancel();}
        public T getReturnValue(){return value;}public int getReturnValueI(){return (Integer)value;}public boolean getReturnValueZ(){return (Boolean)value;}
    }''')
    # JEI 29.x methods used by the display-only plugin. Fixtures do not implement JEI.
    add('mezz.jei.api.JeiPlugin','public @interface JeiPlugin { }')
    add('mezz.jei.api.IModPlugin','''public interface IModPlugin {
        net.minecraft.resources.Identifier getPluginUid();
        default void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration r){}
        default void registerRecipes(mezz.jei.api.registration.IRecipeRegistration r){}
        default void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration r){}
    }''')
    add('mezz.jei.api.recipe.types.IRecipeType','''public interface IRecipeType<T> {
        static <T> IRecipeType<T> create(String n,String p,Class<? extends T> c){return new IRecipeType<T>(){};}
    }''')
    add('mezz.jei.api.recipe.IFocusGroup','public interface IFocusGroup { }')
    add('mezz.jei.api.gui.drawable.IDrawable','public interface IDrawable { }')
    add('mezz.jei.api.gui.builder.IIngredientAcceptor','''public interface IIngredientAcceptor<T extends IIngredientAcceptor<T>> {
        T add(net.minecraft.world.item.ItemStack s);T add(net.minecraft.world.level.material.Fluid f,long amount);
    }''')
    add('mezz.jei.api.gui.builder.IRecipeSlotBuilder','''public interface IRecipeSlotBuilder extends IIngredientAcceptor<IRecipeSlotBuilder> {
        IRecipeSlotBuilder setFluidRenderer(long c,boolean s,int w,int h);
        IRecipeSlotBuilder setOutputSlotBackground();IRecipeSlotBuilder setStandardSlotBackground();
    }''')
    add('mezz.jei.api.gui.builder.IRecipeLayoutBuilder','public interface IRecipeLayoutBuilder {IRecipeSlotBuilder addInputSlot(int x,int y);IRecipeSlotBuilder addOutputSlot(int x,int y);}')
    add('mezz.jei.api.helpers.IGuiHelper','public interface IGuiHelper {mezz.jei.api.gui.drawable.IDrawable createDrawableItemStack(net.minecraft.world.item.ItemStack s);}')
    add('mezz.jei.api.helpers.IJeiHelpers','public interface IJeiHelpers {IGuiHelper getGuiHelper();}')
    add('mezz.jei.api.recipe.category.IRecipeCategory','''public interface IRecipeCategory<T> {
        mezz.jei.api.recipe.types.IRecipeType<T> getRecipeType();net.minecraft.network.chat.Component getTitle();int getWidth();int getHeight();mezz.jei.api.gui.drawable.IDrawable getIcon();
        void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder b,T recipe,mezz.jei.api.recipe.IFocusGroup focuses);
        default net.minecraft.resources.Identifier getIdentifier(T recipe){return null;}
    }''')
    add('mezz.jei.api.registration.IRecipeCategoryRegistration','''public interface IRecipeCategoryRegistration {
        mezz.jei.api.helpers.IJeiHelpers getJeiHelpers();void addRecipeCategories(mezz.jei.api.recipe.category.IRecipeCategory<?>... c);
    }''')
    add('mezz.jei.api.registration.IRecipeRegistration','''public interface IRecipeRegistration {
        <T> void addRecipes(mezz.jei.api.recipe.types.IRecipeType<T> t,java.util.List<T> r);
        void addItemStackInfo(java.util.List<net.minecraft.world.item.ItemStack> s,net.minecraft.network.chat.Component... c);
    }''')
    add('mezz.jei.api.registration.IRecipeCatalystRegistration','''public interface IRecipeCatalystRegistration {
        void addCraftingStation(mezz.jei.api.recipe.types.IRecipeType<?> t,net.minecraft.world.item.ItemStack... s);
    }''')
    for path, body in stubs.items():
        p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
    return sorted(stubs)

if __name__=='__main__':
    import sys
    print(len(write_stubs(Path(sys.argv[1]))), 'offline ABI fixture files written')
