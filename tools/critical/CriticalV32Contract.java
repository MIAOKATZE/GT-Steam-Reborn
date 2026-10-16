package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import com.google.gson.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.*;

/** Production completion, periodic integrity and native GT/StructureLib formation checks. */
public final class CriticalV32Contract {
    static net.minecraft.item.Item hatchItem=new gregtech.common.blocks.ItemMachines(AIR);
    static class Input extends MTEHatchInputBus {Input(){super("v32",1,new String[0],null);}@Override public net.minecraft.item.ItemStack getStackForm(long n){return new net.minecraft.item.ItemStack(hatchItem,(int)n,30002);}}
    static class Dynamo extends MTEHatchDynamo {Dynamo(){super("v32",1,new String[0],null);}@Override public net.minecraft.item.ItemStack getStackForm(long n){return new net.minecraft.item.ItemStack(hatchItem,(int)n,30001);}}
    static Field deep(Object o,String name)throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    static void initialize(MTECriticalMachine m)throws Exception {
        set(m,"portSnapshot",new LinkedHashMap<>());set(m,"hatchEnergySpent",new IdentityHashMap<>());
        set(m,"loomValidationTick",Long.MIN_VALUE);
        deep(m,"mInventory").set(m,new net.minecraft.item.ItemStack[2]);
        deep(m,"mStartUpCheck").setInt(m,200);
        Field walker=deep(m,"centerWalker");Constructor<?> ctor=walker.getType().getDeclaredConstructors()[0];ctor.setAccessible(true);walker.set(m,ctor.newInstance(m));
        Field center=deep(m,"center");center.set(m,center.getType().getDeclaredConstructor().newInstance());
        for(Class<?> c=m.getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())&&!Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);if(f.get(m)==null)f.set(m,f.getType().isInterface()?new ArrayList<>():f.getType().getDeclaredConstructor().newInstance());
            }
        }
    }
    static void invoke(Object value,String method)throws Exception {
        Method m=value.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(value);
    }
    static MTECriticalMachine attach(CriticalMachineState s)throws Exception {
        SparseWorld world=(SparseWorld)s.worldObj;
        net.minecraft.world.chunk.Chunk chunk=(net.minecraft.world.chunk.Chunk)unsafe.allocateInstance(net.minecraft.world.chunk.Chunk.class);
        world.chunks=(net.minecraft.world.chunk.IChunkProvider)Proxy.newProxyInstance(
            net.minecraft.world.chunk.IChunkProvider.class.getClassLoader(),new Class<?>[]{net.minecraft.world.chunk.IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists")?world.loaded:m.getName().equals("provideChunk")?chunk:null);
        Field provider=net.minecraft.world.World.class.getDeclaredField("chunkProvider");provider.setAccessible(true);provider.set(world,world.chunks);
        for (net.minecraft.tileentity.TileEntity tile:world.tiles.values()) {
            MTECriticalMachine machine=(MTECriticalMachine)((BaseMetaTileEntity)tile).getMetaTileEntity();
            CriticalMachineState local=new CriticalMachineState();set(local,"geometryVersion",1);set(machine,"state",local);
            initialize(machine);
        }
        for (net.minecraft.tileentity.TileEntity tile:((SparseWorld)s.worldObj).tiles.values()) {
            BaseMetaTileEntity gt=(BaseMetaTileEntity)tile;
            MTECriticalMachine m=(MTECriticalMachine)gt.getMetaTileEntity();
            if (m.isKindSelector()) {
                set(m,"state",s);s.bindHost(gt);
                // Fixture has a complete real loom; ask the production validator to check it.
                set(m,"portSnapshot",new LinkedHashMap<>());
                set(m,"hatchEnergySpent",new IdentityHashMap<>());
                set(m,"loomValidationTick",Long.MIN_VALUE);
                for(Class<?> c=m.getClass();c!=null;c=c.getSuperclass()) {
                    for(Field f:c.getDeclaredFields()) {
                        if (List.class.isAssignableFrom(f.getType())&&!Modifier.isStatic(f.getModifiers())) {
                            f.setAccessible(true);if(f.get(m)==null)f.set(m,f.getType().isInterface()?new ArrayList<>():f.getType().getDeclaredConstructor().newInstance());
                        }
                    }
                }
                for(net.minecraft.tileentity.TileEntity other:world.tiles.values()) {
                    MTECriticalMachine partner=(MTECriticalMachine)((BaseMetaTileEntity)other).getMetaTileEntity();
                    if(!partner.isKindSelector()) partner.getLocalCriticalState().setLoomOwnedMain(s);
                }
                return m;
            }
        }
        throw new AssertionError("loom controller missing");
    }
    static BaseMetaTileEntity hatch(Class<?> type)throws Exception {
        BaseMetaTileEntity gt=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
        gregtech.api.interfaces.metatileentity.IMetaTileEntity m=(gregtech.api.interfaces.metatileentity.IMetaTileEntity)unsafe.allocateInstance(type==MTEHatchDynamo.class?Dynamo.class:Input.class);
        Field f=gregtech.api.metatileentity.MetaTileEntity.class.getDeclaredField("mBaseMetaTileEntity");f.setAccessible(true);f.set(m,gt);gt.setMetaTileEntity(m);
        int id=type==MTEHatchDynamo.class?30001:30002;gt.setMetaTileID((short)id);gregtech.api.GregTechAPI.METATILEENTITIES[id]=m;
        for(Class<?> c=m.getClass();c!=null;c=c.getSuperclass())for(Field tier:c.getDeclaredFields())if(tier.getName().equals("mTier")){tier.setAccessible(true);tier.setByte(m,(byte)1);}
        return gt;
    }
    static void run()throws Exception {
        materials();SparseWorld w=world();CriticalMachineState s=state(w,31,ForgeDirection.NORTH);fill(s);
        MTECriticalMachine host=attach(s);
        CriticalWorldData data=CriticalWorldData.get(w);
        check(data.acquire(s.getStructureId(),"owner",s.getOriginX(),s.getOriginY(),s.getOriginZ()),"fixture region ownership acquired");
        check(data.bindTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"fixture paid task bound");
        check(host.forceLoomStructureCheck(),"complete casing loom forms with no chambers");
        check(CriticalLoomStructure.validateGeometry(host),"native StructureLib geometry accepts complete casing loom");
        check(CriticalGeometry.optionalWorkPorts(s).stream().allMatch(v->w.isAirBlock(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v))),"all main work ports empty");
        check(!host.getDefaultHasMaintenanceChecks(),"maintenance default disabled");
        MTECriticalMachine main=(MTECriticalMachine)((BaseMetaTileEntity)w.getTileEntity(s.blockWorldX(56,35),s.blockWorldY(4),s.blockWorldZ(56,35))).getMetaTileEntity();
        CriticalGeometry.Voxel manual=CriticalLoomStructure.voxels(s).stream().filter(v->!CriticalStructures.isController(v)).findFirst().orElseThrow();
        String manualPos=key(s.blockWorldX(manual),s.blockWorldY(manual.y),s.blockWorldZ(manual));
        w.blocks.remove(manualPos);host.forceLoomStructureCheck();
        check(!main.checkStructure(true,main.getBaseMetaTileEntity()),"real generated-main GT check first reports incomplete");
        put(s,manual,CriticalLoomStructure.shell(manual));check(host.forceLoomStructureCheck(),"restore loom before construction completes");
        // This is the real terminal branch reached after the last paid stage, not a valid flag write.
        set(s,"state",1);set(s,"stage",((List<?>)field(s,"stageCache")).size());
        long energyBefore=s.getBufferedEnergy();
        invoke(s,"build");
        check(s.getState()==2&&s.isGeometryValidated(),"actual first build finalization immediately forms");
        check(!s.isCompletionValidationPending(),"first completion has no 6000-tick wait");
        check(s.getBufferedEnergy()==energyBefore&&!s.isWorking(),"forming empty interfaces grants no EU or free recipe activity");
        check(monitor(s).getCompletedRounds()==0,"completion performs no disguised full sweep");
        check(s.getVisualData().getBoolean("valid"),"animation validity snapshot immediate");
        main.onPostTick(main.getBaseMetaTileEntity(),1);
        check(deep(main,"mMachine").getBoolean(main),"passive main native GT formation refreshes immediately after construction");
        List<gregtech.api.structure.error.StructureError> errors=new ArrayList<>();
        host.checkMachine(host.getBaseMetaTileEntity(),null,errors);
        check(errors.isEmpty(),"GT checkMachine reports no missing hatch or maintenance error; fabrication="+s.isFabricationHostValid()+" loom="+s.isLoomStructureValid()+" owner="+data.ownsTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ())+" fingerprint="+field(s,"jobFingerprint")+"/"+field(s,"modelFingerprint"));
        Method matcher=CriticalMachineState.class.getDeclaredMethod("matches",CriticalGeometry.Voxel.class);matcher.setAccessible(true);
        for(CriticalGeometry.Voxel v:geometry(s)) check((Boolean)matcher.invoke(s,v),"post-completion geometry "+v.model+" "+v.x+","+v.y+","+v.z);
        check(s.matchesOptionalWorkPorts(),"post-completion optional work ports still valid");
        ticks(s,5999);check(s.isGeometryValidated()&&monitor(s).getCompletedRounds()==0,"first ordinary round keeps initial trust");
        ticks(s,1);check(s.isGeometryValidated()&&monitor(s).getCompletedRounds()==1,"ordinary clean sweep completes on schedule");
        CriticalGeometry.Voxel missing=geometry(s).stream().filter(v->!CriticalLoomStructure.isLoomVoxel(v)&&!CriticalStructures.isController(v)).findFirst().orElseThrow();
        w.blocks.remove(key(s.blockWorldX(missing),s.blockWorldY(missing.y),s.blockWorldZ(missing)));
        ticks(s,6000);check(!s.isGeometryValidated(),"subsequent missing target invalidates");
        main.onPostTick(main.getBaseMetaTileEntity(),2);check(!deep(main,"mMachine").getBoolean(main),"passive main revokes native GT formation after damage");
        put(s,missing,missing.block);ticks(s,5999);check(!s.isGeometryValidated(),"hand repair still needs full clean round");
        ticks(s,1);check(s.isGeometryValidated(),"full clean repair round restores");
        main.onPostTick(main.getBaseMetaTileEntity(),3);check(deep(main,"mMachine").getBoolean(main),"passive main restores native GT formation after clean sweep");
        NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);CriticalMachineState reload=new CriticalMachineState();reload.readFromNBT(n);
        check(!reload.isGeometryValidated(),"NBT never persists construction trust");
        set(s,"state",4);set(s,"repairScanPending",false);set(s,"repairStages",new ArrayList<>());set(s,"stage",0);
        invoke(s,"repair");check(s.getState()==2&&!s.isGeometryValidated(),"paid repair finalization does not receive first-build trust");
        ticks(s,5999);check(!s.isGeometryValidated(),"paid repair waits entire clean sweep");ticks(s,1);check(s.isGeometryValidated(),"paid repair normal validation restores");
        CriticalGeometry.Voxel loom=CriticalLoomStructure.voxels(s).stream().filter(CriticalStructures::isHatchPosition).findFirst().orElseThrow();
        BaseMetaTileEntity dynamo=hatch(MTEHatchDynamo.class), input=hatch(MTEHatchInputBus.class);
        String pos=key(s.blockWorldX(loom),s.blockWorldY(loom.y),s.blockWorldZ(loom));
        w.blocks.remove(pos);w.tiles.put(pos,dynamo);
        check(!s.isStandardHatch(loom)&&!CriticalLoomStructure.matchesVoxel(s,loom),"loom rejects real dynamo in both matching paths");
        CriticalStructures.PaidHatchElement loomElement=new CriticalStructures.PaidHatchElement(true);
        check(!loomElement.couldBeValid(host,w,s.blockWorldX(loom),s.blockWorldY(loom.y),s.blockWorldZ(loom),null),"StructureLib loom candidate rejects dynamo");
        check(!loomElement.check(host,w,s.blockWorldX(loom),s.blockWorldY(loom.y),s.blockWorldZ(loom)),"StructureLib actual loom element rejects dynamo");
        check(CriticalMachineState.isStandardHatchHost(dynamo),"main retains supported dynamo");
        w.tiles.put(pos,input);
        check(s.isStandardHatch(loom)&&CriticalLoomStructure.matchesVoxel(s,loom),"ordinary real input bus remains optional replacement");
        check(loomElement.couldBeValid(host,w,s.blockWorldX(loom),s.blockWorldY(loom.y),s.blockWorldZ(loom),null),"StructureLib loom candidate accepts ordinary input bus");
        // StructureLib's stack predicate uses the feather's damage accessor; supply it without vanilla/FML bootstrap.
        Class.forName("net.minecraft.init.Items");Field feather=net.minecraft.init.Items.class.getDeclaredField("feather");
        unsafe.putObject(unsafe.staticFieldBase(feather),unsafe.staticFieldOffset(feather),new net.minecraft.item.Item());
        var loomItems=loomElement.getBlocksToPlace(host,w,0,0,0,null,null).getStacks();
        boolean loomDynamo=false,loomInput=false;for(var i:loomItems){loomDynamo|=i.getItemDamage()==30001;loomInput|=i.getItemDamage()==30002;}
        check(!loomDynamo&&loomInput,"real StructureLib loom catalogue excludes LV dynamo and keeps LV input bus");
        var mainItems=new CriticalStructures.PaidHatchElement(false).getBlocksToPlace(host,w,0,0,0,null,null).getStacks();
        boolean mainDynamo=false;for(var i:mainItems)mainDynamo|=i.getItemDamage()==30001;
        check(mainDynamo,"main catalogue retains LV dynamo");
    }
    public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        run();JsonObject n=new JsonObject();n.addProperty("status","PASS");n.addProperty("checks",checks);
        n.addProperty("coverage","Production build/repair finalization, GT205 checkMachine, StructureLib complete casing loom and real hatch types, periodic integrity, NBT and animation validity");
        n.addProperty("limitation","SparseWorld terminal-stage fixture; not end-to-end construction payment, no full MC/FML/server or rendered GL");
        JsonObject origins=new JsonObject();
        for(Class<?> c:new Class<?>[]{CriticalMachineState.class,CriticalStructureMonitor.class,CriticalStructures.class,CriticalLoomStructure.class,MTECriticalMachine.class,MTEMultiBlockBase.class}) {
            origins.addProperty(c.getName(),Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        }
        n.add("codeSources",origins);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(n));System.out.println(n);
    }
}
