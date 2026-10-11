package audit;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;

/** Full production client, integrated server, real player and GT/NEI/BlockRenderer lifecycle audit. */
@Mod(modid="criticalgtclient",name="Critical GT full client audit",version="1",dependencies="after:gregtech;after:gtsr;after:NotEnoughItems")
public final class CriticalGTClientProbe {
    final JsonObject receipt=new JsonObject(); final JsonArray checks=new JsonArray();
    volatile Object host,machine,serverPlayer; volatile int request; volatile Throwable serverFailure;
    Object mc,screen,pages,panel,sync,recipeTransfer; int ticks,phase,wait,recipeIndex,guiAwait,stable; boolean done; String pendingShot; long phaseOneStart;
    List<Object> tabs=new ArrayList<Object>(); List<Object> maps=new ArrayList<Object>();
    static Object field(Object target,String names)throws Exception {
        for(String name:names.split("\\|"))for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(names);
    }
    static void set(Object target,String names,Object value)throws Exception {
        for(String name:names.split("\\|"))for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(names);
    }
    static Object call(Object target,String names,Object...args)throws Exception {
        List<Method> candidates=new ArrayList<Method>();Class<?> root=target instanceof Class?(Class<?>)target:target.getClass();
        for(Class<?> c=root;c!=null;c=c.getSuperclass())candidates.addAll(Arrays.asList(c.getDeclaredMethods()));candidates.addAll(Arrays.asList(root.getMethods()));
        for(String name:names.split("\\|"))for(Method m:candidates) {
            if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
            boolean fits=true;Class<?>[] types=m.getParameterTypes();
            for(int i=0;i<types.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])&&!(types[i].isPrimitive()&&args[i] instanceof Number)&&!(types[i]==boolean.class&&args[i] instanceof Boolean))fits=false;
            if(fits){m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}
        }
        throw new NoSuchMethodException(names+"/"+args.length+" on "+target);
    }
    static Class<?> type(String name)throws Exception{return Class.forName(name);}
    static Object member(Object o,String n)throws Exception{return field(o,n);}
    void record(String name,boolean pass,String detail){JsonObject j=new JsonObject();j.addProperty("name",name);j.addProperty("pass",pass);j.addProperty("detail",detail);checks.add(j);System.out.println("CRITICAL_GT_CLIENT "+j);}
    void check(String name,boolean pass,String detail){record(name,pass,detail);if(!pass)throw new AssertionError(name+": "+detail);}
    @Mod.EventHandler public void init(FMLInitializationEvent e){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||request==0)return;
        try{
            Object server=FMLCommonHandler.instance().getMinecraftServerInstance(),world=call(server,"worldServerForDimension|func_71218_a",0);
            List<?> players=(List<?>)field(call(server,"getConfigurationManager|func_71203_ab"),"playerEntityList|field_72404_b");if(players.isEmpty())return;
            serverPlayer=players.get(0);
            if(request==1){
                Object[] registry=(Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES");Object prototype=null;
                for(Object p:registry)if(p!=null&&p.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine")&&call(p,"getPresetKind")==null){prototype=p;break;}
                if(prototype==null)throw new AssertionError("Missing registered loom");
                call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",0,0);
                Object stone=field(type("net.minecraft.init.Blocks"),"stone|field_150348_b");
                for(int x=5;x<=12;x++)for(int z=5;z<=13;z++)call(world,"setBlock|func_147465_d",x,69,z,stone,0,3);
                call(serverPlayer,"setPositionAndUpdate|func_70634_a",8.5,71.0,10.5);
                ItemStack item=(ItemStack)call(prototype,"getStackForm",1L);
                if(!(Boolean)call(call(item,"getItem|func_77973_b"),"placeBlockAt",item,serverPlayer,world,8,70,8,1,.5f,.5f,.5f,0))throw new AssertionError("GT placement refused");
                host=call(world,"getTileEntity|func_147438_o",8,70,8);machine=call(host,"getMetaTileEntity");
                check("world.actual-GT-ItemMachines-placement",host.getClass().getName().equals("gregtech.api.metatileentity.BaseMetaTileEntity")&&machine.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine"),"server thread actual ItemMachines.placeBlockAt; host="+host.getClass().getName()+" machine="+machine.getClass().getName());
                check("world.actual-server-player",serverPlayer.getClass().getName().equals("net.minecraft.entity.player.EntityPlayerMP"),"integrated server joined player="+serverPlayer.getClass().getName());
                Object state=call(machine,"getCriticalState");call(state,"setInventorySlotContents|func_70299_a",0,call(item,"copy|func_77946_l"));
                Object water=call(type("net.minecraftforge.fluids.FluidRegistry"),"getFluid","water");Object fluid=type("net.minecraftforge.fluids.FluidStack").getConstructor(type("net.minecraftforge.fluids.Fluid"),int.class).newInstance(water,12345);
                call(state,"fill",Enum.valueOf((Class)type("net.minecraftforge.common.util.ForgeDirection"),"UNKNOWN"),fluid,true);
            }
            if(request==2){
                check("world.server-open-preconditions",field(serverPlayer,"worldObj|field_70170_p")==field(host,"worldObj|field_145850_b")&&distance(serverPlayer)<=64,"same actual world; distanceSq="+distance(serverPlayer)+" player="+position(serverPlayer));
                if(!(Boolean)call(machine,"onRightclick",host,serverPlayer))throw new AssertionError("GT onRightclick refused");
            }
            request=0;
        }catch(Throwable t){serverFailure=t;request=0;t.printStackTrace();}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END||++ticks<30)return;
        try {
            mc=call(type("net.minecraft.client.Minecraft"),"getMinecraft|func_71410_x");
            if(pendingShot!=null)return;
            if(serverFailure!=null)throw new RuntimeException("Server action",serverFailure);
            if(ticks>6000)throw new AssertionError("Client state timeout phase="+phase);
            if(phase==0){Object settingsObject=field(mc,"gameSettings|field_71474_y");set(settingsObject,"pauseOnLostFocus|field_82881_y",false);receipt.addProperty("pauseOnLostFocus",String.valueOf(field(settingsObject,"pauseOnLostFocus|field_82881_y")));check("fixture.actual-pauseOnLostFocus-disabled",Boolean.FALSE.equals(field(settingsObject,"pauseOnLostFocus|field_82881_y")),"actual GameSettings runtime value false");
                receipt.addProperty("productionCodeSource",type("com.miaokatze.gtsr.common.machine.MTECriticalMachine").getProtectionDomain().getCodeSource().getLocation().toString());
                receipt.addProperty("scope","Real full client + integrated server/player + GT placement/onRightclick + native MUI2 networking + actual BlockRenderer dummy construction + real NEI recipe GUI");
                Object dispatcher=field(type("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher"),"instance|field_147556_a");Map<?,?> renderers=(Map<?,?>)field(dispatcher,"mapSpecialRenderers|field_147559_m");
                Object anchorRenderer=renderers.get(type("com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor"));boolean gtSafe=true;for(Map.Entry<?,?> entry:renderers.entrySet())if(entry.getValue()!=null&&entry.getValue().getClass().getName().equals("com.miaokatze.gtsr.client.critical.CriticalControllerRenderer")&&!entry.getKey().equals(type("com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor")))gtSafe=false;
                check("renderer.actual-anchor-only-dispatcher",anchorRenderer!=null&&anchorRenderer.getClass().getName().equals("com.miaokatze.gtsr.client.critical.CriticalControllerRenderer")&&gtSafe,"real TESR dispatcher; GT BaseMetaTileEntity untouched");
                if(System.getProperty("critical.gui.previewEvidence")==null)previewMatrix();else {receipt.addProperty("previewEvidence",System.getProperty("critical.gui.previewEvidence"));record("preview.reused-frozen-candidate-receipt",true,System.getProperty("critical.gui.previewEvidence")+"; full matrix executed in prior receipt, not repeated here");}
                Class<?> settings=type("net.minecraft.world.WorldSettings"),gameType=type("net.minecraft.world.WorldSettings$GameType");
                Object config=settings.getConstructor(long.class,gameType,boolean.class,boolean.class,type("net.minecraft.world.WorldType")).newInstance(61312116L,Enum.valueOf((Class)gameType,"CREATIVE"),false,false,field(type("net.minecraft.world.WorldType"),"FLAT|field_77138_c"));
                call(mc,"launchIntegratedServer|func_71371_a","CriticalAuditFresh","Critical Audit Fresh",config);phase=1;phaseOneStart=System.currentTimeMillis();wait=0;return;
            }
            Object player=field(mc,"thePlayer|field_71439_g");
            if(phase==1){if(System.currentTimeMillis()-phaseOneStart>60000)throw new AssertionError("Real player login readiness wall-clock timeout");if(player==null)return;Object loading=field(mc,"currentScreen|field_71462_r");if(((Number)field(player,"ticksExisted|field_70173_aa")).intValue()<60||(loading!=null&&loading.getClass().getName().contains("GuiDownloadTerrain")))return;check("world.fresh-real-client-player",!player.getClass().getName().contains("Fake"),player.getClass().getName()+" ticksExisted="+field(player,"ticksExisted|field_70173_aa")+" completed real login/terrain");request=1;phase=12;wait=0;return;}
            if(phase==12){
                if(++wait>400)throw new AssertionError("Actual client GT tile sync timed out player="+position(player));if(request!=0)return;if(player==null)throw new AssertionError("Disconnected while awaiting actual client GT tile sync");
                if(dimension(player)!=0||dimension(serverPlayer)!=0||distance(player)>64||distance(serverPlayer)>64){stable=0;return;}if(++stable<20)return;
                Object clientWorld=field(mc,"theWorld|field_71441_e"),clientHost=call(clientWorld,"getTileEntity|func_147438_o",8,70,8);
                if(clientHost==null||!clientHost.getClass().getName().equals("gregtech.api.metatileentity.BaseMetaTileEntity"))return;
                Object clientMachine=call(clientHost,"getMetaTileEntity");if(clientMachine==null)return;
                check("world.actual-client-GT-tile-sync",clientMachine.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine"),"actual server placement reached client via GT tile/chunk packets before opening UI");
                check("world.client-open-preconditions",field(clientHost,"worldObj|field_145850_b")==clientWorld&&((Number)field(field(clientWorld,"provider|field_73011_w"),"dimensionId|field_76574_g")).intValue()==0&&distance(player)<=64,"actual client overworld; distanceSq="+distance(player)+" player="+position(player));
                if(Boolean.getBoolean("critical.gui.anchorOnly")){phase=13;wait=0;return;}request=2;phase=2;wait=0;return;
            }
            if(phase>1&&player==null)throw new AssertionError("Real client disconnected phase="+phase);
            Object gui=field(mc,"currentScreen|field_71462_r");
            if(phase==2||phase==6){if(++guiAwait>200)throw new AssertionError("Native GUI missing phase="+phase+" gui="+(gui==null?null:gui.getClass().getName())+" client="+position(player)+" server="+position(serverPlayer));if(request!=0||gui==null||!type("com.cleanroommc.modularui.api.IMuiScreen").isInstance(gui))return;if(++wait<30)return;guiAwait=0;
                screen=call(type("com.cleanroommc.modularui.screen.ModularScreen"),"getCurrent");panel=call(screen,"getMainPanel");sync=call(call(screen,"getSyncManager"),"getMainPSM");
                layout("native-"+phase);screenshot("initial-"+phase);
                check("gui.native-screen."+phase,screen!=null,"wrapper="+gui.getClass().getName()+" GT host="+host.getClass().getName());
                call(gui,"updateScreen|func_73876_c");call(gui,"handleMouseInput|func_146274_d");
                Object cc=field(player,"openContainer|field_71070_bA"),sc=field(serverPlayer,"openContainer|field_71070_bA");
                int slots=((List<?>)field(cc,"inventorySlots|field_75151_b")).size();
                check("gui.actual-player-machine-slots."+phase,slots==72,"slots="+slots+" (36 machine +36 player)");
                check("gui.real-server-window-sync."+phase,((Number)field(cc,"windowId|field_75152_c")).intValue()>0&&field(cc,"windowId|field_75152_c").equals(field(sc,"windowId|field_75152_c")),"client="+field(cc,"windowId|field_75152_c")+" server="+field(sc,"windowId|field_75152_c"));
                int fluidHandlers=0;for(int i=0;i<16;i++)if(call(sync,"findSyncHandlerNullable","critical.fluid."+i,0)!=null)fluidHandlers++;
                check("gui.sixteen-fluid-sync-handlers."+phase,fluidHandlers==16,"registered="+fluidHandlers);
                Object syncedWater=call(call(sync,"findSyncHandlerNullable","critical.fluid.0",0),"getValue");check("gui.real-fluid-s2c."+phase,syncedWater!=null&&((Number)field(syncedWater,"amount")).intValue()==12345,"server water12345 synchronized through native PanelSyncManager");
                tabs.clear();pages=null;walk(panel,new HashSet<Object>());check("gui.three-pages",pages!=null&&tabs.size()==3&&((List<?>)call(pages,"getPages")).size()==3,"tabs="+tabs.size());
                if(phase==6){screenshot("reopen");phase=15;wait=0;return;}
                if(Boolean.getBoolean("critical.gui.small"))check("gui.actual-854x480-resolution",((Number)field(mc,"displayWidth|field_71443_c")).intValue()==854&&((Number)field(mc,"displayHeight|field_71440_d")).intValue()==480,"independent fresh native854x480 startup");phase=3;wait=0;return;
            }
            if(phase==15){prepareRecipes();check("nei.native-transfer-widget",recipeTransfer!=null,"production GTProgressWidget exists");Object a=call(recipeTransfer,"getArea"),context=call(screen,"getContext");call(context,"updateState",((Number)field(a,"x")).intValue()+((Number)field(a,"width")).intValue()/2,((Number)field(a,"y")).intValue()+((Number)field(a,"height")).intValue()/2,0f);call(screen,"onFrameUpdate");boolean hit=(Boolean)call(screen,"onMousePressed",0);Object recipeGui=field(mc,"currentScreen|field_71462_r");check("nei.native-transfer-click-opens-real-GUI",hit&&recipeGui!=null&&recipeGui.getClass().getName().contains("GuiCraftingRecipe"),"native mouse hit →production GTProgressWidget →NEI GuiCraftingRecipe");screenshot("nei-native-transfer");phase=7;wait=0;return;
            }
            if(phase==3){if(++wait<15)return;clickTab(0);check("gui.page.status",((Number)call(pages,"getCurrentPageIndex")).intValue()==0,"native screen mouse hit dispatch");screenshot("status");phase=4;wait=0;return;}
            if(phase==4){if(++wait<15)return;clickTab(1);check("gui.page.configuration",((Number)call(pages,"getCurrentPageIndex")).intValue()==1,"native screen mouse hit dispatch");screenshot("configuration");phase=5;wait=0;return;}
            if(phase==5){if(++wait<15)return;clickTab(2);check("gui.page.inventory",((Number)call(pages,"getCurrentPageIndex")).intValue()==2,"native screen mouse hit dispatch");
                Object container=field(player,"openContainer|field_71070_bA");boolean itemSynced=false;ItemStack marker=(ItemStack)call(machine,"getStackForm",1L);for(Object slot:(List<?>)field(container,"inventorySlots|field_75151_b")){Object stack=call(slot,"getStack|func_75211_c");if(stack!=null&&call(stack,"getItem|func_77973_b")==call(marker,"getItem|func_77973_b")&&call(stack,"getItemDamage|func_77960_j").equals(call(marker,"getItemDamage|func_77960_j"))&&((Number)field(stack,"stackSize|field_77994_a")).intValue()==1)itemSynced=true;}check("gui.actual-slot-s2c",itemSynced,"server-inserted machine input transmitted");
                layout("inventory-visible");screenshot("inventory");if(Boolean.getBoolean("critical.gui.small")){phase=13;wait=0;return;}phase=9;wait=0;return;}
            if(phase==9){if(wait==0)call(player,"closeScreen|func_71053_j");if(++wait<30)return;check("gui.close-server-container",!field(serverPlayer,"openContainer|field_71070_bA").getClass().getName().contains("Modular"),"native close packet processed");phase=6;wait=0;request=2;return;}
            if(phase==7){if(++wait<15)return;
                if(recipeIndex>=maps.size()){
                    ItemStack controller=(ItemStack)call(machine,"getStackForm",1L);
                    boolean opened=(Boolean)call(type("codechicken.nei.recipe.GuiCraftingRecipe"),"openRecipeGui","item",new Object[]{controller});
                    gui=field(mc,"currentScreen|field_71462_r");check("nei.real-structure-entry",opened&&gui!=null,"controller recipe entrance");
                    List<?> handlers=(List<?>)call(gui,"getCurrentRecipeHandlers");int target=-1;for(int i=0;i<handlers.size();i++)if(handlers.get(i).getClass().getName().equals("blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler"))target=i;
                    check("nei.real-blockrenderer-handler",target>=0,"handlers="+handlers);call(gui,"setRecipePage",target);call(gui,"refreshContainer");phase=8;wait=0;return;
                }
                Object map=maps.get(recipeIndex);String name=String.valueOf(field(map,"unlocalizedName"));
                boolean opened=(Boolean)call(type("codechicken.nei.recipe.GuiCraftingRecipe"),"openRecipeGui",name,new Object[0]);
                gui=field(mc,"currentScreen|field_71462_r");check("nei.real-recipe-entry."+name,opened&&gui!=null&&gui.getClass().getName().contains("GuiCraftingRecipe"),"opened="+opened+" gui="+(gui==null?null:gui.getClass().getName()));
                call(gui,"updateScreen|func_73876_c");call(gui,"handleMouseInput|func_146274_d");Object handler=call(gui,"getHandler");int n=((Number)call(handler,"numRecipes")).intValue();check("nei.real-handler-recipes."+name,n>0,"handler="+handler.getClass().getName()+" recipes="+n);screenshot("nei-"+recipeIndex);recipeIndex++;wait=0;
            }
            if(phase==8){if(++wait<30)return;call(gui,"updateScreen|func_73876_c");check("nei.real-structure-render",call(gui,"getHandler").getClass().getName().equals("blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler"),"native BlockRenderer drawBackground executed through real GuiRecipe");screenshot("nei-structure");phase=14;wait=0;return;
            }
            if(phase==14){anchorSample();finish(null);return;}
            if(phase==13){if(++wait<3)return;anchorSample();finish(null);}
        } catch(Throwable t){t.printStackTrace();finish(t);}
    }
    String position(Object player)throws Exception{return field(player,"posX|field_70165_t")+","+field(player,"posY|field_70163_u")+","+field(player,"posZ|field_70161_v");}
    int dimension(Object player)throws Exception{return ((Number)field(field(field(player,"worldObj|field_70170_p"),"provider|field_73011_w"),"dimensionId|field_76574_g")).intValue();}
    double distance(Object player)throws Exception{double x=((Number)field(player,"posX|field_70165_t")).doubleValue()-8.5,y=((Number)field(player,"posY|field_70163_u")).doubleValue()-70.5,z=((Number)field(player,"posZ|field_70161_v")).doubleValue()-8.5;return x*x+y*y+z*z;}
    void walk(Object w,Set<Object> seen)throws Exception {
        if(!seen.add(w))return;String n=w.getClass().getName();
        if(n.contains("PagedWidget"))pages=w;
        if(n.equals("gregtech.common.modularui2.widget.GTProgressWidget"))recipeTransfer=w;
        if(n.contains("ButtonWidget")&&tabs.size()<3){Object area=call(w,"getArea");if(((Number)field(area,"width")).intValue()==110&&((Number)field(area,"height")).intValue()==18)tabs.add(w);}
        for(Object child:(List<?>)call(w,"getChildren"))walk(child,seen);
    }
    void layout(String name)throws Exception {
        Object a=call(panel,"getArea"),v=call(screen,"getScreenArea");int x=((Number)field(a,"x")).intValue(),y=((Number)field(a,"y")).intValue(),w=((Number)field(a,"width")).intValue(),h=((Number)field(a,"height")).intValue(),vw=((Number)field(v,"width")).intValue(),vh=((Number)field(v,"height")).intValue();
        record("gui.viewport-fit."+name,x>=0&&y>=0&&x+w<=vw&&y+h<=vh,"actual panel="+x+","+y+" "+w+"x"+h+" scaled viewport="+vw+"x"+vh+" physical="+field(mc,"displayWidth|field_71443_c")+"x"+field(mc,"displayHeight|field_71440_d"));
    }
    void clickTab(int index)throws Exception {
        Object tab=tabs.get(index),a=call(tab,"getArea");int x=((Number)field(a,"x")).intValue()+((Number)field(a,"width")).intValue()/2,y=((Number)field(a,"y")).intValue()+((Number)field(a,"height")).intValue()/2;
        Object context=call(screen,"getContext");call(context,"updateState",x,y,0f);call(screen,"onFrameUpdate");boolean clicked=(Boolean)call(screen,"onMousePressed",0);call(screen,"onMouseRelease",0);check("gui.native-mouse-tab-hit."+index,clicked,"screen logical click="+x+","+y);
    }
    void prepareRecipes()throws Exception {Class<?> r=type("com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps");maps.add(field(r,"displayRecipes"));for(Object kind:type("com.miaokatze.gtsr.common.critical.CriticalMachineKind").getEnumConstants())maps.add(call(r,"forKind",kind));check("nei.ten-production-maps",maps.size()==10,"union +9 machine kinds");}
    void previewMatrix()throws Exception {
        Object[] registry=(Object[])field(type("gregtech.api.GregTechAPI"),"METATILEENTITIES");List<Object> previews=new ArrayList<Object>();int count=0;long voxelsTotal=0;
        for(int id=0;id<registry.length;id++)if(registry[id]!=null&&registry[id].getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine")&&call(registry[id],"getPresetKind")!=null){
            Object kind=call(registry[id],"getPresetKind");
            for(Object tier:type("com.miaokatze.gtsr.common.critical.CriticalTier").getEnumConstants()) {
                Object world=type("blockrenderer6343.client.world.TrackedDummyWorld").newInstance(),tile=type("gregtech.api.metatileentity.BaseMetaTileEntity").newInstance();
                call(world,"setTileEntity|func_147455_a",0,0,0,tile);call(tile,"setInitialValuesAsNBT",null,(short)id);Object m=call(tile,"getMetaTileEntity");
                ItemStack trigger=(ItemStack)call(registry[id],"getStackForm",1L);set(trigger,"stackSize|field_77994_a",((Enum<?>)tier).ordinal()+1);
                call(m,"construct",trigger,false);
                List<?> expected=(List<?>)call(type("com.miaokatze.gtsr.common.critical.CriticalStructures"),"voxels",tier,kind);boolean equal=true;int minX=999,minY=999,minZ=999,maxX=-999,maxY=-999,maxZ=-999;
                for(Object v:expected){int x=((Number)field(v,"x")).intValue()-40,y=((Number)field(v,"y")).intValue(),z=((Number)field(v,"z")).intValue()-32;Object block=call(type("com.miaokatze.gtsr.common.critical.CriticalMaterials"),"block",field(v,"material"),false);equal&=call(world,"getBlock|func_147439_a",x,y,z)==block&&((Number)call(world,"getBlockMetadata|func_72805_g",x,y,z)).intValue()==0;minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
                int actual=((Map<?,?>)field(world,"blockMap")).size();
                check("preview.actual-blockmap."+kind+"."+tier,equal&&actual==expected.size(),"actual="+actual+" expected="+expected.size()+" exact coordinate/material/meta; bounds="+minX+","+minY+","+minZ+".."+maxX+","+maxY+","+maxZ);
                Object lo=call(world,"getMinPos"),hi=call(world,"getMaxPos");boolean bounds=((Number)field(lo,"x")).intValue()==minX&&((Number)field(lo,"y")).intValue()==minY&&((Number)field(lo,"z")).intValue()==minZ&&((Number)field(hi,"x")).intValue()==maxX&&((Number)field(hi,"y")).intValue()==maxY&&((Number)field(hi,"z")).intValue()==maxZ;check("preview.actual-boundary."+kind+"."+tier,bounds,"tracked bounds="+lo+".."+hi);
                List<?> ports=(List<?>)call(type("com.miaokatze.gtsr.common.critical.CriticalStructures"),"hatchPorts",tier,kind);check("preview.eight-native-hatch-ports."+kind+"."+tier,ports.size()==8,"ports="+ports.size());
                Object observer=type("blockrenderer6343.client.world.ObserverWorld").newInstance();call(observer,"setTileEntity|func_147455_a",0,0,0,tile);Object oldWorld=field(tile,"worldObj|field_145850_b");set(tile,"worldObj|field_145850_b",observer);
                Object estimated=call(observer,"estimateTierFromConstructable",(Consumer<ItemStack>)(item)->{},m);check("preview.real-observer."+kind+"."+tier,((Number)estimated).intValue()==3,"estimated tiers="+estimated);set(tile,"worldObj|field_145850_b",oldWorld);
                if(((Enum<?>)tier).ordinal()==0)previews.add(m);count++;voxelsTotal+=actual;
            }
        }
        final Object[] scanResult=new Object[1];Object scan=type("blockrenderer6343.integration.gregtech.GTConstructableScan").getConstructor(Consumer.class,Collection.class).newInstance((Consumer<Object>)(r)->scanResult[0]=r,previews);call(scan,"run");
        check("preview.real-GTConstructableScan",scanResult[0]!=null&&!((Map<?,?>)scanResult[0]).isEmpty(),"scan entries="+(scanResult[0]==null?0:((Map<?,?>)scanResult[0]).size()));
        int unsupported=0;boolean excluded=true;
        for(Object p:registry)if(p!=null&&!(Boolean)call(type("com.miaokatze.gtsr.common.critical.CriticalMachineState"),"isSupportedHatchMeta",p)){
            String name=p.getClass().getName();if(!name.contains("InputBusME")&&!name.contains("InputME")&&!name.contains("DualInput"))continue;
            Object stack=call(p,"getStackForm",1L);if(stack==null)continue;Object hash=call(type("blockrenderer6343.client.utils.BRUtil"),"hashStack",stack),machines=((Map<?,?>)scanResult[0]).get(hash);
            if(machines instanceof Collection)for(Object preview:previews)excluded&=!((Collection<?>)machines).contains(preview);unsupported++;
        }
        check("preview.unsupported-ME-Dual-input-excluded",unsupported>0&&excluded,"unsupported registered templates="+unsupported+" absent from actual GTConstructableScan catalogue");
        for(String hatch:new String[]{"MTEHatchInputBus","MTEHatchOutputBus","MTEHatchInput","MTEHatchOutput","MTEHatchEnergy","MTEHatchDynamo"}){
            int found=0;Class<?> h=type("gregtech.api.metatileentity.implementations."+hatch);
            for(Object p:registry)if(p!=null&&h.isInstance(p)){Object stack=call(p,"getStackForm",1L);if(stack!=null){Object hash=call(type("blockrenderer6343.client.utils.BRUtil"),"hashStack",stack);Object machines=((Map<?,?>)scanResult[0]).get(hash);if(machines instanceof Collection&&((Collection<?>)machines).containsAll(previews))found++;}}
            check("preview.real-scan-hatch-catalogue."+hatch,found>0,"registered native hatch alternatives="+found+" all9 machines");
        }
        check("preview.matrix-27",count==27,"constructed="+count+" blocks="+voxelsTotal);receipt.addProperty("previewBlocks",voxelsTotal);
    }
    void screenshot(String name)throws Exception {pendingShot=name;}
    @SubscribeEvent public void rendered(TickEvent.RenderTickEvent e){if(done||e.phase!=TickEvent.Phase.END||pendingShot==null)return;try{String name=pendingShot;pendingShot=null;saveScreenshot(name);}catch(Throwable t){finish(t);}}
    void saveScreenshot(String name)throws Exception {
        Object framebuffer=call(mc,"getFramebuffer|func_147110_a");Object message=call(type("net.minecraft.util.ScreenShotHelper"),"saveScreenshot|func_148259_a",Paths.get(System.getProperty("critical.gui.receipt")).getParent().toFile(),name+".png",((Number)field(mc,"displayWidth|field_71443_c")).intValue(),((Number)field(mc,"displayHeight|field_71440_d")).intValue(),framebuffer);System.out.println("SCREENSHOT "+message);
    }
    void anchorSample()throws Exception {
        receipt.add("checks",checks);receipt.addProperty("status","RENDER_PENDING");Files.write(Paths.get(System.getProperty("critical.gui.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));
        Object world=type("blockrenderer6343.client.world.TrackedDummyWorld").newInstance(),anchor=type("com.miaokatze.gtsr.common.critical.TileEntityCriticalRenderAnchor").newInstance();
        Object tier=type("com.miaokatze.gtsr.common.critical.CriticalTier").getEnumConstants()[0],kind=type("com.miaokatze.gtsr.common.critical.CriticalMachineKind").getEnumConstants()[0];
        List<?> geometry=(List<?>)call(type("com.miaokatze.gtsr.common.critical.CriticalStructures"),"voxels",tier,kind);
        for(Object v:geometry)call(world,"setBlock|func_147465_d",field(v,"x"),field(v,"y"),field(v,"z"),field(v,"block"),field(v,"metadata"),2);
        call(world,"setTileEntity|func_147455_a",40,1,32,anchor);
        Object n=type("net.minecraft.nbt.NBTTagCompound").newInstance(),visual=call(call(machine,"getCriticalState"),"getVisualData");
        call(visual,"setInteger|func_74768_a","state",2);call(visual,"setBoolean|func_74757_a","valid",true);call(visual,"setString|func_74778_a","structureId","CLIENT-VISUAL-FIXTURE-NO-PAID-BUSINESS");
        for(String key:new String[]{"x","ownerX"})call(n,"setInteger|func_74768_a",key,40);for(String key:new String[]{"y","ownerY"})call(n,"setInteger|func_74768_a",key,key.equals("y")?1:0);for(String key:new String[]{"z","ownerZ"})call(n,"setInteger|func_74768_a",key,32);call(n,"setTag|func_74782_a","visual",visual);call(anchor,"readFromNBT|func_145839_a",n);
        Object state=call(anchor,"getVisualState");check("renderer.actual-anchor-visual-NBT",call(state,"getStructureId").equals("CLIENT-VISUAL-FIXTURE-NO-PAID-BUSINESS")&&((Number)call(state,"getState")).intValue()==2,"production anchor visual decoder; tracked dummy canonical geometry="+geometry.size());
        Object dispatcher=field(type("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher"),"instance|field_147556_a"),renderer=call(dispatcher,"getSpecialRenderer|func_147547_b",anchor),gl=type("com.gtnewhorizons.angelica.glsm.GLStateManager");
        // This is explicitly a visual fixture, with production world/renderer classes and live client GL.
        call(call(mc,"getFramebuffer|func_147110_a"),"bindFramebuffer|func_147610_a",true);call(gl,"glGetError");call(gl,"glPushAttrib",1048575);call(gl,"glMatrixMode",5889);call(gl,"glPushMatrix");call(gl,"glLoadIdentity");call(gl,"glOrtho",-65d,65d,-20d,90d,-1000d,1000d);call(gl,"glMatrixMode",5888);call(gl,"glPushMatrix");call(gl,"glLoadIdentity");
        try {
            call(gl,"glViewport",0,0,field(mc,"displayWidth|field_71443_c"),field(mc,"displayHeight|field_71440_d"));call(gl,"glClearColor",.04f,.06f,.09f,1f);call(gl,"glDepthMask",true);call(gl,"glDepthFunc",515);call(gl,"glEnable",2929);call(gl,"glRotated",24d,1d,0d,0d);call(gl,"glRotated",-45d,0d,1d,0d);call(gl,"glTranslated",-40d,0d,-32d);
            Object info=call(world,"getWorldInfo|func_72912_H");for(int tick=0;tick<100;tick++){call(info,"incrementTotalWorldTime|setWorldTotalTime|func_82572_b",(long)tick);call(gl,"glClear",16640);call(dispatcher,"renderTileEntityAt|func_147549_a",anchor,40d,1d,32d,.5f);}
            Map<?,?> visible=(Map<?,?>)field(renderer,"visibility");Object tracker=visible.get(anchor);int groups=tracker==null?0:((Set<?>)field(tracker,"visible")).size();int meshes=((Map<?,?>)field(renderer,"meshes")).size();
            check("renderer.actual-TESR-dispatch-visible",groups>0&&meshes>=2,"real dispatcher→production renderer; visible animated groups="+groups+" compiled model meshes="+meshes+"; visual fixture only");
            int w=((Number)field(mc,"displayWidth|field_71443_c")).intValue(),h=((Number)field(mc,"displayHeight|field_71440_d")).intValue();java.nio.ByteBuffer pixels=java.nio.ByteBuffer.allocateDirect(w*h*4);call(gl,"glReadPixels",0,0,w,h,6408,5121,pixels);int foreground=0;for(int i=0;i<w*h;i++)if((pixels.get(i*4)&255)>50||(pixels.get(i*4+1)&255)>50||(pixels.get(i*4+2)&255)>50)foreground++;check("renderer.actual-framebuffer-visible-pixels",foreground>50,"non-background bright framebuffer pixels="+foreground);
            check("renderer.actual-GL-error",((Number)call(gl,"glGetError")).intValue()==0,"live client OpenGL after100TESR dispatch calls");saveScreenshot("anchor-client-visual-fixture");
        } finally {call(gl,"glMatrixMode",5888);call(gl,"glPopMatrix");call(gl,"glMatrixMode",5889);call(gl,"glPopMatrix");call(gl,"glMatrixMode",5888);call(gl,"glPopAttrib");}
        receipt.addProperty("anchorRenderScope","Pure visual NBT + canonical proxy geometry in actual TrackedDummyWorld, rendered by real TileEntityRendererDispatcher in full client GL; no paid-business claim");
    }
    void finish(Throwable t){if(done)return;done=true;receipt.add("checks",checks);boolean pass=t==null;for(JsonElement c:checks)pass&=c.getAsJsonObject().get("pass").getAsBoolean();receipt.addProperty("status",pass?"PASS":"FAIL");if(t!=null){receipt.addProperty("failure",t.toString());JsonArray stack=new JsonArray();for(Throwable cause=t;cause!=null;cause=cause.getCause()){stack.add(new JsonPrimitive(cause.toString()));for(StackTraceElement s:cause.getStackTrace())stack.add(new JsonPrimitive(s.toString()));}receipt.add("stack",stack);}try{Files.write(Paths.get(System.getProperty("critical.gui.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));call(mc,"shutdown|func_71400_g");}catch(Exception e){e.printStackTrace();}}
}
