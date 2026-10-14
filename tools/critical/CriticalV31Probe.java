package com.miaokatze.gtsr.client.critical;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.*;
import org.lwjgl.opengl.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.critical.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Production native lists/TESR plus sparse-world NBT. No game/FML startup. */
public final class CriticalV31Probe {
    static class RenderWorld extends World {
        long tick;Map<String,Block> blocks=new HashMap<>();
        RenderWorld(){super(null,"unused",(net.minecraft.world.WorldProvider)null,null,null);}
        @Override public long getTotalWorldTime(){return tick;}
        @Override public int getHeight(){return 256;}
        @Override public int getLightBrightnessForSkyBlocks(int x,int y,int z,int min){return 0x00b000b0;}
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider(){return (net.minecraft.world.chunk.IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{net.minecraft.world.chunk.IChunkProvider.class},(p,m,a)->m.getName().equals("chunkExists")?true:null);}
        @Override public boolean blockExists(int x,int y,int z){return true;}
        @Override public Block getBlock(int x,int y,int z){return blocks.get(x+","+y+","+z);}
        @Override public int getBlockMetadata(int x,int y,int z){return 0;}
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(int x,int y,int z){return null;}
        @Override public void markBlockForUpdate(int x,int y,int z){}
        @Override public void markBlockRangeForRenderUpdate(int a,int b,int c,int d,int e,int f){}
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider(){return null;}
        @Override protected int func_152379_p(){return 0;}
        @Override public net.minecraft.entity.Entity getEntityByID(int id){return null;}
    }
    static Path out; static int checks; static sun.misc.Unsafe unsafe; static int texture;
    static Class<?> helper; static final int SIZE=768;
    static Field field(Class<?> t,String n)throws Exception {while(t!=null){try{Field f=t.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){t=t.getSuperclass();}}throw new NoSuchFieldException(n);}
    static Object invoke(String n,Object...args)throws Exception {for(Method m:helper.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(null,args);}throw new NoSuchMethodException(n);}
    static void check(boolean pass,String n){checks++;if(!pass)throw new AssertionError(n);}
    static IIcon icon(String name,int panel){return (IIcon)Proxy.newProxyInstance(IIcon.class.getClassLoader(),new Class<?>[]{IIcon.class},(p,m,a)->switch(m.getName()){
        case "getIconWidth","getIconHeight"->32;
        case "getMinU"->(panel%128)/128f;case "getMaxU"->(panel%128+1)/128f;
        case "getMinV"->(panel/128)/128f;case "getMaxV"->(panel/128+1)/128f;
        case "getInterpolatedU"->(float)((panel%128+(Double)a[0]/16)/128);
        case "getInterpolatedV"->(float)((panel/128+(Double)a[0]/16)/128);
        case "getIconName"->name;default->throw new UnsupportedOperationException(m.getName());});}
    static void atlas()throws Exception {
        Map<String,IIcon> icons=new LinkedHashMap<>();
        for(var e:CriticalGeometry.readJson("materials.json").entrySet())CriticalMaterialTextures.register(e.getKey(),name->icons.computeIfAbsent(name,n->icon(n,icons.size())));
        check(icons.size()<16384,"atlas fits");ByteBuffer b=BufferUtils.createByteBuffer(4096*4096*4);
        int panel=0;for(String name:icons.keySet()){
            String path="/assets/"+name.replace(':','/')+".png";var stream=CriticalV31Probe.class.getResourceAsStream(path.replace("/gtsr/","/gtsr/textures/blocks/"));
            if(stream==null)throw new IllegalStateException("Missing PNG "+path);
            BufferedImage im=ImageIO.read(stream);stream.close();
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){int color=im.getRGB(x*im.getWidth()/32,y*Math.min(im.getWidth(),im.getHeight())/32);int at=(((panel/128)*32+y)*4096+(panel%128)*32+x)*4;b.put(at,(byte)(color>>>16));b.put(at+1,(byte)(color>>>8));b.put(at+2,(byte)color);b.put(at+3,(byte)(color>>>24));}panel++;
        }
        texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,4096,4096,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);
    }
    static void camera(){GL11.glViewport(0,0,SIZE,SIZE);GL11.glDepthMask(true);GL11.glClearColor(.02f,.03f,.04f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-85,85,-85,85,-600,600);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotated(18,1,0,0);GL11.glRotated(-28,0,1,0);GL11.glTranslated(-56,-65,-56);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glColor4f(1,1,1,1);}
    static byte[] pixels(String name)throws Exception {GL11.glFinish();ByteBuffer b=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);check(GL11.glGetError()==0,"GL no errors "+name);byte[] data=new byte[b.remaining()];b.get(data);if(name!=null){BufferedImage im=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int i=(y*SIZE+x)*4;im.setRGB(x,SIZE-y-1,0xff000000|(data[i]&255)<<16|(data[i+1]&255)<<8|data[i+2]&255);}ImageIO.write(im,"png",out.resolve(name+".png").toFile());}return data;}
    static NBTTagCompound descriptor(CriticalMachineKind kind,boolean valid){NBTTagCompound n=new NBTTagCompound();n.setInteger("geometryVersion",2);n.setInteger("v2LayoutRevision",31);n.setInteger("state",2);n.setBoolean("valid",valid);n.setBoolean("loomVisible",true);n.setBoolean("loomJournalOwner",true);n.setBoolean("loomController",true);n.setString("structureId","probe-"+kind);n.setTag("config",new CriticalConfiguration(CriticalTier.T3,kind,8,0,0).write());return n;}
    static CriticalMachineState setup(Object world,CriticalMachineKind kind)throws Exception {CriticalMachineState s=new CriticalMachineState();s.readVisualData(descriptor(kind,true));s.worldObj=(World)world;return s;}
    static JsonObject tesr(Minecraft mc)throws Exception {
        RenderWorld w=(RenderWorld)unsafe.allocateInstance(RenderWorld.class);w.blocks=new HashMap<>();field(World.class,"isRemote").setBoolean(w,true);
        NBTTagCompound n=descriptor(CriticalMachineKind.BATTERY,true);TileEntityCriticalRenderAnchor anchor=new TileEntityCriticalRenderAnchor();anchor.setWorldObj(w);field(anchor.getClass(),"descriptor").set(anchor,n);
        CriticalMachineState s=anchor.getVisualState();s.readVisualData(n);anchor.xCoord=CriticalRenderAnchors.getX(s);anchor.yCoord=CriticalRenderAnchors.getY(s);anchor.zCoord=CriticalRenderAnchors.getZ(s);
        CriticalGeometry.Voxel main=null,plugin=null;
        for(String slug:new String[]{CriticalGeometry.visualLoomSlug(s),CriticalGeometry.visualBaseSlug(s),CriticalGeometry.visualMachineSlug(s)})for(CriticalGeometry.Voxel v:CriticalGeometry.getModel(slug).voxels){w.blocks.put(s.blockWorldX(v)+","+s.blockWorldY(v.y)+","+s.blockWorldZ(v),CriticalLoomStructure.isLoomVoxel(v)?CriticalLoomStructure.shell(v):v.block);if(slug.equals(CriticalGeometry.visualMachineSlug(s))&&CriticalGeometry.isDynamic(slug,v.group))main=v;}
        for(CriticalGeometry.Voxel v:CriticalGeometry.physicalPlugins(s)){w.blocks.put(s.blockWorldX(v)+","+s.blockWorldY(v.y)+","+s.blockWorldZ(v),v.block);plugin=v;}
        CriticalLoomProxyVisibility.refresh(anchor);CriticalGroupVisibility vis=new CriticalGroupVisibility(s);for(int i=0;i<400;i++){w.tick++;vis.update(s);}
        CriticalControllerRenderer r=new CriticalControllerRenderer();net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher=(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher)unsafe.allocateInstance(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class);dispatcher.field_147553_e=mc.renderEngine;r.func_147497_a(dispatcher);
        ((Map<Object,Object>)field(r.getClass(),"visibility").get(r)).put(anchor,vis);
        CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);pixels("battery-actual-tesr");
        Map<?,?> motion=(Map<?,?>)field(r.getClass(),"motionClocks").get(r);CriticalContinuousMotion.Context context=(CriticalContinuousMotion.Context)motion.get(anchor);check(context!=null,"actual TESR continuous context");long frame=context.frames();double seconds=context.seconds();
        camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);check(context.frames()==frame&&context.seconds()==seconds,"same anchor repeated render does not advance motion");
        field(Minecraft.class,"isGamePaused").setBoolean(mc,true);CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);check(context.seconds()==seconds,"actual paused Minecraft singleton freezes installation clock");field(Minecraft.class,"isGamePaused").setBoolean(mc,false);
        TileEntityCriticalRenderAnchor other=new TileEntityCriticalRenderAnchor();other.setWorldObj(w);field(other.getClass(),"descriptor").set(other,n);other.xCoord=anchor.xCoord;other.yCoord=anchor.yCoord;other.zCoord=anchor.zCoord;long firstFrames=context.frames();camera();r.renderTileEntityAt(other,other.xCoord,other.yCoord,other.zCoord,0);CriticalContinuousMotion.Context otherContext=(CriticalContinuousMotion.Context)motion.get(other);check(otherContext!=context&&context.frames()==firstFrames,"two controller anchors own isolated contexts in same real renderer");
        check(main!=null&&CriticalLoomProxyVisibility.animated(w,s.blockWorldX(main),s.blockWorldY(main.y),s.blockWorldZ(main),main.block,main.metadata),"published main mesh ownership");
        if(plugin!=null)check(CriticalLoomProxyVisibility.animated(w,s.blockWorldX(plugin),s.blockWorldY(plugin.y),s.blockWorldZ(plugin),plugin.block,plugin.metadata),"published plugin mesh ownership");
        long particles=CriticalRenderMetrics.particlesDrawn;NBTTagCompound invalid=(NBTTagCompound)n.copy();invalid.setBoolean("valid",false);s.readVisualData(invalid);w.tick++;CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);pixels("battery-invalid-fallback");
        check(CriticalRenderMetrics.particlesDrawn==particles,"invalid geometry emits no particles");check(!CriticalLoomProxyVisibility.animated(w,s.blockWorldX(main),s.blockWorldY(main.y),s.blockWorldZ(main),main.block,main.metadata),"invalid entire main payload falls back to physical blocks");check(context.seconds()==seconds,"invalid main clock frozen");
        if(plugin!=null)check(!CriticalLoomProxyVisibility.animated(w,s.blockWorldX(plugin),s.blockWorldY(plugin.y),s.blockWorldZ(plugin),plugin.block,plugin.metadata),"invalid plugin physical fallback");
        s.readVisualData(n);w.tick++;CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);check(CriticalLoomProxyVisibility.animated(w,s.blockWorldX(main),s.blockWorldY(main.y),s.blockWorldZ(main),main.block,main.metadata),"main payload returns after valid repair snapshot");
        long beforeReload=context.frames();r.onResourceManagerReload(null);w.tick++;CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);check(context.frames()==beforeReload+1,"reload reuses installation context");check(CriticalAnimation.currentContext()==null,"render context cleared after draw");
        NBTTagCompound noPlugins=(NBTTagCompound)n.copy();noPlugins.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.BATTERY,0,0,0).write());s.readVisualData(noPlugins);CriticalLoomProxyVisibility.refresh(anchor);
        if(plugin!=null)check(!CriticalLoomProxyVisibility.animated(w,s.blockWorldX(plugin),s.blockWorldY(plugin.y),s.blockWorldZ(plugin),plugin.block,plugin.metadata),"plugin count change withdraws old slot ownership");
        s.readVisualData(n);CriticalLoomProxyVisibility.refresh(anchor);vis=new CriticalGroupVisibility(s);for(int i=0;i<400;i++){w.tick++;vis.update(s);}((Map<Object,Object>)field(r.getClass(),"visibility").get(r)).put(anchor,vis);CriticalRenderMetrics.frameStarts++;camera();r.renderTileEntityAt(anchor,anchor.xCoord,anchor.yCoord,anchor.zCoord,0);
        if(plugin!=null)check(CriticalLoomProxyVisibility.animated(w,s.blockWorldX(plugin),s.blockWorldY(plugin.y),s.blockWorldZ(plugin),plugin.block,plugin.metadata),"plugin count restoration republishes installed slot ownership");
        r.onResourceManagerReload(null);CriticalLoomProxyVisibility.remove(anchor);JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("actualTESRCalls",8);result.addProperty("invalidMainAndPluginPhysicalFallback",true);result.addProperty("sameFrameNoDoubleIntegration",true);result.addProperty("pauseAndControllerIsolation",true);result.addProperty("pluginCountChangeWithdrawRestore",true);return result;
    }
    static JsonArray nbt()throws Exception {
        JsonArray result=new JsonArray();
        for(int revision=25;revision<=30;revision++)for(int state:new int[]{1,2}){
            NBTTagCompound n=descriptor(CriticalMachineKind.SUN,true);n.setInteger("v2LayoutRevision",revision);n.setInteger("state",state);n.setString("jobId","persisted");n.setInteger("cursor",173);n.setLong("jobFingerprint",123456789L);n.setByteArray("placed",new byte[]{1,3,7});
            CriticalMachineState s=new CriticalMachineState();s.readFromNBT(n);NBTTagCompound saved=new NBTTagCompound();s.writeToNBT(saved);
            check(s.getV2LayoutRevision()==revision,"old revision preserved");check(saved.getInteger("cursor")==173,"cursor preserved");check(saved.getLong("jobFingerprint")==123456789L,"fingerprint preserved");check(Arrays.equals(saved.getByteArray("placed"),new byte[]{1,3,7}),"placed indices preserved");
            JsonObject row=new JsonObject();row.addProperty("revision",revision);row.addProperty("state",state);row.addProperty("machineSlug",CriticalGeometry.visualMachineSlug(s));row.addProperty("baseSlug",CriticalGeometry.visualBaseSlug(s));result.add(row);
        }return result;
    }
    public static void main(String[] args)throws Exception {
        out=Path.of(args[0]);unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);helper=Class.forName("com.miaokatze.gtsr.common.critical.CriticalIntegrityContract");field(helper,"unsafe").set(null,unsafe);invoke("materials");
        JsonObject report=new JsonObject();Pbuffer p=null;
        try {
            report.add("legacyNBT",nbt());p=new Pbuffer(SIZE,SIZE,new PixelFormat(8,24,0),null,null);p.makeCurrent();atlas();
            report.addProperty("driver",GL11.glGetString(GL11.GL_RENDERER));report.addProperty("GL",GL11.glGetString(GL11.GL_VERSION));
            Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);field(Minecraft.class,"theMinecraft").set(null,mc);mc.renderEngine=new TextureManager(null){@Override public void bindTexture(ResourceLocation r){GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);}};
            mc.gameSettings=(net.minecraft.client.settings.GameSettings)unsafe.allocateInstance(net.minecraft.client.settings.GameSettings.class);mc.thePlayer=(net.minecraft.client.entity.EntityClientPlayerMP)unsafe.allocateInstance(net.minecraft.client.entity.EntityClientPlayerMP.class);mc.thePlayer.posX=56;mc.thePlayer.posY=80;mc.thePlayer.posZ=56;
            JsonArray renders=new JsonArray();
            for(CriticalMachineKind kind:CriticalMachineKind.values()){
                RenderWorld w=(RenderWorld)unsafe.allocateInstance(RenderWorld.class);w.blocks=new HashMap<>();CriticalMachineState s=setup(w,kind);CriticalControllerRenderer r=new CriticalControllerRenderer();
                net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher=(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher)unsafe.allocateInstance(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class);dispatcher.field_147553_e=mc.renderEngine;r.func_147497_a(dispatcher);
                field(r.getClass(),"renderingController").set(r,s);field(r.getClass(),"currentLight").set(r,new CriticalLightCache());
                String slug=CriticalGeometry.visualMachineSlug(s);List<JsonObject> groups=new ArrayList<>();for(var e:CriticalGeometry.getModel(slug).animations.entrySet())groups.add(e.getValue().getAsJsonObject());
                CriticalContinuousMotion.Context context=new CriticalContinuousMotion.Context();context.beginFrame(0,groups,"idle");CriticalAnimation.beginFrame(context);
                Method render=r.getClass().getDeclaredMethod("renderModel",String.class,double.class,double.class,String.class);render.setAccessible(true);camera();long before=CriticalRenderMetrics.displayListDraws;render.invoke(r,slug,0D,0D,"idle");byte[] first=pixels(slug+"-idle");
                context.beginFrame(5,groups,"working");camera();render.invoke(r,slug,.5D,5D,"working");byte[] second=pixels(slug+"-working");check(CriticalRenderMetrics.displayListDraws>before,"actual native lists drawn "+slug);check(!Arrays.equals(first,second),"actual idle/work pixels differ "+slug);
                JsonObject row=new JsonObject();row.addProperty("slug",slug);row.addProperty("draws",CriticalRenderMetrics.displayListDraws-before);row.addProperty("pixelChange",true);renders.add(row);CriticalAnimation.endFrame();r.onResourceManagerReload(null);
            }
            report.add("nativeRenders",renders);report.add("actualTESR",tesr(mc));report.addProperty("status","PASS");
        } catch(Throwable error){report.addProperty("status","FAIL");report.addProperty("failure",error.toString());error.printStackTrace();}
        finally {if(p!=null)p.destroy();report.addProperty("checks",checks);report.addProperty("scope","Real production Geometry/State NBT, native mesh compilation, CTM/emission/transparent drawing and continuous snapshot; patched MC Tessellator; sparse World, preseed material identities, frame0 atlas, GL lightmap adapter. No FML, full client/server, native atlas sprite ticking or TESR dispatcher.");JsonObject sources=new JsonObject();for(Class<?> t:new Class<?>[]{CriticalGeometry.class,CriticalMachineState.class,CriticalControllerRenderer.class,CriticalMeshCache.class,CriticalAnimation.class})sources.addProperty(t.getSimpleName(),t.getProtectionDomain().getCodeSource().getLocation().toString());report.add("codeSources",sources);Files.writeString(out.resolve("runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    }
}
