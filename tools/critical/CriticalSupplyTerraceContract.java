package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import com.google.gson.*;

/** Actual candidate State/NBT/placement methods in SparseWorld, without FML or a client. */
public final class CriticalSupplyTerraceContract {
    static JsonObject releasedBase;
    static List<Object[]> reconstructed;
    static long releasedFingerprint(CriticalMachineState state) {
        CriticalMachineKind kind=state.getKind();
        Map<Long,Object[]> cells=new HashMap<>();
        for(JsonElement entry:releasedBase.getAsJsonArray("blocks")) {
            JsonArray r=entry.getAsJsonArray();int x=r.get(0).getAsInt(),y=r.get(1).getAsInt(),z=r.get(2).getAsInt();
            cells.put(CriticalGeometry.Voxel.positionKey(x,y,z),new Object[]{x,y,z,r.get(3).getAsString(),r.get(4).getAsString(),Math.min(3,y/16+1)});
        }
        for(String slug:new String[]{"v2_"+kind.key,"v2_loom"}) {
            // Existing eight decorative modules are retained by Configuration, even when ctor args are zero.
            // Their inverse sampler and released mount metadata are unchanged by the terrace.
            if(slug.endsWith("loom"))for(CriticalGeometry.Voxel v:CriticalGeometry.physicalPlugins(state))
                cells.put(v.positionKey(),new Object[]{v.x,v.y,v.z,v.material,v.group,v.stage});
            for(CriticalGeometry.Voxel v:CriticalGeometry.getModel(slug).voxels)
                cells.put(v.positionKey(),new Object[]{v.x,v.y,v.z,v.material,v.group,v.stage});
        }
        List<Object[]> rows=new ArrayList<>(cells.values());rows.sort(Comparator.comparingInt((Object[] r)->(Integer)r[5])
            .thenComparingInt(r->(Integer)r[1]).thenComparingInt(r->(Integer)r[0]).thenComparingInt(r->(Integer)r[2]));
        reconstructed=rows;
        long h=0xcbf29ce484222325L;for(Object[] r:rows){h=(h^CriticalGeometry.Voxel.positionKey((Integer)r[0],(Integer)r[1],(Integer)r[2]))*0x100000001b3L;
            h=(h^r[3].hashCode()^r[4].hashCode()^(Integer)r[5])*0x100000001b3L;}
        return h;
    }
    static Object call(CriticalMachineState s,String name)throws Exception {
        Method m=CriticalMachineState.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(s);
    }
    static void budget(CriticalMachineState s)throws Exception {
        set(s,"blocksChangedThisTick",0);set(s,"lightsChangedThisTick",0);
        set(s,"constructionBlockBudget",128);set(s,"constructionLightBudget",16);set(s,"blockBudgetStarted",0L);
    }
    static BitSet installed(CriticalMachineState s)throws Exception{return (BitSet)field(s,"supplyTerracePlaced");}
    static NBTTagCompound save(CriticalMachineState s){NBTTagCompound n=new NBTTagCompound();s.writeToNBT(n);return n;}
    static NBTTagCompound paid(NBTTagCompound n){n=(NBTTagCompound)n.copy();n.removeTag("supplyTerracePlaced");n.removeTag("supplyTerraceCursor");return n;}
    static CriticalMachineState prepared(SparseWorld w,int revision,CriticalMachineKind kind,ForgeDirection facing)throws Exception {
        CriticalMachineState s=state(w,revision,facing);
        set(s,"config",new CriticalConfiguration(CriticalTier.T3,kind,0,0,0));set(s,"geometryCache",null);
        geometry(s);set(s,"jobFingerprint",field(s,"modelFingerprint"));set(s,"structureValid",true);
        CriticalWorldData data=CriticalWorldData.get(w);
        check(data.acquire(s.getStructureId(),"owner",s.getOriginX(),s.getOriginY(),s.getOriginZ(),s.getRegionSize(),s.getRegionHeight(),s.xCoord,s.yCoord,s.zCoord),"reserve actual owner rectangle");
        check(data.bindTask(s.getStructureId(),"owner",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"bind exact task");
        return s;
    }
    static String pos(CriticalMachineState s,CriticalGeometry.Voxel v){return key(s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v));}
    static void supplement(CriticalMachineState s)throws Exception {
        budget(s);call(s,"supplementSupplyTerrace");
        check((Integer)field(s,"blocksChangedThisTick")<=128 && (Integer)field(s,"lightsChangedThisTick")<=16,"existing placement/light budget");
    }
    static void finish(CriticalMachineState s)throws Exception {
        for(int i=0;i<2000&&(Integer)field(s,"supplyTerraceCursor")<CriticalGeometry.supplyTerrace().size();i++)supplement(s);
        check(installed(s).cardinality()==1314,"all free extension cells placed exactly once");
    }
    static void erase(CriticalMachineState s)throws Exception {
        for(int i=0;i<2000&&!installed(s).isEmpty();i++){budget(s);call(s,"removeSupplyTerrace");}
        check(installed(s).isEmpty(),"all recorded free cells removed");
    }
    static void matrix()throws Exception {
        for(CriticalMachineKind kind:new CriticalMachineKind[]{CriticalMachineKind.SOLAR,CriticalMachineKind.TURBINE,CriticalMachineKind.BATTERY,CriticalMachineKind.SUN})
            for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.SOUTH,ForgeDirection.WEST,ForgeDirection.EAST}) {
                SparseWorld w=world();CriticalMachineState s=prepared(w,31,kind,facing);
                NBTTagCompound before=paid(save(s));long fingerprint=(Long)field(s,"jobFingerprint");
                long expected=releasedFingerprint(s);
                if(fingerprint!=expected) {
                    List<CriticalGeometry.Voxel> actual=geometry(s);
                    System.out.println("FINGERPRINT "+kind+" revision="+s.getV2LayoutRevision()+" actual="+fingerprint+" expected="+expected+" rows="+actual.size()+"/"+reconstructed.size());
                    for(int i=0;i<Math.min(actual.size(),reconstructed.size());i++) {
                        CriticalGeometry.Voxel v=actual.get(i);Object[] a={v.x,v.y,v.z,v.material,v.group,v.stage};
                        if(!Arrays.equals(a,reconstructed.get(i))){System.out.println("FIRST DIFF "+i+" actual="+Arrays.toString(a)+" expected="+Arrays.toString(reconstructed.get(i)));break;}
                    }
                }
                check(fingerprint==expected,"released v41 exact paid order/fingerprint independent reconstruction");
                supplement(s);check(installed(s).cardinality()>0&&installed(s).cardinality()<1314,"bounded partial upgrade");
                check(before.equals(paid(save(s))),"partial supplement preserves every prior NBT field");
                CriticalGeometry.Voxel first=CriticalGeometry.supplyTerrace().get(0);
                check(s.protectsPlayerBreak(s.blockWorldX(first),s.blockWorldY(first.y),s.blockWorldZ(first)),"owned new shell protected");
                check(CriticalWorldData.get(w).protectsPlayerBreak(w,s.blockWorldX(first),s.blockWorldY(first.y),s.blockWorldZ(first)),"persisted region protection works without loaded live host");
                NBTTagCompound saved=save(s);CriticalMachineState resumed=new CriticalMachineState();resumed.readFromNBT(saved);
                resumed.worldObj=w;resumed.xCoord=s.xCoord;resumed.yCoord=s.yCoord;resumed.zCoord=s.zCoord;
                geometry(resumed);set(resumed,"structureValid",true);s=resumed;
                check(s.getV2LayoutRevision()==31,"paid reload keeps revision 31");
                finish(s);
                check(before.equals(paid(save(s)))&&fingerprint==(Long)field(s,"modelFingerprint"),"full upgrade preserves original paid NBT and fingerprint");
                int blocks=w.blocks.size();supplement(s);check(w.blocks.size()==blocks&&installed(s).cardinality()==1314,"completed upgrade does not repeat");
                NBTTagCompound snapshot=s.protectionSnapshot();CriticalMachineState claim=new CriticalMachineState();
                claim.worldObj=w;claim.xCoord=s.xCoord;claim.yCoord=s.yCoord;claim.zCoord=s.zCoord;claim.readProtectionSnapshot(snapshot);
                check(claim.protectsPlayerBreak(s.blockWorldX(first),s.blockWorldY(first.y),s.blockWorldZ(first)),"protection bitmap reload");
                erase(s);check(w.blocks.isEmpty(),"free shell teardown leaves no owned blocks or item entities");
                check(before.equals(paid(save(s))),"free shell removal has no recovery or debit ledger entry");
            }
    }
    static void blockers()throws Exception {
        SparseWorld w=world();CriticalMachineState s=prepared(w,31,CriticalMachineKind.SUN,ForgeDirection.NORTH);
        CriticalGeometry.Voxel first=CriticalGeometry.supplyTerrace().get(0);
        w.blocks.put(pos(s,first),first.block);String status=s.getStatus();int reason=s.getReasonCode();
        supplement(s);check(installed(s).isEmpty()&&(Integer)field(s,"supplyTerraceCursor")==0,"same-material foreign shell is not adopted");
        check(s.getSupplyTerraceNoticeKey().equals("view.terrace.blocked")&&!s.getSupplyTerraceNoticeDetail().isEmpty(),"foreign block exposes independent coordinate notice");
        check(s.getStatus().equals(status)&&s.getReasonCode()==reason,"supplement obstruction is not a production blocker");
        check(!s.protectsPlayerBreak(s.blockWorldX(first),s.blockWorldY(first.y),s.blockWorldZ(first)),"foreign shell is not protected by addon");
        w.blocks.clear();w.loaded=false;int reads=w.unloadedReads;supplement(s);
        check(w.unloadedReads==reads&&installed(s).isEmpty(),"unloaded target causes no world read or placement");
        check(s.getSupplyTerraceNoticeKey().equals("view.terrace.unloaded"),"unloaded target coordinate notice");
        w.loaded=true;supplement(s);check(!installed(s).isEmpty(),"cleared and loaded target automatically resumes");
        Block foreign=new Block(Material.rock){};w.blocks.put(pos(s,first),foreign);budget(s);
        // This position is lowest in the deterministic journal; the reverse pass may first remove later cells.
        for(int i=0;i<2000&&installed(s).cardinality()>1;i++){budget(s);call(s,"removeSupplyTerrace");}
        check(w.blocks.get(pos(s,first))==foreign&&installed(s).get(0),"teardown never deletes foreign replacement");
        w.blocks.put(pos(s,first),first.block);erase(s);check(w.blocks.isEmpty(),"conflict removal resumes without drops");
        SparseWorld empty=world();CriticalMachineState pending=prepared(empty,31,CriticalMachineKind.SUN,ForgeDirection.NORTH);
        set(pending,"state",1);supplement(pending);check(installed(pending).isEmpty(),"old in-progress paid job is untouched");
        set(pending,"state",2);set(pending,"structureValid",false);supplement(pending);check(installed(pending).isEmpty(),"invalid original structure is not supplemented");
        set(pending,"structureValid",true);set(pending,"jobId","foreign-task");supplement(pending);check(installed(pending).isEmpty(),"wrong task cannot claim free shells");
        set(pending,"jobId","paid-partial");set(pending,"enabled",false);supplement(pending);check(!installed(pending).isEmpty(),"completed valid old job supplements while production is off");
        SparseWorld currentWorld=world();CriticalMachineState current=prepared(currentWorld,32,CriticalMachineKind.SUN,ForgeDirection.NORTH);
        supplement(current);check(installed(current).isEmpty(),"new layout uses paid construction only");
    }
    static void history()throws Exception {
        for(int revision:new int[]{24,25,26,27,28,29,30,31}) {
            SparseWorld w=world();CriticalMachineState s=prepared(w,revision,CriticalMachineKind.SUN,ForgeDirection.NORTH);
            Set<Long> old=new HashSet<>();for(CriticalGeometry.Voxel v:geometry(s))old.add(v.positionKey());
            check(CriticalGeometry.supplyTerrace().stream().noneMatch(v->old.contains(v.positionKey())),"historical paid/manual geometry has no terrace conflict: "+revision);
            long fingerprint=(Long)field(s,"jobFingerprint");supplement(s);
            check((Long)field(s,"modelFingerprint")==fingerprint&&!installed(s).isEmpty(),"historical task fingerprint remains unchanged: "+revision);
        }
        CriticalMachineState empty=new CriticalMachineState();NBTTagCompound n=new NBTTagCompound();
        n.setInteger("geometryVersion",2);n.setInteger("v2LayoutRevision",31);n.setBoolean("loomJournalOwner",true);n.setBoolean("loomController",true);
        empty.readFromNBT(n);check(empty.getV2LayoutRevision()==32,"uncommissioned old loom selects current layout");
    }
    public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        releasedBase=new JsonParser().parse(Files.readString(Path.of(args[1]))).getAsJsonObject();
        CriticalTerracePortsContract.seedRegistry();materials();
        StringBuilder stable=new StringBuilder();for(CriticalGeometry.Voxel v:CriticalGeometry.supplyTerrace())
            stable.append(v.x).append(',').append(v.y).append(',').append(v.z).append(',').append(v.material).append(';');
        String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stable.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(digest.equals("60fa36add408d290619860c086e3f841aea2e448f5d6ef3fbde647e316e102fd"),"persisted V32 terrace position/material index order is immutable");
        CriticalTerracePortsContract.run();matrix();blockers();history();
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("checks",checks);
        result.addProperty("scope","candidate JAR production placement/NBT/ownership methods with sparse World; no FML, full client, live player or full paid construction run");
        result.addProperty("additionalGTSlots",30);result.addProperty("totalGTSlots",43);
        result.addProperty("nativeGTDiscoveryAndCrossPortIO",true);
        result.addProperty("fixtureBoundary","Native GT chamber tiles and production discoverHatches/addToMachineList/IO; sparse World completion flags and method calls, finite output capacity; no actual player ItemStack placement or full server lifecycle");
        JsonObject origins=new JsonObject();for(Class<?> c:new Class<?>[]{CriticalMachineState.class,CriticalGeometry.class,CriticalLayoutDefinition.class,CriticalWorldData.class,CriticalConstructionLedger.class,com.miaokatze.gtsr.common.machine.MTECriticalMachine.class,CriticalEnergyPorts.class,CriticalHatchFunding.class})
            origins.addProperty(c.getName(),Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        result.add("codeSources",origins);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("PASS CriticalSupplyTerraceContract checks="+checks);
    }
}
