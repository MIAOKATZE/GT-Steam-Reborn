import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.*;
import tectech.thing.metaTileEntity.hatch.*;

/** Actual .265 hatch classes and controller paths, with sparse world/storage seeds; no game or Gradle launch. */
public final class CriticalPower265Probe {
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static JsonArray cases = new JsonArray();
    private static String report;
    private static boolean green;
    private static int nextId = 30000;

    public static void main(String[] args) throws Exception {
        System.setProperty("critical.power.report", args[0]);
        System.setProperty("critical.power.green", args.length > 1 ? args[1] : "false");
        Issue37RegistrationRegression.launch("CriticalPower265Probe", "5.09.54.265");
    }

    private static Field field(Object value, String name) throws Exception {
        for (Class<?> c = value instanceof Class ? (Class<?>)value : value.getClass(); c != null; c = c.getSuperclass())
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object value, String name, Object contents) throws Exception { field(value,name).set(value,contents); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static String source(Class<?> type) { return type.getProtectionDomain().getCodeSource().getLocation().toString(); }
    private static BaseMetaTileEntity attach(MetaTileEntity meta, SparseWorld world, int x) throws Exception {
        return attach(meta,world,x,64,0);
    }
    private static BaseMetaTileEntity attach(MetaTileEntity meta, SparseWorld world, int x, int y, int z) throws Exception {
        BaseMetaTileEntity tile=new BaseMetaTileEntity();
        tile.setWorldObj(world); tile.xCoord=x;tile.yCoord=y;tile.zCoord=z;
        set(meta,"mBaseMetaTileEntity",tile);tile.setMetaTileEntity(meta);world.tiles.put(x+":"+y+":"+z,tile);
        set(tile,"mID",(short)nextId);gregtech.api.GregTechAPI.METATILEENTITIES[nextId++]=meta;
        return tile;
    }
    private static SparseWorld world() throws Exception {
        SparseWorld w=(SparseWorld)unsafe.allocateInstance(SparseWorld.class);w.tiles=new HashMap<>();w.playerEntities=new ArrayList<>();
        set(w,"provider",new WorldProviderSurface());
        set(w,"chunkProvider",java.lang.reflect.Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(),new Class<?>[]{IChunkProvider.class},
            (p,m,a)->m.getName().equals("chunkExists")?true:null));
        return w;
    }
    private static MTECriticalMachine host(SparseWorld world, CriticalMachineKind kind) throws Exception {
        MTECriticalMachine host=new MTECriticalMachine("critical_power_fixture",kind);
        attach(host,world,0);
        set(host.getLocalCriticalState(),"config",new CriticalConfiguration(CriticalTier.T3,kind==null?CriticalMachineKind.SUN:kind,0,0,0));
        return host;
    }
    private static MTEHatch hatch(String family, int amps) {
        String[] text={"actual native probe"};ITexture[][][] textures=new ITexture[0][][];
        switch(family) {
            case "energy": return new MTEHatchEnergy("energy",9,text,textures);
            case "multi": return new MTEHatchEnergyMulti("multi",9,amps,text,textures);
            case "laser_input": return new MTEHatchEnergyTunnel("laser_input",9,amps,text,textures);
            case "dynamo": return new MTEHatchDynamo("dynamo",9,text,textures);
            case "multi_dynamo": return new MTEHatchDynamoMulti("multi_dynamo",9,amps,text,textures);
            case "laser_output": return new MTEHatchDynamoTunnel("laser_output",9,amps,text,textures);
            default: throw new IllegalArgumentException(family);
        }
    }
    @SuppressWarnings("unchecked")
    private static boolean port(MTECriticalMachine host, SparseWorld w, boolean loom) throws Exception {
        Constructor<?> constructor=Class.forName("com.miaokatze.gtsr.common.critical.CriticalStructures$PaidHatchElement").getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        IStructureElement<MTECriticalMachine> element=(IStructureElement<MTECriticalMachine>)constructor.newInstance(loom);
        return element.check(host,w,1,64,0);
    }
    private static void inputs() throws Exception {
        for(String family:new String[]{"energy","multi","laser_input"}) for(int amps:new int[]{4,64}) {
            SparseWorld w=world();MTECriticalMachine host=host(w,null);MTEHatch input=hatch(family,amps);
            BaseMetaTileEntity tile=attach(input,w,1);
            long budget=input.maxEUInput()*input.maxWorkingAmperesIn();set(tile,"mStoredEnergy",budget*2);
            check(CriticalMachineState.isEnergyInputHatchMeta(input),family+" input classified");
            boolean accepted=port(host,w,true);check(accepted,family+" loom native element accepted");
            check(host.getExoticAndNormalEnergyHatchList().contains(input),family+" registered input list");
            check(host.criticalRecipePower()==budget,family+" native per-hatch rate budget");
            CriticalEnergyDebit debit=host.debitCriticalEnergy(budget);
            check(tile.getStoredEU()==budget,family+" actual native EU decreased once");
            check(host.criticalRecipePower()==0,family+" no second spend of same tick amperes");
            JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("amps",amps);
            row.addProperty("nativeWorkingAmps",input.maxWorkingAmperesIn());row.addProperty("budget",budget);
            row.addProperty("storedAfter",tile.getStoredEU());row.addProperty("class",input.getClass().getName());cases.add(row);
        }
    }
    private static void outputs() throws Exception {
        for(String family:new String[]{"dynamo","multi_dynamo","laser_output"}) for(int amps:new int[]{4,64}) {
            SparseWorld w=world();MTECriticalMachine host=host(w,CriticalMachineKind.SUN);MTEHatch output=hatch(family,amps);
            BaseMetaTileEntity tile=attach(output,w,1);
            boolean main=port(host,w,false);host.clearHatches();boolean loom=port(host,w,true);host.clearHatches();
            boolean exotic=host.addExoticDynamoToMachineList(tile,0);
            if(!exotic)check(host.addDynamoToMachineList(tile,0),"normal dynamo registered");
            long packet=output.maxEUOutput();long request=packet*32;host.emitCriticalEnergy(request);
            long delivered=tile.getStoredEU();
            if(green) {
                check(main,family+" native main element accepted");check(loom,family+" native loom element accepted");
                check(delivered==packet*Math.min(16L,output.maxAmperesOut()),family+" native delivery respects amp cap");
                host.emitCriticalEnergy(0);check(tile.getStoredEU()==delivered,family+" repeated call cannot duplicate amp budget");
            } else {
                if(output.maxAmperesOut()>4)check(!main,family+" current >4A structure limitation reproduced");
                check(!loom,family+" current loom output exclusion reproduced");
                if(exotic)check(delivered==0,family+" current exotic output loop omission reproduced");
            }
            check(!CriticalMachineState.isEnergyInputHatchMeta(output),family+" never classified as input");
            JsonObject row=new JsonObject();row.addProperty("family",family);row.addProperty("amps",amps);row.addProperty("nativeAmps",output.maxAmperesOut());
            row.addProperty("mainAccepted",main);row.addProperty("loomAccepted",loom);row.addProperty("exoticRegistered",exotic);
            row.addProperty("nativeDelivered",delivered);row.addProperty("request",request);cases.add(row);
        }
    }
    private static void highVoltageAndDuplicates() throws Exception {
        SparseWorld w=world();MTECriticalMachine host=host(w,null);
        MTEHatchEnergyMulti input=new MTEHatchEnergyMulti("max_input",14,64,new String[]{"fixture"},new ITexture[0][][]);
        BaseMetaTileEntity tile=attach(input,w,1);set(tile,"mStoredEnergy",input.maxEUInput()*128L);
        check(host.addExoticEnergyInputToMachineList(tile,0),"native MAX input add");
        check(host.addExoticEnergyInputToMachineList(tile,0),"duplicate native adder call remains bounded");
        long before=tile.getStoredEU(),cap=MTECriticalMachine.criticalEnergyTickCap();
        check(host.criticalRecipePower()==cap,"MAX multiA still capped by existing controller EU/t");
        host.debitCriticalEnergy(cap);
        check(before-tile.getStoredEU()==cap&&host.criticalRecipePower()==0,"duplicate input references debit once and preserve total tick cap");
        SparseWorld outWorld=world();MTECriticalMachine outHost=host(outWorld,CriticalMachineKind.SUN);
        MTEHatchDynamoMulti high=new MTEHatchDynamoMulti("max_output",14,64,new String[]{"fixture"},new ITexture[0][][]);
        BaseMetaTileEntity out=attach(high,outWorld,1);outHost.addExoticDynamoToMachineList(out,0);
        outHost.addExoticDynamoToMachineList(out,0);
        outHost.emitCriticalEnergy(cap*2);
        if(green) {
            check(out.getStoredEU()==cap,"MAX output preserves16A and long EU delivery");
            outHost.emitCriticalEnergy(0);check(out.getStoredEU()==cap,"duplicate output references cannot reuse tick budget");
        } else check(out.getStoredEU()==0,"baseline MAX exotic output omission");
    }
    private static CriticalGeometry.Voxel voxel(int x,int y,int z,String model) throws Exception {
        Constructor<CriticalGeometry.Voxel> c=CriticalGeometry.Voxel.class.getDeclaredConstructor(int.class,int.class,int.class,
            Block.class,int.class,String.class,String.class,String.class);c.setAccessible(true);
        return c.newInstance(x,y,z,gregtech.api.GregTechAPI.sBlockCasings1,0,"fixture","static",model);
    }
    private static void discoveryPartitionAndLegacyCredit() throws Exception {
        SparseWorld w=world();MTECriticalMachine host=host(w,null);CriticalMachineState state=host.getLocalCriticalState();
        set(state,"geometryVersion",2);set(state,"v2LayoutRevision",31);set(state,"state",1);
        CriticalGeometry.Voxel loom=voxel(50,4,47,"v2_loom"),main=voxel(50,5,34,"v2_optional_work_port");
        set(state,"hatchPortsCache",Collections.unmodifiableList(Arrays.asList(loom,main)));
        MTEHatchEnergyMulti input=(MTEHatchEnergyMulti)hatch("multi",64);
        int x=state.blockWorldX(loom),y=state.blockWorldY(loom.y),z=state.blockWorldZ(loom);
        BaseMetaTileEntity in=attach(input,w,x,y,z);long rate=input.maxEUInput()*64;set(in,"mStoredEnergy",rate*2);
        MTEHatchEnergyMulti other=(MTEHatchEnergyMulti)hatch("multi",64);
        BaseMetaTileEntity excluded=attach(other,w,state.blockWorldX(main),state.blockWorldY(main.y),state.blockWorldZ(main));set(excluded,"mStoredEnergy",rate*2);
        check(state.getResourceHatchPorts().equals(Collections.singletonList(loom)),"construction uses loom ports only");
        java.lang.reflect.Method discover=MTECriticalMachine.class.getDeclaredMethod("discoverHatches",boolean.class);discover.setAccessible(true);
        discover.invoke(host,true);discover.invoke(host,true);
        check(host.getExoticAndNormalEnergyHatchList().size()==1&&host.getExoticAndNormalEnergyHatchList().get(0)==input,
            "forced rediscovery does not duplicate hatches or import commissioned main during construction");
        host.debitCriticalEnergy(rate);check(in.getStoredEU()==rate&&excluded.getStoredEU()==rate*2,"fabrication pays only from owned eligible input partition");
        set(state,"state",2);check(state.getResourceHatchPorts().equals(Collections.singletonList(main)),"running journal keeps main-only recipe port partition");
        set(state,"state",0);
        MTEHatchDynamoMulti output=(MTEHatchDynamoMulti)hatch("multi_dynamo",64);BaseMetaTileEntity out=attach(output,w,x,y,z);
        discover.invoke(host,true);discover.invoke(host,true);
        long credit=output.maxEUOutput()*4;set(state,"pendingGenerated",credit);
        NBTTagCompound saved=new NBTTagCompound();state.writeToNBT(saved);
        CriticalMachineState restored=new CriticalMachineState();restored.readFromNBT(saved);
        check(((Long)field(restored,"pendingGenerated").get(restored))==credit,"old pendingGenerated journal survives native NBT roundtrip");
        java.lang.reflect.Method emit=CriticalMachineState.class.getDeclaredMethod("emitEnergy");emit.setAccessible(true);emit.invoke(state);
        check(((Long)field(state,"pendingGenerated").get(state))==0&&out.getStoredEU()==credit,
            "loom existing legacy credit settles through actual dynamo without generating new EU");
        check(host.getBaseMetaTileEntity().getStoredEU()==0,"legacy credit not copied into both host and output");
        JsonObject row=new JsonObject();row.addProperty("scope","two declared canonical port-role fixture cells; no full paid structure");
        row.addProperty("forcedDiscoveryPasses",4);row.addProperty("legacyCreditDelivered",out.getStoredEU());cases.add(row);
    }
    private static void kindGates() throws Exception {
        for(CriticalMachineKind kind:CriticalMachineKind.values()) {
            SparseWorld w=world();MTECriticalMachine host=host(w,kind);MTEHatch output=hatch("multi_dynamo",64);BaseMetaTileEntity tile=attach(output,w,1);
            host.addExoticDynamoToMachineList(tile,0);long packet=output.maxEUOutput();host.emitCriticalEnergy(packet);
            boolean originalOutputKind=kind==CriticalMachineKind.SUN||kind==CriticalMachineKind.TURBINE||kind==CriticalMachineKind.BATTERY||kind==CriticalMachineKind.ACCELERATOR;
            check(tile.getStoredEU()==(originalOutputKind?packet:0),"existing direct emit kind gate preserved: "+kind);
        }
    }
    @SuppressWarnings("unchecked")
    private static void mixedOutputLists() throws Exception {
        SparseWorld w=world();MTECriticalMachine host=host(w,CriticalMachineKind.SUN);
        MTEHatch normal=hatch("dynamo",1),multi=hatch("multi_dynamo",64),laser=hatch("laser_output",64);
        BaseMetaTileEntity n=attach(normal,w,1),m=attach(multi,w,2),l=attach(laser,w,3);
        host.addDynamoToMachineList(n,0);host.addExoticDynamoToMachineList(m,0);host.addExoticDynamoToMachineList(l,0);
        long packet=normal.maxEUOutput();host.emitCriticalEnergy(packet*32);
        check(n.getStoredEU()==packet&&m.getStoredEU()==packet*15&&l.getStoredEU()==0,
            "mixed output lists spend one shared16A allowance without touching an unselected hatch");
        check(((List<?>)field(host,"mDynamoHatches").get(host)).size()==1
            &&((List<?>)field(host,"mExoticDynamoHatches").get(host)).size()==2,"both native output lists restored after isolated packet calls");
        host.emitCriticalEnergy(0);
        check(n.getStoredEU()+m.getStoredEU()+l.getStoredEU()==packet*16,"mixed repeated call cannot over-emit same tick");
        int highTier=gregtech.api.enums.GTValues.V.length-1;
        if(gregtech.api.enums.GTValues.V[highTier]>Integer.MAX_VALUE) {
            SparseWorld highWorld=world();MTECriticalMachine highHost=host(highWorld,CriticalMachineKind.SUN);
            MTEHatchDynamoMulti high=new MTEHatchDynamoMulti("unsupported_high_voltage",highTier,4,new String[]{"fixture"},new ITexture[0][][]);
            BaseMetaTileEntity highTile=attach(high,highWorld,1);highHost.addExoticDynamoToMachineList(highTile,0);
            highHost.emitCriticalEnergy(packet);
            check(highTile.getStoredEU()==0&&highHost.getBaseMetaTileEntity().getStoredEU()==packet,
                "existing above-INT native-helper voltage guard retains energy in host");
        }
    }
    public static void run(String version) throws Exception {
        report=System.getProperty("critical.power.report");green=Boolean.getBoolean("critical.power.green");
        unsafe=Issue37Runtime.bootstrap(version);
        inputs();outputs();highVoltageAndDuplicates();
        if(green){discoveryPartitionAndLegacyCredit();kindGates();mixedOutputLists();}
        JsonObject result=new JsonObject();result.addProperty("status",green?"PASS_TARGETED_NATIVE_API":"RED_LIMITATIONS_REPRODUCED");
        result.addProperty("checks",checks);result.addProperty("GT",source(MTEHatchEnergyTunnel.class));result.addProperty("product",source(MTECriticalMachine.class));
        result.addProperty("boundary","Actual .265 classes, StructureLib port element, real controller debit/emit and real hatch storage; sparse world/EU seeds, no full formation, cable/laser transport, Forge server or game");
        result.add("cases",cases);Files.write(Paths.get(report),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes("UTF-8"));
        System.out.println(result.get("status").getAsString()+" checks="+checks);
    }
    private static final class SparseWorld extends World {
        Map<String,TileEntity> tiles;
        private SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public TileEntity getTileEntity(int x,int y,int z){return tiles.get(x+":"+y+":"+z);}
        @Override public Block getBlock(int x,int y,int z){return tiles.containsKey(x+":"+y+":"+z)?gregtech.api.GregTechAPI.sBlockMachines:net.minecraft.init.Blocks.air;}
        @Override public int getBlockMetadata(int x,int y,int z){return 0;}
        @Override public boolean blockExists(int x,int y,int z){return true;}
        @Override public void markBlockForUpdate(int x,int y,int z){}
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile){}
        @Override public long getTotalWorldTime(){return 1;}
        @Override protected IChunkProvider createChunkProvider(){return null;}
        @Override protected int func_152379_p(){return 0;}
        @Override public Entity getEntityByID(int id){return null;}
    }
}
