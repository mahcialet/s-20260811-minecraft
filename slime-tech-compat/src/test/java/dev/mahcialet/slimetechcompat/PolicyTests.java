package dev.mahcialet.slimetechcompat.policytests;
import dev.mahcialet.slimetechcompat.CompatConfig;
import dev.mahcialet.slimetechcompat.safety.*;
import java.util.*;
import java.nio.file.*;
import java.util.function.Function;

/** Dependency-free policy/config tests; also runnable by `gradle policyTest`. */
public final class PolicyTests {
    private static int tests;
    static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);tests++;System.out.println("PASS "+name);}
    static TankPolicy.Contents c(int n,String id){return new TankPolicy.Contents(n,id);}
    static boolean blocked(TankPolicy.Contents... entries){return TankPolicy.assess(List.of(entries), s->s.startsWith("missing:")?"":s).blocked();}
    static void configReject(String text,String name)throws Exception{
        Path p=Files.createTempDirectory("stc-reject").resolve("config.properties");Files.writeString(p,text);
        try{CompatConfig.load(p);throw new AssertionError(name);}catch(IllegalStateException expected){check(expected.getCause() instanceof IllegalArgumentException,name);}
    }
    public static void main(String[] args)throws Exception{
        check(!blocked(c(0,"")),"empty tank allowed");
        check(!blocked(c(8000,"minecraft:water"),c(1000,"minecraft:water")),"same fluid allowed");
        check(!blocked(c(0,"minecraft:lava"),c(50,"minecraft:water")),"empty stale ID is not a fluid");
        check(blocked(c(1,"minecraft:water"),c(1,"minecraft:lava")),"different fluids rejected");
        check(blocked(c(1,"minecraft:water"),c(0,""),c(1,"minecraft:lava")),"empty bridge does not hide mixture");
        check(blocked(c(1,"")),"nonempty without ID rejected");
        check(blocked(c(1,null)),"null ID rejected");
        check(blocked(c(1,"missing:oil")),"missing registry ID rejected");
        check(blocked(c(-1,"minecraft:water")),"negative amount rejected");
        check(blocked(c(8001,"minecraft:water")),"overcapacity amount rejected");
        var original=new ArrayList<>(List.of(c(4000,"minecraft:water"),c(7000,"minecraft:lava")));
        var saved=List.copyOf(original);TankPolicy.assess(original,Function.identity());
        check(original.equals(saved),"mixed validation does not mutate contents");
        check(!TankPolicy.assess(List.of(c(10,"minecraft:water"),c(10,"minecraft:flowing_water")),s->"minecraft:water").blocked(),"canonical aliases can agree");
        var resolve=(java.util.function.ToIntFunction<String>)(id->id.equals("other:oil")?1007:0);
        var builtin=(java.util.function.IntFunction<String>)(code->Map.of(1,"minecraft:water",2,"minecraft:lava",3,"slime_tech:condensed_slime",4,"slime_tech:radioactive_slime").getOrDefault(code,""));
        for(int code=1;code<=4;code++){
            var r=PumpIdentityPolicy.restore(1000,code,"",0,resolve,builtin);
            check(!r.quarantined()&&r.runtimeCode()==code&&!r.stableId().isEmpty(),"legacy fixed code "+code+" migrates");
        }
        var r=PumpIdentityPolicy.restore(1000,1000,"",0,id->1000,builtin);
        check(r.quarantined()&&r.runtimeCode()==0&&r.unresolvedCode()==1000,"legacy dynamic number never trusted");
        r=PumpIdentityPolicy.restore(1000,1000,"other:oil",0,resolve,builtin);
        check(!r.quarantined()&&r.runtimeCode()==1007,"stable ID remaps across session order");
        r=PumpIdentityPolicy.restore(1000,1000,"missing:oil",0,resolve,builtin);
        check(r.quarantined()&&r.stableId().equals("missing:oil"),"removed fluid ID preserved in quarantine");
        r=PumpIdentityPolicy.restore(1000,0,"",1000,resolve,builtin);
        check(r.quarantined()&&r.unresolvedCode()==1000,"quarantine survives repeat restore");
        r=PumpIdentityPolicy.restore(0,1000,"other:oil",1000,resolve,builtin);
        check(!r.quarantined()&&r.runtimeCode()==0&&r.stableId().isEmpty(),"empty pump clears identity safely");
        for(int n:new int[]{-1,4001}) check(PumpIdentityPolicy.restore(n,2,"",0,resolve,builtin).quarantined(),"invalid pump amount "+n+" blocked");
        check(PumpIdentityPolicy.restore(20,5,"",0,resolve,builtin).quarantined(),"unknown nonbuiltin code blocked");
        check(PumpIdentityPolicy.restore(20,2,"bad:id",0,resolve,builtin).quarantined(),"invalid explicit identity overrides numeric fallback");
        Path path=Files.createTempDirectory("stc-config").resolve("slime-tech-compat.properties");
        var s=CompatConfig.load(path);check(!s.blockNonWaterWorldSources()&&!s.fixGrinderEnergy()&&s.maxTankNetworkMembers()==512,"safe compatibility defaults");
        String unchanged=Files.readString(path);CompatConfig.load(path);check(Files.readString(path).equals(unchanged),"config not overwritten on reload");
        Files.writeString(path,"blockNonWaterWorldSources=true\nfixGrinderEnergy=true\nmaxTankNetworkMembers=1\n");s=CompatConfig.load(path);
        check(s.blockNonWaterWorldSources()&&s.fixGrinderEnergy()&&s.maxTankNetworkMembers()==1,"opt-in policies parsed");
        configReject("fixGrinderEnergy=maybe\n","invalid boolean rejects startup");
        configReject("fixGrinderEnergi=true\n","misspelled key rejected");
        configReject("maxTankNetworkMembers=0\n","zero limit rejected");
        configReject("maxTankNetworkMembers=4097\n","oversized limit rejected");
        configReject("maxTankNetworkMembers=NaN\n","invalid integer rejected");
        System.out.println("POLICY_TESTS="+tests);
    }
}
