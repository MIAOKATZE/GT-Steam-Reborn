package com.miaokatze.gtsr.client.critical;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.critical.*;

/** Complete production renderNativeMeshes, real material/mesh/pose/light-cache paths, sparse World and real GPU. */
public final class Renderer41Probe {
    static final int SIZE=512, ATLAS=2048, CELL=32;
    static sun.misc.Unsafe unsafe;
    static Path out;
    static int atlas, lightmap;
    static final Map<String,Integer> textures=new LinkedHashMap<>();
    static final Method RENDER, COMPILE;
    static {try{
        RENDER=CriticalControllerRenderer.class.getDeclaredMethod("renderNativeMeshes",List.class,double.class,double.class,String.class);RENDER.setAccessible(true);
        COMPILE=CriticalMeshCache.class.getDeclaredMethod("compileList",List.class,boolean.class);COMPILE.setAccessible(true);
    }catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static void set(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static IIcon register(String name){int index=textures.computeIfAbsent(name,ignored->textures.size());int x=(index%(ATLAS/CELL))*CELL,y=(index/(ATLAS/CELL))*CELL;
        return (IIcon)Proxy.newProxyInstance(IIcon.class.getClassLoader(),new Class<?>[]{IIcon.class},(p,m,a)->switch(m.getName()){
            case "getInterpolatedU" -> (float)((x+(Double)a[0]*2)/ATLAS);
            case "getInterpolatedV" -> (float)((y+(Double)a[0]*2)/ATLAS);
            case "getMinU" -> (float)x/ATLAS; case "getMaxU" -> (float)(x+CELL)/ATLAS;
            case "getMinV" -> (float)y/ATLAS; case "getMaxV" -> (float)(y+CELL)/ATLAS;
            case "getIconWidth","getIconHeight" -> CELL; case "getIconName" -> name;
            default -> throw new UnsupportedOperationException(m.getName());});}
    static int upload(BufferedImage image){ByteBuffer b=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}b.flip();
        int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_CLAMP);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return id;}
    @SuppressWarnings("unchecked") static void materials()throws Exception{
        Constructor<?> ctor=CriticalMaterials.MaterialBlock.class.getDeclaredConstructor(CriticalMaterialDefinitions.Definition.class);ctor.setAccessible(true);
        Field f=CriticalMaterials.class.getDeclaredField("MATERIALS");f.setAccessible(true);Map<String,Object> blocks=(Map<String,Object>)f.get(null);
        for(var d:CriticalMaterialDefinitions.catalog().definitions().values()){blocks.put(d.id,ctor.newInstance(d));CriticalMaterialTextures.register(d.id,Renderer41Probe::register);}
        Method resolve=CriticalMaterialTextures.class.getDeclaredMethod("resolve",CriticalMaterialDefinitions.Definition.class);resolve.setAccessible(true);
        Map<String,Object> tables=new HashMap<>();for(var d:CriticalMaterialDefinitions.catalog().definitions().values())tables.put(d.id,resolve.invoke(null,d));
        for(var entry:CriticalMaterialDefinitions.catalog().aliases().entrySet())tables.put(entry.getKey(),tables.get(entry.getValue()));
        f=CriticalMaterialTextures.class.getDeclaredField("tables");f.setAccessible(true);f.set(null,tables);
        BufferedImage image=new BufferedImage(ATLAS,ATLAS,BufferedImage.TYPE_INT_ARGB);
        for(var entry:textures.entrySet()){String name="/assets/gtsr/textures/blocks/"+entry.getKey().split(":",2)[1]+".png";BufferedImage tile=ImageIO.read(Renderer41Probe.class.getResourceAsStream(name));int x=entry.getValue()%(ATLAS/CELL)*CELL,y=entry.getValue()/(ATLAS/CELL)*CELL;
            for(int py=0;py<CELL;py++)for(int px=0;px<CELL;px++)image.setRGB(x+px,y+py,tile.getRGB(px*tile.getWidth()/CELL,py*tile.getWidth()/CELL));}
        atlas=upload(image);
        // Native fixed-function lightmap uses packed 0..240 coordinates and (coord+8)/256 texel centers.
        BufferedImage lm=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int sky=0;sky<16;sky++)for(int block=0;block<16;block++){int v=(int)Math.round(255*Math.max(sky,block)/15D);lm.setRGB(block,sky,0xff000000|v<<16|v<<8|v);} 
        GL13.glActiveTexture(GL13.GL_TEXTURE1);lightmap=upload(lm);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glLoadIdentity();GL11.glScaled(1/256D,1/256D,1/256D);GL11.glTranslated(8,8,8);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glBindTexture(GL11.GL_TEXTURE_2D,atlas);
    }
    static CriticalControllerRenderer renderer(SampleWorld world)throws Exception{
        CriticalMachineState state=new CriticalMachineState();state.worldObj=world;state.xCoord=56;state.yCoord=4;state.zCoord=35;
        CriticalControllerRenderer renderer=new CriticalControllerRenderer();set(renderer,"renderingController",state);set(renderer,"currentLight",new CriticalLightCache());set(renderer,"anchorLight",0);
        return renderer;
    }
    static void camera(double radius,double cx,double cy,double cz){GL11.glViewport(0,0,SIZE,SIZE);GL11.glDepthMask(true);GL11.glClearColor(.02F,.03F,.04F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-radius,radius,-radius,radius,-700,700);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotated(18,1,0,0);GL11.glRotated(-28,0,1,0);GL11.glTranslated(-cx,-cy,-cz);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.01F);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_CULL_FACE);}
    static JsonObject capture(String name)throws Exception{GL11.glFinish();ByteBuffer p=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage im=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);int visible=0,white=0;long sum=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int i=(y*SIZE+x)*4,r=p.get(i)&255,g=p.get(i+1)&255,b=p.get(i+2)&255;im.setRGB(x,SIZE-y-1,0xff000000|r<<16|g<<8|b);if(r!=5||g!=8||b!=10){visible++;sum+=r+g+b;if(Math.min(r,Math.min(g,b))>=240)white++;}}ImageIO.write(im,"png",out.resolve(name+".png").toFile());JsonObject j=new JsonObject();j.addProperty("name",name);j.addProperty("visiblePixels",visible);j.addProperty("nearWhitePixels",white);j.addProperty("foregroundMean",visible==0?0:sum/(visible*3D));j.addProperty("glError",GL11.glGetError());return j;}
    static JsonObject model(String slug,int level)throws Exception{SampleWorld world=(SampleWorld)unsafe.allocateInstance(SampleWorld.class);world.level=level;CriticalControllerRenderer renderer=renderer(world);List<CriticalMeshCache.Mesh> meshes=new CriticalMeshCache().get(slug);int maximumQueries=0;for(int n=0;n<30;n++){world.tick++;int before=world.queries;camera(slug.equals("v2_loom")?52:110,56,slug.equals("v2_loom")?24:110,56);RENDER.invoke(renderer,meshes,.5D,45D,"working");maximumQueries=Math.max(maximumQueries,world.queries-before);}JsonObject j=capture(slug+"-light"+level);j.addProperty("lightQueries",world.queries);j.addProperty("maxQueriesPerTick",maximumQueries);j.addProperty("meshes",meshes.size());return j;}
    static JsonObject patch(int level,int compileBrightness,boolean moved)throws Exception{
        Constructor<?> voxelCtor=Arrays.stream(CriticalGeometry.Voxel.class.getDeclaredConstructors()).filter(c->c.getParameterCount()==8).findFirst().orElseThrow();voxelCtor.setAccessible(true);Object voxel=voxelCtor.newInstance(0,0,0,null,0,"fixture","fixture","fixture");
        Constructor<?> faceCtor=Arrays.stream(Class.forName(CriticalMeshCache.class.getName()+"$Face").getDeclaredConstructors()).filter(c->c.getParameterCount()==5).findFirst().orElseThrow();faceCtor.setAccessible(true);
        IIcon icon=CriticalMaterialTextures.icon("v2_ceramic_armor",1,0,false,false);Object face=faceCtor.newInstance(voxel,1,icon,null,0F);
        Tessellator.instance.setBrightness(compileBrightness);int albedo=(int)COMPILE.invoke(null,List.of(face),false),emission=(int)COMPILE.invoke(null,List.of(face),true);
        JsonObject animation=new JsonObject();if(moved){JsonObject motion=new JsonObject();motion.add("offset",new JsonParser().parse("[10,0,0]"));motion.addProperty("periodSeconds",4);motion.addProperty("phase",Math.PI/2);animation.add("idleMotion",motion);}
        var mesh=new CriticalMeshCache.Mesh(albedo,emission,animation,0,"fixture",new double[]{.5,1,.5},new double[][]{{.5,2,.5}});
        SampleWorld world=(SampleWorld)unsafe.allocateInstance(SampleWorld.class);world.level=level;world.regional=moved;CriticalControllerRenderer renderer=renderer(world);world.tick=1;
        camera(1,moved?10.5:.5,1,.5);RENDER.invoke(renderer,List.of(mesh),0D,0D,"idle");JsonObject j=capture("patch-light"+level+"-compile"+compileBrightness+"-moved"+moved);j.addProperty("lastSampleX",world.lastX);j.addProperty("lastLightX",OpenGlHelper.lastBrightnessX);j.addProperty("lastLightY",OpenGlHelper.lastBrightnessY);
        if(!moved){world.topOnly=true;camera(1,.5,1,.5);Tessellator.instance.startDrawingQuads();Method staticRenderer=CriticalMaterialRenderer.class.getDeclaredMethod("renderV2",IBlockAccess.class,int.class,int.class,int.class,CriticalMaterials.MaterialBlock.class);staticRenderer.setAccessible(true);staticRenderer.invoke(null,world,0,0,0,CriticalMaterials.block("v2_ceramic_armor",false));Tessellator.instance.draw();capture("static-light"+level);}
        return j;
    }
    public static void main(String[] args)throws Exception{out=Paths.get(args[0]);Files.createDirectories(out);Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);Pbuffer surface=new Pbuffer(SIZE,SIZE,new PixelFormat(8,24,0),null,null);surface.makeCurrent();try{OpenGlHelper.defaultTexUnit=GL13.GL_TEXTURE0;OpenGlHelper.lightmapTexUnit=GL13.GL_TEXTURE1;materials();JsonArray cases=new JsonArray();for(int level:new int[]{0,8,15}){cases.add(model("v2_loom",level));cases.add(model("v2_solar",level));cases.add(patch(level,0,false));cases.add(patch(level,0x00f000f0,false));}cases.add(patch(15,0,true));JsonObject report=new JsonObject();report.addProperty("scope","Actual renderNativeMeshes + actual CriticalLightCache/MechanicalPose/MeshCache/PNG/Tessellator/OpenGlHelper and static renderV2 comparator. Sparse coordinate-aware World; 16x16 analytic lightmap with native packed-coordinate mapping. Only TESR texture binding is fixture plumbing. No FML/client or FX; full dynamic loom/Solar meshes are drawn.");report.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));report.addProperty("controllerCodeSource",CriticalControllerRenderer.class.getProtectionDomain().getCodeSource().getLocation().toString());report.addProperty("meshCodeSource",CriticalMeshCache.class.getProtectionDomain().getCodeSource().getLocation().toString());report.addProperty("lightCacheCodeSource",CriticalLightCache.class.getProtectionDomain().getCodeSource().getLocation().toString());report.add("cases",cases);Files.writeString(out.resolve("runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println(report);}finally{surface.destroy();}}
    static final Block AIR=new Block(net.minecraft.block.material.Material.air){@Override public boolean isOpaqueCube(){return false;}};
    static final Block SOLID=new Block(net.minecraft.block.material.Material.rock){};
    static final class SampleWorld extends World{int level,lastX,queries;long tick;boolean regional,topOnly;private SampleWorld(){super(null,"fixture",(WorldProvider)null,null,null);}@Override public long getTotalWorldTime(){return tick;}@Override public int getHeight(){return 256;}@Override public boolean blockExists(int x,int y,int z){return true;}@Override public Block getBlock(int x,int y,int z){return x==0&&y==0&&z==0?CriticalMaterials.block("v2_ceramic_armor",false):y>=1?AIR:SOLID;}@Override public int getBlockMetadata(int x,int y,int z){return 0;}@Override public int getLightBrightnessForSkyBlocks(int x,int y,int z,int minimum){queries++;lastX=x;int value=regional?(x<5?15:0):level;return value*16;}@Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}@Override protected int func_152379_p(){return 0;}@Override public Entity getEntityByID(int id){return null;}}
}
