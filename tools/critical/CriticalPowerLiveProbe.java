package com.miaokatze.gtsr.common.dimension.testlab;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLoomStructure;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;
import com.miaokatze.gtsr.common.critical.CriticalRuntime;
import com.miaokatze.gtsr.common.critical.CriticalStructures;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.mojang.authlib.GameProfile;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.structure.error.StructureError;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoTunnel;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel;

/** Owned test-mod helper. Called once per real ServerTick END; never sets paid/READY/formation fields. */
public final class CriticalPowerLiveProbe {

    private static CriticalPowerLiveProbe instance;
    private static final Class<?>[] TYPES = { MTEHatchEnergy.class, MTEHatchEnergyMulti.class,
        MTEHatchEnergyTunnel.class, MTEHatchDynamo.class, MTEHatchDynamoMulti.class,
        MTEHatchDynamoTunnel.class };

    private final Path receipt;
    private final JsonArray checks = new JsonArray();
    private final JsonArray cases = new JsonArray();
    private final List<int[]> loadedChunks = new ArrayList<>();
    private WorldServer world;
    private IGregTechTileEntity controller, current, receiver;
    private MTECriticalMachine machine;
    private CriticalMachineState state;
    private TestLabStructure loom;
    private ForgeDirection outward;
    private int portX, portY, portZ, ticks, testCase, laserStarted;
    private long laserSeed;
    private boolean built, finished;

    private CriticalPowerLiveProbe(Path receipt) { this.receipt = receipt; }

    /** Returns true after writing PASS/FAIL. The owning mod controls shutdown and other audits. */
    public static boolean onTick(MinecraftServer server, Path receipt) throws Exception {
        if (instance == null) instance = new CriticalPowerLiveProbe(receipt);
        CriticalPowerLiveProbe p = instance;
        if (p.finished) return true;
        try {
            p.tick();
        } catch (Throwable failure) {
            p.record("execution", false, failure.toString());
            failure.printStackTrace();
            p.finish();
        }
        return p.finished;
    }

    private void tick() throws Exception {
        if (++ticks > 300) throw new IllegalStateException("Live loom/laser fixture exceeded 300 END ticks");
        if (world == null) { setup(); return; }
        // A dedicated server has no nearby real player. Retain only this small fixture's real chunks.
        for (int[] chunk : loadedChunks) TestLabFixtures.requireChunk(world, chunk[0], chunk[1]);
        if (!built) {
            if (!loom.buildTick()) return;
            built = true;
            check("manual-loom-force-check", machine.forceLoomStructureCheck(), loom.describe());
            checkMachine("manual-loom");
            selectPort();
            return;
        }
        if (testCase < TYPES.length) { portCase(TYPES[testCase++]); return; }
        if (receiver == null) { laserReceiver(); return; }
        if (receiver.getStoredEU() > 0) {
            long received = receiver.getStoredEU(), source = current.getStoredEU();
            long loss = laserSeed - received - source;
            long nativeLoss = ((MTEHatch)current.getMetaTileEntity()).maxAmperesOut();
            check("laser-natural-direct-transfer", source == 0 && received > 0
                && (loss == nativeLoss || loss == nativeLoss * 2),
                "seed=" + laserSeed + " source=" + source + " received=" + received
                    + " nativeLoss=" + loss + " sourceTimer=" + current.getTimer()
                    + " targetTimer=" + receiver.getTimer() + " elapsed=" + (ticks-laserStarted));
            check("journal-stays-idle", state.getState() == 0 && !state.isStructureValidated(),
                "No paid journal, READY or commissioned main was created");
            finish();
        } else if (ticks-laserStarted > 80) {
            throw new IllegalStateException("Natural adjacent laser transfer timed out: source="
                + current.getStoredEU() + " target=" + receiver.getStoredEU()
                + " sourceTimer=" + current.getTimer() + " targetTimer=" + receiver.getTimer());
        }
    }

