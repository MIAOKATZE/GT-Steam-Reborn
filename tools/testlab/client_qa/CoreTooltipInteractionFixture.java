package audit;

import static audit.Mui2ClientQa.*;

import com.google.gson.*;
import cpw.mods.fml.common.eventhandler.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraftforge.client.event.GuiScreenEvent;

/** Explicit owned full-client probe. Never included in either production or testlab mod jars. */
public final class CoreTooltipInteractionFixture {
    public interface Hooks { void complete(Map<String,Object> receipt, Throwable failure); void screenshot(String name); }
    private final Hooks hooks;
    private final Path save, output;
    private final JsonObject plan;
    private final Map<String,Object> receipt = new LinkedHashMap<>();
    private final List<Map<String,Object>> checks = new ArrayList<>(), renders = new ArrayList<>();
    private final Map<Integer,List<Map<String,Object>>> componentOrders = new LinkedHashMap<>();
    private final Object[] original = new Object[3], stacks = new Object[3];
    private volatile Object inventory;
    private volatile boolean ready, restored, cancel;
    private volatile Throwable failure;
    private Object mc, gui, pinBinding;
    private final Map<Object,Integer> rebound = new LinkedHashMap<>();
    private int oldPinCode, phase, frames, ticks, hoverX, hoverY, target, oldMouseX, oldMouseY;
    private boolean opened, finished, pointerOwned, away, configured;
    private final long started = System.nanoTime();
    private static final String CONTROL="com.miaokatze.miaokatzecore.client.tooltip.TooltipController";
    private static final String PIN="com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning";

