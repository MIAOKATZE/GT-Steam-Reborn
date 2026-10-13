import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/** Micro launcher: applies real production mixins without game/FML startup. */
public final class CriticalAngelicaV29Probe {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Map<String,Object> pluginGate = pluginGate();
        ArrayList<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator))
            urls.add(new File(entry).toURI().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(URL[]::new));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.");
        Launch.classLoader.addClassLoaderExclusion("com.llamalad7.mixinextras.");
        Launch.classLoader.addClassLoaderExclusion("org.lwjgl.");
        Launch.classLoader.addClassLoaderExclusion("CriticalAngelicaV29Probe");
        Field platform = MixinBootstrap.class.getDeclaredField("platform");
        platform.setAccessible(true);
        platform.set(null, new org.spongepowered.asm.launch.platform.MixinPlatformManager() {
            @Override public void init() {} // Deliberately prevent automatic FML/coremod container discovery.
            @Override public Collection<String> getPhaseProviderClasses() { return Collections.emptyList(); }
        });
        MixinBootstrap.init();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("mixins.gtsr.probe.v29.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        String[] targets = {"com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext",
            "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask",
            "com.miaokatze.gtsr.common.critical.CriticalMaterials$MaterialBlock"};
        ArrayList<String> loaded = new ArrayList<>();
        for (String target : targets) {
            Class<?> type = Class.forName(target, false, Launch.classLoader);
            if (type.getClassLoader()!=Launch.classLoader) throw new AssertionError("Target escaped transforming loader: "+target);
            long merged = Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("gtsr$")).count();
            if (merged==0) throw new AssertionError("No merged production handler: "+target);
            loaded.add(target+" merged="+merged);
            System.out.println("TRANSFORMED "+target+" merged="+merged);
        }
        Class<?> runtime = Class.forName("CriticalAngelicaV29Runtime",true,Launch.classLoader);
        Method exercise = runtime.getDeclaredMethod("exercise");
        exercise.setAccessible(true);
        Object result = exercise.invoke(null);
        Files.writeString(out.resolve("runtime.json"), "{\"transformedTargets\": "+
            new com.google.gson.Gson().toJson(loaded)+",\"fullMinecraftStarted\":false,\"execution\":"+
            new com.google.gson.Gson().toJson(result)+",\"actualProductionPluginGate\":"+
            new com.google.gson.Gson().toJson(pluginGate)+"}");
    }

    private static final String CONTEXT_RESOURCE="com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext.class";
    private static final String TASK_RESOURCE="com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask.class";

    /** Define the actual repo plugin and its nested gate, hiding optional class definitions. */
    static final class GateLoader extends ClassLoader {
        final Map<String,byte[]> resources;
        final ArrayList<String> definitions=new ArrayList<>();
        final LinkedHashMap<String,Integer> resourceReads=new LinkedHashMap<>();
        int optionalDefinitionAttempts;
        GateLoader(Map<String,byte[]> resources){super(CriticalAngelicaV29Probe.class.getClassLoader());this.resources=resources;}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(name.startsWith("com.gtnewhorizons.angelica.")||name.startsWith("org.embeddedt.embeddium.")){
                optionalDefinitionAttempts++;
                throw new AssertionError("Plugin attempted optional target definition: "+name);
            }
            if(!name.startsWith("com.miaokatze.gtsr.mixin.GTSRMixinPlugin"))return super.loadClass(name,resolve);
            Class<?> type=findLoadedClass(name);
            if(type==null){
                try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    if(input==null)throw new ClassNotFoundException(name);
                    byte[] bytes=input.readAllBytes();type=defineClass(name,bytes,0,bytes.length);definitions.add(name);
                }catch(java.io.IOException error){throw new ClassNotFoundException(name,error);}
            }
            if(resolve)resolveClass(type);return type;
        }
        @Override public java.io.InputStream getResourceAsStream(String name){
            if(name.equals(CONTEXT_RESOURCE)||name.equals(TASK_RESOURCE)){
                resourceReads.merge(name,1,Integer::sum);
                byte[] bytes=resources.get(name);return bytes==null?null:new java.io.ByteArrayInputStream(bytes);
            }
            return super.getResourceAsStream(name);
        }
    }

    static byte[] changedCall(byte[] bytes,String enclosingMethod,String calledMethod)throws Exception{
        var node=new org.spongepowered.asm.lib.tree.ClassNode();
        new org.spongepowered.asm.lib.ClassReader(bytes).accept(node,0);
        int changes=0;
        for(var method:node.methods)if(method.name.equals(enclosingMethod)){
            for(var instruction:method.instructions.toArray())
                if(instruction instanceof org.spongepowered.asm.lib.tree.MethodInsnNode call&&call.name.equals(calledMethod)){
                    call.name="gtsr$unsupportedShape";changes++;
                }
        }
        if(changes!=1)throw new AssertionError("Shape mutation expected one invocation, got "+changes);
        var writer=new org.spongepowered.asm.lib.ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }

    static Map<String,Object> pluginGate()throws Exception{
        byte[] context=CriticalAngelicaV29Probe.class.getClassLoader().getResourceAsStream(CONTEXT_RESOURCE).readAllBytes();
        byte[] task=CriticalAngelicaV29Probe.class.getClassLoader().getResourceAsStream(TASK_RESOURCE).readAllBytes();
        var variants=new LinkedHashMap<String,Map<String,byte[]>>();
        variants.put("actualAngelica228",Map.of(CONTEXT_RESOURCE,context,TASK_RESOURCE,task));
        variants.put("missingAngelica",Map.of());
        variants.put("unknownVertexBufferCall",Map.of(CONTEXT_RESOURCE,changedCall(context,"copyRawBuffer","getVertexBuffer"),TASK_RESOURCE,task));
        variants.put("unknownRawCaptureCall",Map.of(CONTEXT_RESOURCE,context,TASK_RESOURCE,changedCall(task,"renderBlock","copyRawBuffer")));
        var results=new LinkedHashMap<String,Object>();
        for(var variant:variants.entrySet()){
            var loader=new GateLoader(variant.getValue());
            var pluginClass=Class.forName("com.miaokatze.gtsr.mixin.GTSRMixinPlugin",true,loader);
            Object plugin=pluginClass.getDeclaredConstructor().newInstance();
            Method selector=pluginClass.getMethod("shouldApplyMixin",String.class,String.class);
            var decisions=new LinkedHashMap<String,Boolean>();
            for(String mixin:new String[]{"CriticalMeshingTaskMixin","CriticalChunkBuildContextMixin","CriticalMaterialFaceMixin"}){
                boolean allowed=(Boolean)selector.invoke(plugin,"ignoredTargetForIndependentSelection",
                    "com.miaokatze.gtsr.mixin.angelica."+mixin);
                if(allowed!=variant.getKey().equals("actualAngelica228"))throw new AssertionError("Unexpected actual plugin decision: "+variant.getKey()+"/"+mixin+"="+allowed);
                decisions.put(mixin,allowed);
            }
            if(loader.optionalDefinitionAttempts!=0)throw new AssertionError("Optional class was defined in gate");
            results.put(variant.getKey(),Map.of("decisions",decisions,"definedClasses",loader.definitions,
                "optionalTargetDefinitionAttempts",loader.optionalDefinitionAttempts,"optionalResourceReads",loader.resourceReads));
        }
        System.out.println("ACTUAL_PRODUCTION_PLUGIN_GATE PASS: 12 decisions; no optional target definitions");
        return Map.of("status","PASS","assertedDecisions",12,"variants",results);
    }
}