    private void setup() throws Exception {
        world = TestLabDimension.world();
        TestLabFixtures.attach(world);
        int x = 8192, y = 68, z = 8192;
        TestLabFixtures.requireChunk(world, x>>4, z>>4);
        check("owned-testlab-empty-controller", air(x,y,z), "isolated testlab at " + x + ',' + y + ',' + z);
        controller = TestLabFixtures.machine(world,x,y,z,CriticalRuntime.getLoomMetaId());
        machine = (MTECriticalMachine)controller.getMetaTileEntity();
        state = machine.getCriticalState();
        FakePlayer player = FakePlayerFactory.get(world,new GameProfile(TestLabFixtures.OWNER,"[Critical power audit]"));
        invoke(player,new String[]{"setPosition","func_70107_b"},
            new Class<?>[]{double.class,double.class,double.class},x+.5,y+.5,z-2.5);
        check("public-configuration", state.configure(player,CriticalTier.T3,CriticalMachineKind.SUN,0,0,0),
            "Real owner/proximity checks; no construction request or debug bypass");
        controller.disableWorking();
        Set<Long> chunks = new LinkedHashSet<>();
        for (CriticalGeometry.Voxel v : CriticalLoomStructure.voxels(state)) {
            int wx=state.blockWorldX(v), wy=state.blockWorldY(v.y), wz=state.blockWorldZ(v);
            chunks.add(((long)(wx>>4)<<32)^(wz>>4&0xffffffffL));
        }
        for (long key : chunks) {
            int cx=(int)(key>>32),cz=(int)key;
            TestLabFixtures.requireChunk(world,cx,cz);
            loadedChunks.add(new int[]{cx,cz});
        }
        for (CriticalGeometry.Voxel v : CriticalLoomStructure.voxels(state)) {
            int wx=state.blockWorldX(v), wy=state.blockWorldY(v.y), wz=state.blockWorldZ(v);
            if (wx==x && wy==y && wz==z) continue;
            if (!air(wx,wy,wz)) throw new IllegalStateException("Refusing nonempty owned loom cell at " + wx+','+wy+','+wz);
        }
        check("bare-loom-rejected", !machine.forceLoomStructureCheck(), "No formation field is assigned by the helper");
        loom = new TestLabStructure(world,machine,CriticalRuntime.getLoomControllerStack());
    }