    public CoreTooltipInteractionFixture(Path path, Hooks hooks) throws Exception {
        this.hooks=hooks;
        plan=new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
        require("core-tooltip-interaction-v1".equals(plan.get("scriptOnly").getAsString()),"Explicit profile required");
        save=Paths.get(System.getProperty("miaotest.host.worldPath")).toAbsolutePath().normalize();
        output=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();
        JsonObject owner=new JsonParser().parse(new String(Files.readAllBytes(save.resolve(".miaotest-host-owner.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        require(System.getProperty("miaotest.host.runId").equals(owner.get("runId").getAsString()),"Exact owned world required");
        receipt.put("runId",System.getProperty("miaotest.host.runId"));receipt.put("checks",checks);receipt.put("renders",renders);receipt.put("componentOrders",componentOrders);
        receipt.put("input","Real private-desktop client; natural inventory hover and actual Forge/MUI input events with scoped lwjgl3ify snapshots; OS input NOT_RUN");
        receipt.put("cleanupPending",true);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private void check(String name,boolean ok,Object detail){Map<String,Object> c=new LinkedHashMap<>();c.put("name",name);c.put("pass",ok);c.put("detail",detail);checks.add(c);System.out.println("CORE_TOOLTIP_QA "+new Gson().toJson(c));require(ok,name+": "+detail);}
    public void cancel(Throwable t){while(t instanceof java.lang.reflect.InvocationTargetException&&t.getCause()!=null)t=t.getCause();if(failure==null)failure=t;cancel=true;}
    public boolean completeForExit(){return finished;}
    public void serverStopping(Object server){if(!restored)cancel(new IllegalStateException("Stopped before inventory restoration"));}
    public void serverTick(Object server){try{
        if(cancel){if(inventory!=null){for(int i=0;i<3;i++)call(inventory,"setInventorySlotContents|func_70299_a",i,original[i]);call(inventory,"markDirty|func_70296_d");}restored=true;return;}
        if(ready)return;
        List<?> actors=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(actors.isEmpty())return;
        require(actors.size()==1,"Single owned actor");Object actor=actors.get(0),world=field(actor,"worldObj|field_70170_p");
        Path actual=((java.io.File)call(call(world,"getSaveHandler|func_72860_G"),"getWorldDirectory|func_75765_b")).toPath().toAbsolutePath().normalize();require(save.equals(actual),"Refuse other world");
        Map<String,Object> artifacts=new LinkedHashMap<>();Map<?,?> mods=(Map<?,?>)call(call(type("cpw.mods.fml.common.Loader"),"instance"),"getIndexedModList");
        for(Map.Entry<String,JsonElement> e:plan.getAsJsonObject("expectedArtifacts").entrySet()){
            Object mod=mods.get(e.getKey());require(mod!=null,"Missing real mod "+e.getKey());Path jar=((java.io.File)call(mod,"getSource")).toPath();String hash=sha(jar);require(hash.equals(e.getValue().getAsString()),"Actual loaded SHA "+e.getKey());
            Map<String,Object> info=new LinkedHashMap<>();info.put("path",jar.toString());info.put("sha256",hash);info.put("version",call(mod,"getVersion"));artifacts.put(e.getKey(),info);
        }receipt.put("artifacts",artifacts);
        require(!Boolean.TRUE.equals(field(field(actor,"capabilities|field_71075_bZ"),"isCreativeMode|field_75098_d")),"Survival actor");
        inventory=field(actor,"inventory|field_71071_by");
        stacks[0]=type("net.minecraft.item.ItemStack").getConstructor(type("net.minecraft.item.Item")).newInstance(field(type("net.minecraft.init.Items"),"stick|field_151055_y"));
        stacks[1]=call(field(type("com.miaokatze.gtsr.common.api.enums.GTSRItemList"),"CriticalSolar"),"get",1L,new Object[0]);
        Object projector=field(type("com.miaokatze.gtmb.register.ProjectorRegistry"),"projector");stacks[2]=type("net.minecraft.item.ItemStack").getConstructor(type("net.minecraft.item.Item")).newInstance(projector);
        for(int i=0;i<3;i++){original[i]=call(inventory,"getStackInSlot|func_70301_a",i);if(original[i]!=null)original[i]=call(original[i],"copy|func_77946_l");call(inventory,"setInventorySlotContents|func_70299_a",i,stacks[i]);}call(inventory,"markDirty|func_70296_d");ready=true;
    }catch(Throwable t){cancel(t);}}
    public void clientTick(Object value)throws Exception{
        mc=value;if(finished)return;
        if(!opened){require(field(mc,"theWorld|field_71441_e")==null,"Refuse existing world");Object gt=Enum.valueOf((Class)type("net.minecraft.world.WorldSettings$GameType"),"SURVIVAL");
            Object settings=type("net.minecraft.world.WorldSettings").getConstructor(long.class,type("net.minecraft.world.WorldSettings$GameType"),boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(20261117L,gt,false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));call(mc,"launchIntegratedServer|func_71371_a",save.getFileName().toString(),"Owned Core tooltip interaction",settings);opened=true;return;}
        if(cancel){restorePointer();if(!restored)return;restoreBindings();if(pinBinding!=null)call(pinBinding,"setKeyCode|func_151462_b",oldPinCode);receipt.put("slotRestored",true);receipt.put("cleanupPending",false);receipt.put("status",failure==null&&phase==99?"PASS":"FAIL");if(failure!=null){receipt.put("failure",failure.toString());List<String> trace=new ArrayList<>();for(Throwable c=failure;c!=null;c=c.getCause()){trace.add(c.toString());for(StackTraceElement at:c.getStackTrace())trace.add(at.toString());}receipt.put("stack",trace);}finished=true;Files.write(output.resolve("core-tooltip-interaction.json"),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);hooks.complete(receipt,failure);return;}
        if(System.nanoTime()-started>600_000_000_000L){cancel(new IllegalStateException("Bounded full-client tooltip deadline phase="+phase));return;}
        if(!ready||field(mc,"thePlayer|field_71439_g")==null)return;
        Object player=field(mc,"thePlayer|field_71439_g");if(((Number)field(player,"ticksExisted|field_70173_aa")).intValue()<80)return;
        if(!configured){Object settings=field(mc,"gameSettings|field_71474_y");set(settings,"limitFramerate|field_74350_i",30);call(type("org.lwjglx.opengl.Display"),"setDisplayMode",type("org.lwjglx.opengl.DisplayMode").getConstructor(int.class,int.class).newInstance(2560,1440));call(mc,"resize|func_71370_a",2560,1440);configured=true;}
        if(gui==null){open(player,0);return;}
        if(++ticks>1600)cancel(new IllegalStateException("Natural tooltip timeout phase="+phase+" renders="+frames));
        if(phase==4&&field(mc,"currentScreen|field_71462_r")==null){check("ordinary.screen-close-clears",!pinned(),pinState());open(player,1);next(5);}
        if(phase==101&&field(mc,"currentScreen|field_71462_r")==null){check("gtmb.normal-play-clears",!pinned(),pinState());open(player,2);next(102);}
        if(phase==13){Object current=field(mc,"currentScreen|field_71462_r");check("url.native-confirmation",current!=null&&type("net.minecraft.client.gui.GuiConfirmOpenLink").isInstance(current),current==null?"null":current.getClass().getName());List<String> strings=new ArrayList<>();for(Class<?> c=current.getClass();c!=null;c=c.getSuperclass())for(java.lang.reflect.Field f:c.getDeclaredFields())if(f.getType()==String.class){f.setAccessible(true);strings.add(String.valueOf(f.get(current)));}check("url.confirmation-address",strings.contains("https://miaokatze.com/blueprints"),strings);check("url.confirmation-retains-pin",pinned(),pinState());receipt.put("externalBrowser","NOT_OPENED: native confirmation declined with Esc");next(131);}
        else if(phase==131&&ticks>15){hooks.screenshot("gtmb-url-confirmation");next(132);}
        else if(phase==132&&ticks>15){Object current=field(mc,"currentScreen|field_71462_r");call(current,"keyTyped|func_73869_a",(char)0,1);check("url.escape-native-callback",field(mc,"currentScreen|field_71462_r")==gui,"Actual GuiConfirmOpenLink keyTyped Esc to parent");next(133);}
        else if(phase==134&&field(mc,"currentScreen|field_71462_r")==null){check("gtmb.final-play-clears",!pinned(),pinState());open(player,0);next(14);}
        else if(phase==16&&field(mc,"currentScreen|field_71462_r")==null){check("final.screen-close-clears",!pinned(),pinState());phase=99;cancel=true;}
    }
    private void open(Object player,int slot)throws Exception{target=slot;away=false;gui=type("net.minecraft.client.gui.inventory.GuiInventory").getConstructor(type("net.minecraft.entity.player.EntityPlayer")).newInstance(player);call(mc,"displayGuiScreen|func_147108_a",gui);frames=ticks=0;}
    private void next(int n){phase=n;frames=ticks=0;}
    private static boolean pinned()throws Exception{return Boolean.TRUE.equals(call(type(CONTROL),"pinned"));}
    private Map<String,Object> pinState()throws Exception{Map<String,Object> r=new LinkedHashMap<>();r.put("pinned",pinned());r.put("key",call(type(PIN),"key"));r.put("x",call(type(PIN),"x"));r.put("y",call(type(PIN),"y"));return r;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(GuiScreenEvent.DrawScreenEvent.Pre e){if(cancel||gui==null||e.gui!=gui)return;try{restorePointer();int w=((Number)field(gui,"width|field_146294_l")).intValue(),h=((Number)field(gui,"height|field_146295_m")).intValue();hoverX=away?12:((Number)field(gui,"guiLeft|field_147003_i")).intValue()+16+target*18;hoverY=away?12:((Number)field(gui,"guiTop|field_147009_r")).intValue()+150;set(e,"mouseX",hoverX);set(e,"mouseY",hoverY);Class<?> m=type("org.lwjglx.input.Mouse");oldMouseX=((Number)field(m,"x")).intValue();oldMouseY=((Number)field(m,"y")).intValue();int pw=((Number)field(mc,"displayWidth|field_71443_c")).intValue(),ph=((Number)field(mc,"displayHeight|field_71440_d")).intValue();set(m,"x",hoverX*pw/w);set(m,"y",(h-hoverY-1)*ph/h);pointerOwned=true;}catch(Throwable t){cancel(t);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void after(GuiScreenEvent.DrawScreenEvent.Post e){if(cancel||gui==null||e.gui!=gui)return;try{if(frames>=6)step();restorePointer();}catch(Throwable t){cancel(t);}}
    @SubscribeEvent public void painted(cpw.mods.fml.common.eventhandler.Event e){if(cancel||gui==null||!e.getClass().getName().equals("com.slprime.chromatictooltips.event.RenderTooltipEvent"))return;try{Object context=field(e,"context"),item=call(context,"getItem");if(item==null)return;Object api=type("com.miaokatze.miaokatzecore.api.tooltip.TooltipPages"),id=call(api,"identity",item);if(!id.equals(call(api,"identity",stacks[target])))return;Map<String,Object> r=new LinkedHashMap<>();r.put("phase",phase);r.put("identity",id);for(String p:new String[]{"x","y","width","height"})r.put(p,field(e,p));r.put("scale",call(context,"getScaleFactor"));r.put("pin",pinState());if(renders.size()<80)renders.add(r);List<Map<String,Object>> order=new ArrayList<>();walk(call(context,"getActivePageComponent"),order);componentOrders.put(phase,order);frames++;}catch(Throwable t){cancel(t);}}
    private void walk(Object c,List<Map<String,Object>> order)throws Exception{Map<String,Object> n=new LinkedHashMap<>();n.put("class",c.getClass().getName());if(c.getClass().getSimpleName().equals("NativePinComponent")){n.put("parts",call(c,"parts"));n.put("width",call(c,"getWidth"));n.put("height",call(c,"getHeight"));}if(c.getClass().getSimpleName().equals("NativeGuideComponent")){Object g=call(c,"guide");n.put("guideKind",String.valueOf(field(g,"kind")));}order.add(n);try{Object children=call(c,"getComponents");if(children instanceof Iterable)for(Object child:(Iterable<?>)children)walk(child,order);}catch(NoSuchMethodException ignored){}}
    private void headerOrder(boolean tier)throws Exception{List<Map<String,Object>> order=componentOrders.get(phase);int title=-1,pin=-1,hint=-1,shift=-1;for(int i=0;i<order.size();i++){Map<String,Object> n=order.get(i);String name=String.valueOf(n.get("class"));if(name.endsWith("TitleEnricher$StackTitleTooltipComponent"))title=i;if(name.endsWith("NativePinComponent"))pin=i;if("TIER".equals(n.get("guideKind")))hint=i;if(name.endsWith("KeyboardModifierComponent"))shift=i;}check("header.actual-order-phase-"+phase,title>=0&&pin>title&&(!tier||(hint>pin&&shift>hint)),order);}
    private void step()throws Exception{
        Object player=field(mc,"thePlayer|field_71439_g");
        switch(phase){
        case 0: check("ordinary.natural-hover",!pinned(),pinState());headerOrder(false);hooks.screenshot("ordinary-hover");check("ordinary.pin-consumed",key(46),"C");check("ordinary.pinned",pinned(),pinState());away=true;next(1);break;
        case 1: check("ordinary.fixed-after-move",pinned()&&call(type(PIN),"pinnedStack")!=null,pinState());Map<String,Object> last=renders.get(renders.size()-1);check("ordinary.fixed-paint-position",Objects.equals(last.get("x"),call(type(PIN),"x"))&&Objects.equals(last.get("y"),call(type(PIN),"y")),last);hooks.screenshot("ordinary-fixed");check("ordinary.unpin-consumed",key(46),"C");check("ordinary.unpinned",!pinned(),pinState());away=false;next(2);break;
        case 2: check("ordinary.repin-consumed",key(46),"C");check("ordinary.repinned",pinned(),pinState());next(3);break;
        case 3: call(mc,"displayGuiScreen|func_147108_a",(Object)null);next(4);break;
        case 5: check("boiler.natural-hover",!pinned(),pinState());headerOrder(true);check("boiler.pin-consumed",key(46),"C");away=true;next(6);break;
        case 6: hooks.screenshot("boiler-fixed-before");clickChart();next(61);break;
        case 61: rebindCharts();next(62);break;
        case 62: hooks.screenshot("boiler-fixed-rebound");restoreBindings();next(7);break;
        case 7: hooks.screenshot("boiler-fixed-selected");open(player,2);next(10);break;
        case 10: check("gtmb.other-item-gui-clears-old-pin",!pinned(),pinState());call(mc,"displayGuiScreen|func_147108_a",(Object)null);next(101);break;
        case 102: check("gtmb.natural-hover",!pinned(),pinState());check("gtmb.pin-consumed",key(46),"C");away=true;next(11);break;
        case 11: hooks.screenshot("gtmb-fixed-url");clickUrl();next(13);break;
        case 133: check("url.returned-fixed-window",pinned()&&Boolean.TRUE.equals(call(type(PIN),"active",gui)),pinState());hooks.screenshot("gtmb-returned-fixed");check("gtmb.unpin-after-confirmation",key(46)&&!pinned(),pinState());call(mc,"displayGuiScreen|func_147108_a",(Object)null);next(134);break;
        case 14: for(Object b:(Object[])field(field(mc,"gameSettings|field_71474_y"),"keyBindings|field_74324_K")){String name=String.valueOf(call(b,"getKeyDescription|func_151464_g"));if(name.equals("miaokatzecore.keys.pin")){pinBinding=b;oldPinCode=((Number)call(b,"getKeyCode|func_151463_i")).intValue();break;}}require(pinBinding!=null,"Registered pin binding");call(pinBinding,"setKeyCode|func_151462_b",48);check("rebind.label",String.valueOf(call(type(CONTROL),"pinKeyLabel")).contains("B"),call(type(CONTROL),"pinKeyLabel"));check("rebind.old-key-ignored",!key(46)&&!pinned(),pinState());check("rebind.new-key-consumed",key(48)&&pinned(),pinState());away=true;next(15);break;
        case 15: hooks.screenshot("ordinary-fixed-rebound");call(pinBinding,"setKeyCode|func_151462_b",oldPinCode);pinBinding=null;call(mc,"displayGuiScreen|func_147108_a",(Object)null);next(16);break;
        default: break;
        }
    }
    // These routes use only bounds collected by actual production drawing, never guessed text positions.
    private Object binding(String name)throws Exception{for(Object b:(Object[])field(field(mc,"gameSettings|field_71474_y"),"keyBindings|field_74324_K"))if(name.equals(String.valueOf(call(b,"getKeyDescription|func_151464_g"))))return b;throw new IllegalStateException("Missing registered binding "+name);}
    private void rebind(String name,int code)throws Exception{Object b=binding(name);rebound.put(b,((Number)call(b,"getKeyCode|func_151463_i")).intValue());call(b,"setKeyCode|func_151462_b",code);}
    private void restoreBindings()throws Exception{for(Map.Entry<Object,Integer> e:rebound.entrySet())call(e.getKey(),"setKeyCode|func_151462_b",e.getValue());rebound.clear();}
    private Object findChart(Object c,String id)throws Exception{if(c.getClass().getSimpleName().equals("NativeChartComponent")){Object chart=call(c,"chart");if(id.equals(field(chart,"chartId")))return chart;}try{for(Object child:(Iterable<?>)call(c,"getComponents")){Object found=findChart(child,id);if(found!=null)return found;}}catch(NoSuchMethodException ignored){}return null;}
    private void rebindCharts()throws Exception{Object api=type("com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage"),key=call(type(PIN),"key"),page=call(api,"forItem",stacks[1]);String selected=String.valueOf(call(api,"selected",key));Object chart=findChart(call(call(type(PIN),"context"),"getActivePageComponent"),selected);require(chart!=null,"Actual selected drawn chart");int count=((List<?>)field(chart,"variants")).size(),before=((Number)call(page,"variant",selected,count)).intValue();
        rebind("miaokatzecore.keys.details",49);rebind("miaokatzecore.keys.select",34);
        check("rebind.V-label-N",String.valueOf(call(type("com.miaokatze.miaokatzecore.api.tooltip.TooltipPages"),"detailKeyLabel")).contains("N"),"V to N");
        check("rebind.V-old-key-ignored",!key(47)&&((Number)call(page,"variant",selected,count)).intValue()==before,"Old V event does not change actual selected chart");
        check("rebind.V-new-key-actual-change",key(49)&&((Number)call(page,"variant",selected,count)).intValue()==(before+1)%count,"N event changes actual selected chart variant");
        check("rebind.Tab-label-G",String.valueOf(call(type(CONTROL),"selectKeyLabel")).contains("G"),"Tab to G");
        check("rebind.Tab-old-key-ignored",!key(15)&&selected.equals(call(api,"selected",key)),"Old Tab event does not change actual selected chart");
        check("rebind.Tab-new-key-actual-change",key(34)&&!selected.equals(call(api,"selected",key)),"G event changes actual selected chart identity");
    }
    private void clickChart()throws Exception{
        Object api=type("com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage"),key=call(type(PIN),"key");
        Object page=call(api,"forItem",stacks[1]);List<?> hits=(List<?>)field(type("com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipMouse"),"active");
        for(Object hit:new ArrayList<Object>(hits)){
            Object action=field(hit,"action");String chart=null;Integer index=null;
            for(java.lang.reflect.Field f:action.getClass().getDeclaredFields()){f.setAccessible(true);Object value=f.get(action);if(value instanceof String&&String.valueOf(value).startsWith("boiler."))chart=(String)value;else if(value instanceof Integer)index=(Integer)value;}
            if(chart==null||index==null)continue;int before=((Number)call(page,"variant",chart,45)).intValue();if(index==before)continue;
            Map<String,Object> detail=hitSnapshot(hit);detail.put("chart",chart);detail.put("index",index);detail.put("before",before);
            check("boiler.label-hit-event",mouse(hit),detail);
            check("boiler.label-selected",((Number)call(page,"variant",chart,45)).intValue()==index&&chart.equals(call(api,"selected",key)),detail);receipt.put("chartClick",detail);return;
        }throw new IllegalStateException("No actually rendered alternate boiler label glyph");
    }
    private void clickUrl()throws Exception{
        Object settings=field(mc,"gameSettings|field_71474_y");set(settings,"chatLinks|field_74359_p",true);set(settings,"chatLinksPrompt|field_74358_q",true);
        List<?> hits=(List<?>)field(type("com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipMouse"),"active");
        for(Object hit:new ArrayList<Object>(hits)){Object action=field(hit,"action");boolean url=false;for(java.lang.reflect.Field f:action.getClass().getDeclaredFields()){f.setAccessible(true);url|="https://miaokatze.com/blueprints".equals(f.get(action));}if(!url)continue;receipt.put("urlClick",hitSnapshot(hit));check("url.real-hit-event",mouse(hit),hitSnapshot(hit));return;}throw new IllegalStateException("No actually rendered GTMB website glyph");
    }
    private Map<String,Object> hitSnapshot(Object hit)throws Exception{Map<String,Object> r=new LinkedHashMap<>();for(String p:new String[]{"x","y","width","height"})r.put(p,field(hit,p));return r;}
    private boolean mouse(Object hit)throws Exception{
        int x=(int)Math.floor(((Number)field(hit,"x")).doubleValue()+((Number)field(hit,"width")).doubleValue()/2),y=(int)Math.floor(((Number)field(hit,"y")).doubleValue()+((Number)field(hit,"height")).doubleValue()/2);
        int w=((Number)field(gui,"width|field_146294_l")).intValue(),h=((Number)field(gui,"height|field_146295_m")).intValue(),pw=((Number)field(mc,"displayWidth|field_71443_c")).intValue(),ph=((Number)field(mc,"displayHeight|field_71440_d")).intValue();Class<?> m=type("org.lwjglx.input.Mouse");int at=((Number)call(field(m,"queue"),"getCurrentPos")).intValue();String[] names={"buttonEvents","buttonEventStates","xEvents","yEvents"};Object[] arrays=new Object[4],old=new Object[4];Object[] values={0,true,x*pw/w,(h-y-1)*ph/h};
        try{for(int i=0;i<4;i++){arrays[i]=field(m,names[i]);old[i]=java.lang.reflect.Array.get(arrays[i],at);java.lang.reflect.Array.set(arrays[i],at,values[i]);}Object e=type("com.cleanroommc.modularui.api.event.MouseInputEvent$Pre").getConstructor(type("net.minecraft.client.gui.GuiScreen")).newInstance(gui);call(net.minecraftforge.common.MinecraftForge.EVENT_BUS,"post",e);return Boolean.TRUE.equals(call(e,"isCanceled"));}finally{for(int i=0;i<4;i++)if(arrays[i]!=null)java.lang.reflect.Array.set(arrays[i],at,old[i]);}
    }
    private boolean key(int key)throws Exception{Class<?> kb=type("org.lwjglx.input.Keyboard");Object oldQueue=field(kb,"eventQueue");ByteBuffer old=(ByteBuffer)field(kb,"sdlKeyPressedArray"),local=ByteBuffer.allocateDirect(old.capacity());for(int i=0;i<old.capacity();i++)local.put(i,old.get(i));for(int k:new int[]{42,54,56,184,29,157})local.put(((Number)call(type("org.lwjglx.input.KeyCodes"),"lwjglToSdlScancode",k)).intValue(),(byte)0);java.util.concurrent.ArrayBlockingQueue<Object> q=new java.util.concurrent.ArrayBlockingQueue<>(2);Object press=Enum.valueOf((Class)type("org.lwjglx.input.Keyboard$KeyState"),"PRESS");q.add(type("org.lwjglx.input.Keyboard$KeyEvent").getConstructor(int.class,int.class,int.class,type("org.lwjglx.input.Keyboard$KeyState"),long.class).newInstance(key,key,0,press,System.nanoTime()));try{set(kb,"sdlKeyPressedArray",local);set(kb,"eventQueue",q);Object e=type("com.cleanroommc.modularui.api.event.KeyboardInputEvent$Pre").getConstructor(type("net.minecraft.client.gui.GuiScreen")).newInstance(gui);call(net.minecraftforge.common.MinecraftForge.EVENT_BUS,"post",e);return Boolean.TRUE.equals(call(e,"isCanceled"));}finally{set(kb,"eventQueue",oldQueue);set(kb,"sdlKeyPressedArray",old);}}
    private void restorePointer()throws Exception{if(pointerOwned){set(type("org.lwjglx.input.Mouse"),"x",oldMouseX);set(type("org.lwjglx.input.Mouse"),"y",oldMouseY);pointerOwned=false;}}
    private static String sha(Path p)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)))b.append(String.format("%02x",v&255));return b.toString();}
    public void render(boolean start){}
}
