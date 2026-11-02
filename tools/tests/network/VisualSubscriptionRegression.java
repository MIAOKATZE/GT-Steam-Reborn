import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.embedded.EmbeddedChannel;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualNetwork;

/** Actual packet admission/drain with native Forge/Netty and isolated live-player state; no TCP/game. */
public final class VisualSubscriptionRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("VisualSubscriptionRuntime", args[0]);
    }
}

class VisualSubscriptionRuntime {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static Field field(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static void set(Object target,Class<?> type,String name,Object value)throws Exception {field(type,name).set(target,value);}
    static <T>T bare(Class<T> type)throws Exception {return (T)unsafe.allocateInstance(type);}
    static void check(boolean value,String name) {checks++; if(!value)throw new AssertionError(name);System.out.println("CHECK "+name+" PASS");}
    static MessageContext context(NetHandlerPlayServer connection)throws Exception {
        Constructor<MessageContext> c=MessageContext.class.getDeclaredConstructor(INetHandler.class,Side.class);
        c.setAccessible(true);return c.newInstance(connection,Side.SERVER);
    }
    static EntityPlayerMP player(SparseWorld world,Configuration config)throws Exception {
        EntityPlayerMP p=bare(EntityPlayerMP.class);p.worldObj=world;p.dimension=0;
        NetHandlerPlayServer connection=bare(NetHandlerPlayServer.class);connection.playerEntity=p;p.playerNetServerHandler=connection;
        NetworkManager manager=new NetworkManager(false);
        set(manager,NetworkManager.class,"channel",new EmbeddedChannel(new io.netty.channel.ChannelInboundHandlerAdapter()));
        set(connection,NetHandlerPlayServer.class,"netManager",manager);config.playerEntityList.add(p);return p;
    }
    static MachineVisualNetwork.Subscribe request(int x)throws Exception {
        MachineVisualNetwork.Subscribe r=new MachineVisualNetwork.Subscribe();
        set(r,r.getClass(),"x",x);set(r,r.getClass(),"y",96);set(r,r.getClass(),"nonce",1L);return r;
    }
    public static void run(String mode)throws Exception {
        unsafe=Issue37Runtime.bootstrap("5.09.54.265");
        SparseWorld world=bare(SparseWorld.class);world.reads=new ArrayList<>();
        FakeServer server=bare(FakeServer.class);server.world=world;set(null,MinecraftServer.class,"mcServer",server);
        Configuration config=bare(Configuration.class);set(config,ServerConfigurationManager.class,"playerEntityList",new ArrayList<>());
        for(Field f:MinecraftServer.class.getDeclaredFields()) if(f.getType()==ServerConfigurationManager.class){f.setAccessible(true);f.set(server,config);}
        EntityPlayerMP a=player(world,config), b=player(world,config);
        MachineVisualNetwork.SubscribeHandler handler=new MachineVisualNetwork.SubscribeHandler();
        MessageContext ca=context(a.playerNetServerHandler), cb=context(b.playerNetServerHandler);
        Object queue=field(MachineVisualNetwork.class,"QUEUE").get(null);
        Method size=queue.getClass().getMethod("size");
        for(int i=0;i<512;i++)handler.onMessage(request(11),ca);
        int retained=(Integer)size.invoke(queue);
        handler.onMessage(request(22),cb);
        int after=(Integer)size.invoke(queue);
        System.out.println("ADMISSION flood="+retained+" afterFriendly="+after);
        if(mode.equals("red")) {
            check(retained==256 && after==256,"baseline-one-owner-fills-global-and-rejects-friendly");
            return;
        }
        check(retained==16 && after==17,"per-connection-bound-admits-friendly");
        Constructor<MachineVisualNetwork> c=MachineVisualNetwork.class.getDeclaredConstructor();c.setAccessible(true);
        MachineVisualNetwork drain=c.newInstance();drain.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.reads.contains(22),"friendly-dispatched-on-first-END");
        check((Integer)size.invoke(queue)==0,"bounded-drain-completes");
        world.reads.clear();handler.onMessage(request(33),ca);
        NetHandlerPlayServer replacement=bare(NetHandlerPlayServer.class);replacement.playerEntity=a;
        set(replacement,NetHandlerPlayServer.class,"netManager",a.playerNetServerHandler.netManager);a.playerNetServerHandler=replacement;
        drain.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.reads.isEmpty(),"old-connection-rejected-before-world-target-read");
        handler.onMessage(request(35),ca);drain.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.reads.isEmpty(),"late-old-handler-cannot-capture-replacement-connection");
        handler.onMessage(request(44),context(replacement));drain.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check(world.reads.contains(44),"replacement-connection-has-independent-admission");
        System.out.println("RESULT PASS checks="+checks+" source="+MachineVisualNetwork.class.getProtectionDomain().getCodeSource().getLocation());
    }
    static class Configuration extends ServerConfigurationManager {Configuration(MinecraftServer s){super(s);}}
    static class SparseWorld extends WorldServer {
        List<Integer> reads;
        SparseWorld(){super(null,null,"fixture",0,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){reads.add(x);return null;}
        @Override public boolean blockExists(int x,int y,int z){return true;}
    }
    static class FakeServer extends MinecraftServer {
        WorldServer world;FakeServer(){super(null,null);}
        @Override public WorldServer worldServerForDimension(int d){return world;}
        @Override protected boolean startServer(){return false;}
        @Override public boolean canStructuresSpawn(){return false;}
        @Override public WorldSettings.GameType getGameType(){return null;}
        @Override public EnumDifficulty func_147135_j(){return null;}
        @Override public boolean isHardcore(){return false;}
        @Override public int getOpPermissionLevel(){return 0;}
        @Override public boolean isDedicatedServer(){return false;}
        @Override public boolean func_152363_m(){return false;}
        @Override public boolean isCommandBlockEnabled(){return false;}
        @Override public String shareToLAN(WorldSettings.GameType t,boolean c){return null;}
    }
}
