/* MIT. Executes selected ORIGINAL 0.1.44 bytecode against explicit Minecraft fixtures.
 * The patched version uses OfflineWeaver's annotation model, not the actual Mixin engine.
 * Run the SAME assertions against baseline bytecode as negative controls.
 */
import dev.wizardjw.slimetech.blockentity.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.item.*;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;

public final class RuntimeSliceTests {
    static int passed,failed;static boolean policies;
    interface Test {void run()throws Exception;}
    static void test(String name,Test t){try{t.run();passed++;System.out.println("PASS "+name);}catch(Throwable e){failed++;System.out.println("FAIL "+name+" :: "+e);e.printStackTrace(System.out);}}
    static void eq(Object actual,Object expected,String label){if(!Objects.equals(actual,expected))throw new AssertionError(label+": expected "+expected+", got "+actual);}
    static void raw(Object o,String field,Object v)throws Exception{var f=o.getClass().getDeclaredField(field);f.setAccessible(true);f.set(o,v);}
    static int amount(FluidTankBlockEntity t)throws Exception{var f=t.getClass().getDeclaredField("localAmount");f.setAccessible(true);return f.getInt(t);}
    static FluidTankBlockEntity tank(Level l,int x,int n,String id)throws Exception{
        var t=new FluidTankBlockEntity(new BlockPos(x,10,0),new BlockState());l.put(t);raw(t,"localAmount",n);raw(t,"storedFluidId",id);return t;
    }
    static PumpBlockEntity pump(Level l){var p=new PumpBlockEntity(new BlockPos(0,10,0),new BlockState());l.put(p);p.getContainerData().set(2,500);return p;}
    static void tick(PumpBlockEntity p){PumpBlockEntity.serverTick(p.getLevel(),p.getBlockPos(),new BlockState(),p);}
    static void load(Object p,Values values)throws Exception{var m=p.getClass().getDeclaredMethod("loadMachineData",ValueInput.class);m.setAccessible(true);m.invoke(p,values);}
    static Values save(Object p)throws Exception{var v=new Values();var m=p.getClass().getDeclaredMethod("saveMachineData",ValueOutput.class);m.setAccessible(true);m.invoke(p,v);return v;}
    public static class Values implements ValueInput,ValueOutput {
        final Map<String,Object> data=new HashMap<>();Values add(String k,Object v){data.put(k,v);return this;}
        public int getIntOr(String k,int d){return data.get(k) instanceof Integer n?n:d;}
        public String getStringOr(String k,String d){return data.get(k) instanceof String s?s:d;}
        public void putInt(String k,int v){data.put(k,v);}public void putString(String k,String v){data.put(k,v);}
    }
    static GrinderBlockEntity grinder(int energy,int progress){
        var b=new GrinderBlockEntity(new BlockPos(0,10,0),new BlockState());new Level().put(b);
        b.getContainerData().set(0,energy);b.getContainerData().set(1,progress);b.getContainerData().set(2,40);
        b.setItem(0,new ItemStack(SlimeTechMachineLogic.INPUT));return b;
    }
    static void tick(GrinderBlockEntity b){GrinderBlockEntity.serverTick(b.getLevel(),b.getBlockPos(),new BlockState(),b);}
    public static void main(String[] args)throws Exception{
        policies=args.length==0||!args[0].equals("off");
        Path cfg=Files.createTempDirectory("stc-slices");System.setProperty("compat.test.configDir",cfg.toString());
        Files.writeString(cfg.resolve("slime-tech-compat.properties"),"blockNonWaterWorldSources="+policies+"\nfixGrinderEnergy="+policies+"\nmaxTankNetworkMembers=3\n");
        test("mixed tank extraction rejected without any fluid loss",()->{
            Level l=new Level();var a=tank(l,0,1000,"minecraft:water");var b=tank(l,1,8000,"minecraft:lava");
            eq(a.extractFluid(null,1,1000,false),0,"mixed extraction");eq(amount(a),1000,"water kept");eq(amount(b),8000,"lava kept");
        });
        test("mixed tank insertion simulation rejected",()->{
            Level l=new Level();var a=tank(l,0,1000,"minecraft:water");var b=tank(l,1,1000,"minecraft:lava");
            eq(a.insertFluid(null,1,500,true),0,"simulated insert blocked");eq(amount(a),1000,"water unchanged");eq(amount(b),1000,"lava unchanged");
        });
        test("empty connecting tank does not hide mixed fluids",()->{
            Level l=new Level();tank(l,0,1000,"minecraft:water");var mid=tank(l,1,0,"");tank(l,2,2000,"minecraft:lava");
            eq(mid.getFluidAmount(),0,"unsafe aggregate hidden");eq(mid.canExtractFluid(null,1),false,"bridge extraction blocked");
        });
        test("same-fluid network still inserts and extracts",()->{
            Level l=new Level();var a=tank(l,0,1000,"minecraft:lava");var b=tank(l,1,0,"");
            eq(a.insertFluid(null,2,3000,false),3000,"insert");eq(a.getFluidAmount(),4000,"aggregate");
            eq(a.extractFluid(null,2,2000,false),2000,"extract");eq(amount(a)+amount(b),2000,"remaining");
        });
        test("simulation never changes raw tank amounts",()->{
            Level l=new Level();var a=tank(l,0,1000,"minecraft:water");var b=tank(l,1,0,"");
            eq(a.insertFluid(null,1,2000,true),2000,"simulate insert result");eq(a.extractFluid(null,1,600,true),600,"simulate extract result");
            eq(amount(a),1000,"raw a");eq(amount(b),0,"raw b");
        });
        test("separating a mixed network restores access",()->{
            Level l=new Level();var a=tank(l,0,1000,"minecraft:water");var b=tank(l,1,1000,"minecraft:lava");
            eq(a.canExtractFluid(null,1),false,"blocked before separation");l.entities.remove(b.getBlockPos());
            eq(a.extractFluid(null,1,500,false),500,"resumed");eq(amount(a),500,"water remaining");eq(amount(b),1000,"disconnected lava preserved");
        });
        test("unregistered raw fluid is hidden, not erased",()->{
            Level l=new Level();var a=tank(l,0,333,"missing:fluid");eq(a.getFluidAmount(),0,"no false aggregate");eq(amount(a),333,"unknown amount retained");
        });
        test("tank size limit fails closed",()->{
            Level l=new Level();var a=tank(l,0,1,"minecraft:water");tank(l,1,0,"");tank(l,2,0,"");tank(l,3,0,"");
            eq(a.getFluidCapacity(),0,"oversized network not partly advertised");eq(amount(a),1,"contents retained");
        });
        test("unloaded neighbor fails closed without world loading",()->{
            Level l=new Level();var a=tank(l,0,222,"minecraft:water");l.unloaded.add(new BlockPos(1,10,0));
            eq(a.getFluidCapacity(),0,"unloaded neighbor blocked");eq(amount(a),222,"contents retained");
        });
        test("pump keeps buffered identity when source disappears",()->{
            Level l=new Level();var p=pump(l);p.getContainerData().set(0,2);p.getContainerData().set(1,1000);tick(p);
            eq(p.getFluidTypeCode(),2,"type retained");eq(p.getFluidAmount(),1000,"amount retained");eq(p.getEnergyStored(),500,"no energy spent");
        });
        test("pump does not turn buffered lava into a newly found water source",()->{
            Level l=new Level();var p=pump(l);p.getContainerData().set(0,2);p.getContainerData().set(1,1000);
            l.fluids.put(p.getBlockPos().relative(Direction.WEST),new FluidState(Fluids.WATER,true));tick(p);
            eq(p.getFluidTypeCode(),2,"old identity retained");eq(p.getFluidAmount(),1000,"different fluid not mixed");eq(p.getEnergyStored(),500,"no energy spent");
        });
        test("water source pumping remains available",()->{
            Level l=new Level();var p=pump(l);l.fluids.put(p.getBlockPos().relative(Direction.WEST),new FluidState(Fluids.WATER,true));tick(p);
            eq(p.getFluidTypeCode(),1,"water identity");eq(p.getFluidAmount(),100,"pump production");eq(p.getEnergyStored(),490,"pump energy");
        });
        test("non-water source policy obeys opt-in switch",()->{
            Level l=new Level();var p=pump(l);l.fluids.put(p.getBlockPos().relative(Direction.WEST),new FluidState(Fluids.LAVA,true));tick(p);
            eq(p.getFluidAmount(),policies?0:100,"source production");eq(p.getEnergyStored(),policies?500:490,"energy unchanged if blocked");
        });
        test("pump stable ID survives dynamic registration order change",()->{
            SlimeTechFluidType.fixtureReset();SlimeTechFluidType.fixtureRegister(1000,"other:oil",new Fluid());
            var p=pump(new Level());p.getContainerData().set(0,1000);p.getContainerData().set(1,1234);p.getContainerData().set(2,721);
            Values data=save(p);SlimeTechFluidType.fixtureReset();SlimeTechFluidType.fixtureRegister(1000,"other:acid",new Fluid());SlimeTechFluidType.fixtureRegister(1001,"other:oil",new Fluid());
            var next=pump(new Level());load(next,data);eq(next.getFluidTypeCode(),1001,"correct remapped oil ID");eq(next.getFluidAmount(),1234,"amount");eq(next.getEnergyStored(),721,"energy");
        });
        test("legacy dynamic pump quarantines despite a colliding runtime code",()->{
            SlimeTechFluidType.fixtureReset();SlimeTechFluidType.fixtureRegister(1000,"other:acid",new Fluid());
            var p=pump(new Level());load(p,new Values().add("FluidType",1000).add("FluidAmount",777).add("Energy",371));
            eq(p.getFluidTypeCode(),0,"no guessed acid identity");eq(p.extractFluid(null,1000,200,false),0,"no unsafe extraction");
            eq(p.getFluidAmount(),777,"quarantined amount retained");eq(p.getEnergyStored(),371,"charge retained");
        });
        test("legacy quarantine survives two save/load cycles",()->{
            SlimeTechFluidType.fixtureReset();var p=pump(new Level());load(p,new Values().add("FluidType",1000).add("FluidAmount",777));
            Values data=save(p);eq(data.getIntOr("SlimeTechCompatUnresolvedCode",-1),1000,"diagnostic code persisted");
            var next=pump(new Level());load(next,data);Values second=save(next);
            eq(next.getFluidTypeCode(),0,"still blocked");eq(second.getIntOr("FluidAmount",0),777,"amount persisted twice");
            eq(second.getIntOr("SlimeTechCompatUnresolvedCode",0),1000,"code persisted twice");
        });
        test("quarantined pump cannot manufacture replacements",()->{
            SlimeTechFluidType.fixtureReset();Level l=new Level();var p=pump(l);load(p,new Values().add("FluidType",1000).add("FluidAmount",777).add("Energy",500));
            l.fluids.put(p.getBlockPos().relative(Direction.WEST),new FluidState(Fluids.WATER,true));tick(p);
            eq(p.getFluidAmount(),777,"frozen amount");eq(p.getFluidTypeCode(),0,"no retyping");eq(p.getEnergyStored(),500,"no energy use");
        });
        test("missing fluid registry ID is preserved then recoverable",()->{
            SlimeTechFluidType.fixtureReset();var p=pump(new Level());load(p,new Values().add("FluidType",1000).add("FluidAmount",432).add("SlimeTechCompatFluidId","other:oil"));
            Values data=save(p);eq(data.getStringOr("SlimeTechCompatFluidId",""),"other:oil","missing ID retained");eq(p.getFluidTypeCode(),0,"missing ID blocked");
            SlimeTechFluidType.fixtureRegister(1013,"other:oil",new Fluid());var restored=pump(new Level());load(restored,data);
            eq(restored.getFluidTypeCode(),1013,"ID recovers after registration");eq(restored.getFluidAmount(),432,"amount recovered");
        });
        test("fixed-code lava pump migrates without losing contents",()->{
            SlimeTechFluidType.fixtureReset();var p=pump(new Level());load(p,new Values().add("FluidType",2).add("FluidAmount",900).add("Energy",123));
            eq(p.getFluidTypeCode(),2,"lava code");eq(p.getFluidAmount(),900,"lava amount");eq(save(p).getStringOr("SlimeTechCompatFluidId",""),"minecraft:lava","stable vanilla ID");
        });
        test("grinder fresh charge follows opt-in setting",()->{
            var g=new GrinderBlockEntity(new BlockPos(0,10,0),new BlockState());eq(g.getEnergyStored(),policies?0:2000,"initial charge");
        });
        test("grinder preserves explicit saved charge",()->{
            var g=grinder(0,0);load(g,new Values().add("EnergyStored",677).add("Progress",12).add("MaxProgress",40));eq(g.getEnergyStored(),677,"saved charge");eq(g.getProgress(),12,"saved progress");
        });
        test("grinder missing energy field follows opt-in setting",()->{
            var g=grinder(0,0);load(g,new Values());eq(g.getEnergyStored(),policies?0:2000,"missing charge fallback");
        });
        test("grinder 15 FE cannot buy a 120 FE completion when enabled",()->{
            var g=grinder(15,39);tick(g);eq(g.getItem(1).isEmpty(),policies,"underfunded output");eq(g.getEnergyStored(),policies?15:0,"no partial charge");
            eq(g.getProgress(),policies?39:0,"progress not reset by blocked completion");
        });
        test("grinder 119 FE boundary",()->{
            var g=grinder(119,39);tick(g);eq(g.getItem(1).isEmpty(),policies,"119 FE output");eq(g.getEnergyStored(),policies?119:0,"119 FE conserved");
        });
        test("grinder full-cost completion succeeds",()->{
            var g=grinder(120,39);tick(g);eq(g.getItem(1).getCount(),1,"one output");eq(g.getItem(0).isEmpty(),true,"one input consumed");eq(g.getEnergyStored(),0,"120 FE spent");eq(g.getProgress(),0,"progress reset");
        });
        System.out.println("SLICE_TESTS passed="+passed+" failed="+failed+" optionalPolicies="+policies);
        if(failed!=0)System.exit(1);
    }
}
