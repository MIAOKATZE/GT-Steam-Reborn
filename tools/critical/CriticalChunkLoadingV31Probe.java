package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.common.config.Configuration;

import com.google.common.collect.*;
import com.google.gson.*;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;


/** Real GTSR manager and Forge ticket operations; sparse loaded World, no server bootstrap or IO. */
public final class CriticalChunkLoadingV31Probe {
    static sun.misc.Unsafe unsafe;
    static final JsonArray checks = new JsonArray();

    static Field field(Class<?> type, String name) throws Exception {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static void check(String name, boolean pass) {
        JsonObject row=new JsonObject(); row.addProperty("name",name); row.addProperty("pass",pass); checks.add(row);
        if(!pass) throw new AssertionError(name);
    }
    @SuppressWarnings("unchecked") static <T> T read(Class<?> type,String name,Object host) throws Exception {
        return (T)field(type,name).get(host);
    }
    static SparseWorld world() throws Exception {
        SparseWorld w=(SparseWorld)unsafe.allocateInstance(SparseWorld.class);
        field(World.class,"provider").set(w,unsafe.allocateInstance(WorldProviderSurface.class));
        w.tiles=new HashMap<>(); w.loaded=new HashSet<>();
        w.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists") ? w.loaded.contains(new ChunkCoordIntPair((int)a[0],(int)a[1])) : null);
        Map<World,Multimap<String,Ticket>> tickets=read(ForgeChunkManager.class,"tickets",null);
        tickets.put(w,ArrayListMultimap.create());
        Map<World,ImmutableSetMultimap<ChunkCoordIntPair,Ticket>> forced=read(ForgeChunkManager.class,"forcedChunks",null);
        forced.put(w,ImmutableSetMultimap.of());
        return w;
    }
    static Ticket ticket(SparseWorld w,BaseMetaTileEntity host) throws Exception {
        Constructor<Ticket> c=Ticket.class.getDeclaredConstructor(String.class,Type.class,World.class);c.setAccessible(true);
        Ticket t=c.newInstance(host==null?"gregtech":"gtsr",Type.NORMAL,w);
        Map<World,Multimap<String,Ticket>> tickets=read(ForgeChunkManager.class,"tickets",null);
        tickets.get(w).put(t.getModId(),t);
        if(host!=null) {
            t.getModData().setInteger("OwnerX",host.xCoord);
            t.getModData().setInteger("OwnerY",host.yCoord);
            t.getModData().setInteger("OwnerZ",host.zCoord);
            Map<TileEntity,Ticket> registrations=read(CriticalChunkTicketManager.class,"REGISTERED",null);
            registrations.put(host,t);
            ForgeChunkManager.forceChunk(t,new ChunkCoordIntPair(host.xCoord>>4,host.zCoord>>4));
        }
        return t;
    }
    static BaseMetaTileEntity host(SparseWorld w) throws Exception {
        BaseMetaTileEntity h=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
        h.setWorldObj(w);h.xCoord=1;h.yCoord=64;h.zCoord=1;w.tiles.put("1,64,1",h);return h;
    }
    static void defaults(Path directory) throws Exception {
        field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,directory.toFile());
        Path configPath=directory.resolve("forgeChunkLoading-probe.cfg");Files.deleteIfExists(configPath);
        Configuration cfg=new Configuration(configPath.toFile(),true);
        field(ForgeChunkManager.class,"config").set(null,cfg);
        field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,25);
        field(ForgeChunkManager.class,"overridesEnabled").setBoolean(null,true);
        CriticalChunkTicketManager.configureDefaults(cfg);
        Method load=ForgeChunkManager.class.getDeclaredMethod("loadConfiguration");load.setAccessible(true);load.invoke(null);
        check("GTSR default64 uses actual Forge constraints without changing GT default25",
            ForgeChunkManager.getMaxChunkDepthFor("gtsr")==64&&ForgeChunkManager.getMaxChunkDepthFor("gregtech")==25);
        cfg.get("gtsr","maximumChunksPerTicket",64).set(17);
        CriticalChunkTicketManager.configureDefaults(cfg);load.invoke(null);
        check("existing lower administrator quota17 preserved",ForgeChunkManager.getMaxChunkDepthFor("gtsr")==17);
        cfg.get("gtsr","maximumChunksPerTicket",64).set(0);load.invoke(null);
        check("zero administrator quota cannot become unlimited",new CriticalChunkLoading().budget()==0);
    }
    static void run() throws Exception {
        GTValues.enableChunkloaders=true; GTValues.alwaysReloadChunkloaders=true;
        field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,128);
        field(ForgeChunkManager.class,"overridesEnabled").setBoolean(null,false);
        // Registration of production event subscribers requires an FML mod lifecycle. Exercise their
        // real methods directly in this isolated probe, while retaining real Forge events/force logic.
        field(CriticalChunkLoading.class,"registered").setBoolean(null,true);
        field(CriticalChunkTicketManager.class,"mod").set(null,new Object());
        SparseWorld w=world();BaseMetaTileEntity h=host(w);
        List<ChunkCoordIntPair> footprint=new ArrayList<>();
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)footprint.add(new ChunkCoordIntPair(x,z));
        w.loaded.addAll(footprint);
        Ticket nativeTicket=ticket(w,h);CriticalChunkLoading loader=new CriticalChunkLoading();
        check("full nonaligned112 footprint forces all64 despite every chunk player-loaded",
            loader.ensureAllStructureChunks(w,h,footprint)&&nativeTicket.getChunkList().size()==64);
        check("one GTSR owner registration",((Map<?,?>)read(CriticalChunkTicketManager.class,"REGISTERED",null)).size()==1);
        check("native restart adopts controller ticket",loader.getActiveChunk().equals(new ChunkCoordIntPair(0,0)));
        check("unchanged ensure retains native ticket identity",loader.ensure(w,h,footprint)
            &&read(CriticalChunkTicketManager.class,"REGISTERED",null) instanceof Map
            &&((Map<?,?>)read(CriticalChunkTicketManager.class,"REGISTERED",null)).get(h)==nativeTicket);
        Ticket foreign=ticket(w,null);ChunkCoordIntPair overlap=new ChunkCoordIntPair(7,7);
        ForgeChunkManager.forceChunk(foreign,overlap);
        check("external persistent force coverage removes duplicate own force",loader.ensure(w,h,footprint)
            &&!nativeTicket.getChunkList().contains(overlap)&&foreign.getChunkList().contains(overlap));
        ForgeChunkManager.releaseTicket(foreign);
        check("external release safely hands chunk back to same native GTSR owner",loader.ensure(w,h,footprint)
            &&nativeTicket.getChunkList().contains(overlap));
        w.loaded.remove(overlap);
        check("durable ticket without loaded chunk is not ready",!loader.ensure(w,h,footprint)
            &&loader.getStatus()==CriticalChunkLoading.Status.WAITING_FOR_CHUNKS);
        w.loaded.add(overlap);check("all loaded restores readiness",loader.ensure(w,h,footprint));
        nativeTicket.setChunkListDepth(1);
        check("native ticket quota insufficient is explicit and never ready",!loader.ensure(w,h,footprint)
            &&loader.getStatus()==CriticalChunkLoading.Status.BUDGET_EXCEEDED&&nativeTicket.getChunkList().isEmpty());
        loader.release();nativeTicket=ticket(w,h);
        check("freshly restored job can recover full preload",loader.ensure(w,h,footprint));
        GTValues.enableChunkloaders=false;
        check("global GT disable releases Forge ticket and blocks construction",!loader.ensure(w,h,footprint)
            &&loader.getStatus()==CriticalChunkLoading.Status.DISABLED&&nativeTicket.getChunkList().isEmpty());
        check("global GT disable removes own registration",
            !((Map<?,?>)read(CriticalChunkTicketManager.class,"REGISTERED",null)).containsKey(h));
        GTValues.enableChunkloaders=true;loader.release();nativeTicket=ticket(w,h);
        check("GT reenable restored ticket resumes",loader.ensure(w,h,footprint));
        loader.setEnabled(false);
        check("per loom chunkloader switch releases and blocks readiness",!loader.ready()
            &&nativeTicket.getChunkList().isEmpty()&&!loader.ensure(w,h,footprint));
        loader.setEnabled(true);nativeTicket=ticket(w,h);check("loom switch on can resume",loader.ensure(w,h,footprint));
        foreign=ticket(w,null);for(ChunkCoordIntPair chunk:footprint)ForgeChunkManager.forceChunk(foreign,chunk);
        check("all externally covered footprint retains only empty owner recovery ticket",loader.ensure(w,h,footprint)
            &&loader.currentSet().isEmpty()&&((Map<?,?>)read(CriticalChunkTicketManager.class,"REGISTERED",null)).containsKey(h));
        ForgeChunkManager.releaseTicket(foreign);
        check("whole footprint foreign release hands back to empty recovery ticket",loader.ensure(w,h,footprint)
            &&nativeTicket.getChunkList().size()==64);
        foreign=ticket(w,null);for(ChunkCoordIntPair chunk:footprint)ForgeChunkManager.forceChunk(foreign,chunk);
        loader.ensure(w,h,footprint);
        loader.release();check("release never removes foreign ticket",foreign.getChunkList().size()==64);
        ForgeChunkManager.releaseTicket(foreign);nativeTicket=ticket(w,h);loader.ensure(w,h,footprint);
        new CriticalChunkLoading.Supervisor().unload(new WorldEvent.Unload(w));
        check("dimension unload releases only this loom ticket",nativeTicket.getChunkList().isEmpty()&&!loader.ready());
    }
    public static void main(String[] args) throws Exception {
        JsonObject report=new JsonObject();
        try {
            unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
            defaults(Path.of(args[0]).getParent());run();report.addProperty("status","PASS");
        } catch(Throwable failure) {
            report.addProperty("status","FAIL");report.addProperty("failure",failure.toString());throw failure;
        } finally {
            report.addProperty("scope","Real GTSR provider/Forge force-unforce-release and quota; GT205 flags; pre-seeded native restart tickets; sparse World; no new-ticket issuance, async IO, actual server restart or mod subscriber registration claimed");
            report.add("checks",checks);Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }
    static final class SparseWorld extends World {
        Map<String,TileEntity> tiles;Set<ChunkCoordIntPair> loaded;IChunkProvider chunks;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){return tiles.get(x+","+y+","+z);}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
