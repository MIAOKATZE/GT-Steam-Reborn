import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityEntangler;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.*;

/** Calls the real per-second pipeline and real multi-hatch debit; replaces only world/explosion effects. */
public final class Issue60OverlimitRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("Issue60Runtime", args[0]);
    }
}

class Issue60Runtime {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    static class Tank extends MTEHatchInput {
        FluidStack fluid;
        Tank() { super("fixture",0,new String[0],null); }
        public FluidTankInfo[] getTankInfo(ForgeDirection side) {
            return new FluidTankInfo[]{new FluidTankInfo(fluid, Integer.MAX_VALUE)};
        }
        public FluidStack drain(ForgeDirection side, FluidStack request, boolean debit) {
            if (fluid==null || !fluid.isFluidEqual(request)) return null;
            int count=Math.min(fluid.amount,request.amount);
            FluidStack result=new FluidStack(fluid,count);
            if(debit) fluid.amount-=count;
            return result;
        }
    }
    static class Machine extends MTESteamSingularityEntangler {
        List<MTEHatch> inputs;
        Machine() { super("fixture"); }
        protected List<MTEHatch> getSteamInputHatches() { return inputs; }
        protected boolean checkOverlimitExplosionChain() { return false; }
        void cycle() { processAggregationCycle(); }
        void step() { mMachine=true; mMaxProgresstime=20; updateOverlimitMechanics(getBaseMetaTileEntity()); }
        void feed(String name,int... amounts) throws Exception {
            inputs=new ArrayList<>();
            for(int count:amounts) {
                Tank tank=(Tank)unsafe.allocateInstance(Tank.class);
                final int coordinate=inputs.size();
                IGregTechTileEntity base=(IGregTechTileEntity)Proxy.newProxyInstance(Tank.class.getClassLoader(),
                    new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->switch(m.getName()) {
                        case "getXCoord" -> coordinate; case "getYCoord" -> (short)0; case "getZCoord" -> 0;
                        case "getMetaTileEntity" -> tank; default -> null;
                    });
                tank.setBaseMetaTileEntity(base);
                tank.fluid=FluidRegistry.getFluidStack(name,count); inputs.add(tank);
            }
        }
    }
    static Machine machine() throws Exception {
        Machine machine=(Machine)unsafe.allocateInstance(Machine.class);
        machine.inputs=new ArrayList<>();
        IGregTechTileEntity base=(IGregTechTileEntity)Proxy.newProxyInstance(Machine.class.getClassLoader(),
            new Class<?>[]{IGregTechTileEntity.class},(p,m,a)->switch(m.getName()) {
                case "isAllowedToWork" -> true; case "getMetaTileEntity" -> machine; default -> null;
            });
        machine.setBaseMetaTileEntity(base); return machine;
    }
    public static void run(String version) throws Exception {
        unsafe=Issue37Runtime.bootstrap(version);
        for(String name:List.of("pyrotheum","cryotheum")) FluidRegistry.registerFluid(new Fluid(name));
        Machine machine=machine();
        machine.feed("pyrotheum",5000); machine.step();
        require(Math.abs(machine.mOverlimit-2.49875d)<1e-10,"5000mB pyrotheum must add 2.5 percentage points before decay: "+machine.mOverlimit);
        machine.mInstability=10;
        machine.feed("cryotheum",1000); machine.step();
        require(Math.abs(machine.mInstability-9.5d)<1e-10,"1000mB cryotheum must subtract 0.5 percentage points: "+machine.mInstability);
        machine=machine();
        for(int second=0;second<250;second++) { machine.feed("pyrotheum",5000); machine.step(); }
        require(machine.mOverlimit>500,"SSE overlimit threshold must be reachable from zero");
        require(machine.mInstability>0,"normal operation above 200 must accumulate instability");
        machine=machine(); machine.mOverlimit=201;
        machine.step();
        require(machine.mInstability>0,"stored overlimit above 200 accumulates instability");
        machine=machine(); machine.mOverlimit=100;
        machine.step();
        require(Math.abs(machine.mOverlimit-99.95d)<1e-10,"natural decay remains unchanged");
        machine=machine();
        machine.feed("pyrotheum",Integer.MAX_VALUE,Integer.MAX_VALUE); machine.step();
        require(machine.mOverlimit>4.9d,"multiple large input hatches cannot overflow consumption sum");
        inputLimits();
        System.out.println("RESULT overlimit PASS checks="+checks+"; actual SSE pipeline, explosion hook suppressed, no game lifecycle");
    }

