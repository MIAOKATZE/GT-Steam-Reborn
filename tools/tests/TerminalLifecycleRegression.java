package com.miaokatze.gtsr.common.gui.mui2.cluster;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import com.cleanroommc.modularui.value.sync.*;
import com.miaokatze.gtsr.common.terminal.*;
import com.miaokatze.gtsr.client.terminal.*;
import com.miaokatze.gtsr.client.gui.terminal.GuiTcdsTerminalScreen;
import io.netty.buffer.Unpooled;

/** Detached production entry-point regression. No Minecraft startup, GL or TCP claim. */
public final class TerminalLifecycleRegression {
    static sun.misc.Unsafe u;
    static int checks;
    static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    static Field field(Class<?> c, String n) throws Exception { Field f=c.getDeclaredField(n); f.setAccessible(true); return f; }
    static void set(Object o, Class<?> c, String n, Object v) throws Exception { field(c,n).set(o,v); }
    static Object get(Object o, Class<?> c, String n) throws Exception { return field(c,n).get(o); }
    static Object invoke(Object o, Class<?> c, String name, Class<?>[] types, Object... args) throws Exception {
        Method m=c.getDeclaredMethod(name,types); m.setAccessible(true);
        try { return m.invoke(o,args); } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
    }
    static byte[] delta(int key, Object value) {
        PacketBuffer b=new PacketBuffer(Unpooled.buffer()); b.writeBoolean(true); b.writeVarIntToBuffer(1<<key); b.writeByte(key);
        if(key==0)b.writeBoolean((Boolean)value);
        else if(key==31)cpw.mods.fml.common.network.ByteBufUtils.writeUTF8String(b,(String)value);
        else b.writeVarIntToBuffer((Integer)value);
        byte[] bytes=new byte[b.readableBytes()]; b.readBytes(bytes); return bytes;
    }
    static PacketTerminalData packet(int version, int heat) {
        return new PacketTerminalData(TerminalUiType.CLUSTER_TERMINAL,0,1,2,3,true,version,delta(1,heat));
    }
    static void read(ClusterSessionSyncHandler h, int version, byte[] payload) throws Exception {
        PacketBuffer b=new PacketBuffer(Unpooled.buffer()); b.writeLong(7); b.writeInt(version); b.writeLong(1); b.writeInt(payload.length); b.writeBytes(payload); h.readOnClient(0,b);
    }
    static void cluster() throws Exception {
        ClusterTerminalFactory.ClusterGuiData data=(ClusterTerminalFactory.ClusterGuiData)u.allocateInstance(ClusterTerminalFactory.ClusterGuiData.class);
        set(data,ClusterTerminalFactory.ClusterGuiData.class,"epoch",7L);
        ClusterSessionSyncHandler h=(ClusterSessionSyncHandler)u.allocateInstance(ClusterSessionSyncHandler.class);
        set(h,ClusterSessionSyncHandler.class,"data",data); set(h,ClusterSessionSyncHandler.class,"incoming",new ConcurrentLinkedQueue<Runnable>());
        set(h,ClusterSessionSyncHandler.class,"drafts",new HashMap<>()); set(h,ClusterSessionSyncHandler.class,"topology",new byte[0]);
        set(h,ClusterSessionSyncHandler.class,"performance",new ClusterPerformanceModel());
        ModularSyncManager manager=(ModularSyncManager)u.allocateInstance(ModularSyncManager.class);set(manager,ModularSyncManager.class,"client",true);
        PanelSyncManager panel=(PanelSyncManager)u.allocateInstance(PanelSyncManager.class);set(panel,PanelSyncManager.class,"modularSyncManager",manager);set(panel,PanelSyncManager.class,"client",true);
        h.init("test",panel);
        read(h,1,delta(1,10)); h.clientTick();
        List<?> before=(List<?>)get(h.performance,ClusterPerformanceModel.class,"rows");
        read(h,2,delta(1,73)); read(h,3,delta(0,true)); h.clientTick();
        check(h.snapshot().getInt(ClusterTerminalData.KEY_HEAT,0)==73,"merged heat");
        check(((List<?>)get(h.performance,ClusterPerformanceModel.class,"rows")).get(0)!=before.get(0),"heat delta before enabled delta refreshes summary");
        boolean found=false; for(ClusterPerformanceModel.PerfRow row:(List<ClusterPerformanceModel.PerfRow>)((List<?>)get(h.performance,ClusterPerformanceModel.class,"rows")).get(0)) if(row.text.contains("73%"))found=true;
        check(found,"summary displays latest heat");
        for(int key:new int[]{2,31}) {
            before=(List<?>)get(h.performance,ClusterPerformanceModel.class,"rows");
            read(h,4+key,delta(key,key==31?"0:3:0.1.2":123)); read(h,5+key,delta(0,false)); h.clientTick();
            check(((List<?>)get(h.performance,ClusterPerformanceModel.class,"rows")).get(key==2?1:2)!=before.get(key==2?1:2),"early delta refreshes column "+key);
        }
        before=(List<?>)get(h.performance,ClusterPerformanceModel.class,"rows");
        set(h.performance,ClusterPerformanceModel.class,"language","different-language"); h.clientTick();
        check(get(h.performance,ClusterPerformanceModel.class,"rows")!=before,"idle client tick keeps language refresh");
    }
    static void server() throws Exception {
        SparseWorld world=(SparseWorld)u.allocateInstance(SparseWorld.class); world.loaded=false;
        world.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},(p,m,a)->{world.providerReads++; if(m.getName().equals("chunkExists"))return world.loaded; throw new AssertionError("loading query "+m.getName());});
        ((Hashtable<Integer,WorldServer>)get(null,DimensionManager.class,"worlds")).put(0,world);
        EntityPlayerMP player=(EntityPlayerMP)u.allocateInstance(EntityPlayerMP.class);
        set(player,Entity.class,"entityUniqueID",UUID.randomUUID()); player.dimension=0; player.worldObj=world; player.playerNetServerHandler=(net.minecraft.network.NetHandlerPlayServer)u.allocateInstance(net.minecraft.network.NetHandlerPlayServer.class);
        FakeServer server=(FakeServer)u.allocateInstance(FakeServer.class); server.world=world;
        set(null,MinecraftServer.class,"mcServer",server);
        Configuration configuration=(Configuration)u.allocateInstance(Configuration.class);
        set(configuration,net.minecraft.server.management.ServerConfigurationManager.class,"playerEntityList",new ArrayList<>());server.setConfigManager(configuration);configuration.playerEntityList.add(player);
        player.playerNetServerHandler.playerEntity=player;
        NetworkManager connection=new NetworkManager(false);
        set(connection,NetworkManager.class,"channel",new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()));
        set(player.playerNetServerHandler,NetHandlerPlayServer.class,"netManager",connection);
        // Preserve the production request/action handler; replace only outbound transport.
        Class.forName(TerminalNet.class.getName());
        Field network=field(TerminalNet.class,"NETWORK"); u.putObject(u.staticFieldBase(network),u.staticFieldOffset(network),u.allocateInstance(RecordingNetwork.class));
        for(boolean route:new boolean[]{true,false}) {
            com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes.clusterTerminal=route;
            ((Map<?,?>)get(null,TerminalNet.class,"LAST_REQUEST_TICK")).clear();
            invoke(null,TerminalNet.class,"handleRequest",new Class<?>[]{EntityPlayerMP.class,PacketTerminalRequest.class},player,new PacketTerminalRequest(TerminalUiType.CLUSTER_TERMINAL,0,1000000,64,1000000));
            invoke(null,TerminalNet.class,"handleAction",new Class<?>[]{EntityPlayerMP.class,PacketTerminalAction.class},player,new PacketTerminalAction(TerminalUiType.CLUSTER_TERMINAL,0,1000000,64,1000000,0,new byte[0]));
            check(world.tileReads==0&&world.providerReads==0,"out of range skips tile/provider with route="+route);
        }
        check(TerminalServerSessions.getAliveBaseTile(0,1,2,3)==null&&world.tileReads==0,"unloaded chunk skips tile query");
        world.loaded=true; TerminalServerSessions.getAliveBaseTile(0,1,2,3); check(world.tileReads==1,"loaded chunk queries tile");
        check(TerminalServerSessions.getAliveBaseTile(-7042010,1,2,3)==null,"unloaded dimension stays unavailable");
        Map<UUID,Long> ticks=(Map<UUID,Long>)get(null,TerminalNet.class,"LAST_REQUEST_TICK"); ticks.put(player.getUniqueID(),1000000L);
        TerminalServerSessions.isDuplicateAction(player.getUniqueID(),1,0,2);
        seedPending();
        invoke(null,TerminalNet.class,"clearServerState",new Class<?>[0]);
        check(ticks.isEmpty(),"server stop clears high-world cooldown");
        check(pendingSize()==0,"server stop discards queued tasks");
        check(!TerminalServerSessions.isDuplicateAction(player.getUniqueID(),1,0,2),"server stop clears debounce");
        invoke(null,TerminalNet.class,"handleRequest",new Class<?>[]{EntityPlayerMP.class,PacketTerminalRequest.class},player,new PacketTerminalRequest(TerminalUiType.CLUSTER_TERMINAL,0,1,2,3));
        check(ticks.containsKey(player.getUniqueID()),"new low-age world request admitted");
    }
    static void seedPending() throws Exception {
        Object pending=get(null,TerminalNet.class,"PENDING");
        if(pending instanceof Queue) ((Queue<Object>)pending).offer(new Object());
        else pending.getClass().getMethod("offer",Object.class,Object.class).invoke(pending,new Object(),new Object());
    }
    static int pendingSize() throws Exception {
        Object pending=get(null,TerminalNet.class,"PENDING");return (Integer)pending.getClass().getMethod("size").invoke(pending);
    }
    static void deliverData(PacketTerminalData packet) throws Exception {
        try { TerminalClientPacketSink.class.getMethod("handleDataScheduled",PacketTerminalData.class,net.minecraft.client.network.NetHandlerPlayClient.class).invoke(null,packet,Minecraft.getMinecraft().getNetHandler()); }
        catch(NoSuchMethodException oldApi) { TerminalClientPacketSink.class.getMethod("handleDataScheduled",PacketTerminalData.class).invoke(null,packet); }
    }
    static void deliverOpen(PacketOpenTerminalUi packet) throws Exception {
        try { TerminalClientPacketSink.class.getMethod("handleOpenScheduled",PacketOpenTerminalUi.class,net.minecraft.client.network.NetHandlerPlayClient.class).invoke(null,packet,Minecraft.getMinecraft().getNetHandler()); }
        catch(NoSuchMethodException oldApi) { TerminalClientPacketSink.class.getMethod("handleOpenScheduled",PacketOpenTerminalUi.class).invoke(null,packet); }
    }
    static void drain(Queue<?> queue) { Object task; while((task=queue.poll())!=null)((Runnable)task).run(); }
    static void cooldown() throws Exception {
        Class.forName(TerminalNet.class.getName());
        UUID id=UUID.randomUUID();
        Map<UUID,Long> ticks=(Map<UUID,Long>)get(null,TerminalNet.class,"LAST_REQUEST_TICK"); ticks.put(id,1000000L);
        TerminalServerSessions.isDuplicateAction(id,1,0,2);
        seedPending();
        ((com.miaokatze.gtsr.main.GTSteamReborn)u.allocateInstance(com.miaokatze.gtsr.main.GTSteamReborn.class)).serverStopped(null);
        check(ticks.isEmpty(),"real serverStopped clears high-world cooldown");
        check(pendingSize()==0,"real serverStopped clears pending tasks");
        check(!TerminalServerSessions.isDuplicateAction(id,1,0,2),"real serverStopped clears debounce");
    }
    static void client() throws Exception {
        Minecraft mc=(Minecraft)u.allocateInstance(Minecraft.class);
        set(null,Minecraft.class,"theMinecraft",mc); Queue<Object> q=new ConcurrentLinkedQueue<>();
        set(mc,Minecraft.class,"field_152351_aB",q); set(mc,Minecraft.class,"field_152352_aC",new Thread());
        net.minecraft.client.entity.EntityClientPlayerMP client=(net.minecraft.client.entity.EntityClientPlayerMP)u.allocateInstance(net.minecraft.client.entity.EntityClientPlayerMP.class);
        set(client,net.minecraft.client.entity.EntityClientPlayerMP.class,"sendQueue",u.allocateInstance(net.minecraft.client.network.NetHandlerPlayClient.class));mc.thePlayer=client;
        WorldClient world=(WorldClient)u.allocateInstance(WorldClient.class); set(world,World.class,"provider",new WorldProviderSurface()); mc.theWorld=world;
        ClusterTerminalClientCache.accept(packet(10000,90));
        Class<?> lifecycle;
        try { lifecycle=Class.forName("com.miaokatze.gtsr.client.terminal.TerminalClientPacketSink$ConnectionLifecycle"); }
        catch(ClassNotFoundException absent) {
            ClusterTerminalClientCache.accept(packet(1,12));
            check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==12,"disconnect must permit same-anchor low version after reconnect");
            return;
        }
        deliverData(packet(10001,91));
        deliverOpen(new PacketOpenTerminalUi(TerminalUiType.STEAM_HUB,0,1,2,3,0));
        Object listener=lifecycle.getConstructor().newInstance();
        lifecycle.getMethod("onDisconnect",cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent.class).invoke(listener,new Object[]{null});
        drain(q);
        check(!ClusterTerminalClientCache.isAnchored(1,2,3,0),"disconnect clears cached anchor and skips queued old data/open");
        lifecycle.getMethod("onConnect",cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent.class).invoke(listener,new Object[]{null});
        deliverData(packet(1,12)); drain(q);
        check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==12,"new connection accepts same-anchor low version");
        deliverData(packet(0,50)); drain(q);
        check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==12,"same-connection monotonic version still enforced");
        deliverData(new PacketTerminalData(TerminalUiType.CLUSTER_TERMINAL,0,8,9,10,true,0,delta(1,34))); drain(q);
        check(ClusterTerminalClientCache.isAnchored(8,9,10,0)&&ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==34,"new anchor accepts lower version");
        deliverData(new PacketTerminalData(TerminalUiType.CLUSTER_TERMINAL,0,1,2,3,false,0,new byte[0])); drain(q);
        check(ClusterTerminalClientCache.isAnchored(8,9,10,0),"old-anchor invalidation preserves new anchor");
        deliverData(new PacketTerminalData(TerminalUiType.CLUSTER_TERMINAL,0,8,9,10,false,0,new byte[0])); drain(q);
        check(!ClusterTerminalClientCache.isAnchored(8,9,10,0),"matching invalidation clears cache");
        for(String name:new String[]{"drilling","cache"}) { Field f=field(HubTerminalClientCache.class,name);f.set(null,u.allocateInstance(f.getType())); }
        Field agg=field(AggregatorClientCache.class,"snapshot");agg.set(null,u.allocateInstance(agg.getType()));
        Field tcds=field(GuiTcdsTerminalScreen.class,"cache");tcds.set(null,u.allocateInstance(tcds.getType()));
        lifecycle.getMethod("onDisconnect",cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent.class).invoke(listener,new Object[]{null});drain(q);
        check(HubTerminalClientCache.drillingSnapshot()==null&&HubTerminalClientCache.cacheSnapshot()==null&&AggregatorClientCache.snapshot()==null&&tcds.get(null)==null,"disconnect clears every legacy cache");
    }
    public static void main(String[] args) throws Exception {
        u=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        set(null,Loader.class,"instance",u.allocateInstance(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class<?>[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.CLIENT:null);
        set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        switch(args[0]) {case "cluster":cluster();break;case "server":server();break;case "cooldown":cooldown();break;case "client":client();break;default:throw new AssertionError();}
        System.out.println("PASS TerminalLifecycleRegression "+args[0]+" checks="+checks);
    }
    public static final class RecordingNetwork extends SimpleNetworkWrapper {
        private RecordingNetwork(){super("unused");}
        @Override public void sendTo(IMessage message,EntityPlayerMP player) {}
    }
    private static final class Configuration extends net.minecraft.server.management.ServerConfigurationManager { private Configuration(){super(null);} }
    private static final class FakeServer extends MinecraftServer {
        SparseWorld world;
        private FakeServer(){super(null,null);}
        @Override public WorldServer worldServerForDimension(int dim){return world;}
        @Override protected boolean startServer(){return false;}
        @Override public boolean canStructuresSpawn(){return false;}
        @Override public WorldSettings.GameType getGameType(){return null;}
        @Override public EnumDifficulty func_147135_j(){return null;}
        @Override public boolean isHardcore(){return false;}
        @Override public int getOpPermissionLevel(){return 0;}
        @Override public boolean isDedicatedServer(){return false;}
        @Override public boolean func_152363_m(){return false;}
        @Override public boolean isCommandBlockEnabled(){return false;}
        @Override public String shareToLAN(WorldSettings.GameType type,boolean cheats){return null;}
    }
    private static final class SparseWorld extends WorldServer {
        boolean loaded; int tileReads,providerReads; IChunkProvider chunks;
        private SparseWorld(){super(null,null,"unused",0,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){tileReads++;return null;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public long getTotalWorldTime(){return 0;}
    }
}
