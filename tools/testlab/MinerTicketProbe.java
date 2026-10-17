package audit;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.google.common.collect.*;
import com.google.gson.*;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeChunkManager;
import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.objects.GTChunkManager;
import com.miaokatze.gtsr.common.machine.MTESingularityMinerNode;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

/** Isolated real node maintenance -> GT205 -> native Forge quota probe. No server lifecycle claim. */
public final class MinerTicketProbe {
    static Field field(Class<?> c,String n)throws Exception { Field f=c.getDeclaredField(n);f.setAccessible(true);return f; }
    static final JsonArray checks=new JsonArray();
    static void check(String name,boolean pass){JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);checks.add(row);}
    @SuppressWarnings("unchecked") static ForgeChunkManager.Ticket legacy(SparseWorld w,BaseMetaTileEntity host)throws Exception {
        Constructor<ForgeChunkManager.Ticket> ctor=ForgeChunkManager.Ticket.class.getDeclaredConstructor(String.class,ForgeChunkManager.Type.class,World.class);ctor.setAccessible(true);
        ForgeChunkManager.Ticket ticket=ctor.newInstance("gregtech",ForgeChunkManager.Type.NORMAL,w);
        ((Multimap)((Map)field(ForgeChunkManager.class,"tickets").get(null)).get(w)).put("gregtech",ticket);
        ((Map)field(GTChunkManager.class,"registeredTickets").get(GTChunkManager.instance)).put(host,ticket);
        ForgeChunkManager.forceChunk(ticket,new ChunkCoordIntPair(host.xCoord>>4,host.zCoord>>4));return ticket;
    }
    static final class SparseWorld extends World {
        Map<String,TileEntity> tiles;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){return tiles.get(x+","+y+","+z);}
        @Override public long getTotalWorldTime(){return 0;}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
    static final class ColdHubWorld extends WorldServer {
        BaseMetaTileEntity hub;IChunkProvider chunks;boolean loaded;
        ColdHubWorld(){super(null,null,"unused",0,null,null);}
        @Override public boolean blockExists(int x,int y,int z){return loaded;}
        @Override public TileEntity getTileEntity(int x,int y,int z){return loaded?hub:null;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
    }
    static final class FixtureHub extends com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub {
        FixtureHub(){super("unused");}
        @Override public List<ChunkCoordIntPair> getChunkLoadingFootprint(){return Arrays.asList(new ChunkCoordIntPair(0,0),new ChunkCoordIntPair(0,1),new ChunkCoordIntPair(1,0),new ChunkCoordIntPair(1,1));}
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        Thread.currentThread().setName("Server thread");
        sun.misc.Unsafe u=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        field(cpw.mods.fml.relauncher.FMLRelaunchLog.class,"side").set(null,cpw.mods.fml.relauncher.Side.SERVER);
        field(cpw.mods.fml.relauncher.FMLRelaunchLog.class,"minecraftHome").set(null,Paths.get(args[0]).toAbsolutePath().getParent().toFile());
        GTValues.enableChunkloaders=true;GTValues.alwaysReloadChunkloaders=false;
        field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,25);
        field(ForgeChunkManager.class,"overridesEnabled").setBoolean(null,false);
        SparseWorld w=(SparseWorld)u.allocateInstance(SparseWorld.class);
        w.tiles=new HashMap<>();
        field(World.class,"provider").set(w,u.allocateInstance(WorldProviderSurface.class));
        ((Map)field(ForgeChunkManager.class,"tickets").get(null)).put(w,ArrayListMultimap.create());
        ((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).put(w,ImmutableSetMultimap.of());
        Method update=MTERemoteWorkerNode.class.getDeclaredMethod("updateChunkLoading",gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);update.setAccessible(true);
        Class<?> manager=null;
        try{manager=Class.forName("com.miaokatze.gtsr.common.machine.base.RemoteWorkerChunkTickets");}catch(ClassNotFoundException baseline){}
        if(manager!=null){
            Object mod=new Object();cpw.mods.fml.common.ModMetadata meta=new cpw.mods.fml.common.ModMetadata();meta.modId="gtsr";meta.name="owned miner fixture";meta.version="fixture";
            cpw.mods.fml.common.DummyModContainer container=new cpw.mods.fml.common.DummyModContainer(meta);
            cpw.mods.fml.common.Loader loader=(cpw.mods.fml.common.Loader)u.allocateInstance(cpw.mods.fml.common.Loader.class);
            cpw.mods.fml.common.LoadController controller=(cpw.mods.fml.common.LoadController)u.allocateInstance(cpw.mods.fml.common.LoadController.class);
            HashBiMap<cpw.mods.fml.common.ModContainer,Object> objects=HashBiMap.create();objects.put(container,mod);
            field(cpw.mods.fml.common.LoadController.class,"modObjectList").set(controller,objects);
            field(cpw.mods.fml.common.Loader.class,"modController").set(loader,controller);
            Map<String,cpw.mods.fml.common.ModContainer> named=new HashMap<>();named.put("gtsr",container);field(cpw.mods.fml.common.Loader.class,"namedMods").set(loader,named);
            field(cpw.mods.fml.common.Loader.class,"instance").set(null,loader);field(manager,"mod").set(null,mod);
            ForgeChunkManager.setForcedChunkLoadingCallback(mod,(ForgeChunkManager.LoadingCallback)(tickets,world)->{});
            field(ForgeChunkManager.class,"defaultMaxCount").setInt(null,200);
        }
        JsonArray rows=new JsonArray();boolean pass=true;
        for(int tier=0;tier<=4;tier++) {
            BaseMetaTileEntity host=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);
            host.setWorldObj(w);host.xCoord=15;host.yCoord=64;host.zCoord=15;
            field(BaseMetaTileEntity.class,"mWorks").setBoolean(host,true);
            MTESingularityMinerNode node=(MTESingularityMinerNode)u.allocateInstance(MTESingularityMinerNode.class);
            node.setBaseMetaTileEntity(host);
            field(MTERemoteWorkerNode.class,"mBound").setBoolean(node,true);
            field(MTESingularityMinerNode.class,"mMinerTier").setInt(node,tier);
            Constructor<ForgeChunkManager.Ticket> ctor=ForgeChunkManager.Ticket.class.getDeclaredConstructor(String.class,ForgeChunkManager.Type.class,World.class);ctor.setAccessible(true);
            ForgeChunkManager.Ticket ticket=ctor.newInstance("gregtech",ForgeChunkManager.Type.NORMAL,w);
            ((Multimap)((Map)field(ForgeChunkManager.class,"tickets").get(null)).get(w)).put("gregtech",ticket);
            ((Map)field(GTChunkManager.class,"registeredTickets").get(GTChunkManager.instance)).put(host,ticket);
            update.invoke(node,host);
            boolean self=ForgeChunkManager.getPersistentChunksFor(w).containsKey(new ChunkCoordIntPair(0,0));
            JsonObject row=new JsonObject();row.addProperty("tier",tier);row.addProperty("nativeCount",ForgeChunkManager.getPersistentChunksFor(w).keySet().size());row.addProperty("depth",ticket.getChunkListDepth());row.addProperty("selfForced",self);
            row.addProperty("selfFlag",field(MTERemoteWorkerNode.class,"mSelfChunkRequested").getBoolean(node));row.addProperty("workFlag",field(MTERemoteWorkerNode.class,"mWorkChunksRequested").getBoolean(node));rows.add(row);pass&=self;
            if(manager!=null){
                int[] expected={25,49,81,169,361};
                check("tier"+tier+" exact full native footprint",ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==expected[tier]);
                check("tier"+tier+" retired legacy GT ticket",ticket.getChunkList().isEmpty()&&!((Map)field(GTChunkManager.class,"registeredTickets").get(GTChunkManager.instance)).containsKey(host));
                field(BaseMetaTileEntity.class,"mWorks").setBoolean(host,false);update.invoke(node,host);
                check("tier"+tier+" stop retains self only",ForgeChunkManager.getPersistentChunksFor(w).keySet().equals(Collections.singleton(new ChunkCoordIntPair(0,0))));
                field(BaseMetaTileEntity.class,"mWorks").setBoolean(host,true);update.invoke(node,host);
                check("tier"+tier+" restart restores footprint",ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==expected[tier]);
                node.onTierChanged();check("tier"+tier+" upgrade releases old footprint",ForgeChunkManager.getPersistentChunksFor(w).isEmpty());
                update.invoke(node,host);check("tier"+tier+" upgrade reacquires footprint",ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==expected[tier]);
                field(MTERemoteWorkerNode.class,"mBound").setBoolean(node,false);update.invoke(node,host);
                check("tier"+tier+" unbind releases",ForgeChunkManager.getPersistentChunksFor(w).isEmpty());
                field(MTERemoteWorkerNode.class,"mBound").setBoolean(node,true);update.invoke(node,host);
                GTValues.enableChunkloaders=false;update.invoke(node,host);
                check("tier"+tier+" global disable clears tickets",ForgeChunkManager.getPersistentChunksFor(w).isEmpty());GTValues.enableChunkloaders=true;
            }
            node.releaseAllChunks();
        }
        if(manager!=null){
            BaseMetaTileEntity host=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);host.setWorldObj(w);host.xCoord=-1;host.yCoord=64;host.zCoord=-17;
            List<ChunkCoordIntPair> work=new ArrayList<>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)work.add(new ChunkCoordIntPair(x,z));
            field(ForgeChunkManager.class,"defaultMaxCount").setInt(null,1);
            boolean full=(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work);
            check("quota failure reported",!full);
            check("quota failure rolls back work and preserves negative-boundary self",ForgeChunkManager.getPersistentChunksFor(w).keySet().equals(Collections.singleton(new ChunkCoordIntPair(-1,-2))));
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            check("quota failure explicit release clears recovery ticket",ForgeChunkManager.getPersistentChunksFor(w).isEmpty());
            field(ForgeChunkManager.class,"defaultMaxCount").setInt(null,200);
            full=(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work);
            check("released quota permits retry full footprint",full&&ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==49);
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            BaseMetaTileEntity second=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);second.setWorldObj(w);second.xCoord=1000;second.yCoord=64;
            BaseMetaTileEntity hub=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);hub.setWorldObj(w);hub.xCoord=2000;hub.yCoord=64;
            manager.getMethod("bindHub",TileEntity.class,TileEntity.class).invoke(null,host,hub);
            manager.getMethod("bindHub",TileEntity.class,TileEntity.class).invoke(null,second,hub);
            check("two nodes share one hub native ticket",((Map)field(manager,"HUBS").get(null)).size()==1);
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            check("first node removal retains shared hub",ForgeChunkManager.getPersistentChunksFor(w).containsKey(new ChunkCoordIntPair(125,0)));
            manager.getMethod("release",TileEntity.class).invoke(null,second);
            check("last node removal releases shared hub",ForgeChunkManager.getPersistentChunksFor(w).isEmpty()&&((Map)field(manager,"BINDINGS").get(null)).isEmpty());
            field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,0);
            MTESingularityMinerNode unbound=(MTESingularityMinerNode)u.allocateInstance(MTESingularityMinerNode.class);unbound.setBaseMetaTileEntity(host);
            ForgeChunkManager.Ticket orphan=legacy(w,host);update.invoke(unbound,host);
            check("unbound legacy restored GT ticket retired",orphan.getChunkList().isEmpty());GTChunkManager.releaseTicket(host);
            orphan=legacy(w,host);unbound.releaseAllChunks();
            check("removal before first tick retires legacy restored GT ticket",orphan.getChunkList().isEmpty());GTChunkManager.releaseTicket(host);
            check("zero native depth follows Forge unlimited semantics within actual footprint",(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work)&&ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==49);
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,7);
            check("depth7 partitions without FIFO loss",(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work)&&ForgeChunkManager.getPersistentChunksFor(w).keySet().size()==49);
            List<ForgeChunkManager.Ticket> group=(List<ForgeChunkManager.Ticket>)((Map)field(manager,"NODES").get(null)).get(host);
            check("depth7 uses seven bounded native tickets",group.size()==7&&group.stream().allMatch(t->t.getChunkList().size()<=7));
            ForgeChunkManager.OrderedLoadingCallback fallback=new ForgeChunkManager.OrderedLoadingCallback(){public void ticketsLoaded(List<ForgeChunkManager.Ticket> ts,World world){}public List<ForgeChunkManager.Ticket> ticketsLoaded(List<ForgeChunkManager.Ticket> ts,World world,int max){return new ArrayList<>();}};
            Constructor<?> constructor=manager.getDeclaredConstructor(ForgeChunkManager.OrderedLoadingCallback.class);constructor.setAccessible(true);Object dispatcher=constructor.newInstance(fallback);
            List<ForgeChunkManager.Ticket> saved=new ArrayList<>(group);
            ((Map)field(manager,"NODES").get(null)).clear();
            MTESingularityMinerNode restored=(MTESingularityMinerNode)u.allocateInstance(MTESingularityMinerNode.class);host.setMetaTileEntity(restored);restored.setBaseMetaTileEntity(host);field(MTERemoteWorkerNode.class,"mBound").setBoolean(restored,true);w.tiles.put(host.xCoord+","+host.yCoord+","+host.zCoord,host);
            Method restore=manager.getMethod("ticketsLoaded",List.class,World.class,int.class);
            GTValues.alwaysReloadChunkloaders=false;
            check("alwaysReload false rejects automatic worker restoration",((List)restore.invoke(dispatcher,saved,w,200)).isEmpty()&&((Map)field(manager,"NODES").get(null)).isEmpty());
            GTValues.alwaysReloadChunkloaders=true;
            List<?> kept=(List<?>)restore.invoke(dispatcher,saved,w,200);
            check("alwaysReload true keeps one legitimate recovery ticket",kept.size()==1&&((Map)field(manager,"NODES").get(null)).size()==1);
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            for(ForgeChunkManager.Ticket t:saved)if(!kept.contains(t))ForgeChunkManager.releaseTicket(t);
            check("restored explicit release leaves no owner ticket",ForgeChunkManager.getPersistentChunksFor(w).isEmpty());
            manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work);
            manager.getMethod("bindHub",TileEntity.class,TileEntity.class).invoke(null,host,hub);
            ((Map)field(ForgeChunkManager.class,"tickets").get(null)).remove(w);((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).remove(w);
            manager.getMethod("unload",net.minecraftforge.event.world.WorldEvent.Unload.class).invoke(dispatcher,new net.minecraftforge.event.world.WorldEvent.Unload(w));
            check("WorldUnload after native registry removal only drops local references",((Map)field(manager,"NODES").get(null)).isEmpty()&&((Map)field(manager,"HUBS").get(null)).isEmpty()&&((Map)field(manager,"BINDINGS").get(null)).isEmpty());
            manager.getMethod("release",TileEntity.class).invoke(null,host);
            check("late removal after unload never releases invalidated native tickets",!((Map)field(ForgeChunkManager.class,"tickets").get(null)).containsKey(w));
            for(boolean serverPresent:new boolean[]{false,true}){
                ((Map)field(ForgeChunkManager.class,"tickets").get(null)).put(w,ArrayListMultimap.create());
                ((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).put(w,ImmutableSetMultimap.of());
                manager.getMethod("load",net.minecraftforge.event.world.WorldEvent.Load.class).invoke(dispatcher,new net.minecraftforge.event.world.WorldEvent.Load(w));
                manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work);
                ForgeChunkManager.Ticket stale=legacy(w,host);
                Object server=null;
                if(serverPresent){server=u.allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);field(net.minecraft.server.MinecraftServer.class,"serverRunning").setBoolean(server,true);}
                field(net.minecraft.server.MinecraftServer.class,"mcServer").set(null,server);
                ((Map)field(ForgeChunkManager.class,"tickets").get(null)).remove(w);((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).remove(w);
                manager.getMethod("unload",net.minecraftforge.event.world.WorldEvent.Unload.class).invoke(dispatcher,new net.minecraftforge.event.world.WorldEvent.Unload(w));
                manager.getMethod("release",TileEntity.class).invoke(null,host);
                check("legacy stale GT reference safe after Forge clear, serverPresent="+serverPresent,((Map)field(GTChunkManager.class,"registeredTickets").get(GTChunkManager.instance)).get(host)==stale&&!((Map)field(ForgeChunkManager.class,"tickets").get(null)).containsKey(w));
                check("late ensure refuses unloaded world, serverPresent="+serverPresent,!(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,host,work)&&((Map)field(manager,"NODES").get(null)).isEmpty());
                check("late restore refuses unloaded world, serverPresent="+serverPresent,((List)restore.invoke(dispatcher,saved,w,200)).isEmpty());
                GTChunkManager.instance.onServerStopped();field(net.minecraft.server.MinecraftServer.class,"mcServer").set(null,null);
            }
            SparseWorld reopened=(SparseWorld)u.allocateInstance(SparseWorld.class);reopened.tiles=new HashMap<>();field(World.class,"provider").set(reopened,u.allocateInstance(WorldProviderSurface.class));
            ((Map)field(ForgeChunkManager.class,"tickets").get(null)).put(reopened,ArrayListMultimap.create());((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).put(reopened,ImmutableSetMultimap.of());
            BaseMetaTileEntity newHost=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);newHost.setWorldObj(reopened);newHost.yCoord=64;
            check("same dimension new world identity may issue tickets after old unload",(Boolean)manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,newHost,Collections.emptyList()));
            manager.getMethod("release",TileEntity.class).invoke(null,newHost);
            ColdHubWorld cold=(ColdHubWorld)u.allocateInstance(ColdHubWorld.class);field(World.class,"provider").set(cold,u.allocateInstance(WorldProviderSurface.class));cold.provider.dimensionId=41234;
            cold.hub=(BaseMetaTileEntity)u.allocateInstance(BaseMetaTileEntity.class);cold.hub.setWorldObj(cold);cold.hub.yCoord=64;
            FixtureHub fixtureHub=(FixtureHub)u.allocateInstance(FixtureHub.class);cold.hub.setMetaTileEntity(fixtureHub);fixtureHub.setBaseMetaTileEntity(cold.hub);
            cold.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},(proxy,m,a)->{if(m.getName().equals("loadChunk")){cold.loaded=true;return null;}if(m.getName().equals("chunkExists"))return cold.loaded;return null;});
            ((Map)field(ForgeChunkManager.class,"tickets").get(null)).put(cold,ArrayListMultimap.create());((Map)field(ForgeChunkManager.class,"forcedChunks").get(null)).put(cold,ImmutableSetMultimap.of());
            Map worlds=(Map)field(net.minecraftforge.common.DimensionManager.class,"worlds").get(null);worlds.put(41234,cold);
            MTESingularityMinerNode recovery=(MTESingularityMinerNode)u.allocateInstance(MTESingularityMinerNode.class);recovery.setBaseMetaTileEntity(newHost);field(MTERemoteWorkerNode.class,"mBound").setBoolean(recovery,true);field(MTERemoteWorkerNode.class,"mHubDim").setInt(recovery,41234);field(MTERemoteWorkerNode.class,"mHubY").setInt(recovery,64);
            newHost.setMetaTileEntity(recovery);reopened.tiles.put("0,64,0",newHost);
            field(ForgeChunkManager.class,"defaultMaxChunks").setInt(null,1);
            manager.getMethod("ensure",TileEntity.class,List.class).invoke(null,newHost,Collections.emptyList());
            List<ForgeChunkManager.Ticket> recoveryTickets=new ArrayList<>((List<ForgeChunkManager.Ticket>)((Map)field(manager,"NODES").get(null)).get(newHost));((Map)field(manager,"NODES").get(null)).clear();
            check("new-world part0 restores before cold-hub normal tick",((List)restore.invoke(dispatcher,recoveryTickets,reopened,200)).size()==1&&!cold.loaded);
            update.invoke(recovery,newHost);
            check("normal restored-node maintenance opens only existing hub world controller chunk",cold.loaded&&worlds.get(41234)==cold);
            check("cold hub control-chain restore forces entire footprint with depth1",ForgeChunkManager.getPersistentChunksFor(cold).keySet().size()==4);
            check("depth1 full hub footprint uses four bounded tickets",((List)((Map)field(manager,"HUBS").get(null)).get(cold.hub)).size()==4);
            recovery.releaseAllChunks();check("cold hub control-chain removal releases complete footprint",ForgeChunkManager.getPersistentChunksFor(cold).isEmpty());worlds.remove(41234);
            field(MTERemoteWorkerNode.class,"mHubDim").setInt(recovery,41235);update.invoke(recovery,newHost);
            check("missing bound hub dimension is never initialized",!worlds.containsKey(41235));recovery.releaseAllChunks();
        }
        for(JsonElement row:checks)pass&=row.getAsJsonObject().get("pass").getAsBoolean();
        JsonObject result=new JsonObject();result.addProperty("status",pass?"PASS":"FAIL");result.add("tiers",rows);result.add("checks",checks);result.addProperty("scope","Real node maintenance -> actual native Forge ticket issuance/quota; sparse World+minimal FML container maps; no full server, disk restoration or actual mining asserted");
        JsonObject codeSources=new JsonObject();for(Class<?> c:new Class<?>[]{MTERemoteWorkerNode.class,MTESingularityMinerNode.class,GTChunkManager.class,ForgeChunkManager.class})codeSources.addProperty(c.getName(),c.getProtectionDomain().getCodeSource().getLocation().toString());if(manager!=null)codeSources.addProperty(manager.getName(),manager.getProtectionDomain().getCodeSource().getLocation().toString());result.add("codeSources",codeSources);
        Files.write(Paths.get(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println(result);if(!pass)throw new AssertionError("Bound miner loses own chunk under actual Forge quota");
    }
}
