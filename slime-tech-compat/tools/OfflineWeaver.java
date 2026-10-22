/* MIT. Offline injection-model verifier, NOT Sponge Mixin and NOT a Minecraft launch test.
 * Reads the patch's real annotation metadata and applies the subset used by this project to
 * original target class files in a disposable build directory. Unrelated GUI methods are
 * replaced with null-return fixtures; selected original behavior remains executable.
 * Original/derived mod classes MUST NEVER be distributed in the patch or source archive.
 */
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

public final class OfflineWeaver implements Opcodes {
    static final String MIXIN="Lorg/spongepowered/asm/mixin/Mixin;";
    static final String INJ="Lorg/spongepowered/asm/mixin/injection/";
    static final String CI="org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    static final String CIR=CI+"Returnable";
    static int verified;
    static AnnotationNode annotation(List<AnnotationNode> list,String name){
        if(list!=null)for(var a:list)if(a.desc.equals(name))return a;return null;
    }
    static AnnotationNode annotation(MethodNode m,String name){var a=annotation(m.visibleAnnotations,name);return a!=null?a:annotation(m.invisibleAnnotations,name);}
    static Object get(AnnotationNode a,String key,Object fallback){
        if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return fallback;
    }
    @SuppressWarnings("unchecked") static List<String> selectors(AnnotationNode a){return (List<String>)get(a,"method",List.of());}
    static List<MethodNode> targets(ClassNode c,AnnotationNode a){
        var out=new ArrayList<MethodNode>();
        for(String sel:selectors(a)){
            int p=sel.indexOf('(');String n=p<0?sel:sel.substring(0,p);String d=p<0?null:sel.substring(p);
            var found=c.methods.stream().filter(m->m.name.equals(n)&&(d==null||d.equals(m.desc))).toList();
            if(found.size()!=1)throw new IllegalStateException("Ambiguous/missing target "+c.name+"."+sel+" -> "+found.size());
            out.add(found.get(0));
        }
        return out;
    }
    static ClassNode read(byte[] b){
        b=b.clone();b[6]=0;b[7]=65; // JVM 21 fixture ONLY. Runtime jar retains independently compiled code.
        var c=new ClassNode();new ClassReader(b).accept(c,0);return c;
    }
    static void check(AnnotationNode a,int n,String label){
        int min=(Integer)get(a,"require",1),max=(Integer)get(a,"allow",min);
        if(n<min||n>max)throw new IllegalStateException(label+" expected "+min+".."+max+", got "+n);
        verified+=n;System.out.println("CONTRACT PASS "+label+" matches="+n);
    }
    static boolean isStatic(MethodNode m){return (m.access&ACC_STATIC)!=0;}
    static MethodInsnNode call(ClassNode target,MethodNode handler){return new MethodInsnNode(isStatic(handler)?INVOKESTATIC:INVOKESPECIAL,target.name,handler.name,handler.desc,false);}
    static InsnList callback(ClassNode target,MethodNode m,MethodNode h,AnnotationNode a){
        var code=new InsnList();Type ret=Type.getReturnType(m.desc);
        String cb=ret.equals(Type.VOID_TYPE)?CI:CIR;
        int var=m.maxLocals++;boolean cancellable=(Boolean)get(a,"cancellable",false);
        code.add(new TypeInsnNode(NEW,cb));code.add(new InsnNode(DUP));code.add(new LdcInsnNode(m.name));
        code.add(new InsnNode(cancellable?ICONST_1:ICONST_0));
        code.add(new MethodInsnNode(INVOKESPECIAL,cb,"<init>","(Ljava/lang/String;Z)V",false));
        code.add(new VarInsnNode(ASTORE,var));
        if(!isStatic(h))code.add(new VarInsnNode(ALOAD,0));
        Type[] expected=Type.getArgumentTypes(h.desc),args=Type.getArgumentTypes(m.desc);
        if(expected.length!=1){
            if(expected.length!=args.length+1)throw new IllegalStateException("callback parameter mismatch "+h.name);
            int slot=isStatic(m)?0:1;
            for(int i=0;i<args.length;i++){
                if(!args[i].equals(expected[i]))throw new IllegalStateException("callback ABI mismatch "+h.name);
                code.add(new VarInsnNode(args[i].getOpcode(ILOAD),slot));slot+=args[i].getSize();
            }
        }
        code.add(new VarInsnNode(ALOAD,var));code.add(call(target,h));
        if(cancellable){
            var cont=new LabelNode();code.add(new VarInsnNode(ALOAD,var));
            code.add(new MethodInsnNode(INVOKEVIRTUAL,CI,"isCancelled","()Z",false));code.add(new JumpInsnNode(IFEQ,cont));
            if(ret.equals(Type.VOID_TYPE))code.add(new InsnNode(RETURN));
            else{
                String suffix=ret.equals(Type.BOOLEAN_TYPE)?"Z":"I";
                code.add(new VarInsnNode(ALOAD,var));code.add(new MethodInsnNode(INVOKEVIRTUAL,CIR,"getReturnValue"+suffix,"()"+suffix,false));
                code.add(new InsnNode(IRETURN));
            }
            code.add(cont);
        }
        return code;
    }
    @SuppressWarnings("unchecked") static void weave(ClassNode target,ClassNode mixin){
        for(String i:mixin.interfaces)if(!target.interfaces.contains(i))target.interfaces.add(i);
        var originalInstructions=new IdentityHashMap<MethodNode,AbstractInsnNode[]>();
        for (var m:target.methods) originalInstructions.put(m,m.instructions.toArray());
        var added=new ArrayList<MethodNode>();
        for(MethodNode h:mixin.methods){
            if(h.name.equals("<init>"))continue;
            var access=annotation(h,"Lorg/spongepowered/asm/mixin/gen/Accessor;");
            if(access!=null){
                String field=(String)get(access,"value","");Type ret=Type.getReturnType(h.desc);
                var f=target.fields.stream().filter(x->x.name.equals(field)&&x.desc.equals(ret.getDescriptor())).findFirst().orElseThrow();
                h.access&=~ACC_ABSTRACT;h.instructions=new InsnList();h.instructions.add(new VarInsnNode(ALOAD,0));
                h.instructions.add(new FieldInsnNode(GETFIELD,target.name,f.name,f.desc));h.instructions.add(new InsnNode(ret.getOpcode(IRETURN)));
                h.maxLocals=1;h.maxStack=1;added.add(h);verified++;System.out.println("CONTRACT PASS accessor "+field);continue;
            }
            AnnotationNode a=annotation(h,INJ+"Inject;");
            if(a!=null){
                int count=0;
                List<AnnotationNode> ats=(List<AnnotationNode>)get(a,"at",List.of());
                for(MethodNode m:targets(target,a))for(var at:ats){
                    String where=(String)get(at,"value","");
                    if(where.equals("HEAD")){m.instructions.insert(callback(target,m,h,a));count++;}
                    else if(where.equals("TAIL")){
                        AbstractInsnNode last=null;for(var n:m.instructions.toArray())if(n.getOpcode()==RETURN)last=n;
                        if(last==null)throw new IllegalStateException("No void TAIL "+m.name);
                        m.instructions.insertBefore(last,callback(target,m,h,a));count++;
                    }else throw new IllegalStateException("unsupported at "+where);
                }
                check(a,count,h.name);added.add(h);continue;
            }
            a=annotation(h,INJ+"Redirect;");
            if(a!=null){
                var at=(AnnotationNode)get(a,"at",null);String wanted=(String)get(at,"target","");
                int semi=wanted.indexOf(';');String owner=wanted.substring(1,semi);String tail=wanted.substring(semi+1);
                int paren=tail.indexOf('(');String name=tail.substring(0,paren),desc=tail.substring(paren);
                int ordinal=(Integer)get(at,"ordinal",-1),count=0;
                for(MethodNode m:targets(target,a)){
                    int seen=0;for(var n:originalInstructions.get(m))if(n instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc)){
                        if(ordinal<0||seen==ordinal){m.instructions.set(n,call(target,h));count++;}seen++;
                    }
                }
                check(a,count,h.name);added.add(h);continue;
            }
            a=annotation(h,INJ+"ModifyConstant;");
            if(a!=null){
                List<AnnotationNode> constants=(List<AnnotationNode>)get(a,"constant",List.of());
                int val=(Integer)get(constants.get(0),"intValue",0),count=0;
                for(MethodNode m:targets(target,a))for(var n:m.instructions.toArray()){
                    boolean match=n instanceof IntInsnNode i&&i.operand==val || n instanceof LdcInsnNode l&&l.cst.equals(val);
                    if(match){if(!isStatic(h))m.instructions.insertBefore(n,new VarInsnNode(ALOAD,0));m.instructions.insert(n,call(target,h));count++;}
                }
                check(a,count,h.name);added.add(h);continue;
            }
            throw new IllegalStateException("unhandled mixin handler "+h.name);
        }
        // No handler may reference its pre-merge mixin class at runtime.
        for(var h:added)for(var n:h.instructions.toArray()){
            if(n instanceof MethodInsnNode call&&call.owner.equals(mixin.name))call.owner=target.name;
            if(n instanceof FieldInsnNode field&&field.owner.equals(mixin.name))field.owner=target.name;
        }
        target.methods.addAll(added);
    }
    static void scrubGui(ClassNode target){
        for(var m:target.methods)if(m.name.equals("createMenu")){
            m.instructions.clear();m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
            m.instructions.add(new InsnNode(ACONST_NULL));m.instructions.add(new InsnNode(ARETURN));
        }
    }
    static void write(Path dir,ClassNode node)throws Exception{
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS){
            @Override protected String getCommonSuperClass(String a,String b){
                if(a.equals(b))return a;
                try{return super.getCommonSuperClass(a,b);}catch(Throwable t){return "java/lang/Object";}
            }
        };
        node.accept(w);Path p=dir.resolve(node.name+".class");Files.createDirectories(p.getParent());Files.write(p,w.toByteArray());
    }
    public static void main(String[] args)throws Exception{
        Path original=Path.of(args[0]),classes=Path.of(args[1]),out=Path.of(args[2]);
        try(JarFile jar=new JarFile(original.toFile())){
            Map<String,String> types=Map.of("FluidTankBlockEntity","FluidTankMixin","PumpBlockEntity","PumpMixin","GrinderBlockEntity","GrinderMixin");
            for(var entry:types.entrySet()){
                String path="dev/wizardjw/slimetech/blockentity/"+entry.getKey()+".class";
                byte[] bytes=jar.getInputStream(jar.getJarEntry(path)).readAllBytes();
                var baseline=read(bytes);scrubGui(baseline);write(out.resolve("baseline"),baseline);
                var target=read(bytes);scrubGui(target);
                var mixin=read(Files.readAllBytes(classes.resolve("dev/mahcialet/slimetechcompat/mixin/"+entry.getValue()+".class")));
                weave(target,mixin);write(out.resolve("patched"),target);
            }
            for(var e:Collections.list(jar.entries()))if(e.getName().matches("dev/wizardjw/slimetech/blockentity/(PumpBlockEntity|FluidTankBlockEntity)\\$.*\\.class")){
                var node=read(jar.getInputStream(e).readAllBytes());write(out.resolve("baseline"),node);write(out.resolve("patched"),node);
            }
        }
        System.out.println("TOTAL_CONTRACT_MATCHES="+verified);
    }
}
