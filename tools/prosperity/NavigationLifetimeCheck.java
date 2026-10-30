import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.*;
import net.minecraft.item.*;
import net.minecraft.util.IChatComponent;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.*;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.*;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.commands.RuinLocateCommand;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.Unpooled;

/** Actual MC equality/player/handler/server identities; explicit FML packet and target-search fixtures. */
public final class NavigationLifetimeCheck {
    private static sun.misc.Unsafe unsafe;
    private static int checks, failures, ids, completions;
    private static FixtureWorld world;
    private static FakeServer server;
    private static BeaconNavigationNetwork beacon;
    private static NavigatorNetwork navigator;
    public static final class Player extends EntityPlayerMP {
        UUID uuid;
        Player() { super(null, null, null, null); }
        @Override public boolean isEntityAlive() { return !isDead; }
        @Override public UUID getUniqueID() { return uuid; }
        @Override public String getCommandSenderName() { return "fixture"; }
        @Override public void addChatMessage(IChatComponent message) {}
    }
    public static final class Connection extends NetworkManager {
        boolean open = true;
        Connection() { super(false); }
        @Override public boolean isChannelOpen() { return open; }
    }
    public static final class FixtureWorld extends WorldServer {
        FixtureWorld() { super(null, null, "fixture", 0, null, null); }
        @Override public long getSeed() { return 17; }
    }
    public static final class Configuration extends ServerConfigurationManager { Configuration() { super(null); } }
    public static final class FakeServer extends MinecraftServer {
        FakeServer() { super(null, null); }
        @Override public WorldServer worldServerForDimension(int dim) { return world; }
        @Override protected boolean startServer() { return false; }
        @Override public boolean canStructuresSpawn() { return false; }
        @Override public WorldSettings.GameType getGameType() { return null; }
        @Override public EnumDifficulty getDifficulty() { return null; }
        @Override public boolean isHardcore() { return false; }
        @Override public int getOpPermissionLevel() { return 0; }
        @Override public boolean isDedicatedServer() { return false; }
        @Override public boolean func_152363_m() { return false; }
        @Override public boolean isCommandBlockEnabled() { return false; }
        @Override public String shareToLAN(WorldSettings.GameType type, boolean cheats) { return null; }
    }
    private static Field field(Class<?> cls, String name) throws Exception { Field f=cls.getDeclaredField(name);f.setAccessible(true);return f; }
    private static void put(Object owner, Class<?> cls, String name, Object value) throws Exception { unsafe.putObject(owner, unsafe.objectFieldOffset(field(cls,name)),value); }
    private static Object get(Class<?> cls, String name) throws Exception { return field(cls,name).get(null); }
    @SuppressWarnings("unchecked") private static Map<EntityPlayerMP, ?> sessions() throws Exception { return (Map<EntityPlayerMP,?>)get(BeaconNavigationNetwork.class,"SESSIONS"); }
    private static Player player() throws Exception {
        Player p=(Player)unsafe.allocateInstance(Player.class); p.setEntityId(++ids);p.uuid=new UUID(17,ids);p.worldObj=world;p.dimension=world.provider.dimensionId;p.ticksExisted=40;
        p.inventory=new InventoryPlayer(p);p.inventory.mainInventory[0]=new ItemStack(new SpacetimeAnchorBeacon());
        p.inventory.mainInventory[1]=new ItemStack(LoreRegistry.RELICS.get(NavigatorNetwork.RELIC_ID));
        NetHandlerPlayServer h=(NetHandlerPlayServer)unsafe.allocateInstance(NetHandlerPlayServer.class);h.playerEntity=p;put(h,NetHandlerPlayServer.class,"netManager",new Connection());p.playerNetServerHandler=h;
        server.getConfigurationManager().playerEntityList.add(p);return p;
    }
    private static void check(boolean value,String message){checks++;if(!value){failures++;System.err.println("FAIL "+message);}}
    private static void tickBeacon(){beacon.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));}
    private static void tickNavigator(){navigator.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));}
    private static void hello(Player p,long nonce){BeaconNavigationNetwork.Hello h=new BeaconNavigationNetwork.Hello();h.fromBytes(Unpooled.buffer().writeLong(nonce).writeInt(p.dimension));new BeaconNavigationNetwork.HelloHandler().onMessage(h,new MessageContext(p.playerNetServerHandler));}
    private static void query(Player p){NavigatorNetwork.Query q=new NavigatorNetwork.Query();q.fromBytes(Unpooled.buffer().writeInt(0).writeInt(1));new NavigatorNetwork.QueryHandler().onMessage(q,new MessageContext(p.playerNetServerHandler));}
    private static void reset() throws Exception {
        for(Class<?> cls:new Class<?>[]{BeaconNavigationNetwork.class,NavigatorNetwork.class}){
            ((Collection<?>)get(cls,"QUEUE")).clear();((java.util.concurrent.atomic.AtomicInteger)get(cls,"QUEUED")).set(0);
        }
        sessions().clear();((Collection<?>)get(NavigatorNetwork.class,"ACTIVE")).clear();((Map<?,?>)get(NavigatorNetwork.class,"LAST_QUERY")).clear();((Map<?,?>)get(NavigatorNetwork.class,"PENDING_GIFTS")).clear();
        server.getConfigurationManager().playerEntityList.clear();SimpleNetworkWrapper.sent.clear();SimpleNetworkWrapper.receivers.clear();RuinLocateCommand.steps=0;RuinLocateCommand.begins=0;completions=0;
        put(world,World.class,"provider",new WorldProviderSurface());
    }
    private static boolean stop(Class<?> type) throws Exception {
        try { type.getMethod("resetServerState").invoke(null); return true; }
        catch (NoSuchMethodException missing) { check(false, type.getSimpleName()+" has stop hook"); return false; }
    }
    private static void prosperity(Player p) throws Exception { put(world,World.class,"provider",new WorldProviderProsperityRuins());p.dimension=world.provider.dimensionId; }
    public static void main(String[] args) throws Exception {
        unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);world=(FixtureWorld)unsafe.allocateInstance(FixtureWorld.class);server=(FakeServer)unsafe.allocateInstance(FakeServer.class);field(MinecraftServer.class,"mcServer").set(null,server);
        Configuration cfg=(Configuration)unsafe.allocateInstance(Configuration.class);put(cfg,ServerConfigurationManager.class,"playerEntityList",new ArrayList<>());server.setConfigManager(cfg);
        Constructor<BeaconNavigationNetwork> bc=BeaconNavigationNetwork.class.getDeclaredConstructor();bc.setAccessible(true);beacon=bc.newInstance();Constructor<NavigatorNetwork> nc=NavigatorNetwork.class.getDeclaredConstructor();nc.setAccessible(true);navigator=nc.newInstance();
        reset();Player old=player();hello(old,1);tickBeacon();check(!sessions().isEmpty(),"live beacon hello establishes session");check(SimpleNetworkWrapper.sent.size()==2,"live hello ACK and target remain available");
        Player replacement=player();replacement.setEntityId(old.getEntityId());replacement.uuid=old.uuid;replacement.playerNetServerHandler=old.playerNetServerHandler;replacement.playerNetServerHandler.playerEntity=replacement;old.isDead=true;server.getConfigurationManager().playerEntityList.remove(0);
        check(old.equals(replacement),"native numeric-ID equality aliases respawn");SimpleNetworkWrapper.sent.clear();hello(replacement,2);tickBeacon();
        check(sessions().size()==1&&sessions().keySet().iterator().next()==replacement,"new lifetime replaces actual weak key");
        check(SimpleNetworkWrapper.sent.size()==2,"respawn receives ACK plus target, not ACK then silent session removal");
        SimpleNetworkWrapper.sent.clear();replacement.ticksExisted=80;tickBeacon();check(SimpleNetworkWrapper.sent.size()==1,"fresh session continues navigation after client stops hello retries");

        reset();old=player();hello(old,3);NetHandlerPlayServer captured=old.playerNetServerHandler;replacement=player();replacement.setEntityId(old.getEntityId());replacement.playerNetServerHandler=captured;captured.playerEntity=replacement;server.getConfigurationManager().playerEntityList.remove(0);tickBeacon();check(SimpleNetworkWrapper.sent.isEmpty()&&sessions().isEmpty(),"queued old entity cannot install navigation under rebound handler");
        reset();old=player();hello(old,4);old.playerNetServerHandler=player().playerNetServerHandler;old.playerNetServerHandler.playerEntity=old;tickBeacon();check(SimpleNetworkWrapper.sent.isEmpty(),"hello cannot cross captured connection lifetime");
        reset();old=player();hello(old,5);server.getConfigurationManager().playerEntityList.clear();tickBeacon();check(SimpleNetworkWrapper.sent.isEmpty(),"offline open-channel player cannot obtain beacon session");
        reset();old=player();hello(old,6);tickBeacon();SimpleNetworkWrapper.sent.clear();old.playerNetServerHandler.playerEntity=player();tickBeacon();check(sessions().isEmpty()&&SimpleNetworkWrapper.sent.isEmpty(),"active session retires when handler changes owner");
        reset();old=player();hello(old,7);tickBeacon();Object session=sessions().values().iterator().next();Field connection=session.getClass().getDeclaredField("connection");connection.setAccessible(true);check(connection.get(session) instanceof java.lang.ref.WeakReference,"weak session value cannot retain player through a strong connection cycle");

        reset();old=player();prosperity(old);query(old);tickNavigator();check(RuinLocateCommand.begins==1&&RuinLocateCommand.steps==1,"live navigator query completes once");check(SimpleNetworkWrapper.sent.size()==1,"live navigator response preserved");
        reset();old=player();prosperity(old);query(old);((Connection)old.playerNetServerHandler.netManager).open=false;tickNavigator();check(RuinLocateCommand.begins==0&&RuinLocateCommand.steps==0,"closed-channel queued navigator cannot start work");
        reset();old=player();prosperity(old);query(old);captured=old.playerNetServerHandler;replacement=player();replacement.playerNetServerHandler=captured;captured.playerEntity=replacement;server.getConfigurationManager().playerEntityList.remove(0);tickNavigator();check(RuinLocateCommand.begins==0,"queued navigator old player rejected after handler rebound");
        reset();old=player();prosperity(old);query(old);old.playerNetServerHandler=player().playerNetServerHandler;old.playerNetServerHandler.playerEntity=old;tickNavigator();check(RuinLocateCommand.begins==0,"navigator queue binds original connection");
        reset();old=player();prosperity(old);boolean admitted=NavigatorNetwork.submitLocate(old,new RuinLocateCommand.Lookup(),x->completions++);check(admitted,"live server-command locate admission preserved");((Connection)old.playerNetServerHandler.netManager).open=false;tickNavigator();check(RuinLocateCommand.steps==0&&completions==0,"disconnect retires active locate before lookup/completion");
        reset();old=player();prosperity(old);NavigatorNetwork.submitLocate(old,new RuinLocateCommand.Lookup(),x->completions++);server.getConfigurationManager().playerEntityList.clear();tickNavigator();check(RuinLocateCommand.steps==0&&completions==0,"removed online player cannot complete active command work");
        reset();old=player();prosperity(old);((Connection)old.playerNetServerHandler.netManager).open=false;check(!NavigatorNetwork.submitLocate(old,new RuinLocateCommand.Lookup(),x->completions++),"offline locate cannot reserve bounded job slot");
        reset();old=player();prosperity(old);query(old);tickNavigator();
        replacement=player();replacement.setEntityId(old.getEntityId());replacement.uuid=old.uuid;
        replacement.playerNetServerHandler=old.playerNetServerHandler;replacement.playerNetServerHandler.playerEntity=replacement;
        server.getConfigurationManager().playerEntityList.remove(0);query(replacement);tickNavigator();
        check(RuinLocateCommand.begins==2,"respawn query does not inherit previous entity throttle");
        check(((Map<?,?>)get(NavigatorNetwork.class,"LAST_QUERY")).keySet().iterator().next()==replacement,"query stamp replaces actual weak key");

        reset();old=player();hello(old,8);tickBeacon();hello(old,9);
        if(stop(BeaconNavigationNetwork.class)) {
            check(sessions().isEmpty(),"beacon stop releases session/world leases");
            check(((Collection<?>)get(BeaconNavigationNetwork.class,"QUEUE")).isEmpty(),"beacon stop discards queued actors");
            check(((java.util.concurrent.atomic.AtomicInteger)get(BeaconNavigationNetwork.class,"QUEUED")).get()==0,"beacon stop resets queue quota");
            stop(BeaconNavigationNetwork.class);hello(old,10);tickBeacon();check(sessions().size()==1,"beacon can resume after idempotent reset");
        }
        reset();old=player();prosperity(old);query(old);tickNavigator();query(old);
        NavigatorNetwork.submitLocate(old,new RuinLocateCommand.Lookup(),x->completions++);
        if(stop(NavigatorNetwork.class)) {
            check(((Collection<?>)get(NavigatorNetwork.class,"QUEUE")).isEmpty(),"navigator stop discards queued actors");
            check(((java.util.concurrent.atomic.AtomicInteger)get(NavigatorNetwork.class,"QUEUED")).get()==0,"navigator stop resets queue quota");
            check(((Collection<?>)get(NavigatorNetwork.class,"ACTIVE")).isEmpty(),"navigator stop releases active world jobs/callbacks");
            check(((Map<?,?>)get(NavigatorNetwork.class,"LAST_QUERY")).isEmpty(),"navigator stop clears query leases");
            check(((Map<?,?>)get(NavigatorNetwork.class,"PENDING_GIFTS")).isEmpty(),"navigator stop clears gift witnesses");
            stop(NavigatorNetwork.class);query(old);tickNavigator();check(RuinLocateCommand.begins==2,"navigator can resume after idempotent reset");
        }
        if(failures!=0)throw new AssertionError(failures+"/"+checks+" navigation lifetime assertions failed");
        System.out.println("PASS NavigationLifetimeCheck: "+checks+" assertions; native players/connections and fixture searches/network");
    }
}