    private void selectPort() throws Exception {
        for (CriticalGeometry.Voxel v : CriticalLoomStructure.voxels(state)) {
            if (!CriticalStructures.isHatchPosition(state,v)) continue;
            int x=state.blockWorldX(v),y=state.blockWorldY(v.y),z=state.blockWorldZ(v);
            for (ForgeDirection d : new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.SOUTH,
                ForgeDirection.WEST,ForgeDirection.EAST}) {
                if (!air(x+d.offsetX,y,z+d.offsetZ)) continue;
                portX=x;portY=y;portZ=z;outward=d;
                return;
            }
        }
        throw new IllegalStateException("Canonical manual loom has no exposed optional hatch position");
    }

    private int nativeId(Class<?> type) {
        for (int id=0;id<GregTechAPI.METATILEENTITIES.length;id++) {
            IMetaTileEntity mte=GregTechAPI.METATILEENTITIES[id];
            if (mte==null || mte.getClass()!=type) continue;
            MTEHatch hatch=(MTEHatch)mte;
            boolean input = type==MTEHatchEnergy.class || type==MTEHatchEnergyMulti.class || type==MTEHatchEnergyTunnel.class;
            long voltage=input?hatch.maxEUInput():hatch.maxEUOutput();
            boolean ordinary=type==MTEHatchEnergy.class || type==MTEHatchDynamo.class;
            // .265 MachineLoader registers 64A multi hatches and 256A (not64A) base UHV laser hatches.
            int requiredAmps=type==MTEHatchEnergyTunnel.class || type==MTEHatchDynamoTunnel.class?256:64;
            if (voltage==GTValues.V[9]
                && (ordinary || (input?hatch.maxWorkingAmperesIn():hatch.maxAmperesOut())==requiredAmps)) return id;
        }
        throw new IllegalStateException("Required registered UHV native hatch missing: " + type.getName());
    }

    private void portCase(Class<?> type) throws Exception {
        invoke(world,new String[]{"setBlockToAir","func_147468_f"},
            new Class<?>[]{int.class,int.class,int.class},portX,portY,portZ);
        int id=nativeId(type);
        current=TestLabFixtures.machine(world,portX,portY,portZ,id);
        current.setFrontFacing(outward);
        MTEHatch hatch=(MTEHatch)current.getMetaTileEntity();
        check(type.getSimpleName()+".force-loom-check",machine.forceLoomStructureCheck(),"registered id=" + id);
        checkMachine(type.getSimpleName());
        // Rebuild native hatch membership after the complete checker, using the real eligible port map.
        check(type.getSimpleName()+".rediscover",machine.forceLoomStructureCheck(),"No direct hatch-list injection");
        machine.setEUVar(0);
        JsonObject row=new JsonObject();row.addProperty("class",type.getName());row.addProperty("id",id);
        row.addProperty("x",portX);row.addProperty("y",portY);row.addProperty("z",portZ);
        if (CriticalMachineState.isEnergyInputHatchMeta(hatch)) {
            long budget=Math.min(hatch.maxEUInput()*hatch.maxWorkingAmperesIn(),MTECriticalMachine.criticalEnergyTickCap());
            hatch.setEUVar(budget*2);
            check(type.getSimpleName()+".native-budget",machine.criticalRecipePower()==budget,"budget="+budget);
            machine.debitCriticalEnergy(budget);
            check(type.getSimpleName()+".actual-debit",current.getStoredEU()==budget,
                "seed="+(budget*2)+" after="+current.getStoredEU());
            check(type.getSimpleName()+".same-tick-no-respend",machine.criticalRecipePower()==0,"working amperes consumed once");
            row.addProperty("inputDebited",budget);
        } else {
            long packet=hatch.maxEUOutput(),seed=packet*32;
            hatch.setEUVar(0);
            long accepted=machine.emitCriticalEnergy(seed);
            long expected=packet*Math.min(16L,hatch.maxAmperesOut());
            check(type.getSimpleName()+".actual-controller-output",accepted==seed && current.getStoredEU()==expected
                && controller.getStoredEU()==seed-expected,
                "seed="+seed+" host="+controller.getStoredEU()+" hatch="+current.getStoredEU());
            machine.emitCriticalEnergy(0);
            check(type.getSimpleName()+".same-tick-no-overemit",current.getStoredEU()==expected,
                "Native amps and shared16A allowance retained");
            row.addProperty("outputDelivered",expected);
            machine.setEUVar(0); // discard this explicitly seeded fixture remainder, not a gameplay journal credit
            if (type==MTEHatchDynamoTunnel.class) laserSeed=expected;
        }
        cases.add(row);
    }

    private void checkMachine(String label) throws Exception {
        List<StructureError> errors=new ArrayList<>();
        machine.checkMachine(controller,CriticalRuntime.getLoomControllerStack(),errors);
        check(label+".checkMachine",errors.isEmpty(),"native errors="+errors);
        check(label+".checkStructure",TestLabStructure.checkStructure(machine,controller),"Native complete structure verdict");
    }

    private void laserReceiver() throws Exception {
        int x=portX+outward.offsetX,y=portY,z=portZ+outward.offsetZ;
        TestLabFixtures.requireChunk(world,x>>4,z>>4);
        loadedChunks.add(new int[]{x>>4,z>>4});
        check("laser-neighbor-empty",air(x,y,z),"Direct native one-cell link; no long pipe or mirror claim");
        receiver=TestLabFixtures.machine(world,x,y,z,nativeId(MTEHatchEnergyTunnel.class));
        receiver.setFrontFacing(outward.getOpposite());
        current.setColorization((byte)3);receiver.setColorization((byte)3);
        ((MTEHatch)receiver.getMetaTileEntity()).setEUVar(0);
        // Natural ticks may have reached the laser before receiver placement. Set only the declared fixture EU seed.
        ((MTEHatch)current.getMetaTileEntity()).setEUVar(laserSeed);
        laserStarted=ticks;
    }

    private boolean air(int x,int y,int z) throws Exception {
        return (Boolean)invoke(world,new String[]{"isAirBlock","func_147437_c"},
            new Class<?>[]{int.class,int.class,int.class},x,y,z);
    }

    private static Object invoke(Object target,String[] names,Class<?>[] parameters,Object...args) throws Exception {
        for (String name:names) {
            Method method;
            try { method=target.getClass().getMethod(name,parameters); }
            catch (NoSuchMethodException absent) { continue; }
            return method.invoke(target,args);
        }
        throw new NoSuchMethodException(target.getClass().getName()+'.'+names[0]);
    }

    private void check(String name,boolean pass,String detail) {
        record(name,pass,detail);
        if (!pass) throw new AssertionError(name+": "+detail);
    }

    private void record(String name,boolean pass,String detail) {
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);row.addProperty("detail",detail);
        checks.add(row);System.out.println("CRITICAL_POWER_LIVE "+row);
    }

    private void finish() throws Exception {
        finished=true;
        boolean pass=true;
        for (com.google.gson.JsonElement row:checks) pass&=row.getAsJsonObject().get("pass").getAsBoolean();
        JsonObject result=new JsonObject();result.addProperty("status",pass?"PASS":"FAIL");
        result.addProperty("ticks",ticks);result.addProperty("dimension",TestLabDimension.id());
        result.addProperty("productSource",MTECriticalMachine.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("gtSource",MTEHatchDynamoTunnel.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("scope","Dedicated real FML server, owned TestLabDimension; full manually built loom from live StructureLib, registered native hatch replacements, complete checkMachine/checkStructure, seeded EU through controller debit/emit, natural adjacent same-color/opposite-facing/same-voltage laser transfer. No paid/READY or full critical main construction; no long cable/pipe/mirror transport claim.");
        result.add("checks",checks);result.add("cases",cases);
        Path parent=receipt.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        Files.write(receipt,new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
    }
}