class CriticalAngelicaV29Runtime {
    static sun.misc.Unsafe unsafe;
    static int assertions;
    static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    static void set(Object target,String name,Object value)throws Exception{
        Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static Object exercise()throws Exception{
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        org.lwjgl.opengl.Pbuffer surface=new org.lwjgl.opengl.Pbuffer(16,16,new org.lwjgl.opengl.PixelFormat(),null,null);
        surface.makeCurrent();
        var context=(com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext)unsafe.allocateInstance(
            com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext.class);
        var vertices=org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder.Vertex.uninitializedQuad();
        set(context,"vertices",vertices);
        set(context,"blockRenderContext",new com.gtnewhorizons.angelica.rendering.celeritas.iris.BlockRenderContext());
        set(context,"textureAtlas",(com.gtnewhorizons.angelica.rendering.celeritas.TextureMapExtension)(u,v)->null);
        var type=new org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex();
        var pass=org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass.builder().name("probe")
            .vertexType(type).primitiveType(org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType.TRIANGULATED).build();
        var material=new org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material(pass,
            org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter.ZERO,true);
        var config=new org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration<>(Map.of("probe",material),
            Map.of("probe",List.of(pass)),material,material,material);
        var buffers=new org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers(config);
        var data=new org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData<>();
        buffers.init(data,0);
        int[] raw=new int[32];
        float[][] points={{0,0,0},{0,1,0},{1,1,0},{1,0,0}};
        for(int i=0;i<4;i++){
            raw[i*8]=Float.floatToRawIntBits(points[i][0]);raw[i*8+1]=Float.floatToRawIntBits(points[i][1]);
            raw[i*8+2]=Float.floatToRawIntBits(points[i][2]);raw[i*8+3]=Float.floatToRawIntBits(i%2);
            raw[i*8+4]=Float.floatToRawIntBits(i/2);raw[i*8+5]=0xff123456;raw[i*8+6]=0x123456;raw[i*8+7]=0x00f000f0;
        }
        int[] ids={-1,-1,-1,-1};
        context.copyRawBuffer(raw,4,new int[4],ids,buffers,material,true,false);
        var facing=org.embeddedt.embeddium.impl.util.QuadUtil.findNormalFace(vertices[0].trueNormal);
        check(facing!=org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED,"ordinary normal direction");
        var builder=buffers.get(material);
        check(builder.getVertexBuffer(facing).count()==4,"ordinary bucket retains face");
        check(builder.getVertexBuffer(org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED).count()==0,"ordinary unassigned empty");
        java.nio.ByteBuffer original=builder.getVertexBuffer(facing).slice();byte[] originalBytes=new byte[original.remaining()];original.get(originalBytes);
        int normal=vertices[0].trueNormal;
        var critical=(net.minecraft.block.Block)unsafe.allocateInstance(com.miaokatze.gtsr.common.critical.CriticalMaterials.MaterialBlock.class);
        Boolean previous=com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.beginCapture(critical);
        context.copyRawBuffer(raw,4,new int[4],ids,buffers,material,true,false);
        com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.endCapture(previous);
        var unassigned=builder.getVertexBuffer(org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED);
        check(unassigned.count()==4,"critical transformed bucket UNASSIGNED");
        java.nio.ByteBuffer changed=unassigned.slice();byte[] changedBytes=new byte[changed.remaining()];changed.get(changedBytes);
        check(Arrays.equals(originalBytes,changedBytes),"native encoded vertex bytes unchanged");
        check(vertices[0].trueNormal==normal,"lighting trueNormal unchanged");
        check(!com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.isCapturing(),"scope cleared");
        var task=unsafe.allocateInstance(ProbeMeshingTask.class);
        Method wrap=Arrays.stream(task.getClass().getSuperclass().getDeclaredMethods()).filter(m->m.getName().contains("gtsr$captureCritical")&&!m.getName().contains("bridge")).findFirst().orElseThrow();
        wrap.setAccessible(true);
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> nativeCopy=a->{
            ((com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext)a[0]).copyRawBuffer(
                (int[])a[1],(Integer)a[2],(int[])a[3],(int[])a[4],
                (org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers)a[5],
                (org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material)a[6],(Boolean)a[7],(Boolean)a[8]);return null;
        };
        float[][][] faces={{{0,0,0},{0,1,0},{1,1,0},{1,0,0}},{{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
            {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},{{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
            {{0,0,1},{0,0,0},{1,0,0},{1,0,1}},{{0,1,0},{0,1,1},{1,1,1},{1,1,0}}};
        ArrayList<String> nativeFaces=new ArrayList<>();
        var ordinary=(net.minecraft.block.Block)unsafe.allocateInstance(net.minecraft.block.Block.class);
        for(float[][] face:faces){
            for(int i=0;i<4;i++)for(int axis=0;axis<3;axis++)raw[i*8+axis]=Float.floatToRawIntBits(face[i][axis]);
            buffers.init(data,0);
            wrap.invoke(task,context,raw,4,new int[4],ids,buffers,material,true,false,nativeCopy,ordinary);
            var direction=org.embeddedt.embeddium.impl.util.QuadUtil.findNormalFace(vertices[0].trueNormal);
            int trueNormal=vertices[0].trueNormal;
            check(direction!=org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED,"face direction assigned");
            nativeFaces.add(direction.name());
            var nativeBuffer=builder.getVertexBuffer(direction).slice();byte[] expected=new byte[nativeBuffer.remaining()];nativeBuffer.get(expected);
            check(builder.getVertexBuffer(direction).count()==4,"ordinary native face count");
            check(unassigned.count()==0,"ordinary unassigned empty");
            wrap.invoke(task,context,raw,4,new int[4],ids,buffers,material,true,false,nativeCopy,critical);
            check(unassigned.count()==4,"wrapped critical unassigned count");
            var criticalBuffer=unassigned.slice();byte[] actual=new byte[criticalBuffer.remaining()];criticalBuffer.get(actual);
            check(Arrays.equals(expected,actual),"six-face native encoded bytes identical");
            check(vertices[0].trueNormal==trueNormal,"six-face trueNormal retained");
            check(!com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.isCapturing(),"wrapped native call cleaned scope");
        }
        check(new HashSet<>(nativeFaces).size()==6,"all six cardinal faces exercised");
        IllegalStateException sentinel=new IllegalStateException("intentional conversion failure");
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> failure=a->{throw sentinel;};
        try{wrap.invoke(task,context,raw,4,new int[4],ids,buffers,material,true,false,failure,critical);throw new AssertionError("exception expected");}
        catch(InvocationTargetException expected){check(expected.getCause()==sentinel,"original conversion exception retained");}
        check(!com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.isCapturing(),"exception clears capture");
        previous=com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.beginCapture(critical);
        wrap.invoke(task,context,raw,4,new int[4],ids,buffers,material,true,false,nativeCopy,ordinary);
        check(com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.isCapturing(),"nested ordinary scope restores previous critical capture");
        com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.endCapture(previous);
        Thread other=new Thread(()->check(!com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.isCapturing(),"threadlocal scope isolation"));
        previous=com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.beginCapture(critical);other.start();other.join();
        com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.endCapture(previous);
        com.miaokatze.gtsr.client.critical.CriticalAngelicaCompat.register();
        var anchor=(net.minecraft.tileentity.TileEntity)unsafe.allocateInstance(com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor.class);
        check(com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry.classify(anchor)==
            com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry.DYNAMIC,"official registry classifies actual anchor DYNAMIC");
        var globalData=new org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData<Object,Object>();
        globalData.globalBlockEntities.add(anchor);
        var section=(org.embeddedt.embeddium.impl.render.chunk.RenderSection)unsafe.allocateInstance(org.embeddedt.embeddium.impl.render.chunk.RenderSection.class);
        set(section,"contextData",globalData);
        var iterator=org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData.generateBlockEntityIterator(
            org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists.empty(),List.of(section));
        check(iterator.hasNext()&&iterator.next()==anchor&&!iterator.hasNext(),"global route enumerates anchor with empty visible render lists");
        int maskSamples=0;
        int unassignedMask=1<<org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing.UNASSIGNED.ordinal();
        for(int x:new int[]{-100,-4,0,8,20,100})for(int y:new int[]{-100,0,8,100})for(int z:new int[]{-100,0,8,100}){
            int mask=org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler.getVisibleFaces(x,y,z,0,0,0);
            check((mask&unassignedMask)!=0,"real CPU face mask retains UNASSIGNED");maskSamples++;
        }
        int gpuSamples=gpuMasks(unassignedMask);
        Constructor<com.miaokatze.gtsr.common.critical.CriticalMaterials.MaterialBlock> materialConstructor=
            com.miaokatze.gtsr.common.critical.CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(
                String.class,boolean.class,boolean.class,boolean.class,boolean.class);
        materialConstructor.setAccessible(true);
        var gravity=materialConstructor.newInstance("v2_gravity_modulator",false,false,true,true);
        ArrayList<String> registeredIcons=new ArrayList<>();
        gravity.registerBlockIcons(name->{registeredIcons.add(name);return (net.minecraft.util.IIcon)Proxy.newProxyInstance(
            net.minecraft.util.IIcon.class.getClassLoader(),new Class[]{net.minecraft.util.IIcon.class},
            (proxy,m,a)->m.getName().equals("getIconName")?name:m.getReturnType()==int.class?32:m.getReturnType()==float.class?0F:null);});
        String bottom=gravity.getIcon(0,0).getIconName(),top=gravity.getIcon(1,0).getIconName(),side=gravity.getIcon(2,0).getIconName();
        check(!bottom.equals(top)&&!bottom.equals(side)&&!top.equals(side),"real getIcon bottom/top/side distinct");
        for(int faceIndex=2;faceIndex<6;faceIndex++)check(gravity.getIcon(faceIndex,0).getIconName().equals(side),"lateral faces retain side sprite");
        for(int faceIndex=0;faceIndex<6;faceIndex++){
            var live=com.miaokatze.gtsr.client.critical.CriticalMaterialTextures.icon("v2_gravity_modulator",faceIndex,0,true,false);
            var frozen=com.miaokatze.gtsr.client.critical.CriticalMaterialTextures.icon("v2_gravity_modulator",faceIndex,0,true,true);
            check(live!=null&&frozen!=null&&!live.getIconName().equals(frozen.getIconName()),"actual texture table live/frozen emission distinct");
        }
        com.miaokatze.gtsr.common.critical.CriticalMaterials.renderId=4321;
        check(gravity.getRenderType()==4321,"ordinary inventory custom render id");
        check(materialConstructor.newInstance("v2_gravity_modulator",true,false,true,true).getRenderType()==4321,"proxy inventory custom render id");
        check(new com.miaokatze.gtsr.client.critical.CriticalMaterialRenderer().shouldRender3DInInventory(4321),"inventory handler declares 3D");
        var transparent=materialConstructor.newInstance("v2_gravity_modulator",false,true,true,true);
        net.minecraft.block.Block[] neighbor={transparent};
        net.minecraft.world.IBlockAccess access=(net.minecraft.world.IBlockAccess)Proxy.newProxyInstance(
            net.minecraft.world.IBlockAccess.class.getClassLoader(),new Class[]{net.minecraft.world.IBlockAccess.class},
            (proxy,m,a)->{if(m.getName().equals("getBlock"))return neighbor[0];throw new UnsupportedOperationException(m.getName());});
        check(!transparent.shouldSideBeRendered(access,0,0,0,2),"transformed material keeps identical glass-neighbor guard");
        neighbor[0]=gravity;
        check(!transparent.shouldSideBeRendered(access,0,0,0,2),"transformed material keeps opaque-neighbor culling");
        Constructor<?> airConstructor=Class.forName("net.minecraft.block.BlockAir").getDeclaredConstructor();airConstructor.setAccessible(true);
        neighbor[0]=(net.minecraft.block.Block)airConstructor.newInstance();
        check(transparent.shouldSideBeRendered(access,0,0,0,2),"transformed material vanilla open face");
        buffers.destroy();
        String renderer=org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER);
        surface.destroy();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("assertions",assertions);result.put("nativeVertexByteCountPerFace",originalBytes.length);result.put("ordinaryFaces",nativeFaces);
        result.put("criticalFacing","UNASSIGNED");result.put("encodedVerticesIdentical",true);result.put("renderer",renderer);
        result.put("scopeExceptionCleanup",true);result.put("officialDynamicRegistry",true);result.put("globalIteratorWithNoVisibleSection",true);
        result.put("cpuFaceMaskSamples",maskSamples);result.put("nativeActualGpuShaderSamples",gpuSamples);
        result.put("gravityGetIcon",Map.of("bottom",bottom,"top",top,"side",side,"registeredSpriteCount",registeredIcons.size(),"liveAndFrozenEmission",true));
        result.put("inventoryCustomRenderIdAnd3D",true);result.put("materialNeighborCulling",true);
        return result;
    }

    static int gpuMasks(int unassignedMask)throws Exception{
        String source=new String(CriticalAngelicaV29Runtime.class.getResourceAsStream("/assets/angelica/shaders/culling/chunk_cull.csh").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        int shader=org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL43.GL_COMPUTE_SHADER);
        org.lwjgl.opengl.GL20.glShaderSource(shader,source);org.lwjgl.opengl.GL20.glCompileShader(shader);
        check(org.lwjgl.opengl.GL20.glGetShaderi(shader,org.lwjgl.opengl.GL20.GL_COMPILE_STATUS)!=0,"real jar GPU shader compile: "+org.lwjgl.opengl.GL20.glGetShaderInfoLog(shader,8192));
        int program=org.lwjgl.opengl.GL20.glCreateProgram();org.lwjgl.opengl.GL20.glAttachShader(program,shader);org.lwjgl.opengl.GL20.glLinkProgram(program);
        check(org.lwjgl.opengl.GL20.glGetProgrami(program,org.lwjgl.opengl.GL20.GL_LINK_STATUS)!=0,"real GPU shader link");
        int[] objects=new int[4];for(int i=0;i<4;i++)objects[i]=org.lwjgl.opengl.GL15.glGenBuffers();
        java.nio.ByteBuffer visible=org.lwjgl.BufferUtils.createByteBuffer(96*8);
        int samples=0;
        for(int x:new int[]{-100,-4,0,8,20,100})for(int y:new int[]{-100,0,8,100})for(int z:new int[]{-100,0,8,100}){
            int mask=org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler.getVisibleFaces(x,y,z,0,0,0);
            visible.putInt(0).putInt(((samples*7)<<8)|mask);samples++;
        }
        visible.flip();
        java.nio.ByteBuffer meta=org.lwjgl.BufferUtils.createByteBuffer(64);
        meta.putInt(0).putInt(0).putInt(0).putInt(127);
        int ordinal=Integer.numberOfTrailingZeros(unassignedMask);
        for(int i=0;i<8;i++)meta.putInt(i>ordinal?4:0);
        meta.putInt(0).putInt(0).putInt(0).putInt(0).flip();
        java.nio.ByteBuffer uniform=org.lwjgl.BufferUtils.createByteBuffer(160);
        for(int i=0;i<6;i++)uniform.putFloat(0).putFloat(0).putFloat(0).putFloat(1);
        uniform.putInt(samples).putInt(-1).putInt(0).putInt(0);
        uniform.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        uniform.putInt(0).putInt(0).putInt(0).putInt(0);
        uniform.putFloat(0).putFloat(0).putFloat(4).putFloat(6).flip();
        java.nio.ByteBuffer output=org.lwjgl.BufferUtils.createByteBuffer(samples*7*20);
        java.nio.ByteBuffer[] payloads={visible,meta,output,uniform};
        for(int i=0;i<4;i++){
            int target=i==3?org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER:org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
            org.lwjgl.opengl.GL15.glBindBuffer(target,objects[i]);org.lwjgl.opengl.GL15.glBufferData(target,payloads[i],org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW);
            org.lwjgl.opengl.GL30.glBindBufferBase(target,i==3?1:i,objects[i]);
        }
        org.lwjgl.opengl.GL20.glUseProgram(program);org.lwjgl.opengl.GL43.glDispatchCompute(2,1,1);
        org.lwjgl.opengl.GL42.glMemoryBarrier(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT|org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER,objects[2]);
        org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER,0,output);
        for(int sample=0;sample<samples;sample++){
            int count=0;for(int command=0;command<7;command++)count+=output.getInt((sample*7+command)*20);
            check(count==6,"actual GPU shader keeps UNASSIGNED six indices for camera mask "+sample);
        }
        uniform.putFloat(12,-1); // A rejecting live frustum still suppresses the draw.
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER,objects[3]);
        org.lwjgl.opengl.GL15.glBufferSubData(org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER,0,uniform);
        org.lwjgl.opengl.GL43.glDispatchCompute(2,1,1);org.lwjgl.opengl.GL42.glMemoryBarrier(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER,objects[2]);
        org.lwjgl.opengl.GL15.glGetBufferSubData(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER,0,output);
        for(int sample=0;sample<samples;sample++){
            int count=0;for(int command=0;command<7;command++)count+=output.getInt((sample*7+command)*20);
            check(count==0,"actual GPU shader retains frustum rejection");
        }
        org.lwjgl.opengl.GL20.glUseProgram(0);for(int object:objects)org.lwjgl.opengl.GL15.glDeleteBuffers(object);
        org.lwjgl.opengl.GL20.glDeleteProgram(program);org.lwjgl.opengl.GL20.glDeleteShader(shader);
        check(org.lwjgl.opengl.GL11.glGetError()==0,"GPU mask pipeline no GL error");
        return samples;
    }
}

class ProbeMeshingTask extends com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuilderMeshingTask {
    ProbeMeshingTask(){super(null,0,null);}
    protected net.minecraft.client.renderer.Tessellator getTessellator(){return null;}
    protected void setRenderPass(int pass){}
    protected net.minecraft.world.IBlockAccess getBlockAccess(){return null;}
    protected com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache getBiomeColorCache(){return null;}
}
