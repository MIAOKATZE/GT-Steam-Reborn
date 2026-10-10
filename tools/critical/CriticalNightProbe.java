import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import com.google.gson.*;
import io.netty.buffer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.*;
import net.minecraftforge.event.world.WorldEvent;
import sun.misc.Unsafe;

/** Actual candidate message/clock/client drain with only client-world/time boundary replaced. */
public final class CriticalNightProbe {
 public static final class ClockWorld extends WorldClient {
  long total;GameRules rules;
  private ClockWorld(){super(null,null,0,EnumDifficulty.NORMAL,null);}
  public long getTotalWorldTime(){return total;}public GameRules getGameRules(){return rules;}
 }
 static Class<?> clock,message;static Object connectionToken;
 static Object msg(int dimension,boolean active,boolean night,boolean advancing,long time)throws Exception {
  Object m=message.getConstructor().newInstance();message.getField("dimension").setInt(m,dimension);message.getField("active").setBoolean(m,active);message.getField("night").setBoolean(m,night);message.getField("time").setLong(m,time);
  for(String flag:new String[]{"advancing","timeRunning"})try{message.getField(flag).setBoolean(m,advancing);}catch(NoSuchFieldException absent){}
  try{message.getField("clientConnection").set(m,connectionToken);}catch(NoSuchFieldException absent){}
  return m;
 }
 static void receive(World w,Object m)throws Exception{clock.getMethod("receiveClient",World.class,message).invoke(null,w,m);}
 static Long time(World w)throws Exception{return (Long)clock.getMethod("time",World.class).invoke(null,w);}
 static boolean night(World w)throws Exception{return (Boolean)clock.getMethod("isNight",World.class).invoke(null,w);}
 static ClockWorld world(Unsafe u,int dimension)throws Exception {
  ClockWorld w=(ClockWorld)u.allocateInstance(ClockWorld.class);w.rules=new GameRules();w.rules.setOrCreateGameRule("doDaylightCycle","true");w.total=1000;
  CriticalGUIProbe.field(World.class,"provider").set(w,u.allocateInstance(WorldProviderSurface.class));w.provider.dimensionId=dimension;CriticalGUIProbe.field(World.class,"isRemote").setBoolean(w,true);return w;
 }
 static void expect(boolean value,String label){if(!value)throw new IllegalStateException(label);}
 public static JsonObject run()throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);
  // FML's normal Loader constructor requires LaunchClassLoader; this isolated process has no game launcher.
  // Only activeModContainer() is needed by the real SimpleNetworkWrapper channel constructor.
  CriticalGUIProbe.field(cpw.mods.fml.common.Loader.class,"instance").set(null,u.allocateInstance(cpw.mods.fml.common.Loader.class));
  clock=Class.forName("com.miaokatze.gtsr.common.critical.CriticalNightClock");message=Class.forName(clock.getName()+"$TimeMessage");Class<?> client=Class.forName("com.miaokatze.gtsr.client.critical.CriticalNightClient");
  Method write=message.getMethod("toBytes",ByteBuf.class),read=message.getMethod("fromBytes",ByteBuf.class);int roundtrips=0;
  for(int bits=0;bits<8;bits++)for(long value:new long[]{-1,0,18000,Long.MAX_VALUE-100}){
   Object original=msg(bits%2==0?78:-1,(bits&1)!=0,(bits&2)!=0,(bits&4)!=0,value),decoded=message.getConstructor().newInstance();ByteBuf buffer=Unpooled.buffer();try{write.invoke(original,buffer);int bytes=buffer.readableBytes();read.invoke(decoded,buffer);expect(buffer.readableBytes()==0,"Message left unread payload");for(Field field:message.getFields())if(!Modifier.isStatic(field.getModifiers()))expect(Objects.equals(field.get(original),field.get(decoded)),"ByteBuf changed "+field.getName());expect(bytes>=15,"Message payload incomplete");roundtrips++;}finally{buffer.release();}
  }
  ClockWorld w=world(u,78);receive(w,msg(78,true,true,false,18000));w.total+=60;expect(time(w)==18000&&night(w),"Night clock advanced");
  receive(w,msg(78,true,false,true,6000));w.total+=7;expect(time(w)==6007&&!night(w),"Local time prediction failed");float angle=w.provider.calculateCelestialAngle(time(w),.5f);expect(Float.isFinite(angle),"Nonfinite celestial prediction");
  w.rules.setOrCreateGameRule("doDaylightCycle","false");receive(w,msg(78,true,false,false,6100));w.total+=40;expect(time(w)==6100&&!night(w),"Stopped/daylight false prediction advanced");
  receive(w,msg(78,false,true,false,18000));expect(time(w)==null&&!night(w),"Inactive clock overrode vanilla");
  receive(w,msg(77,true,true,false,18000));expect(time(w)==null,"Wrong dimension packet accepted");
  CriticalGUIProbe.field(World.class,"isRemote").setBoolean(w,false);receive(w,msg(78,true,true,false,18000));expect(time(w)==null,"Client message touched server world");CriticalGUIProbe.field(World.class,"isRemote").setBoolean(w,true);
  receive(w,msg(78,true,false,true,7000));ClockWorld replacement=world(u,78);expect(time(replacement)==null,"Old state leaked into same-dimension new world");receive(replacement,msg(78,true,false,true,8000));expect(time(w)==null&&time(replacement)==8000,"World identity failed to replace old state");
  ConcurrentLinkedQueue<Object> queue=new ConcurrentLinkedQueue<>();Minecraft mc=Minecraft.getMinecraft();
  connectionToken=u.allocateInstance(net.minecraft.client.network.NetHandlerPlayClient.class);CriticalGUIProbe.field(mc.thePlayer.getClass(),"sendQueue").set(mc.thePlayer,connectionToken);
  mc.theWorld=replacement;queue.add(msg(77,true,true,false,18000));client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,queue);expect(queue.isEmpty()&&time(replacement)==8000,"Crossdimension queued packet accepted");
  queue.add(msg(78,true,true,false,18000));client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,queue);expect(night(replacement)&&time(replacement)==18000,"Queued dimension clock not delivered");
  Class<?> events=Class.forName(clock.getName()+"$Events");Object event=events.getConstructor().newInstance();events.getMethod("unload",WorldEvent.Unload.class).invoke(event,new WorldEvent.Unload(replacement));expect(time(replacement)==null,"Unload retained client clock state");
  // Same connection/dimension uses the same server dimension clock; reconnect gets a distinct handler token.
  mc.theWorld=null;queue.add(msg(78,true,true,false,18000));client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,queue);int retainedWithoutWorld=queue.size();
  Object previousToken=connectionToken;connectionToken=u.allocateInstance(net.minecraft.client.network.NetHandlerPlayClient.class);CriticalGUIProbe.field(mc.thePlayer.getClass(),"sendQueue").set(mc.thePlayer,connectionToken);
  ClockWorld newer=world(u,78);mc.theWorld=newer;client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,queue);expect(!night(newer)&&time(newer)==null,"Reconnected world accepted old connection message");
  Class<?> handler=Class.forName(clock.getName()+"$TimeHandler"),context=Class.forName("cpw.mods.fml.common.network.simpleimpl.MessageContext");Object h=handler.getConstructor().newInstance();Constructor<?> cc=context.getDeclaredConstructor(net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);cc.setAccessible(true);Object currentContext=cc.newInstance(connectionToken,cpw.mods.fml.relauncher.Side.CLIENT),oldContext=cc.newInstance(previousToken,cpw.mods.fml.relauncher.Side.CLIENT);
  Method onMessage=handler.getMethod("onMessage",message,context);ConcurrentLinkedQueue<?> incoming=(ConcurrentLinkedQueue<?>)CriticalGUIProbe.field(clock,"INCOMING").get(null);
  onMessage.invoke(h,msg(78,true,true,false,18000),currentContext);Object newest=msg(78,true,false,true,10000);onMessage.invoke(h,newest,currentContext);expect(incoming.size()==1,"Same dimension did not retain latest message");expect(message.getField("clientConnection").get(newest)==connectionToken,"Actual handler lost connection token");
  client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,incoming);newer.total+=5;expect(time(newer)==10005&&!night(newer),"Current connection latest prediction failed");
  onMessage.invoke(h,msg(78,true,false,false,12000),currentContext);onMessage.invoke(h,msg(78,true,true,false,18000),oldContext);expect(incoming.size()==2,"Old callback removed current connection queued packet");client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,incoming);expect(time(newer)==12000&&!night(newer),"Old callback displaced current connection time");
  events.getMethod("unload",WorldEvent.Unload.class).invoke(event,new WorldEvent.Unload(newer));onMessage.invoke(h,msg(78,true,true,false,18000),oldContext);client.getMethod("drain",ConcurrentLinkedQueue.class).invoke(null,incoming);expect(time(newer)==null&&!night(newer),"Post-unload old callback accepted");
  onMessage.invoke(h,msg(78,true,false,false,10100),currentContext);events.getMethod("unload",WorldEvent.Unload.class).invoke(event,new WorldEvent.Unload(newer));expect(incoming.isEmpty(),"Unload retained actual incoming queue");
  JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("clockCodeSource",clock.getProtectionDomain().getCodeSource().getLocation().toString());result.addProperty("clientCodeSource",client.getProtectionDomain().getCodeSource().getLocation().toString());result.addProperty("byteBufRoundTrips",roundtrips);result.addProperty("clientClockChecks",21);result.addProperty("predictedCelestialAngle",angle);result.addProperty("retainedWithoutWorld",retainedWithoutWorld);result.addProperty("lateOldConnectionRejected",true);result.addProperty("latestMessageCoalescing",true);result.addProperty("oldCallbackCannotDisplaceCurrentQueue",true);result.addProperty("lateQueueBoundary","Same-dimension reconnect/old handler callbacks rejected by real production connection token check; same live connection/dimension uses authoritative shared dimension clock.");result.addProperty("scope","Actual candidate TimeMessage ByteBuf, receiveClient/time/isNight, TimeHandler.onMessage, CriticalNightClient.drain and unload handler; fixture WorldClient time/provider/gameRules and NetHandlerPlayClient identities; only FML Loader startup allocated without LaunchClassLoader to initialize real SimpleNetworkWrapper. Celestial angle prediction uses real WorldProviderSurface; actual World mixin weaving remains native validation.");return result;
 }
}
