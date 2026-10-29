package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.math.BigInteger;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.google.gson.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Candidate-bound floating-hull update with frozen paid journals and real discovered GT inventories. */
public final class CriticalSupplyHull33Contract {
    static int migrations,ioMatrices;
    static Object invoke(CriticalMachineState s,String name)throws Exception{Method m=CriticalMachineState.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(s);}
    static boolean matches(CriticalMachineState s,CriticalGeometry.Voxel v)throws Exception{Method m=CriticalMachineState.class.getDeclaredMethod("matches",CriticalGeometry.Voxel.class);m.setAccessible(true);return (Boolean)m.invoke(s,v);}
    static BitSet bits(CriticalMachineState s,String name)throws Exception{return (BitSet)field(s,name);}
    static NBTTagCompound save(CriticalMachineState s){NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);return n;}
    static NBTTagCompound paid(NBTTagCompound n){n=(NBTTagCompound)n.copy();for(String key:new String[]{"supplyHullPlaced","supplyPaidRetired","supplyHullComplete","supplyTerracePlaced"})n.removeTag(key);return n;}
    static void step(CriticalMachineState s)throws Exception{CriticalSupplyTerraceContract.budget(s);invoke(s,"supplementSupplyTerrace");
        check((Integer)field(s,"blocksChangedThisTick")<=128&&(Integer)field(s,"lightsChangedThisTick")<=16,"shared physical/light update budget");}
    static void finish(CriticalMachineState s)throws Exception{for(int i=0;i<3000&&!(Boolean)field(s,"supplyHullComplete");i++)step(s);
        check((Boolean)field(s,"supplyHullComplete")&&bits(s,"supplyHullPlaced").cardinality()==476,"approved hull completes without recharging");}
    static MTECriticalMachine attach(CriticalMachineState s)throws Exception{
        MTECriticalMachine m=CriticalV32Contract.attach(s);
        for(TileEntity tile:((SparseWorld)s.worldObj).tiles.values())CriticalTerracePortsContract.deepSet(tile,"mReturnedCoordinates",new net.minecraft.util.ChunkCoordinates());
        check(m.forceLoomStructureCheck(),"real unchanged loom still forms");return m;
    }
    static CriticalMachineState rig(int revision,CriticalMachineKind kind,ForgeDirection facing,int oldFree)throws Exception{
        SparseWorld w=world();CriticalMachineState s=state(w,revision,facing);
        set(s,"config",new CriticalConfiguration(CriticalTier.T3,kind,0,0,0));set(s,"geometryCache",null);geometry(s);
        set(s,"jobFingerprint",field(s,"modelFingerprint"));fill(s);
        List<CriticalGeometry.Voxel> all=geometry(s);BitSet paid=bits(s,"placed");for(int i=0;i<all.size();i++)if(!CriticalLoomStructure.isLoomVoxel(all.get(i)))paid.set(i);
        for(int i=0;i<oldFree;i++){CriticalGeometry.Voxel v=CriticalGeometry.supplyTerrace().get(i);put(s,v,v.block);bits(s,"supplyTerracePlaced").set(i);}
        set(s,"supplyTerraceCursor",oldFree);
        attach(s);CriticalWorldData data=CriticalWorldData.get(w);
        check(data.acquire(s.getStructureId(),"owner",s.getOriginX(),s.getOriginY(),s.getOriginZ(),s.getRegionSize(),s.getRegionHeight(),s.xCoord,s.yCoord,s.zCoord),"owner region acquired");
        check(data.bindTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"original paid job remains bound");
        set(s,"structureValid",true);return s;
    }
    static void validate(CriticalMachineState s)throws Exception{
        CriticalV32Fixture.monitor(s).reset();for(int i=0;i<1500&&!s.isStructureValidated();i++)s.tickStructureMonitor();
        check(s.isStructureValidated(),"real incremental checker accepts retired old footprint and unchanged core");
    }
    static void assertOriginalMatches(CriticalMachineState s)throws Exception{
        for(CriticalGeometry.Voxel v:geometry(s))if(!CriticalStructures.isController(v))check(matches(s,v),"frozen paid/member position still valid after authorized retirement");
    }
    static void migrationMatrix()throws Exception{
        for(int revision:new int[]{31,32})for(CriticalMachineKind kind:new CriticalMachineKind[]{CriticalMachineKind.SOLAR,CriticalMachineKind.TURBINE,CriticalMachineKind.BATTERY,CriticalMachineKind.SUN})
            for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.SOUTH,ForgeDirection.WEST,ForgeDirection.EAST}){
                CriticalMachineState s=rig(revision,kind,facing,revision==31?1314:0);long fingerprint=(Long)field(s,"jobFingerprint");
                NBTTagCompound before=paid(save(s));BitSet original=(BitSet)bits(s,"placed").clone();
                step(s);NBTTagCompound partial=save(s);CriticalMachineState resumed=new CriticalMachineState();resumed.readFromNBT(partial);
                resumed.worldObj=s.worldObj;resumed.xCoord=s.xCoord;resumed.yCoord=s.yCoord;resumed.zCoord=s.zCoord;
                geometry(resumed);attach(resumed);s=resumed;finish(s);
                check(s.getV2LayoutRevision()==revision,"paid profile is never relabelled 33");
                check(fingerprint==(Long)field(s,"modelFingerprint")&&fingerprint==(Long)field(s,"jobFingerprint"),"paid fingerprint unchanged after reload and physical thinning");
                check(original.equals(bits(s,"placed")),"paid placement indices are unchanged by update");
                check(before.equals(paid(save(s))),"all prior paid NBT fields retain exact values");
                check(bits(s,"supplyTerracePlaced").isEmpty(),"old 1314 free bitmap clears only its retired physical blocks");
                check(bits(s,"supplyHullPlaced").cardinality()==476,"exact approved new ownership only");
                SparseWorld w=(SparseWorld)s.worldObj;
                for(CriticalGeometry.Voxel v:CriticalGeometry.supplyHull())check(w.getBlock(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v))==v.block,"world equals approved hull voxel");
                for(CriticalGeometry.Voxel v:CriticalGeometry.legacySupplyFootprint())if(CriticalGeometry.supplyHullIndex(v.positionKey())==null)
                    check(w.isAirBlock(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v)),"obsolete own solid/front row removed without a leftover post");
                assertOriginalMatches(s);set(s,"structureValid",false);validate(s);
                int blocks=w.blocks.size();step(s);check(w.blocks.size()==blocks,"completed update never repeats placements");
                migrations++;
            }
    }
    static MTECriticalMachine machine(CriticalMachineState s){return (MTECriticalMachine)((BaseMetaTileEntity)s.getHost()).getMetaTileEntity();}
    static void io(CriticalMachineState s)throws Exception{
        MTECriticalMachine machine=machine(s);List<CriticalGeometry.Voxel> ports=CriticalGeometry.hullWorkPorts();List<BaseMetaTileEntity> tiles=new ArrayList<>();
        check(ports.size()==90,"ninety actual approved GT slots");
        for(int i=0;i<ports.size();i++)tiles.add(CriticalTerracePortsContract.chamber(s,ports.get(i),i%6,20));
        CriticalTerracePortsContract.discover(machine);
        check(machine.getCriticalInputBusses().size()==30&&machine.getCriticalInputHatches().size()==15,"real discovery collects all new inputs");
        check(machine.getCriticalOutputBusses().size()==15&&machine.getCriticalOutputHatches().size()==15,"real discovery collects all new outputs");
        check(((List<?>)CriticalV32Contract.deep(machine,"mDynamoHatches").get(machine)).size()==15,"real discovery collects new dynamos");
        for(CriticalGeometry.Voxel p:ports){check(s.isStandardHatch(p),"new real GT chamber needs owned supply");
            check(!s.protectsPlayerBreak(s.blockWorldX(p),s.blockWorldY(p.y),s.blockWorldZ(p)),"GT chamber remains player property");}
        check(CriticalTerracePortsContract.itemTotal(machine.criticalPhysicalItemsSnapshot())==600,"cross-bus actual inventory total");
        List<CriticalEnergyPorts.Input> input=CriticalEnergyPorts.snapshot(machine);
        check(CriticalEnergyPorts.total(input,new FluidStack(CriticalTerracePortsContract.fluid,1)).equals(BigInteger.valueOf(300)),"cross-hatch actual fluid total");
        check(CriticalEnergyPorts.debit(CriticalEnergyPorts.take(input,new FluidStack(CriticalTerracePortsContract.fluid,1),BigInteger.valueOf(290))),"cross-hatch debit");
        check(CriticalTerracePortsContract.fluidTotal(machine.criticalPhysicalFluidsSnapshot())==10,"exact fluid remainder");
        CriticalEnergyPorts.OutputPlan plan=CriticalEnergyPorts.outputs(machine,List.of(new CriticalEnergyPorts.FluidAmount(new FluidStack(CriticalTerracePortsContract.fluid,1),BigInteger.valueOf(1250))),new ItemStack[]{new ItemStack(CriticalTerracePortsContract.MATERIAL,700)});
        check(plan!=null,"combined new output space");plan.commit();
        check(CriticalTerracePortsContract.retainedOutputItems(tiles)==700&&CriticalTerracePortsContract.retainedOutputFluids(tiles)==1250,"native output transactions split across new ports");
        set(s,"state",3);set(s,"cursor",geometry(s).size());
        for(int i=0;i<2000&&!bits(s,"supplyHullPlaced").isEmpty();i++){CriticalSupplyTerraceContract.budget(s);invoke(s,"removeSupplyHull");}
        if(s.getV2LayoutRevision()>=33)for(int i=0;i<100;i++){CriticalSupplyTerraceContract.budget(s);if(Boolean.TRUE.equals(invoke(s,"removePaidTerraceSupplies")))break;}
        for(BaseMetaTileEntity t:tiles)check(s.worldObj.getTileEntity(t.xCoord,t.yCoord,t.zCoord)==t,"no GT tile removed with support cleanup");
        check(CriticalTerracePortsContract.retainedOutputItems(tiles)==700&&CriticalTerracePortsContract.retainedOutputFluids(tiles)==1250,"player inventories survive cleanup");
        for(CriticalGeometry.Voxel p:ports)check(!s.isStandardHatch(p),"no floating hatch recognized after owned support cleanup");ioMatrices++;
    }
    static void ioMatrix()throws Exception{
        for(int revision:new int[]{31,32,33})for(CriticalMachineKind kind:new CriticalMachineKind[]{CriticalMachineKind.SOLAR,CriticalMachineKind.TURBINE,CriticalMachineKind.BATTERY,CriticalMachineKind.SUN})
            for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.SOUTH,ForgeDirection.WEST,ForgeDirection.EAST}){
                CriticalMachineState s=rig(revision,kind,facing,0);if(revision<33)finish(s);io(s);
            }
    }
    static void retainedChamber()throws Exception{
        for(int revision:new int[]{31,32}){
            CriticalMachineState s=rig(revision,CriticalMachineKind.SUN,ForgeDirection.WEST,revision==31?1314:0);
            CriticalGeometry.Voxel old=CriticalGeometry.terraceWorkPorts().get(0);BaseMetaTileEntity tile=CriticalTerracePortsContract.chamber(s,old,0,37);
            SparseWorld w=(SparseWorld)s.worldObj;MTECriticalMachine host=machine(s);CriticalTerracePortsContract.discover(host);
            check(s.isStandardHatch(old),"legacy chamber starts valid");
            for(int i=0;i<100;i++)step(s);
            check(!(Boolean)field(s,"supplyHullComplete")&&s.getSupplyTerraceNoticeKey().equals("view.terrace.move_hatch"),"old out-of-shape chamber gives visible move notice");
            check(w.getTileEntity(tile.xCoord,tile.yCoord,tile.zCoord)==tile&&tile.getMetaTileEntity().getStackInSlot(0).stackSize==37,"old chamber and contents untouched");
            check(s.isStandardHatch(old),"old real support and resource path retained while waiting");CriticalTerracePortsContract.discover(host);
            check(host.getCriticalInputBusses().size()==1,"real discovery retains old loaded chamber");
            CriticalGeometry.Voxel destination=CriticalGeometry.hullWorkPorts().stream().filter(p->w.isAirBlock(s.blockWorldX(p),s.blockWorldY(p.y),s.blockWorldZ(p))).findFirst().orElseThrow();
            w.tiles.remove(key(tile.xCoord,tile.yCoord,tile.zCoord));w.blocks.remove(key(tile.xCoord,tile.yCoord,tile.zCoord));
            tile.xCoord=s.blockWorldX(destination);tile.yCoord=s.blockWorldY(destination.y);tile.zCoord=s.blockWorldZ(destination);
            w.tiles.put(key(tile.xCoord,tile.yCoord,tile.zCoord),tile);w.blocks.put(key(tile.xCoord,tile.yCoord,tile.zCoord),AIR);
            finish(s);check(s.isStandardHatch(destination)&&!s.isStandardHatch(old),"moving player tile unlocks thinning and new IO");
            check(tile.getMetaTileEntity().getStackInSlot(0).stackSize==37,"migration never drains or recreates inventory");
            for(int i=0;i<1000&&!bits(s,"supplyHullPlaced").isEmpty();i++){CriticalSupplyTerraceContract.budget(s);invoke(s,"removeSupplyHull");}
            check(w.getTileEntity(tile.xCoord,tile.yCoord,tile.zCoord)==tile&&tile.getMetaTileEntity().getStackInSlot(0).stackSize==37,"new chamber survives removal of actual owned hull");
        }
    }
    static void boundaries()throws Exception{
        CriticalMachineState s=rig(31,CriticalMachineKind.SUN,ForgeDirection.NORTH,300);SparseWorld w=(SparseWorld)s.worldObj;
        NBTTagCompound before=paid(save(s));set(s,"state",1);step(s);check(bits(s,"supplyHullPlaced").isEmpty()&&bits(s,"supplyPaidRetired").isEmpty(),"in-progress paid work order is not altered");set(s,"state",2);
        CriticalGeometry.Voxel p=CriticalGeometry.supplyHull().stream().filter(v->!CriticalGeometry.isLegacySupplyPosition(v.positionKey())).findFirst().orElseThrow();
        put(s,p,p.block);for(int i=0;i<100;i++)step(s);
        int index=CriticalGeometry.supplyHullIndex(p.positionKey());check(!bits(s,"supplyHullPlaced").get(index),"same-material foreign cell is never adopted");
        check(s.getStatus().equals(before.getString("status")),"update obstruction never becomes production fail");
        w.blocks.remove(key(s.blockWorldX(p),s.blockWorldY(p.y),s.blockWorldZ(p)));finish(s);
        CriticalGeometry.Voxel core=geometry(s).stream().filter(v->!CriticalLoomStructure.isLoomVoxel(v)&&!CriticalStructures.isController(v)&&!CriticalGeometry.isLegacySupplyPosition(v.positionKey())).findFirst().orElseThrow();
        int coreIndex=geometry(s).indexOf(core);bits(s,"supplyPaidRetired").set(coreIndex);w.blocks.remove(key(s.blockWorldX(core),s.blockWorldY(core.y),s.blockWorldZ(core)));
        check(!matches(s,core),"retirement mask cannot exempt unrelated core geometry");
        put(s,core,core.block);
        CriticalMachineState unloaded=rig(32,CriticalMachineKind.SUN,ForgeDirection.EAST,0);SparseWorld uw=(SparseWorld)unloaded.worldObj;uw.loaded=false;int reads=uw.unloadedReads;step(unloaded);
        check(reads==uw.unloadedReads&&bits(unloaded,"supplyHullPlaced").isEmpty(),"unloaded positions are not read or changed");
    }
    public static void main(String[] args)throws Exception{
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        CriticalTerracePortsContract.seedRegistry();materials();CriticalTerracePortsContract.fluid=new Fluid("gtsr_hull33_contract");FluidRegistry.registerFluid(CriticalTerracePortsContract.fluid);
        check(CriticalGeometry.supplyTerrace().size()==1314,"released bitmap retains 1314 exact indices");check(CriticalGeometry.supplyHull().size()==476&&CriticalGeometry.hullWorkPorts().size()==90,"approved hull/GT counts");
        migrationMatrix();retainedChamber();boundaries();ioMatrix();
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("checks",checks);result.addProperty("migrationCases",migrations);result.addProperty("nativeIOCases",ioMatrices);
        result.addProperty("boundary","candidate JAR methods, real GT hatch types/native discovery/IO in SparseWorld; controlled completion/owner setup and manual player-tile relocation; no full client or real user world");
        JsonObject origins=new JsonObject();for(Class<?> c:new Class<?>[]{CriticalMachineState.class,CriticalGeometry.class,CriticalLayoutDefinition.class,CriticalWorldData.class,MTECriticalMachine.class,CriticalEnergyPorts.class})
            origins.addProperty(c.getName(),Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());result.add("codeSources",origins);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result));System.out.println("PASS SupplyHull33 checks="+checks);
    }
}
