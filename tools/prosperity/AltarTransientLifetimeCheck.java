import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.world.World;
import com.miaokatze.gtsr.common.dimension.prosperity.altar.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.Unpooled;

/** Complete reading + packet sources, native identities; explicit no-op presentation/payment/world. */
public final class AltarTransientLifetimeCheck {
    private static int checks, failures;
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object invoke(String method,Class<?>[] types,Object...args)throws Exception{
        Method m=AltarReadingLifecycleCheck.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static void check(boolean condition,String name){checks++;if(!condition){failures++;System.err.println("FAIL "+name);}}
    @SuppressWarnings("unchecked") private static Map<EntityPlayerMP,?> sessions()throws Exception{return (Map<EntityPlayerMP,?>)field(SpacetimeAltarStory.class,"SESSIONS").get(null);}
    private static AltarReadingLifecycleCheck.Player player()throws Exception{return (AltarReadingLifecycleCheck.Player)invoke("player",new Class<?>[0]);}
    private static UUID read(AltarReadingLifecycleCheck.Player p)throws Exception{return (UUID)invoke("read",new Class<?>[]{AltarReadingLifecycleCheck.Player.class},p);}
    private static void reset()throws Exception{invoke("reset",new Class<?>[0]);((Map<?,?>)field(SpacetimeAltarStoryNetwork.class,"PENDING").get(null)).clear();}
    private static void queue(NetHandlerPlayServer handler,UUID nonce){SpacetimeAltarStoryNetwork.Activate request=new SpacetimeAltarStoryNetwork.Activate();request.fromBytes(Unpooled.buffer().writeLong(nonce.getMostSignificantBits()).writeLong(nonce.getLeastSignificantBits()));new SpacetimeAltarStoryNetwork.ActivateHandler().onMessage(request,new MessageContext(handler));}
    private static void tick(){new SpacetimeAltarStoryNetwork.Drain().tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));}
    private static boolean stop(Class<?> type)throws Exception{try{type.getMethod("resetServerState").invoke(null);return true;}catch(NoSuchMethodException missing){check(false,type.getSimpleName()+" exposes stop cleanup");return false;}}
    public static void main(String[]args)throws Exception{
        AltarReadingLifecycleCheck.main(new String[0]);
        AltarReadingLifecycleCheck.FixtureWorld world=(AltarReadingLifecycleCheck.FixtureWorld)field(AltarReadingLifecycleCheck.class,"world").get(null);
        TileSpacetimeAltar core=(TileSpacetimeAltar)field(AltarReadingLifecycleCheck.class,"core").get(null);
        AltarReadingLifecycleCheck.FakeServer server=(AltarReadingLifecycleCheck.FakeServer)field(AltarReadingLifecycleCheck.class,"server").get(null);
        reset();AltarReadingLifecycleCheck.Player p=player();UUID nonce=read(p);Object session=sessions().get(p);
        for(String member:new String[]{"player","connection","board","world"})check(field(session.getClass(),member).get(session) instanceof WeakReference,"idle reading value does not strongly retain "+member);
        world.time=1201;tick();check(sessions().isEmpty(),"idle expired lease retires without another activation/read");world.time=0;
        reset();p=player();read(p);((AltarReadingLifecycleCheck.Connection)p.playerNetServerHandler.netManager).open=false;tick();check(sessions().isEmpty(),"idle disconnected reading retires");
        reset();p=player();read(p);server.getConfigurationManager().playerEntityList.clear();tick();check(sessions().isEmpty(),"idle online-list removal retires reading");
        reset();p=player();nonce=read(p);queue(p.playerNetServerHandler,nonce);tick();check(core.activations==1,"valid queued reading activates exactly once");tick();check(core.activations==1,"drained nonce cannot replay");
        reset();AltarReadingLifecycleCheck.Player old=player();UUID oldNonce=read(old);queue(old.playerNetServerHandler,oldNonce);
        AltarReadingLifecycleCheck.Player replacement=player();replacement.setEntityId(old.getEntityId());UUID fresh=read(replacement);
        old.isDead=true;server.getConfigurationManager().playerEntityList.remove(old);queue(replacement.playerNetServerHandler,fresh);tick();
        check(core.activations==1,"equal numeric player IDs cannot discard replacement's valid queued activation");
        reset();p=player();nonce=read(p);queue(p.playerNetServerHandler,nonce);
        if(stop(SpacetimeAltarStory.class)){check(sessions().isEmpty(),"stop releases reading leases");stop(SpacetimeAltarStory.class);}
        if(stop(SpacetimeAltarStoryNetwork.class)){check(((Map<?,?>)field(SpacetimeAltarStoryNetwork.class,"PENDING").get(null)).isEmpty(),"stop releases pending activation actors");stop(SpacetimeAltarStoryNetwork.class);}
        if(failures!=0)throw new AssertionError(failures+"/"+checks+" altar transient assertions failed");
        System.out.println("PASS AltarTransientLifetimeCheck: "+checks+" assertions plus native R2 reading assertions");
    }
}
