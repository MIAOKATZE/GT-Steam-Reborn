package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.*;

/** Native GT chamber tiles are discovered by the real host, never injected into its hatch lists. */
final class CriticalTerracePortsContract {
    static final Item MACHINE_ITEM=new gregtech.common.blocks.ItemMachines(AIR);
    static final Item MATERIAL=new Item();
    static Fluid fluid;
    static void seedRegistry()throws Exception {
        // Same registry-only bootstrap as the existing ledger/RC2 contracts; no FML startup.
        Field loader=cpw.mods.fml.common.Loader.class.getDeclaredField("instance");loader.setAccessible(true);
        loader.set(null,unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        Method blocks=net.minecraft.block.Block.blockRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);blocks.setAccessible(true);
        blocks.invoke(net.minecraft.block.Block.blockRegistry,9,"minecraft:water",new net.minecraft.block.Block(net.minecraft.block.material.Material.water){});
        blocks.invoke(net.minecraft.block.Block.blockRegistry,11,"minecraft:lava",new net.minecraft.block.Block(net.minecraft.block.material.Material.lava){});
        gregtech.api.GregTechAPI.sBlockMachines=new net.minecraft.block.Block(net.minecraft.block.material.Material.iron){};
        blocks.invoke(net.minecraft.block.Block.blockRegistry,1810,"gregtech:terrace_machine",gregtech.api.GregTechAPI.sBlockMachines);
        Method items=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);items.setAccessible(true);
        items.invoke(Item.itemRegistry,1810,"gregtech:terrace_machine",MACHINE_ITEM);
        items.invoke(Item.itemRegistry,31710,"gtsr:terrace_fixture_component",MATERIAL);
        Class.forName("net.minecraft.init.Items");Field feather=net.minecraft.init.Items.class.getDeclaredField("feather");
        unsafe.putObject(unsafe.staticFieldBase(feather),unsafe.staticFieldOffset(feather),MATERIAL);
    }
    static class InBus extends MTEHatchInputBus {
        InBus(){super("terrace-input",1,new String[0],null);}
        @Override public ItemStack getStackForm(long n){return new ItemStack(MACHINE_ITEM,(int)n,31000);}
        @Override public int getCircuitSlot(){return -1;}
    }
    static class InFluid extends MTEHatchInput {
        InFluid(){super("terrace-fluid-input",1,new String[0],null);}
        @Override public ItemStack getStackForm(long n){return new ItemStack(MACHINE_ITEM,(int)n,31001);}
    }
    static class OutBus extends MTEHatchOutputBus {
        OutBus(){super("terrace-output",1,new String[0],null);}
        @Override public ItemStack getStackForm(long n){return new ItemStack(MACHINE_ITEM,(int)n,31002);}
    }
    static class OutFluid extends MTEHatchOutput {
        OutFluid(){super("terrace-fluid-output",1,new String[0],null);}
        @Override public ItemStack getStackForm(long n){return new ItemStack(MACHINE_ITEM,(int)n,31003);}
        @Override public int getCapacity(){return 100;}
        @Override public boolean canStoreFluid(FluidStack f){return f.getFluid()==fluid;}
        @Override public void onEmptyingContainerWhenEmpty(){}
    }
    static class Dynamo extends MTEHatchDynamo {
        Dynamo(){super("terrace-dynamo",1,new String[0],null);}
        @Override public ItemStack getStackForm(long n){return new ItemStack(MACHINE_ITEM,(int)n,31004);}
    }
    static void deepSet(Object o,String name,Object value)throws Exception{CriticalV32Contract.deep(o,name).set(o,value);}
    static MTECriticalMachine host(CriticalMachineState s)throws Exception {
        SparseWorld world=(SparseWorld)s.worldObj;
        net.minecraft.world.chunk.Chunk chunk=(net.minecraft.world.chunk.Chunk)unsafe.allocateInstance(net.minecraft.world.chunk.Chunk.class);
        world.chunks=(net.minecraft.world.chunk.IChunkProvider)Proxy.newProxyInstance(net.minecraft.world.chunk.IChunkProvider.class.getClassLoader(),new Class<?>[]{net.minecraft.world.chunk.IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists")?world.loaded:m.getName().equals("provideChunk")?chunk:null);
        Field provider=net.minecraft.world.World.class.getDeclaredField("chunkProvider");provider.setAccessible(true);provider.set(world,world.chunks);
        MTECriticalMachine host=(MTECriticalMachine)unsafe.allocateInstance(MTECriticalMachine.class);
        CriticalV32Contract.initialize(host);set(host,"state",s);set(host,"presetKind",null);
        BaseMetaTileEntity tile=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
        deepSet(tile,"mReturnedCoordinates",new net.minecraft.util.ChunkCoordinates());
        deepSet(host,"mBaseMetaTileEntity",tile);tile.setMetaTileEntity(host);tile.setWorldObj(s.worldObj);
        tile.xCoord=s.xCoord;tile.yCoord=s.yCoord;tile.zCoord=s.zCoord;deepSet(tile,"mFacing",s.getStructureFacing());
        ((SparseWorld)s.worldObj).tiles.put(key(tile.xCoord,tile.yCoord,tile.zCoord),tile);s.bindHost(tile);
        return host;
    }
    static BaseMetaTileEntity chamber(CriticalMachineState s,CriticalGeometry.Voxel p,int family,int stock)throws Exception {
        Class<?> type=switch(family){case 0,1->InBus.class;case 2->InFluid.class;case 3->OutBus.class;case 4->OutFluid.class;default->Dynamo.class;};
        MetaTileEntity m=(MetaTileEntity)unsafe.allocateInstance(type);
        deepSet(m,"watchers",new ArrayList<>());
        BaseMetaTileEntity tile=(BaseMetaTileEntity)unsafe.allocateInstance(BaseMetaTileEntity.class);
        deepSet(tile,"mReturnedCoordinates",new net.minecraft.util.ChunkCoordinates());
        deepSet(m,"mBaseMetaTileEntity",tile);deepSet(m,"mInventory",new ItemStack[family==3?1:4]);deepSet(m,"mTier",(byte)1);
        tile.setMetaTileEntity(m);tile.setWorldObj(s.worldObj);tile.xCoord=s.blockWorldX(p);tile.yCoord=s.blockWorldY(p.y);tile.zCoord=s.blockWorldZ(p);
        deepSet(tile,"mFacing",s.getStructureFacing().getOpposite());tile.setMetaTileID((short)(31000+Math.max(0,family-1)));
        if(family<2)m.setInventorySlotContents(0,new ItemStack(MATERIAL,stock));
        if(family==2)deepSet(m,"mFluid",new FluidStack(fluid,stock));
        SparseWorld w=(SparseWorld)s.worldObj;w.tiles.put(key(tile.xCoord,tile.yCoord,tile.zCoord),tile);
        w.blocks.put(key(tile.xCoord,tile.yCoord,tile.zCoord),AIR);
        return tile;
    }
    static void discover(MTECriticalMachine host)throws Exception {
        Method method=MTECriticalMachine.class.getDeclaredMethod("discoverHatches",boolean.class);method.setAccessible(true);method.invoke(host,true);
    }
    static void installPaid(CriticalMachineState s)throws Exception {
        List<CriticalGeometry.Voxel> all=geometry(s);BitSet paid=(BitSet)field(s,"placed");
        for(CriticalGeometry.Voxel v:CriticalGeometry.terraceSupplies()){put(s,v,v.block);paid.set(all.indexOf(v));}
        CriticalWorldData.get(s.worldObj).updateProtection(s);
    }
    static int itemTotal(ItemStack[] stacks){int total=0;for(ItemStack stack:stacks)if(stack.getItem()==MATERIAL)total+=stack.stackSize;return total;}
    static int fluidTotal(FluidStack[] stacks){int total=0;for(FluidStack f:stacks)if(f.getFluid()==fluid)total+=f.amount;return total;}
    static int retainedOutputItems(List<BaseMetaTileEntity> tiles){int total=0;for(BaseMetaTileEntity tile:tiles)
        if(tile.getMetaTileEntity() instanceof OutBus bus)for(int slot=0;slot<bus.getSizeInventory();slot++) {
            ItemStack stack=bus.getStackInSlot(slot);if(stack!=null&&stack.getItem()==MATERIAL)total+=stack.stackSize;
        }return total;}
    static int retainedOutputFluids(List<BaseMetaTileEntity> tiles){int total=0;for(BaseMetaTileEntity tile:tiles)
        if(tile.getMetaTileEntity() instanceof OutFluid hatch)for(FluidTankInfo tank:hatch.getTankInfo(ForgeDirection.UNKNOWN))
            if(tank.fluid!=null&&tank.fluid.getFluid()==fluid)total+=tank.fluid.amount;return total;}
    static void freeClear(CriticalMachineState s)throws Exception {
        for(int tick=0;tick<2000&&!CriticalSupplyTerraceContract.installed(s).isEmpty();tick++) {
            CriticalSupplyTerraceContract.budget(s);CriticalSupplyTerraceContract.call(s,"removeSupplyTerrace");
        }
    }
    static void paidClear(CriticalMachineState s)throws Exception {
        for(int tick=0;tick<100;tick++) {
            CriticalSupplyTerraceContract.budget(s);
            if(Boolean.TRUE.equals(CriticalSupplyTerraceContract.call(s,"removePaidTerraceSupplies")))return;
        }
        throw new AssertionError("paid support cleanup did not finish");
    }
    static void matrix()throws Exception {
        for(int revision:new int[]{31,32})for(CriticalMachineKind kind:new CriticalMachineKind[]{CriticalMachineKind.SOLAR,CriticalMachineKind.TURBINE,CriticalMachineKind.BATTERY,CriticalMachineKind.SUN})
            for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.SOUTH,ForgeDirection.WEST,ForgeDirection.EAST}) {
                SparseWorld w=world();CriticalMachineState s=CriticalSupplyTerraceContract.prepared(w,revision,kind,facing);
                MTECriticalMachine machine=host(s);List<CriticalGeometry.Voxel> ports=CriticalGeometry.terraceWorkPorts();
                check(ports.size()==30,"thirty new one-block GT slots");
                List<BaseMetaTileEntity> tiles=new ArrayList<>();
                for(int i=0;i<ports.size();i++)tiles.add(chamber(s,ports.get(i),i%6,20));
                discover(machine);
                check(machine.getCriticalInputBusses().isEmpty()&&machine.getCriticalInputHatches().isEmpty(),"floating GT tiles cannot be discovered before supply support ownership");
                for(CriticalGeometry.Voxel p:ports)check(!s.isStandardHatch(p),"unowned empty support never enables a chamber");
                if(revision==31)CriticalSupplyTerraceContract.finish(s);else installPaid(s);
                discover(machine);
                check(s.getResourceHatchPorts().size()==43,"recipe port candidates include original thirteen and thirty terrace slots");
                check(machine.getCriticalInputBusses().size()==10&&machine.getCriticalInputHatches().size()==5,"real discovery adds new item/fluid inputs");
                check(machine.getCriticalOutputBusses().size()==5&&machine.getCriticalOutputHatches().size()==5,"real discovery adds new item/fluid outputs");
                check(((List<?>)CriticalV32Contract.deep(machine,"mDynamoHatches").get(machine)).size()==5,"real discovery adds new dynamos");
                for(CriticalGeometry.Voxel p:ports) {
                    check(s.isStandardHatch(p),"new owned real GT tile is a standard hatch");
                    check(!s.protectsPlayerBreak(s.blockWorldX(p),s.blockWorldY(p.y),s.blockWorldZ(p)),"player GT chamber never enters free/paid shell ownership");
                    check(s.protectsPlayerBreak(s.blockWorldX(p),s.blockWorldY(p.y-1),s.blockWorldZ(p)),"real supply casing stays protected");
                }
                check(itemTotal(machine.criticalPhysicalItemsSnapshot())==200,"physical item snapshot aggregates ten discovered terrace busses");
                check(fluidTotal(machine.criticalPhysicalFluidsSnapshot())==100,"physical fluid snapshot aggregates five discovered terrace hatches");
                List<CriticalEnergyPorts.Input> inputs=CriticalEnergyPorts.snapshot(machine);
                check(CriticalEnergyPorts.total(inputs,new FluidStack(fluid,1)).equals(BigInteger.valueOf(100)),"energy IO uses all newly discovered fluid hatches");
                check(CriticalEnergyPorts.debit(CriticalEnergyPorts.take(inputs,new FluidStack(fluid,1),BigInteger.valueOf(90))),"actual cross-hatch fluid debit succeeds");
                check(fluidTotal(machine.criticalPhysicalFluidsSnapshot())==10,"cross-hatch debit leaves exact actual inventory");
                CriticalNativeMEFunding.ItemConsumption consumed=CriticalHatchFunding.consumeAllItem(machine,machine.getCriticalInputBusses(),new ItemStack(MATERIAL,1));
                check(consumed.result==CriticalNativeMEFunding.Result.SUCCESS&&consumed.amount==200,"actual cross-bus item debit consumes all discovered inventory once");
                check(itemTotal(machine.criticalPhysicalItemsSnapshot())==0,"cross-bus debit leaves no physical input items");
                CriticalEnergyPorts.OutputPlan plan=CriticalEnergyPorts.outputs(machine,List.of(new CriticalEnergyPorts.FluidAmount(new FluidStack(fluid,1),BigInteger.valueOf(250))),new ItemStack[]{new ItemStack(MATERIAL,130)});
                check(plan!=null,"real new output lists have combined space");plan.commit();
                check(itemTotal(machine.criticalOutputItemsSnapshot())==130&&fluidTotal(machine.criticalOutputFluidsSnapshot())==250,"real transaction splits outputs across new busses and hatches");
                // An occupied new chamber is player property; both free and paid support cleanup leave it intact.
                set(s,"state",3);set(s,"cursor",geometry(s).size());
                if(kind==CriticalMachineKind.SUN){((NBTTagCompound)field(s,"stellarRuntime")).setLong("refund",1);discover(machine);
                    check(((List<?>)CriticalV32Contract.deep(machine,"mDynamoHatches").get(machine)).size()==5,"deferred supplies retain terrace dynamos during pending stellar refund");}
                for(CriticalGeometry.Voxel p:ports)check(s.protectsPlayerBreak(s.blockWorldX(p),s.blockWorldY(p.y-1),s.blockWorldZ(p)),"deferred supply remains protected after reverse cursor passed it");
                if(revision==31)freeClear(s);else paidClear(s);
                for(BaseMetaTileEntity tile:tiles)check(w.getTileEntity(tile.xCoord,tile.yCoord,tile.zCoord)==tile,"teardown leaves every player chamber tile at its own coordinate");
                check(retainedOutputItems(tiles)==130&&retainedOutputFluids(tiles)==250,"support cleanup preserves actual player output inventory after recipe lists change");
                for(CriticalGeometry.Voxel p:ports)check(!s.isStandardHatch(p),"removed supply immediately revokes new port even while GT tile remains");
                discover(machine);check(machine.getCriticalInputBusses().isEmpty()&&machine.getCriticalInputHatches().isEmpty(),"real discovery drops floating chambers after support cleanup");
            }
    }
    static void negatives()throws Exception {
        SparseWorld w=world();CriticalMachineState s=CriticalSupplyTerraceContract.prepared(w,31,CriticalMachineKind.SUN,ForgeDirection.NORTH);
        MTECriticalMachine host=host(s);CriticalGeometry.Voxel p=CriticalGeometry.terraceWorkPorts().get(0);
        CriticalGeometry.Voxel support=CriticalGeometry.terraceSupplies().get(0);chamber(s,p,0,7);put(s,support,support.block);
        check(!s.isStandardHatch(p),"foreign supply of exactly the same material is not an owned work port");discover(host);
        check(host.getCriticalInputBusses().isEmpty(),"foreign matching supply is excluded by actual discovery");
        w.blocks.remove(CriticalSupplyTerraceContract.pos(s,support));CriticalSupplyTerraceContract.finish(s);discover(host);
        check(host.getCriticalInputBusses().size()==1,"clearing support conflict allows free construction and later discovery");
        set(s,"jobId","foreign-task");check(!s.isStandardHatch(p),"copied/wrong task cannot use compatibility support ownership");discover(host);
        check(host.getCriticalInputBusses().isEmpty(),"wrong task removes chamber from actual discovered list");
        set(s,"jobId","paid-partial");w.loaded=false;int reads=w.unloadedReads;check(!s.isStandardHatch(p)&&w.unloadedReads==reads,"unloaded support makes no world reads or fake hatch promise");
    }
    static void run()throws Exception {
        fluid=new Fluid("gtsr_terrace_contract");check(FluidRegistry.registerFluid(fluid),"bounded fixture fluid registered");
        matrix();negatives();
    }
}