    static void inputLimits() throws Exception {
        var control=new com.miaokatze.gtsr.common.terminal.entanglement.EntanglementInputs();
        Machine machine=machine();machine.feed("pyrotheum",5000,5000);
        require(machine.applyEntanglementLimits(Map.of("0,0,0,3",0L,"1,0,0,3",1000L),0),"apply independent hatch limits");
        machine.step();
        require(((Tank)machine.inputs.get(0)).fluid.amount==5000&&((Tank)machine.inputs.get(1)).fluid.amount==4000,"zero and finite limits debit actual tanks independently");
        require(Math.abs(machine.mOverlimit-com.miaokatze.gtsr.common.terminal.entanglement.EntanglementMath.decayOverlimit(5d*1000/6000))<1e-10,"heat state uses actual limited auxiliary consumption");
        require(!machine.applyEntanglementLimits(Map.of("1,0,0,3",2000L),0),"stale revision rejected");
        require(!machine.applyEntanglementLimits(Map.of("999,0,0,3",2000L),1),"unconnected input rejected");
        require(machine.applyEntanglementLimits(Map.of(),1,true),"restore-all accepted");
        machine.step();
        require(((Tank)machine.inputs.get(0)).fluid.amount==0&&((Tank)machine.inputs.get(1)).fluid.amount==0,"restored defaults consume all inputs");
        require(machine.entanglementInputRows().size()==2,"empty observed fluids retain their editing rows");
        require(control.apply(Map.of("1,2,3,4",0L,"4,5,6,3",Long.MAX_VALUE),0),"long limits accepted");
        net.minecraft.nbt.NBTTagCompound nbt=new net.minecraft.nbt.NBTTagCompound();control.save(nbt);
        var loaded=new com.miaokatze.gtsr.common.terminal.entanglement.EntanglementInputs();loaded.load(nbt);
        net.minecraft.nbt.NBTTagCompound second=new net.minecraft.nbt.NBTTagCompound();loaded.save(second);
        require(nbt.equals(second),"NBT roundtrip preserves zero and max-long limits");
        var parse=com.miaokatze.gtsr.common.gui.mui2.entanglement.EntanglementTerminalSession.class;
        var method=parse.getMethod("parse",String.class);
        require(method.invoke(null,"").equals(-1L)&&method.invoke(null,"0").equals(0L),"blank and zero semantics");
        require(method.invoke(null,"9223372036854775807").equals(Long.MAX_VALUE),"exact long precision");
        require(method.invoke(null,"9223372036854775808")==null&&method.invoke(null,"-1")==null&&method.invoke(null,"1.5")==null,"invalid limits rejected without floating-point conversion");
        machine=machine();machine.feed("steam",10000);
        require(machine.applyEntanglementLimits(Map.of("0,0,0,0",1000L),0),"steam cap applied");
        machine.cycle();
        require(((Tank)machine.inputs.get(0)).fluid.amount==9000,"production cycle honors steam cap");
        require(Math.abs(machine.mHeat-.005*.5*1000/201000)<1e-12,"production heat follows actual steam debit");
        machine=machine();machine.feed("ic2superheatedsteam",1000);Tank higher=(Tank)machine.inputs.get(0);
        machine.feed("steam",1000);machine.inputs.add(higher);
        // Distinct kinds at the same position are independently limited, including a multi-fluid hatch.
        require(machine.applyEntanglementLimits(Map.of("0,0,0,1",0L),0),"high grade can be disabled");
        machine.cycle();
        require(higher.fluid.amount==1000&&((Tank)machine.inputs.get(0)).fluid.amount==0,"highest enabled grade fallback reaches actual cycle");
        var snapshot=com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot.assemble(false,true,true,true,true,42.6,240,35,99,0,0,machine.entanglementInputRows());
        byte[] encoded=snapshot.encode();
        var decoded=com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot.decode(encoded);
        require(Arrays.equals(encoded,decoded.encode())&&decoded.tear==0,"bounded snapshot roundtrip; SSE tear is always absent");
        require(encoded.length<2048,"snapshot fits fixed network budget");
        boolean trailing=false;
        try{com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot.decode(Arrays.copyOf(encoded,encoded.length+1));}catch(java.io.IOException expected){trailing=true;}
        require(trailing,"trailing network bytes rejected");
    }
}
