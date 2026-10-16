package audit;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.lang.invoke.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Actual registered GT machines, GT ItemMachines placement and vanilla TE NBT reconstruction. */
@Mod(modid="criticalgtintegration",name="Critical GT integration audit",version="1",dependencies="after:gregtech;after:gtsr")
public final class CriticalGTIntegrationProbe {
    JsonArray checks=new JsonArray();boolean done;Object observationHost,naturalBatchHost,naturalBatchPlayer,naturalBatchState,naturalBatchInput,naturalBatchOutput;ItemStack naturalBatchSource,naturalCircuit;int naturalBatchTicks,naturalBatchExpected,naturalCircuitSlot;int naturalTicks;long pausedEnergy;int pausedCursor;JsonObject restartExpected;
    void check(String name,boolean pass,String detail){JsonObject j=new JsonObject();j.addProperty("name",name);j.addProperty("pass",pass);j.addProperty("detail",detail);checks.add(j);System.out.println("CRITICAL_GT_CHECK "+j);}
    static Object field(Object target,String name)throws Exception {
        for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object target,String names,Object...args)throws Exception {
        if(!(target instanceof Class)&&target.getClass().getName().startsWith("gregtech.api.metatileentity.")){
            String name=names.split("\\|")[0];Class<?> result=null;Class<?>[] parameters=null;
            if(name.equals("getStackForm"))return Class.forName("gregtech.api.interfaces.metatileentity.IMetaTileEntity").getMethod(name,long.class).invoke(target,args);
            if(name.equals("maxEUStore")||name.equals("maxEUInput")||name.equals("maxEUOutput")||name.equals("maxWorkingAmperesIn")){result=long.class;parameters=new Class<?>[0];}
            else if(name.equals("getCapacity")||name.equals("getSizeInventory")||name.equals("getCircuitSlot")){result=int.class;parameters=new Class<?>[0];}
            else if(name.equals("getStackInSlot")){result=ItemStack.class;parameters=new Class<?>[]{int.class};}
            else if(name.equals("setInventorySlotContents")){result=void.class;parameters=new Class<?>[]{int.class,ItemStack.class};}
            else if(name.equals("fill")){result=int.class;parameters=new Class<?>[]{net.minecraftforge.common.util.ForgeDirection.class,net.minecraftforge.fluids.FluidStack.class,boolean.class};}
            else if(name.equals("drain")){result=net.minecraftforge.fluids.FluidStack.class;parameters=new Class<?>[]{net.minecraftforge.common.util.ForgeDirection.class,int.class,boolean.class};}
            if(result!=null)return invokeExact(target,names,result,parameters,args);
        }
        if(!(target instanceof Class)&&target.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine")){
            String name=names.split("\\|")[0];
            if(name.equals("getStackForm"))return Class.forName("gregtech.api.interfaces.metatileentity.IMetaTileEntity").getMethod(name,long.class).invoke(target,args);
            String result=null;
            if(name.equals("getCriticalState"))result="com.miaokatze.gtsr.common.critical.CriticalMachineState";
            else if(name.equals("getPresetKind"))result="com.miaokatze.gtsr.common.critical.CriticalMachineKind";
            else if(name.equals("getStructureDefinition"))result="com.gtnewhorizon.structurelib.structure.IStructureDefinition";
            else if(name.equals("createTooltip"))result="gregtech.api.util.MultiblockTooltipBuilder";
            else if(name.equals("getRecipeMap"))result="gregtech.api.recipe.RecipeMap";
            Class<?> returnType=result!=null?Class.forName(result):name.equals("consumeCriticalEnergy")?boolean.class:long.class;
            Class<?>[] parameters=new Class<?>[args.length];for(int i=0;i<args.length;i++)parameters[i]=args[i] instanceof Long?long.class:args[i].getClass();
            try{return MethodHandles.privateLookupIn(target.getClass(),MethodHandles.lookup()).findVirtual(target.getClass(),name,MethodType.methodType(returnType,parameters)).bindTo(target).invokeWithArguments(args);}catch(Throwable t){if(t instanceof Exception)throw (Exception)t;throw new IllegalStateException("Exact MTE method "+name,t);}
        }
        for(String name:names.split("\\|"))for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())for(Method m:declared(c,names)){
            if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
            boolean fits=true;Class<?>[] types=m.getParameterTypes();for(int i=0;i<types.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])&&!(types[i].isPrimitive()&&args[i] instanceof Number)&&!(types[i]==boolean.class&&args[i] instanceof Boolean))fits=false;
            if(fits){m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}
        }
        throw new NoSuchMethodException(names);
    }
    static Method[] declared(Class<?> type,String requested){try{return type.getDeclaredMethods();}catch(LinkageError e){throw new IllegalStateException("Fixture reflection type="+type.getName()+" requested="+requested,e);}}
    static Object invokeExact(Object target,String names,Class<?> result,Class<?>[] parameters,Object[] args)throws Exception {for(String name:names.split("\\|"))try{return MethodHandles.privateLookupIn(target.getClass(),MethodHandles.lookup()).findVirtual(target.getClass(),name,MethodType.methodType(result,parameters)).bindTo(target).invokeWithArguments(args);}catch(NoSuchMethodException ignored){}catch(Throwable t){if(t instanceof Exception)throw (Exception)t;throw new IllegalStateException("Exact GT method "+name,t);}throw new NoSuchMethodException(names);}
    static ItemStack copy(ItemStack item)throws Exception{return (ItemStack)call(item,"copy|func_77946_l");}
    static ItemStack physicalCircuit()throws Exception {ItemStack item=copy((ItemStack)call(Class.forName("gregtech.api.util.GTUtility"),"getIntegratedCircuit",7));try{set(item,"stackSize",1);}catch(NoSuchFieldException e){set(item,"field_77994_a",1);}NBTTagCompound marker=new NBTTagCompound();call(marker,"setString|func_74778_a","criticalFixtureMarker","config-preservation");call(item,"setTagCompound|func_77982_d",marker);return item;}
    static boolean preservedCircuit(ItemStack actual,ItemStack expected)throws Exception {return actual!=null&&amount(actual)==0&&(Boolean)call(actual,"isItemEqual|func_77969_a",expected)&&(Boolean)call(ItemStack.class,"areItemStackTagsEqual|func_77970_a",actual,expected);}
    static void set(Object target,String name,Object value)throws Exception {for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static int amount(ItemStack item)throws Exception {try{return ((Number)field(item,"stackSize")).intValue();}catch(NoSuchFieldException e){return ((Number)field(item,"field_77994_a")).intValue();}}
    static boolean items(ItemStack[] actual,ItemStack[] expected)throws Exception {
        if(actual.length!=expected.length)return false;
        for(int i=0;i<actual.length;i++)if(actual[i]==null||expected[i]==null){if(actual[i]!=expected[i])return false;}else if(amount(actual[i])!=amount(expected[i])||!(Boolean)call(actual[i],"isItemEqual|func_77969_a",expected[i])||!(Boolean)call(ItemStack.class,"areItemStackTagsEqual|func_77970_a",actual[i],expected[i]))return false;
        return true;
    }
    static boolean fluids(net.minecraftforge.fluids.FluidStack[] actual,net.minecraftforge.fluids.FluidStack[] expected) {
        if(actual.length!=expected.length)return false;
        for(int i=0;i<actual.length;i++)if(actual[i]==null||expected[i]==null){if(actual[i]!=expected[i])return false;}else if(actual[i].amount!=expected[i].amount||!actual[i].isFluidEqual(expected[i]))return false;
        return true;
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event){FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.START&&naturalBatchHost!=null){try{keepRegionLoaded(naturalBatchHost,naturalBatchState);}catch(Exception e){throw new IllegalStateException("Fixture natural region loading",e);}return;}
        if(event.phase!=TickEvent.Phase.END)return;
        if(done){
            if(naturalBatchHost!=null){try{naturalBatchTicks++;if(naturalBatchTicks==10){int escrow=0;for(ItemStack held:(ItemStack[])field(naturalBatchState,"escrow"))if(held!=null&&(Boolean)call(held,"isItemEqual|func_77969_a",naturalBatchSource))escrow+=amount(held);int total=countLike(naturalBatchInput,Integer.MAX_VALUE,naturalBatchSource)+countLike(naturalBatchState,18,naturalBatchSource)+escrow;check("GT.natural-batch.real-input-escrow-conserved",total==16&&!String.valueOf(call(naturalBatchState,"getBatchId")).isEmpty()&&((Number)call(naturalBatchState,"getBatchProgress")).intValue()>0,"ten natural world ticks, native bus+journal+private escrow="+total+"; "+stateDetail(naturalBatchHost,naturalBatchState));telemetry(naturalBatchHost,naturalBatchPlayer,naturalBatchState,"running-batch");}
                if(naturalBatchTicks>=450){ItemStack config=(ItemStack)call(naturalBatchInput,"getStackInSlot|func_70301_a",naturalCircuitSlot);check("GT.natural-batch.native-output-once",outputItems(naturalBatchOutput)==naturalBatchExpected&&String.valueOf(call(naturalBatchState,"getBatchId")).isEmpty()&&(Boolean)call(naturalBatchHost,"isAllowedToWork"),"450 natural world ticks from physical native inputs; output="+outputItems(naturalBatchOutput)+" expected="+naturalBatchExpected+"; "+stateDetail(naturalBatchHost,naturalBatchState));check("GT.natural-batch.native-circuit-preserved",preservedCircuit(config,naturalCircuit),"450 natural ticks preserve native ghost(0) config circuit while consuming normal recipe inputs");naturalBatchHost=null;finish();}
            }catch(Throwable t){check("GT.natural-batch.execution",false,t.toString());t.printStackTrace();naturalBatchHost=null;finish();}return;}
            if(observationHost==null)return;
            try{Object state=call(call(observationHost,"getMetaTileEntity"),"getCriticalState");naturalTicks++;
                if(naturalTicks==150){check("GT.natural.fabrication-waits-over-100-ticks",(Boolean)call(observationHost,"isAllowedToWork")&&(Boolean)call(state,"isEnabled")&&((Number)call(state,"getState")).intValue()==1,"150 natural server ticks; GT timer="+call(observationHost,"getTimer")+" status="+call(state,"getStatus"));call(observationHost,"disableWorking");pausedEnergy=((Number)call(observationHost,"getStoredEU")).longValue();pausedCursor=((Number)call(state,"getCursor")).intValue();}
                if(naturalTicks>=180){check("GT.natural.soft-hammer-not-auto-restored",!(Boolean)call(observationHost,"isAllowedToWork")&&!(Boolean)call(state,"isEnabled")&&((Number)call(state,"getCursor")).intValue()==pausedCursor&&((Number)call(observationHost,"getStoredEU")).longValue()==pausedEnergy,"30 further natural GT ticks after native disableWorking");Object world=call(observationHost,"getWorld|func_145831_w");Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);telemetry(observationHost,player,state,"paused-natural-job");restartExpected=snapshot(observationHost,state);observationHost=null;finish();}
            }catch(Throwable t){check("GT.natural.execution",false,t.toString());t.printStackTrace();observationHost=null;finish();}return;
        }done=true;
        try{if(Boolean.getBoolean("critical.restart"))restart();else tests();}catch(Throwable t){check("execution",false,t.toString());t.printStackTrace();}
        if(observationHost==null&&naturalBatchHost==null)finish();
    }
    void tests()throws Exception {
        Object server=FMLCommonHandler.instance().getMinecraftServerInstance();Object world=call(server,"worldServerForDimension|func_71218_a",0);
        Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);
        Object[] registry=(Object[])field(Class.forName("gregtech.api.GregTechAPI"),"METATILEENTITIES");List<Integer> ids=new ArrayList<Integer>();
        for(int i=0;i<registry.length;i++)if(registry[i]!=null&&registry[i].getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine"))ids.add(i);
        check("GT.registry.ten-critical-machines",ids.size()==10,"ids="+ids);
        List<ItemStack> controllerItems=new ArrayList<ItemStack>();int index=0;Set<Object> structureKinds=new HashSet<Object>();
        for(int id:ids){
            Object prototype=registry[id];String location=String.valueOf(prototype.getClass().getProtectionDomain().getCodeSource().getLocation());
            check("GT.registry.binding."+id,location.contains("production-gtsr.jar"),location);
            ItemStack stack=(ItemStack)call(prototype,"getStackForm",1L);controllerItems.add(copy(stack));Object item=call(stack,"getItem|func_77973_b");
            check("GT.controller.item."+id,item.getClass().getName().equals("gregtech.common.blocks.ItemMachines")&&((Number)call(stack,"getItemDamage|func_77960_j")).intValue()==id,"item="+item.getClass().getName());
            int x=-19+index++*3,y=70,z=-33;call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
            call(player,"setPosition|func_70107_b",x+.5,y+.5,z+.5);
            boolean placed=(Boolean)call(item,"placeBlockAt",stack,player,world,x,y,z,1,.5f,1f,.5f,0);
            Object host=call(world,"getTileEntity|func_147438_o",x,y,z);
            check("GT.place.actual-base."+id,placed&&host!=null&&host.getClass().getName().equals("gregtech.api.metatileentity.BaseMetaTileEntity"),"placed="+placed+" host="+(host==null?null:host.getClass().getName()));
            if(host==null)continue;
            Object machine=call(host,"getMetaTileEntity"),state=call(machine,"getCriticalState");
            check("GT.place.candidate-mte."+id,machine.getClass()==prototype.getClass(),machine.getClass().getName());
            check("GT.place.state-bound-host."+id,call(state,"getHost")==host,"production engine bound to actual GT world tile");
            Object preset=call(machine,"getPresetKind"),kind=call(state,"getKind");
            check("GT.place.preset."+id,preset==null||(preset==kind),"preset="+preset+" state="+kind);
            if(structureKinds.add(kind))structures(machine,state,world);
            Object tooltip=call(machine,"createTooltip");String[] information=(String[])call(tooltip,"getInformation"),structure=(String[])call(tooltip,"getStructureInformation");
            boolean resolved=true;for(String line:information)resolved&=!line.contains("gtsr.critical.");for(String line:structure)resolved&=!line.contains("gtsr.critical.");
            check("GT.tooltip."+id,information.length>=7&&structure.length>=7&&resolved,"information="+Arrays.toString(information)+" structure="+Arrays.toString(structure));
            ItemStack marker=copy(stack);call(state,"setInventorySlotContents|func_70299_a",0,marker);
            NBTTagCompound n=new NBTTagCompound();call(host,"writeToNBT|func_145841_b",n);
            Object restored=call(Class.forName("net.minecraft.tileentity.TileEntity"),"createAndLoadEntity|func_145827_c",n);
            check("GT.nbt.registered-te-factory."+id,restored!=null&&restored.getClass()==host.getClass(),"vanilla registered TileEntity.createAndLoadEntity; nbt="+n);
            if(restored==null)continue;
            call(world,"setTileEntity|func_147455_a",x,y,z,restored);Object restoredMachine=call(restored,"getMetaTileEntity"),restoredState=call(restoredMachine,"getCriticalState");
            ItemStack restoredStack=(ItemStack)call(restoredState,"getStackInSlot|func_70301_a",0);
            check("GT.nbt.mte-and-inventory."+id,restoredMachine.getClass()==prototype.getClass()&&restoredStack!=null&&((Number)call(restoredStack,"getItemDamage|func_77960_j")).intValue()==id,"host/meta/engine reconstructed by production GT NBT loader");
            check("GT.nbt.preset."+id,preset==null||call(restoredState,"getKind")==preset,"restored kind="+call(restoredState,"getKind"));
        }
        recipes(controllerItems);if(Boolean.getBoolean("critical.hatchGuards"))hatchGuards(world,player,registry);primitiveGT(world,player,registry);
    }
    void hatchGuards(Object world,Object player,Object[] registry)throws Exception {
        Class<?> state=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMachineState");String[] names={"Hatch_Input_Bus_IV","Hatch_Input_IV","Hatch_Input_Multi_2x2_EV","Hatch_Output_Bus_IV","Hatch_Output_ULV","Hatch_Energy_UV","Hatch_Dynamo_UHV","Hatch_Input_Bus_ME","Hatch_Input_ME","Hatch_CraftingInput_Bus_ME"};
        for(int i=0;i<names.length;i++){ItemStack item=itemList(names[i]);int id=((Number)call(item,"getItemDamage|func_77960_j")).intValue();boolean expected=i<7,actual=(Boolean)call(state,"isSupportedHatchMeta",registry[id]);check("GT.hatch-support.registered-template."+names[i],actual==expected,"actual registered MTE="+registry[id].getClass().getName()+" supported="+actual+" expected="+expected);Object host=place(world,player,item,4121+i*3,70,-33);check("GT.hatch-support.actual-host."+names[i],(Boolean)call(state,"isStandardHatchHost",host)==expected,"genuine GT ItemMachines placement and production shared host predicate; no AE network construction/tick");call(world,"setBlockToAir|func_147468_f",4121+i*3,70,-33);}
        for(Object mte:registry)if(mte!=null&&mte.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine")){check("GT.hatch-support.no-crafting-ME-buffer",!(Boolean)invokeExact(mte,"supportsCraftingMEBuffer",boolean.class,new Class<?>[0],new Object[0]),"production standard GT ME crafting buffer integration explicitly disabled");break;}
    }
    void structures(Object machine,Object state,Object world)throws Exception {
        Class<?> library=Class.forName("com.miaokatze.gtsr.common.critical.CriticalStructures");Object kind=call(state,"getKind"),definition=call(machine,"getStructureDefinition");
        check("GT.structure.real-definition."+kind,definition.getClass().getName().equals("com.gtnewhorizon.structurelib.structure.StructureDefinition"),definition.getClass().getName());
        Map<?,?> spaces=(Map<?,?>)field(definition,"occupiedSpaces");Map<?,?> pieces=(Map<?,?>)call(definition,"getShapes");
        check("GT.structure.three-tiers."+kind,pieces.size()==3,"pieces="+pieces.keySet());
        Method contained=definition.getClass().getMethod("isContainedInStructure",String.class,int.class,int.class,int.class);
        for(Object tier:Class.forName("com.miaokatze.gtsr.common.critical.CriticalTier").getEnumConstants()){
            String name=(String)call(library,"piece",tier);List<?> voxels=(List<?>)call(library,"voxels",tier,kind),ports=(List<?>)call(library,"hatchPorts",tier,kind);int top=0;
            for(Object v:voxels)top=Math.max(top,((Number)field(v,"y")).intValue());
            boolean matching=((Collection<?>)spaces.get(name)).size()==voxels.size()+1;
            int sampled=0;for(int vi=0;vi<voxels.size();vi+=Math.max(1,voxels.size()/16)){Object v=voxels.get(vi);matching&=(Boolean)contained.invoke(definition,name,76-((Number)field(v,"x")).intValue(),top-((Number)field(v,"y")).intValue(),((Number)field(v,"z")).intValue());sampled++;}
            matching&=(Boolean)contained.invoke(definition,name,36,top,32);
            Object[] elements=(Object[])call(definition,"getStructureFor",name);
            check("GT.structure.coordinates."+kind+"."+tier,matching&&elements.length>1,"compiled occupied cardinality="+voxels.size()+"; sampled "+sampled+" canonical coordinates + controller; full preview matrix belongs to client; top="+top+" elements="+elements.length);
            boolean safePorts=ports.size()==8;for(Object port:ports){Object material=field(port,"block");safePorts&=((Number)field(port,"y")).intValue()==0&&!((Boolean)field(material,"proxy"))&&!((Boolean)field(material,"animated"))&&!((Boolean)field(material,"transparent"));}
            check("GT.structure.hatch-ports."+kind+"."+tier,safePorts,"ports="+ports.size()+" all static opaque base blocks");
        }
        int displayElements=0;boolean displayValid=true,noFreePlace=true,survivalReject=true;
        for(Object element:((Map<?,?>)call(definition,"getElements")).values())if(element.getClass().getName().endsWith("$MaterialElement")){
            displayElements++;Object visible=field(element,"visible");displayValid&=!((Boolean)field(visible,"proxy"));
            Object placement=call(element,"getBlocksToPlace",machine,world,1201,70,201,null,null);Iterable<?> stacks=(Iterable<?>)call(placement,"getStacks");boolean has=false;for(Object stack:stacks){has=true;displayValid&=stack instanceof ItemStack&&call(stack,"getItem|func_77973_b")!=null;}displayValid&=has;
            noFreePlace&=!(Boolean)call(element,"placeBlock",machine,world,1201,70,201,null);
            survivalReject&=String.valueOf(call(element,"survivalPlaceBlock",machine,world,1201,70,201,null,null)).equals("REJECT");
        }
        check("GT.structure.display-materials."+kind,displayElements>0&&displayValid,"actual MaterialElement BlocksToPlace exposes non-proxy material ItemStacks; count="+displayElements);
        check("GT.structure.paid-build-boundary."+kind,noFreePlace&&survivalReject,"actual element placeBlock/survivalPlaceBlock reject unpaid construction in real server world");
    }
    Object place(Object world,Object player,ItemStack stack,int x,int y,int z)throws Exception {
        call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);call(player,"setPosition|func_70107_b",x+.5,y+.5,z+.5);
        Object item=call(stack,"getItem|func_77973_b");
        if(!(Boolean)call(item,"placeBlockAt",stack,player,world,x,y,z,1,.5f,1f,.5f,0))throw new IllegalStateException("GT place failed at "+x+","+y+","+z);
        return call(world,"getTileEntity|func_147438_o",x,y,z);
    }
    ItemStack itemList(String name)throws Exception {Class<?> type=Class.forName("gregtech.api.enums.ItemList");Object value=Enum.valueOf((Class)type,name);return (ItemStack)call(value,"get",1L,new Object[0]);}
    boolean breakCanceled(Object world,Object player,Object host)throws Exception {
        int x=((Number)field(host,"field_145851_c")).intValue(),y=((Number)field(host,"field_145848_d")).intValue(),z=((Number)field(host,"field_145849_e")).intValue();
        Object block=call(world,"getBlock|func_147439_a",x,y,z);int metadata=((Number)call(world,"getBlockMetadata|func_72805_g",x,y,z)).intValue();
        Object event=Class.forName("net.minecraftforge.event.world.BlockEvent$BreakEvent").getConstructor(int.class,int.class,int.class,net.minecraft.world.World.class,net.minecraft.block.Block.class,int.class,net.minecraft.entity.player.EntityPlayer.class).newInstance(x,y,z,world,block,metadata,player);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post((cpw.mods.fml.common.eventhandler.Event)event);
        return (Boolean)call(event,"isCanceled");
    }
    void primitiveGT(Object world,Object player,Object[] registry)throws Exception {
        Object loom=null,turbine=null;for(Object p:registry)if(p!=null&&p.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine")){Object preset=call(p,"getPresetKind");if(preset==null)loom=p;else if(((Enum) preset).name().equals("TURBINE"))turbine=p;}
        if(loom==null||turbine==null)throw new IllegalStateException("Loom/turbine registration missing");
        Object host=place(world,player,(ItemStack)call(loom,"getStackForm",1L),501,70,-33);Object machine=call(host,"getMetaTileEntity"),state=call(machine,"getCriticalState");
        check("GT.break.idle-empty-allowed",!breakCanceled(world,player,host),"real Forge BreakEvent on empty idle GT controller");
        call(state,"setInventorySlotContents|func_70299_a",0,(ItemStack)call(loom,"getStackForm",1L));
        check("GT.break.inventory-blocked",breakCanceled(world,player,host),"real Forge BreakEvent preserves input inventory");call(state,"setInventorySlotContents|func_70299_a",0,null);
        Class<?> tiers=Class.forName("com.miaokatze.gtsr.common.critical.CriticalTier"),kinds=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMachineKind");Object t1=tiers.getEnumConstants()[0],t3=tiers.getEnumConstants()[2],processing=Enum.valueOf((Class)kinds,"PROCESSING");
        check("GT.pause.configure",(Boolean)call(state,"configure",player,t1,processing,0,0,0),"native GT engine configured for real paid construction");
        int ox=((Number)call(state,"getOriginX")).intValue(),oz=((Number)call(state,"getOriginZ")).intValue();Object provider=call(world,"getChunkProvider|func_72863_F");for(int cx=ox>>4;cx<=(ox+79)>>4;cx++)for(int cz=oz>>4;cz<=(oz+79)>>4;cz++)call(provider,"loadChunk|func_73158_c",cx,cz);
        boolean started=(Boolean)call(state,"startConstruction",player);check("GT.pause.paid-job-start",started,String.valueOf(call(state,"getStatus")));
        if(started){
            check("GT.break.actual-ledger-blocked",breakCanceled(world,player,host),"production startConstruction acquired real job/region ledger");
            ItemStack[] costs=(ItemStack[])call(state,"getConstructionCostsForGui");int slot=0;for(ItemStack source:costs){int left=amount(source);while(left>0){ItemStack part=copy(source);int count=Math.min(64,left);try{set(part,"stackSize",count);}catch(NoSuchFieldException e){set(part,"field_77994_a",count);}call(state,"setInventorySlotContents|func_70299_a",slot++,part);left-=count;}}
            call(host,"increaseStoredEnergyUnits",((Number)call(state,"energyCapacity")).longValue(),false);
            int before=((Number)call(state,"getCursor")).intValue();long euBefore=((Number)call(host,"getStoredEU")).longValue();call(host,"disableWorking");
            for(int i=0;i<5;i++){set(machine,"energySpentThisTick",0L);call(state,"updateEntity|func_145845_h");}
            check("GT.pause.native-disable-working",!(Boolean)call(state,"isEnabled")&&((Number)call(state,"getCursor")).intValue()==before&&((Number)call(host,"getStoredEU")).longValue()==euBefore,"five production engine updates with real GT host disabled; cursor="+before+" eu="+euBefore);
            call(host,"enableWorking");set(machine,"energySpentThisTick",0L);call(state,"updateEntity|func_145845_h");
            check("GT.pause.native-enable-resumes",(Boolean)call(state,"isEnabled")&&((Number)call(state,"getCursor")).intValue()>before&&((Number)call(host,"getStoredEU")).longValue()<euBefore,String.valueOf(call(state,"getStatus")));
        }
        Object energyHost=place(world,player,(ItemStack)call(turbine,"getStackForm",1L),901,70,-33),energyMachine=call(energyHost,"getMetaTileEntity"),energyState=call(energyMachine,"getCriticalState");Object turbineKind=Enum.valueOf((Class)kinds,"TURBINE");
        check("GT.energy.tier3-configure",(Boolean)call(energyState,"configure",player,t3,turbineKind,0,0,0),"native long-policy tests use real Tier 3 GT host");
        long nativeInputBudget=0;
        for(int i=0;i<9;i++){Object inputHost=place(world,player,itemList("Hatch_Energy_UMV"),904+i*2,70,-33),input=call(inputHost,"getMetaTileEntity");long inputStore=((Number)call(input,"maxEUStore")).longValue();call(inputHost,"setStoredEU",inputStore);((List)field(energyMachine,"mEnergyHatches")).add(input);nativeInputBudget+=((Number)call(input,"maxEUInput")).longValue()*((Number)call(input,"maxWorkingAmperesIn")).longValue();}
        long longDemand=(long)Integer.MAX_VALUE+1024L;long totalBefore=((Number)call(energyMachine,"availableCriticalEnergy")).longValue();boolean consumed=(Boolean)call(energyMachine,"consumeCriticalEnergy",longDemand);long totalAfter=((Number)call(energyMachine,"availableCriticalEnergy")).longValue();
        check("GT.energy.long-native-hatch-consumption",consumed&&totalBefore-totalAfter==longDemand,"nine real rated UMV hatches attached to production lists; no cable network claim; demand="+longDemand+" before="+totalBefore+" after="+totalAfter);
        boolean nativeOver=(Boolean)call(energyMachine,"consumeCriticalEnergy",nativeInputBudget-longDemand+1);
        check("GT.energy.native-hatch-per-tick-amp-budget",!nativeOver&&((Number)call(energyMachine,"availableCriticalEnergy")).longValue()==totalAfter,"reject cumulative draw exceeding sum of native maxEUInput*maxWorkingAmperesIn; budget="+nativeInputBudget);
        set(energyMachine,"energySpentThisTick",0L);long budget=((Number)call(t3,"recipeVoltage")).longValue()*16;long beforeReject=((Number)call(energyMachine,"availableCriticalEnergy")).longValue();boolean excessive=(Boolean)call(energyMachine,"consumeCriticalEnergy",budget+1);
        check("GT.energy.input-16A-rejection",!excessive&&((Number)call(energyMachine,"availableCriticalEnergy")).longValue()==beforeReject,"reject >16A before draining real GT buffers");
        List<Object> dynamos=new ArrayList<Object>();for(int i=0;i<18;i++){Object dh=place(world,player,itemList("Hatch_Dynamo_UMV"),960+i*2,70,-33);Object dynamo=call(dh,"getMetaTileEntity");dynamos.add(dh);((List)field(energyMachine,"mDynamoHatches")).add(dynamo);}
        long voltage=((Number)call(call(dynamos.get(0),"getMetaTileEntity"),"maxEUOutput")).longValue(),generated=voltage*18L;call(energyHost,"setStoredEU",0L);long accepted=((Number)call(energyMachine,"emitCriticalEnergy",generated)).longValue();long delivered=0;boolean perHatch=true,lateEmpty=true;for(int i=0;i<dynamos.size();i++){long stored=((Number)call(dynamos.get(i),"getStoredEU")).longValue();delivered+=stored;perHatch&=stored<=voltage;if(i>=16)lateEmpty&=stored==0;}
        long buffered=((Number)call(energyHost,"getStoredEU")).longValue();
        check("GT.energy.output-native-16A",delivered==voltage*16&&perHatch&&lateEmpty,"18 real UMV dynamos; voltage="+voltage+" delivered="+delivered);
        check("GT.energy.output-int-accumulator-safe",delivered>Integer.MAX_VALUE&&accepted==generated&&buffered+delivered==generated,"GT205 native output helper ran real packets; generated="+generated+" accepted="+accepted+" buffered="+buffered+" delivered="+delivered);
        call(energyHost,"setStoredEU",(long)Integer.MAX_VALUE+987654L);telemetry(energyHost,player,energyState,"buffer-over-int");
        if(started)paidProcessing(world,player,host,machine,state);
        solarFluid(world,player,loom,t1,kinds);
        partialInput(world,player,loom,t1,processing);
        legacyAndNatural(world,player,loom,t1,processing);
    }
    void advance(Object host)throws Exception {call(host,"updateEntity|func_145845_h");}
    void fillInventory(Object inventory,ItemStack[] sources)throws Exception {
        int size=((Number)call(inventory,"getSizeInventory|func_70302_i_")).intValue(),circuitSlot=inventory.getClass().getName().contains("MTEHatchInputBus")?((Number)call(inventory,"getCircuitSlot")).intValue():-1;for(int i=0;i<Math.min(18,size);i++)if(i!=circuitSlot)call(inventory,"setInventorySlotContents|func_70299_a",i,null);
        int slot=0;for(ItemStack source:sources){int left=amount(source);while(left>0){if(slot>=size||slot>=18)throw new IllegalStateException("Input costs exceed slot fixture");ItemStack part=copy(source);int count=Math.min(64,left);try{set(part,"stackSize",count);}catch(NoSuchFieldException e){set(part,"field_77994_a",count);}call(inventory,"setInventorySlotContents|func_70299_a",slot++,part);left-=count;}}
    }
    void finishPaid(Object host,Object state)throws Exception {
        int previous=-1,ticks=0;
        while(((Number)call(state,"getState")).intValue()==1&&ticks++<6000){
            int stage=((Number)call(state,"getStage")).intValue();if(stage!=previous){fillInventory(state,(ItemStack[])call(state,"getConstructionCostsForGui"));call(host,"setStoredEU",((Number)call(state,"energyCapacity")).longValue());previous=stage;}
            advance(host);
        }
        for(int i=0;i<100&&!(Boolean)call(state,"isStructureValidated");i++)advance(host);
        check("GT.paid.complete-to-ready",((Number)call(state,"getState")).intValue()==2&&(Boolean)call(state,"isStructureValidated"),"real registered component/GT energy-paid construction; BaseMetaTileEntity updates="+ticks+" status="+call(state,"getStatus"));
        if(((Number)call(state,"getState")).intValue()!=2)throw new IllegalStateException("Paid build failed: "+call(state,"getStatus"));
    }
    List<Object> installPorts(Object world,Object player,Object state,boolean multi)throws Exception {
        List<?> ports=(List<?>)call(state,"getHatchPorts");String[] names={"Hatch_Input_Bus_IV","Hatch_Output_Bus_IV",multi?"Hatch_Input_Multi_2x2_EV":"Hatch_Input_IV","Hatch_Output_ULV","Hatch_Energy_UV","Hatch_Dynamo_UHV"};List<Object> hatches=new ArrayList<Object>();
        for(int i=0;i<names.length;i++){Object port=ports.get(i);int x=((Number)call(state,"getOriginX")).intValue()+((Number)field(port,"x")).intValue(),y=((Number)call(state,"getOriginY")).intValue()+((Number)field(port,"y")).intValue(),z=((Number)call(state,"getOriginZ")).intValue()+((Number)field(port,"z")).intValue();Object host=place(world,player,itemList(names[i]),x,y,z);hatches.add(host);}
        return hatches;
    }
    void supply(Object state,List<Object> hatches,Object batch)throws Exception {
        Object inputBus=call(hatches.get(0),"getMetaTileEntity"),fluidInput=call(hatches.get(2),"getMetaTileEntity");fillInventory(inputBus,(ItemStack[])field(batch,"itemInputs"));
        for(net.minecraftforge.fluids.FluidStack f:(net.minecraftforge.fluids.FluidStack[])field(batch,"fluidInputs")){int filled=((Number)call(fluidInput,"fill",net.minecraftforge.common.util.ForgeDirection.UNKNOWN,f.copy(),true)).intValue();check("GT.port.native-fluid-fill."+field(batch,"id"),filled==f.amount,"actual standard GT input hatch accepted="+filled);}
        Object energy=call(hatches.get(4),"getMetaTileEntity");call(hatches.get(4),"setStoredEU",((Number)call(energy,"maxEUStore")).longValue());
    }
    int outputItems(Object bus)throws Exception {int total=0,size=((Number)call(bus,"getSizeInventory|func_70302_i_")).intValue();for(int i=0;i<size;i++){ItemStack item=(ItemStack)call(bus,"getStackInSlot|func_70301_a",i);if(item!=null)total+=amount(item);}return total;}
    int countLike(Object inventory,int limit,ItemStack sample)throws Exception {int total=0,size=Math.min(limit,((Number)call(inventory,"getSizeInventory|func_70302_i_")).intValue());for(int i=0;i<size;i++){ItemStack item=(ItemStack)call(inventory,"getStackInSlot|func_70301_a",i);if(item!=null&&(Boolean)call(item,"isItemEqual|func_77969_a",sample))total+=amount(item);}return total;}
    String stateDetail(Object host,Object state)throws Exception {return "state="+call(state,"getState")+" reason="+call(state,"getReasonCode")+" enabled="+call(state,"isEnabled")+" GTallowed="+call(host,"isAllowedToWork")+" validated="+call(state,"isStructureValidated")+" batch="+call(state,"getBatchId")+" progress="+call(state,"getBatchProgress")+"/"+call(state,"getBatchDuration")+" EUt="+call(state,"getBatchEuPerTick");}
    void telemetry(Object host,Object player,Object state,String label)throws Exception {
        Object machine=call(host,"getMetaTileEntity");String[] info=(String[])invokeExact(machine,"getInfoData",String[].class,new Class<?>[0],new Object[0]);NBTTagCompound tag=new NBTTagCompound();
        int x=((Number)field(host,"field_145851_c")).intValue(),y=((Number)field(host,"field_145848_d")).intValue(),z=((Number)field(host,"field_145849_e")).intValue();Object world=call(host,"getWorld|func_145831_w");
        invokeExact(machine,"getWailaNBTData",void.class,new Class<?>[]{net.minecraft.entity.player.EntityPlayerMP.class,net.minecraft.tileentity.TileEntity.class,NBTTagCompound.class,net.minecraft.world.World.class,int.class,int.class,int.class},new Object[]{player,host,tag,world,x,y,z});
        boolean same=true;String[] keys={"progress","maxProgress","criticalState","criticalReason","criticalStage","criticalJobProgress","criticalJobTotal"},methods={"getBatchProgress","getBatchDuration","getState","getReasonCode","getStage","getConstructionPlaced","getJobTotal"};for(int i=0;i<keys.length;i++)same&=((Number)call(tag,"getInteger|func_74762_e",keys[i])).intValue()==((Number)call(state,methods[i])).intValue();
        long eu=(Boolean)call(state,"isWorking")?((Number)call(state,"getBatchEuPerTick")).longValue():0L,buffered=((Number)call(state,"getBufferedEnergy")).longValue(),capacity=((Number)call(state,"energyCapacity")).longValue();same&=((Number)call(tag,"getLong|func_74763_f","energyUsage")).longValue()==eu&&((Number)call(tag,"getLong|func_74763_f","criticalBufferedEnergy")).longValue()==buffered&&((Number)call(tag,"getLong|func_74763_f","criticalEnergyCapacity")).longValue()==capacity;if(label.equals("running-batch"))same&=eu>0&&((Number)call(state,"getBatchProgress")).intValue()>0;
        for(String key:new String[]{"energyUsage","criticalBufferedEnergy","criticalEnergyCapacity"})same&=((Number)call(call(tag,"getTag|func_74781_a",key),"getId|func_74732_a")).intValue()==4;
        check("GT.telemetry.waila-native-nbt."+label,same,"production Waila server NBT matches actual journal, progress and long energy; usage="+eu+" buffered="+buffered+" capacity="+capacity);
        String formatted=String.valueOf(call(Class.forName("com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil"),"formatNumber",buffered));boolean resolved=info.length>1&&info[info.length-1].contains(formatted);for(String line:info)resolved&=!line.contains("gtsr.critical.");check("GT.telemetry.scanner-native-lines."+label,resolved,"actual scanner lines="+Arrays.toString(info));
    }
    Object firstBatch(Object state)throws Exception {return ((List<?>)call(Class.forName("com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes"),"list",call(state,"getConfiguration"))).get(0);}
    void paidProcessing(Object world,Object player,Object host,Object machine,Object state)throws Exception {
        finishPaid(host,state);List<Object> hatches=installPorts(world,player,state,false);for(int i=0;i<30;i++)advance(host);
        check("GT.port.six-native-hatch-types",((List<?>)field(machine,"mInputBusses")).size()==1&&((List<?>)field(machine,"mOutputBusses")).size()==1&&((List<?>)field(machine,"mInputHatches")).size()==1&&((List<?>)field(machine,"mOutputHatches")).size()==1&&((List<?>)field(machine,"mEnergyHatches")).size()==1&&((List<?>)field(machine,"mDynamoHatches")).size()==1,"physical paid structure ports discovered by production host");
        Object batch=firstBatch(state);supply(state,hatches,batch);Object output=call(hatches.get(1),"getMetaTileEntity");int expected=0;for(ItemStack item:(ItemStack[])field(batch,"itemOutputs"))expected+=amount(item);
        Object input=call(hatches.get(0),"getMetaTileEntity");int circuitSlot=((Number)call(input,"getCircuitSlot")).intValue();ItemStack circuit=physicalCircuit();call(input,"setInventorySlotContents|func_70299_a",circuitSlot,circuit);ItemStack installedCircuit=(ItemStack)call(input,"getStackInSlot|func_70301_a",circuitSlot);check("GT.fixture.native-circuit-immediate-storage",preservedCircuit(installedCircuit,circuit),"slot="+circuitSlot+" invSize="+call(input,"getSizeInventory|func_70302_i_")+" backingLength="+((Object[])field(input,"mInventory")).length+" source="+circuit+" stored="+installedCircuit);for(int i=0;i<5;i++)advance(host);
        ItemStack inputSample=((ItemStack[])field(batch,"itemInputs"))[0];int escrow=0;for(ItemStack held:(ItemStack[])field(state,"escrow"))if(held!=null&&(Boolean)call(held,"isItemEqual|func_77969_a",inputSample))escrow+=amount(held);int total=countLike(input,Integer.MAX_VALUE,inputSample)+countLike(state,18,inputSample)+escrow;
        check("GT.port.full-native-stack-conserved",total==amount(inputSample),"actual bus + journal inputs + private escrow="+total+" expected="+amount(inputSample)+"; "+stateDetail(host,state));ItemStack circuitAfter=(ItemStack)call(input,"getStackInSlot|func_70301_a",circuitSlot);check("GT.port.native-circuit-config-preserved",preservedCircuit(circuitAfter,circuit),"native nonconsumable ghost(0) circuit slot="+circuitSlot+" expected="+circuit+" actual="+circuitAfter+" count="+(circuitAfter==null?-1:amount(circuitAfter)));
        int ticks=0;while(outputItems(output)<expected&&ticks++<700)advance(host);
        check("GT.batch.native-input-to-output",outputItems(output)==expected&&String.valueOf(call(state,"getBatchId")).isEmpty(),"actual GT input bus import/paid engine/GT output bus; ticks="+ticks+" items="+outputItems(output)+"; "+stateDetail(host,state));
        supply(state,hatches,batch);for(int i=0;i<25;i++)advance(host);telemetry(host,player,state,"running-batch");String batchId=String.valueOf(call(state,"getBatchId"));
        List<?> voxels=(List<?>)call(Class.forName("com.miaokatze.gtsr.common.critical.CriticalStructures"),"voxels",call(state,"getTier"),call(state,"getKind"));Object damaged=null;for(Object v:voxels)if(((Number)field(v,"y")).intValue()>2){damaged=v;break;}
        int dx=((Number)call(state,"getOriginX")).intValue()+((Number)field(damaged,"x")).intValue(),dy=((Number)call(state,"getOriginY")).intValue()+((Number)field(damaged,"y")).intValue(),dz=((Number)call(state,"getOriginZ")).intValue()+((Number)field(damaged,"z")).intValue();call(world,"setBlockToAir|func_147468_f",dx,dy,dz);
        for(int i=0;i<100&&(Boolean)call(state,"isStructureValidated");i++)advance(host);int paused=((Number)call(state,"getCycleTicks")).intValue();for(int i=0;i<10;i++)advance(host);
        check("GT.repair.invalid-ready-pauses-private-batch",!(Boolean)call(state,"isStructureValidated")&&!batchId.isEmpty()&&String.valueOf(call(state,"getBatchId")).equals(batchId)&&((Number)call(state,"getCycleTicks")).intValue()==paused,"real missing structural block; private escrow/batch survived GT stopMachine");
        call(player,"setPosition|func_70107_b",((Number)field(host,"field_145851_c")).intValue()+.5,70.5,-32.5);
        boolean repairing=(Boolean)call(state,"repairStructure",player);check("GT.repair.owner-start",repairing,String.valueOf(call(state,"getStatus")));
        int previous=-1;for(int i=0;i<700&&((Number)call(state,"getState")).intValue()==4;i++){int stage=((Number)call(state,"getStage")).intValue();if(stage!=previous){fillInventory(state,(ItemStack[])call(state,"getConstructionCostsForGui"));call(host,"setStoredEU",((Number)call(state,"energyCapacity")).longValue());previous=stage;}advance(host);}
        ticks=0;while(outputItems(output)<expected*2&&ticks++<700)advance(host);
        check("GT.repair.native-batch-resumes-once",outputItems(output)==expected*2&&String.valueOf(call(state,"getBatchId")).isEmpty()&&(Boolean)call(state,"isStructureValidated"),"physical repaired geometry, native output count="+outputItems(output));
        fillInventory(state,new ItemStack[0]);boolean dismantle=(Boolean)call(state,"startDismantle",player);check("GT.dismantle.start",dismantle,String.valueOf(call(state,"getStatus")));call(host,"setStoredEU",((Number)call(state,"energyCapacity")).longValue());
        for(int i=0;i<3000&&((Number)call(state,"getState")).intValue()!=0;i++)advance(host);
        boolean survived=true;for(Object hatch:hatches)survived&=call(world,"getTileEntity|func_147438_o",((Number)field(hatch,"field_145851_c")).intValue(),((Number)field(hatch,"field_145848_d")).intValue(),((Number)field(hatch,"field_145849_e")).intValue())==hatch;
        check("GT.dismantle.preserves-player-hatches",((Number)call(state,"getState")).intValue()==0&&survived,"real paid dismantle removed journalled structure while all six native hatches remain");
    }
    void solarFluid(Object world,Object player,Object loom,Object tier,Class<?> kinds)throws Exception {
        Object host=place(world,player,(ItemStack)call(loom,"getStackForm",1L),2009,70,-33),state=call(call(host,"getMetaTileEntity"),"getCriticalState");Object solar=Enum.valueOf((Class)kinds,"SOLAR");call(state,"configure",player,tier,solar,0,0,0);int ox=((Number)call(state,"getOriginX")).intValue(),oz=((Number)call(state,"getOriginZ")).intValue();Object provider=call(world,"getChunkProvider|func_72863_F");for(int x=ox>>4;x<=(ox+79)>>4;x++)for(int z=oz>>4;z<=(oz+79)>>4;z++)call(provider,"loadChunk|func_73158_c",x,z);
        if(!(Boolean)call(state,"startConstruction",player))throw new IllegalStateException("Solar paid start failed");finishPaid(host,state);List<Object> hatches=installPorts(world,player,state,true);for(int i=0;i<30;i++)advance(host);
        Object batch=firstBatch(state);supply(state,hatches,batch);Object output=call(hatches.get(3),"getMetaTileEntity"),multi=call(hatches.get(2),"getMetaTileEntity");for(int i=0;i<3;i++)advance(host);check("GT.fluid.solar-batch-start-before-extra-input",!String.valueOf(call(state,"getBatchId")).isEmpty(),"water-only exact batch first acquired real escrow");net.minecraftforge.fluids.FluidStack secondWater=gregtech.api.enums.Materials.Water.getFluid(1);call(multi,"fill",net.minecraftforge.common.util.ForgeDirection.UNKNOWN,secondWater.copy(),true);
        net.minecraftforge.fluids.FluidStack extra=gregtech.api.enums.Materials.Oxygen.getGas(123);int extraFilled=((Number)call(multi,"fill",net.minecraftforge.common.util.ForgeDirection.UNKNOWN,extra.copy(),true)).intValue();
        long expected=0;for(net.minecraftforge.fluids.FluidStack f:(net.minecraftforge.fluids.FluidStack[])field(batch,"fluidOutputs"))expected+=f.amount;long exported=0;boolean outputType=true;int ticks=0,drains=0;int capacity=((Number)call(output,"getCapacity")).intValue();
        while(exported<expected&&ticks++<1000){advance(host);net.minecraftforge.fluids.FluidStack drained=(net.minecraftforge.fluids.FluidStack)call(output,"drain",net.minecraftforge.common.util.ForgeDirection.UNKNOWN,capacity,true);if(drained!=null){outputType&=drained.isFluidEqual(((net.minecraftforge.fluids.FluidStack[])field(batch,"fluidOutputs"))[0]);exported+=drained.amount;drains++;}}
        check("GT.fluid.native-partial-large-output",expected==160000&&expected>capacity&&exported==expected&&drains>1&&outputType,"solar160000L through one native "+capacity+"L output hatch across "+drains+" drains; ticks="+ticks+" exported="+exported);
        long oxygen=0,water=0;for(net.minecraftforge.fluids.FluidTankInfo tank:(net.minecraftforge.fluids.FluidTankInfo[])call(state,"getTankInfo",net.minecraftforge.common.util.ForgeDirection.UNKNOWN))if(tank.fluid!=null){if(tank.fluid.isFluidEqual(extra))oxygen+=tank.fluid.amount;if(tank.fluid.isFluidEqual(secondWater))water+=tank.fluid.amount;}
        check("GT.fluid.native-multi-input-all-tanks",extraFilled==123&&oxygen==123&&water==1,"actual multi hatch loaded water+oxygen; second fluid arrived in production journal without getFluid-only loss; oxygen="+oxygen+" water="+water);
    }
    void partialInput(Object world,Object player,Object loom,Object tier,Object kind)throws Exception {
        Object host=place(world,player,(ItemStack)call(loom,"getStackForm",1L),2509,70,-33),state=call(call(host,"getMetaTileEntity"),"getCriticalState");call(state,"configure",player,tier,kind,0,0,0);List<Object> hatches=installPorts(world,player,state,false);Object bus=call(hatches.get(0),"getMetaTileEntity");ItemStack source=((ItemStack[])field(firstBatch(state),"itemInputs"))[0],preexisting=copy(source);try{set(preexisting,"stackSize",60);}catch(NoSuchFieldException e){set(preexisting,"field_77994_a",60);}call(state,"setInventorySlotContents|func_70299_a",0,preexisting);call(bus,"setInventorySlotContents|func_70299_a",0,copy(source));
        int configSlot=((Number)call(bus,"getCircuitSlot")).intValue();ItemStack circuit=physicalCircuit();call(bus,"setInventorySlotContents|func_70299_a",configSlot,copy(circuit));call(bus,"setInventorySlotContents|func_70299_a",1,copy(circuit));for(int i=0;i<10;i++)advance(host);
        ItemStack first=(ItemStack)call(state,"getStackInSlot|func_70301_a",0),second=(ItemStack)call(state,"getStackInSlot|func_70301_a",1),config=(ItemStack)call(bus,"getStackInSlot|func_70301_a",configSlot);int total=countLike(state,18,source)+countLike(bus,Integer.MAX_VALUE,source);
        check("GT.port.partial-target-capacity-conserved",first!=null&&amount(first)==64&&second!=null&&amount(second)==12&&total==76&&call(bus,"getStackInSlot|func_70301_a",0)==null,"real native source16 + preexisting journal60 distributed64+12; total="+total+"; no active business batch seeded");
        check("GT.port.circuit-slot-versus-consumable-circuit",preservedCircuit(config,circuit)&&call(bus,"getStackInSlot|func_70301_a",1)==null&&countLike(state,18,circuit)==1,"native configured circuit stays slot"+configSlot+" while identical ordinary ingredient slot transfers normally; configured="+config+" ordinary="+call(bus,"getStackInSlot|func_70301_a",1)+" journalCircuitCount="+countLike(state,18,circuit));
    }
    void legacyAndNatural(Object world,Object player,Object loom,Object tier,Object kind)throws Exception {
        Object provider=call(world,"getChunkProvider|func_72863_F");call(provider,"loadChunk|func_73158_c",1501>>4,-33>>4);Object oldBlock=field(Class.forName("com.miaokatze.gtsr.common.critical.CriticalRuntime"),"controller");call(world,"setBlock|func_147465_d",1501,70,-33,oldBlock,0,3);Object old=call(world,"getTileEntity|func_147438_o",1501,70,-33);NBTTagCompound legacy=new NBTTagCompound();call(old,"writeToNBT|func_145841_b",legacy);call(legacy,"setLong|func_74772_a","energy",123456789L);call(old,"readFromNBT|func_145839_a",legacy);Object oldState=call(old,"getCriticalState");call(oldState,"setInventorySlotContents|func_70299_a",0,(ItemStack)call(loom,"getStackForm",1L));
        call(player,"setPosition|func_70107_b",1501.5,70.5,-32.5);call(oldState,"configure",player,tier,kind,0,0,0);int oldOx=((Number)call(oldState,"getOriginX")).intValue(),oldOz=((Number)call(oldState,"getOriginZ")).intValue();for(int x=oldOx>>4;x<=(oldOx+79)>>4;x++)for(int z=oldOz>>4;z<=(oldOz+79)>>4;z++)call(provider,"loadChunk|func_73158_c",x,z);
        boolean legacyStarted=(Boolean)call(oldState,"startConstruction",player);String legacyJob=String.valueOf(call(oldState,"getStructureId"));check("GT.legacy.real-old-job-start",legacyStarted&&!legacyJob.isEmpty(),"legacy engine acquired actual construction region/job before migration");
        long time=((Number)call(world,"getTotalWorldTime|func_82737_E")).longValue();call(call(world,"getWorldInfo|func_72912_H"),"setWorldTotalTime|func_82572_b",((time+19)/20)*20);advance(old);Object migrated=call(world,"getTileEntity|func_147438_o",1501,70,-33);Object machine=call(migrated,"getMetaTileEntity"),state=call(machine,"getCriticalState");
        check("GT.legacy.actual-auto-migration",migrated.getClass().getName().equals("gregtech.api.metatileentity.BaseMetaTileEntity")&&machine.getClass().getName().equals("com.miaokatze.gtsr.common.machine.MTECriticalMachine"),"legacy world tile natural conversion entry replaced with real registered GT host");
        check("GT.legacy.inventory-energy-preserved",call(state,"getStackInSlot|func_70301_a",0)!=null&&((Number)call(migrated,"getStoredEU")).longValue()==123456789L,"production migration copied real inventory and legacy long EU buffer");
        check("GT.legacy.active-job-region-preserved",legacyStarted&&String.valueOf(call(state,"getStructureId")).equals(legacyJob)&&((Number)call(state,"getState")).intValue()==1&&breakCanceled(world,player,migrated),"actual existing legacy construction job survived GT migration with guarded region/ledger");
        int ox=((Number)call(state,"getOriginX")).intValue(),oz=((Number)call(state,"getOriginZ")).intValue();for(int x=ox>>4;x<=(ox+79)>>4;x++)for(int z=oz>>4;z<=(oz+79)>>4;z++)call(provider,"loadChunk|func_73158_c",x,z);
        boolean started=((Number)call(state,"getState")).intValue()==1;check("GT.natural.waiting-job-start",started,String.valueOf(call(state,"getStatus")));if(started)observationHost=migrated;
    }
    JsonObject snapshot(Object host,Object state)throws Exception {NBTTagCompound n=new NBTTagCompound();call(state,"writeToNBT|func_145841_b",n);JsonObject j=new JsonObject();for(String key:new String[]{"owner","structureId","jobId"})j.addProperty(key,String.valueOf(call(n,"getString|func_74779_i",key)));j.addProperty("jobFingerprint",((Number)call(n,"getLong|func_74763_f","jobFingerprint")).longValue());j.addProperty("state",((Number)call(state,"getState")).intValue());j.addProperty("stage",((Number)call(state,"getStage")).intValue());j.addProperty("cursor",((Number)call(state,"getCursor")).intValue());j.addProperty("eu",((Number)call(host,"getStoredEU")).longValue());ItemStack marker=(ItemStack)call(state,"getStackInSlot|func_70301_a",0);j.addProperty("inputMarkerCount",marker==null?0:amount(marker));j.addProperty("allowed",(Boolean)call(host,"isAllowedToWork"));return j;}
    void restart()throws Exception {
        Object server=FMLCommonHandler.instance().getMinecraftServerInstance(),world=call(server,"worldServerForDimension|func_71218_a",0),provider=call(world,"getChunkProvider|func_72863_F");for(int x=1461>>4;x<=(1540)>>4;x++)for(int z=-65>>4;z<=14>>4;z++)call(provider,"loadChunk|func_73158_c",x,z);
        Object host=call(world,"getTileEntity|func_147438_o",1501,70,-33);check("GT.restart.real-world-restored-base",host!=null&&host.getClass().getName().equals("gregtech.api.metatileentity.BaseMetaTileEntity"),"new native process loaded previous private world from disk");if(host==null)return;
        Object state=call(call(host,"getMetaTileEntity"),"getCriticalState");JsonObject previous=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(System.getProperty("critical.restart.expected"))),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("restartExpected"),actual=snapshot(host,state);check("GT.restart.journal-inventory-energy-switch",actual.equals(previous),"persisted expected="+previous+" actual="+actual);
        Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);call(player,"setPosition|func_70107_b",1501.5,70.5,-32.5);check("GT.restart.ledger-break-guard",breakCanceled(world,player,host),"persisted real job protected after process restart");
        call(host,"enableWorking");fillInventory(state,(ItemStack[])call(state,"getConstructionCostsForGui"));call(host,"setStoredEU",((Number)call(state,"energyCapacity")).longValue());int cursor=((Number)call(state,"getCursor")).intValue();for(int i=0;i<5;i++)advance(host);check("GT.restart.real-job-resumes",((Number)call(state,"getCursor")).intValue()>cursor,"saved region/job resumed using actual components and GT host energy; status="+call(state,"getStatus"));
        startNaturalBatch(world,player);
    }
    void keepRegionLoaded(Object host,Object state)throws Exception {Object provider=call(call(host,"getWorld|func_145831_w"),"getChunkProvider|func_72863_F");int ox=((Number)call(state,"getOriginX")).intValue(),oz=((Number)call(state,"getOriginZ")).intValue();for(int x=ox>>4;x<=(ox+79)>>4;x++)for(int z=oz>>4;z<=(oz+79)>>4;z++)call(provider,"loadChunk|func_73158_c",x,z);}
    void startNaturalBatch(Object world,Object player)throws Exception {
        ItemStack loom=(ItemStack)call(Class.forName("com.miaokatze.gtsr.common.critical.CriticalRuntime"),"getLoomControllerStack");Object host=place(world,player,loom,3509,70,-33),state=call(call(host,"getMetaTileEntity"),"getCriticalState");Object tier=Enum.valueOf((Class)Class.forName("com.miaokatze.gtsr.common.critical.CriticalTier"),"T1"),kind=Enum.valueOf((Class)Class.forName("com.miaokatze.gtsr.common.critical.CriticalMachineKind"),"PROCESSING");call(state,"configure",player,tier,kind,0,0,0);keepRegionLoaded(host,state);check("GT.natural-batch.owner-nearby-paid-start",(Boolean)call(state,"startConstruction",player),"real FakePlayer owner positioned by GT placement at controller within permission range");finishPaid(host,state);List<Object> hatches=installPorts(world,player,state,false);for(int i=0;i<30;i++)advance(host);Object batch=firstBatch(state);supply(state,hatches,batch);
        naturalBatchInput=call(hatches.get(0),"getMetaTileEntity");naturalBatchOutput=call(hatches.get(1),"getMetaTileEntity");naturalCircuitSlot=((Number)call(naturalBatchInput,"getCircuitSlot")).intValue();naturalCircuit=physicalCircuit();call(naturalBatchInput,"setInventorySlotContents|func_70299_a",naturalCircuitSlot,copy(naturalCircuit));naturalBatchSource=((ItemStack[])field(batch,"itemInputs"))[0];for(ItemStack item:(ItemStack[])field(batch,"itemOutputs"))naturalBatchExpected+=amount(item);naturalBatchState=state;naturalBatchPlayer=player;naturalBatchHost=host;
        check("GT.natural-batch.fixture-loading-boundary",true,"after component-paid preparation, native inputs supplied without advancing production; ServerTick START loads real 80x80 region each tick; only world ticks execute production for next450ticks");
    }
    void recipes(List<ItemStack> controllers)throws Exception {
        Class<?> maps=Class.forName("com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps");Object union=field(maps,"displayRecipes");Collection<?> unionRecipes=(Collection<?>)call(union,"getAllRecipes");
        Class<?> kindsType=Class.forName("com.miaokatze.gtsr.common.critical.CriticalMachineKind"),tiersType=Class.forName("com.miaokatze.gtsr.common.critical.CriticalTier"),cfgType=Class.forName("com.miaokatze.gtsr.common.critical.CriticalConfiguration");
        int total=0;Set<Integer> unionCodes=new HashSet<Integer>();for(Object r:unionRecipes)unionCodes.add(((Number)field(r,"mSpecialValue")).intValue());
        for(Object kind:kindsType.getEnumConstants()){
            Object map=call(maps,"forKind",kind);Collection<?> registered=(Collection<?>)call(map,"getAllRecipes");int count=0;
            for(Object tier:tiersType.getEnumConstants()){
                Object cfg=cfgType.getConstructor(tiersType,kindsType,int.class,int.class,int.class).newInstance(tier,kind,0,0,0);
                List<?> batches=(List<?>)call(Class.forName("com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes"),"list",cfg);int batchIndex=0;
                for(Object batch:batches){int code=((Number)call(maps,"displayCode",cfg,batchIndex++)).intValue();Object actual=null;for(Object r:registered)if(((Number)field(r,"mSpecialValue")).intValue()==code){actual=r;break;}
                    boolean same=actual!=null&&unionCodes.contains(code)&&((Number)field(actual,"mDuration")).intValue()==((Number)field(batch,"durationTicks")).intValue()&&((Number)field(actual,"mEUt")).longValue()==((Number)field(batch,"euPerTick")).longValue();
                    if(actual!=null)same&=items((ItemStack[])field(actual,"mInputs"),(ItemStack[])field(batch,"itemInputs"))&&items((ItemStack[])field(actual,"mOutputs"),(ItemStack[])field(batch,"itemOutputs"))&&fluids((net.minecraftforge.fluids.FluidStack[])field(actual,"mFluidInputs"),(net.minecraftforge.fluids.FluidStack[])field(batch,"fluidInputs"))&&fluids((net.minecraftforge.fluids.FluidStack[])field(actual,"mFluidOutputs"),(net.minecraftforge.fluids.FluidStack[])field(batch,"fluidOutputs"));
                    check("GT.recipe.example."+code,same,"kind="+kind+" tier="+tier+" batch="+field(batch,"id")+"; actual GT IO quantities/duration/EUt vs authoritative batch");count++;}
            }
            check("GT.recipe.kind-count."+kind,registered.size()==count,"actual="+registered.size()+" authoritative batches="+count);total+=count;
        }
        check("GT.recipe.union",unionRecipes.size()==total&&total==45,"actual="+unionRecipes.size()+" authoritative="+total);
        Collection<?> assembler=(Collection<?>)call(field(Class.forName("gregtech.api.recipe.RecipeMaps"),"assemblerRecipes"),"getAllRecipes");
        for(ItemStack target:controllers){int damage=((Number)call(target,"getItemDamage|func_77960_j")).intValue();boolean found=false;for(Object recipe:assembler)for(ItemStack output:(ItemStack[])field(recipe,"mOutputs"))if(output!=null&&(Boolean)call(target,"isItemEqual|func_77969_a",output))found=true;check("GT.recipe.controller-assembler."+damage,found,"registered real assembler output");}
        if(!controllers.isEmpty()){
            Object crafting=call(Class.forName("net.minecraft.item.crafting.CraftingManager"),"getInstance|func_77594_a");Collection<?> all=(Collection<?>)call(crafting,"getRecipeList|func_77592_b");
            ItemStack loom=(ItemStack)call(Class.forName("com.miaokatze.gtsr.common.critical.CriticalRuntime"),"getLoomControllerStack");boolean found=false;
            for(Object recipe:all){ItemStack output=(ItemStack)call(recipe,"getRecipeOutput|func_77571_b");if(output!=null&&(Boolean)call(loom,"isItemEqual|func_77969_a",output))found=true;}
            check("GT.recipe.loom-crafting-output",found,"registered vanilla CraftingManager recipe output equals GT loom controller");
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer p){return true;}public boolean func_75145_c(net.minecraft.entity.player.EntityPlayer p){return true;}},3,3);
            String[] ingredients={"Robot_Arm_ZPM","Field_Generator_ZPM","Robot_Arm_ZPM","Sensor_ZPM","Casing_ZPM","Emitter_ZPM","Robot_Arm_ZPM","Circuit_Quantummainframe","Robot_Arm_ZPM"};for(int i=0;i<ingredients.length;i++)call(grid,"setInventorySlotContents|func_70299_a",i,itemList(ingredients[i]));
            Object world=call(FMLCommonHandler.instance().getMinecraftServerInstance(),"worldServerForDimension|func_71218_a",0);ItemStack crafted=(ItemStack)call(crafting,"findMatchingRecipe|func_82787_a",grid,world);
            check("GT.recipe.loom-real-quantummainframe-grid",crafted!=null&&(Boolean)call(loom,"isItemEqual|func_77969_a",crafted),"actual 3x3 GT ingredients including ZPM Circuit_Quantummainframe match production recipe");
        }
    }
    void finish(){
        JsonObject result=new JsonObject();boolean pass=true;for(JsonElement row:checks)pass&=row.getAsJsonObject().get("pass").getAsBoolean();result.addProperty("status",pass?"PASS":"FAIL");result.add("checks",checks);if(restartExpected!=null)result.add("restartExpected",restartExpected);result.addProperty("scope","Actual GT registry/placement/NBT; component-paid construction and production BaseMetaTileEntity updates; physical native bus/fluid/energy/dynamo ports, repair/dismantle, legacy conversion and natural switch persistence. Direct primitive energy tests attach real registered hatches to native lists and do not claim cable-network wiring. Client UI/NEI preview and process restart use separate probes.");
        try{Files.write(Paths.get(System.getProperty("critical.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));call(FMLCommonHandler.instance().getMinecraftServerInstance(),"initiateShutdown|func_71263_m");}catch(Exception e){throw new RuntimeException(e);}
    }
}
