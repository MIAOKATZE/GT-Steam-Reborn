package com.miaokatze.gtsr.tests;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import com.miaokatze.gtsr.common.network.*;
import com.miaokatze.gtsr.common.terminal.*;
import com.miaokatze.gtsr.client.terminal.*;

/** Real MC/Forge/Netty and production packet entry points. Unsafe supplies isolated players/worlds, not a game. */
public final class PacketAuthorityRegression {
    static sun.misc.Unsafe u;
    static int checks;
    static Field f(Class<?> c,String name)throws Exception {Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static void set(Object o,Class<?> c,String n,Object v)throws Exception {f(c,n).set(o,v);}
    static Object get(Object o,Class<?> c,String n)throws Exception {return f(c,n).get(o);}
    static <T>T bare(Class<T> c)throws Exception {return (T)u.allocateInstance(c);}
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static MessageContext context(INetHandler handler,Side side)throws Exception {
        Constructor<MessageContext> c=MessageContext.class.getDeclaredConstructor(INetHandler.class,Side.class);c.setAccessible(true);return c.newInstance(handler,side);
    }
    static Object queue(Class<?> c,String name)throws Exception{return get(null,c,name);}
    static int size(Object queue)throws Exception{return (Integer)queue.getClass().getMethod("size").invoke(queue);}
    static void clear(Object queue)throws Exception{queue.getClass().getMethod("clear").invoke(queue);}
    static SparseWorld world; static FakeServer server; static EntityPlayerMP player; static NetHandlerPlayServer connection;
    static void setupServer()throws Exception {
        world=bare(SparseWorld.class);world.chunks=(IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},(p,m,a)->m.getName().equals("chunkExists")?true:null);
        server=bare(FakeServer.class);server.world=world;set(null,MinecraftServer.class,"mcServer",server);
        Configuration config=bare(Configuration.class);set(config,ServerConfigurationManager.class,"playerEntityList",new ArrayList<>());server.setConfigManager(config);
        player=bare(EntityPlayerMP.class);player.worldObj=world;player.dimension=0;set(player,Entity.class,"entityUniqueID",UUID.randomUUID());
        player.inventory=new InventoryPlayer(player);
        connection=bare(NetHandlerPlayServer.class);connection.playerEntity=player;player.playerNetServerHandler=connection;
        NetworkManager manager=new NetworkManager(false);set(manager,NetworkManager.class,"channel",new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()));set(connection,NetHandlerPlayServer.class,"netManager",manager);
        config.playerEntityList.add(player);
    }
    static void terminalFlood()throws Exception {
        setupServer();Object q=queue(TerminalNet.class,"PENDING");clear(q);
        TerminalNet.RequestHandler handler=new TerminalNet.RequestHandler();MessageContext ctx=context(connection,Side.SERVER);
        for(int n=0;n<10000;n++)handler.onMessage(new PacketTerminalRequest(TerminalUiType.TCDS,0,1,2,3),ctx);
        check(size(q)<=16,"one connection retained "+size(q)+" requests (expected <=16)");
    }
    static void bindFlood()throws Exception {
        setupServer();Object q=queue(GTSRFXNet.class,"PENDING_BIND");clear(q);
        GTSRFXNet.HubBindHandler handler=new GTSRFXNet.HubBindHandler();MessageContext ctx=context(connection,Side.SERVER);
        for(int n=0;n<10000;n++)handler.onMessage(new HubBindMessage(1,2,3,0),ctx);
        check(size(q)<=16,"one connection retained "+size(q)+" bind requests (expected <=16)");
    }
    static void wandFlood()throws Exception {
        setupServer();Object q=queue(WandNet.class,"PENDING");clear(q);
        WandNet.TargetHandler handler=new WandNet.TargetHandler();MessageContext ctx=context(connection,Side.SERVER);
        for(int n=0;n<10000;n++)handler.onMessage(new WandNet.PacketTarget(),ctx);
        check(size(q)<=16,"one connection retained "+size(q)+" wand requests (expected <=16)");
    }
    static void disconnected()throws Exception {
        setupServer();clear(queue(TerminalNet.class,"PENDING"));
        new TerminalNet.RequestHandler().onMessage(new PacketTerminalRequest(TerminalUiType.TCDS,0,1,2,3),context(connection,Side.SERVER));
        connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("fixture disconnect"));
        new TerminalNet.ServerDrain().onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(((Map<?,?>)get(null,TerminalNet.class,"LAST_REQUEST_TICK")).isEmpty(),"disconnected terminal request reached business dispatch");
    }
    static void replacedConnection()throws Exception {
        setupServer();clear(queue(TerminalNet.class,"PENDING"));
        new TerminalNet.RequestHandler().onMessage(new PacketTerminalRequest(TerminalUiType.TCDS,0,1,2,3),context(connection,Side.SERVER));
        NetHandlerPlayServer replacement=bare(NetHandlerPlayServer.class);replacement.playerEntity=player;
        set(replacement,NetHandlerPlayServer.class,"netManager",connection.netManager);player.playerNetServerHandler=replacement;
        new TerminalNet.ServerDrain().onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(((Map<?,?>)get(null,TerminalNet.class,"LAST_REQUEST_TICK")).isEmpty(),"old connection retained authority after handler replacement");
    }
    static void bindDisconnected()throws Exception {
        setupServer();clear(queue(GTSRFXNet.class,"PENDING_BIND"));
        new GTSRFXNet.HubBindHandler().onMessage(new HubBindMessage(0,0,0,0),context(connection,Side.SERVER));
        connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("fixture disconnect"));
        new GTSRFXNet.BindServerDrain().onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.tileReads==0,"disconnected bind packet consulted target world");
    }
    static void authority()throws Exception {
        setupServer();Class<?> type=Class.forName("com.miaokatze.gtsr.common.network.ServerPacketAuthority");Method method=type.getMethod("isCurrentPlayer",EntityPlayerMP.class,NetHandlerPlayServer.class);
        check((Boolean)method.invoke(null,player,connection),"live exact player accepted");
        EntityPlayerMP other=bare(EntityPlayerMP.class);set(other,Entity.class,"entityUniqueID",player.getUniqueID());
        server.getConfigurationManager().playerEntityList.clear();server.getConfigurationManager().playerEntityList.add(other);
        check(!(Boolean)method.invoke(null,player,connection),"UUID equality must not authorize replaced player");
        server.getConfigurationManager().playerEntityList.clear();server.getConfigurationManager().playerEntityList.add(player);
        connection.playerEntity=other;check(!(Boolean)method.invoke(null,player,connection),"respawn-reassigned handler rejected");connection.playerEntity=player;
        world.isRemote=true;check(!(Boolean)method.invoke(null,player,connection),"client world rejected");world.isRemote=false;
        player.isDead=true;check(!(Boolean)method.invoke(null,player,connection),"dead player rejected");player.isDead=false;
        player.worldObj=bare(SparseWorld.class);check(!(Boolean)method.invoke(null,player,connection),"obsolete world rejected");
    }
    static PacketTerminalData packet(int version,int heat){
        PacketBuffer b=new PacketBuffer(Unpooled.buffer());b.writeBoolean(true);b.writeVarIntToBuffer(2);b.writeByte(1);b.writeVarIntToBuffer(heat);byte[] p=new byte[b.readableBytes()];b.readBytes(p);
        return new PacketTerminalData(TerminalUiType.CLUSTER_TERMINAL,0,1,2,3,true,version,p);
    }
    static void drain(Queue<?> q){Object x;while((x=q.poll())!=null)((Runnable)x).run();}
    static void staleClient()throws Exception {
        Minecraft mc=bare(Minecraft.class);set(null,Minecraft.class,"theMinecraft",mc);Queue<Object> scheduled=new ConcurrentLinkedQueue<>();set(mc,Minecraft.class,"scheduledTasks",scheduled);set(mc,Minecraft.class,"mcThread",new Thread());
        WorldClient w=bare(WorldClient.class);set(w,World.class,"provider",new WorldProviderSurface());mc.theWorld=w;
        EntityClientPlayerMP client=bare(EntityClientPlayerMP.class);mc.thePlayer=client;
        NetHandlerPlayClient old=bare(NetHandlerPlayClient.class),current=bare(NetHandlerPlayClient.class);set(client,EntityClientPlayerMP.class,"sendQueue",current);
        TerminalClientPacketSink.ConnectionLifecycle lifecycle=new TerminalClientPacketSink.ConnectionLifecycle();lifecycle.onDisconnect(null);lifecycle.onConnect(null);drain(scheduled);
        new TerminalNet.DataHandler().onMessage(packet(1,12),context(current,Side.CLIENT));drain(scheduled);
        check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==12,"current connection data accepted");
        new TerminalNet.DataHandler().onMessage(packet(99999,91),context(old,Side.CLIENT));drain(scheduled);
        check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==12,"old-channel callback after reconnect overwrote new cache");
        new TerminalNet.DataHandler().onMessage(packet(2,14),context(current,Side.CLIENT));drain(scheduled);
        check(ClusterTerminalClientCache.getInt(ClusterTerminalData.KEY_HEAT,0)==14,"new connection remains live after stale reply");
    }
    static void history()throws Exception {
        setupServer();
        com.miaokatze.gtsr.common.items.HubTerminal item=bare(com.miaokatze.gtsr.common.items.HubTerminal.class);
        net.minecraft.item.ItemStack terminal=new net.minecraft.item.ItemStack(item,1);
        terminal.setTagCompound(new net.minecraft.nbt.NBTTagCompound());terminal.getTagCompound().setString("gtsr.terminalId","fixture-terminal");player.inventory.mainInventory[0]=terminal;
        Class<?> dataClass=Class.forName("com.miaokatze.gtsr.common.terminal.MachineHistoryFactory$Data");
        Constructor<?> dataConstructor=dataClass.getDeclaredConstructor(EntityPlayer.class,List.class,boolean.class);dataConstructor.setAccessible(true);Object data=dataConstructor.newInstance(player,Collections.emptyList(),true);
        Class<?> sessionClass=Class.forName("com.miaokatze.gtsr.common.terminal.MachineHistoryFactory$Session");Constructor<?> ctor=sessionClass.getDeclaredConstructor(dataClass);ctor.setAccessible(true);Object session=ctor.newInstance(data);
        com.cleanroommc.modularui.value.sync.ModularSyncManager manager=bare(com.cleanroommc.modularui.value.sync.ModularSyncManager.class);
        com.cleanroommc.modularui.screen.ModularContainer container=bare(com.cleanroommc.modularui.screen.ModularContainer.class);set(manager,com.cleanroommc.modularui.value.sync.ModularSyncManager.class,"container",container);player.openContainer=container;
        com.cleanroommc.modularui.value.sync.PanelSyncManager panel=bare(com.cleanroommc.modularui.value.sync.PanelSyncManager.class);set(panel,com.cleanroommc.modularui.value.sync.PanelSyncManager.class,"modularSyncManager",manager);
        ((com.cleanroommc.modularui.value.sync.SyncHandler<?>)session).init("fixture",panel);
        Method active=sessionClass.getDeclaredMethod("active");active.setAccessible(true);
        check((Boolean)active.invoke(session),"real history session accepts connected player/held terminal/container");
        connection.netManager.closeChannel(new net.minecraft.util.ChatComponentText("disconnected"));
        check(!(Boolean)active.invoke(session),"history session remains writable after disconnect with retained openContainer");
    }
    static void budget()throws Exception {
        setupServer();Object q=queue(TerminalNet.class,"PENDING");clear(q);
        TerminalNet.RequestHandler h=new TerminalNet.RequestHandler();
        for(int n=0;n<8;n++) {NetHandlerPlayServer c=bare(NetHandlerPlayServer.class);c.playerEntity=player;MessageContext ctx=context(c,Side.SERVER);for(int i=0;i<16;i++)h.onMessage(new PacketTerminalRequest(TerminalUiType.TCDS,0,1,2,3),ctx);}
        int before=size(q);check(before<=256,"global queue bound: "+before);
        new TerminalNet.ServerDrain().onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(before-size(q)<=64,"tick budget drained "+(before-size(q))+" requests");check(size(q)>0,"budget leaves later work queued");
        TerminalNet.clearServerState();check(size(q)==0,"shutdown clears remaining requests");
    }
    static void cleanup()throws Exception {
        setupServer();new WandNet.TargetHandler().onMessage(new WandNet.PacketTarget(),context(connection,Side.SERVER));new GTSRFXNet.HubBindHandler().onMessage(new HubBindMessage(),context(connection,Side.SERVER));
        Map sessions=(Map)get(null,WandNet.class,"SESSIONS");sessions.put(UUID.randomUUID(),new Object());
        WandNet.class.getMethod("clearServerState").invoke(null);GTSRFXNet.class.getMethod("clearServerState").invoke(null);
        check(size(queue(WandNet.class,"PENDING"))==0 && sessions.isEmpty(),"wand shutdown drops requests/session refs");check(size(queue(GTSRFXNet.class,"PENDING_BIND"))==0,"bind shutdown drops requests");
    }
    static class RecordingNetwork extends SimpleNetworkWrapper {
        int sent;RecordingNetwork(){super("unused");}
        @Override public void sendTo(IMessage message,EntityPlayerMP target){sent++;}
    }
    static void dimensionFx()throws Exception {
        setupServer();Class.forName(GTSRFXNet.class.getName());RecordingNetwork network=bare(RecordingNetwork.class);Field f=f(GTSRFXNet.class,"NETWORK");u.putObject(u.staticFieldBase(f),u.staticFieldOffset(f),network);
        EntityPlayerMP other=bare(EntityPlayerMP.class);other.worldObj=bare(SparseWorld.class);other.dimension=1;server.getConfigurationManager().playerEntityList.add(other);
        GTSRFXNet.sendAbsorb(world,0,0,0,1,1,1);check(network.sent==1,"absorb sent across worlds to "+network.sent+" players at same coordinates");
    }
    static void malformed(int index)throws Exception {
        IMessage[] packets={new PacketTerminalRequest(TerminalUiType.TCDS,0,1,2,3),new PacketTerminalAction(TerminalUiType.TCDS,0,1,2,3,1,new byte[]{1}),packet(1,1),new PacketOpenTerminalUi(TerminalUiType.TCDS,0,1,2,3,0)};
        IMessage p=packets[index];{io.netty.buffer.ByteBuf b=Unpooled.buffer();p.toBytes(b);b.writeByte(99);IMessage next=p.getClass().getConstructor().newInstance();next.fromBytes(b);check((Boolean)next.getClass().getMethod("isCorrupt").invoke(next),p.getClass().getSimpleName()+" accepted trailing bytes");}
    }
    public static void main(String[] args)throws Exception {
        u=(sun.misc.Unsafe)f(sun.misc.Unsafe.class,"theUnsafe").get(null);set(null,Loader.class,"instance",bare(Loader.class));
        Object side=Proxy.newProxyInstance(IFMLSidedHandler.class.getClassLoader(),new Class<?>[]{IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?Side.CLIENT:null);set(FMLCommonHandler.instance(),FMLCommonHandler.class,"sidedDelegate",side);
        switch(args[0]){case "terminal-flood":terminalFlood();break;case "bind-flood":bindFlood();break;case "wand-flood":wandFlood();break;case "disconnect":disconnected();break;case "connection":replacedConnection();break;case "bind-disconnect":bindDisconnected();break;case "authority":authority();break;case "client":staleClient();break;case "malformed-request":malformed(0);break;case "malformed-action":malformed(1);break;case "malformed-data":malformed(2);break;case "malformed-open":malformed(3);break;case "history":history();break;case "budget":budget();break;case "cleanup":cleanup();break;case "fx-dimension":dimensionFx();break;default:throw new AssertionError(args[0]);}
        System.out.println("PASS "+args[0]+" checks="+checks);
    }
    static class Configuration extends ServerConfigurationManager {Configuration(){super(null);}}
    static class SparseWorld extends WorldServer {
        int tileReads;IChunkProvider chunks;SparseWorld(){super(null,null,"fixture",0,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){tileReads++;return null;}
        @Override public IChunkProvider getChunkProvider(){return chunks;}
        @Override public boolean blockExists(int x,int y,int z){return true;}
        @Override public long getTotalWorldTime(){return 42;}
    }
    static class FakeServer extends MinecraftServer {
        WorldServer world;FakeServer(){super(null,null);}
        @Override public WorldServer worldServerForDimension(int d){return world;}
        @Override protected boolean startServer(){return false;}
        @Override public boolean canStructuresSpawn(){return false;}
        @Override public WorldSettings.GameType getGameType(){return null;}
        @Override public EnumDifficulty getDifficulty(){return null;}
        @Override public boolean isHardcore(){return false;}
        @Override public int getOpPermissionLevel(){return 0;}
        @Override public boolean isDedicatedServer(){return false;}
        @Override public boolean func_152363_m(){return false;}
        @Override public boolean isCommandBlockEnabled(){return false;}
        @Override public String shareToLAN(WorldSettings.GameType type,boolean cheats){return null;}
    }
}
