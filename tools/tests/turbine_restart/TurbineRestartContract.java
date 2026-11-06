import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.launchwrapper.*;

/**
 * Detached GT265 / StructureLib 1.4.47 lifecycle contract; never starts a server or client.
 * Runs production NBT, ticks, fuel settlement and native pressure-hatch drain. Unsafe supplies
 * installation fields without mod registration. Structure results, energy insertion and pollution
 * are explicit boundary fakes; FML side and observer networking use separate detached gates.
 */
public final class TurbineRestartContract {
    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) urls.add(Path.of(p).toUri().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>(); Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        Launch.minecraftHome = new File(".");
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData", Object[].class)
            .invoke(null, (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."), new ArrayList<>() });
        Method run = Launch.classLoader.loadClass("TurbineRestartRuntime").getMethod("run");
        run.setAccessible(true); run.invoke(null);
    }
}

class TurbineRestartRuntime {
    static sun.misc.Unsafe unsafe;
    static int checks;
    static final Map<Object,boolean[]> switches=new IdentityHashMap<>();
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    static Field field(Class<?> type,String name) throws Exception {
        for (Class<?> c=type;c!=null;c=c.getSuperclass()) try {Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException next){}
        throw new NoSuchFieldException(name);
    }
    static void set(Object o,String name,Object value)throws Exception{field(o.getClass(),name).set(o,value);}
    static int number(Object o,String name)throws Exception{return field(o.getClass(),name).getInt(o);}
    static <T extends gregtech.api.metatileentity.implementations.MTEMultiBlockBase> T machine(Class<T> type)throws Exception {
        T m=(T)unsafe.allocateInstance(type);
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) for(Field f:c.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);
            if(f.getType()==ArrayList.class||f.getType()==List.class)f.set(m,new ArrayList<>());
            if(f.getType()==Set.class)f.set(m,new HashSet<>());
        }
        set(m,"mInventory",new net.minecraft.item.ItemStack[3]);
        m.mCoils=new it.unimi.dsi.fastutil.longs.LongArrayList();
        set(m,"machineVisualState",new com.miaokatze.gtsr.common.machinevisual.MachineVisualState());
        set(m,"checkRecipeResult",gregtech.api.recipe.check.CheckRecipeResultRegistry.NONE);
        set(m,"mExtendedFacing",com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.DEFAULT);
        set(m,"mLimits",(com.gtnewhorizon.structurelib.alignment.IAlignmentLimits)(direction,rotation,flip)->true);
        m.mStartUpCheck=100;m.mUpdate=1000;
        m.efficiencyDecrease=m instanceof com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine?0:1000;
        net.minecraft.world.World world=(net.minecraft.world.World)unsafe.allocateInstance(net.minecraft.world.WorldServer.class);
        set(world,"provider",new net.minecraft.world.WorldProviderSurface());
        set(world,"worldInfo",new net.minecraft.world.storage.WorldInfo(new net.minecraft.nbt.NBTTagCompound()));
        final boolean[] active={false,true};
        var tile=(gregtech.api.interfaces.tileentity.IGregTechTileEntity)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{gregtech.api.interfaces.tileentity.IGregTechTileEntity.class},(p,method,args)->{
            switch(method.getName()) {
                case "isServerSide":return true;
                case "isAllowedToWork":return active[1];
                case "getWorld":return world;
                case "getFrontFacing":return net.minecraftforge.common.util.ForgeDirection.NORTH;
                case "getMetaTileEntity":return m;
                case "setActive":active[0]=(Boolean)args[0];return null;
                case "isActive":return active[0];
            }
            Class<?> r=method.getReturnType();
            if(r==boolean.class)return false;if(r==int.class)return 0;if(r==long.class)return 0L;if(r==byte.class)return(byte)0;if(r==short.class)return(short)0;if(r==double.class)return 0D;if(r==float.class)return 0F;
            return null;
        });
        m.setBaseMetaTileEntity(tile);
        switches.put(tile,active);
        return m;
    }
    public static void run()throws Exception {
        net.minecraft.init.Bootstrap.func_151354_b();
        System.out.println("SOURCE GT="+gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class.getProtectionDomain().getCodeSource().getLocation());
        Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);unsafe=(sun.misc.Unsafe)u.get(null);
        var m=machine(com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine.class);
        var n=new net.minecraft.nbt.NBTTagCompound();n.setInteger("turbineEfficiency",125);n.setInteger("mEfficiency",12500);n.setInteger("idleTicks",19);n.setInteger("workingTicks",7);n.setInteger("fullFlowTicks",11);n.setInteger("materialTier",2);
        m.loadNBTData(n);
        m.onPostTick(m.getBaseMetaTileEntity(),1);
        System.out.println("OBSERVE startup="+m.mStartUpCheck+" rotor="+number(m,"turbineEfficiency")+" idle="+number(m,"idleTicks")+" GT="+m.mEfficiency);
        check(number(m,"turbineEfficiency")==125,"startup must preserve loaded rotor efficiency");
        check(number(m,"idleTicks")==19,"startup must freeze partial idle second");
        check(number(m,"workingTicks")==7&&number(m,"fullFlowTicks")==11,"startup freezes warmup counters");
        check(m.mEfficiency==12500,"startup preserves loaded GT display efficiency");
        var saved=new net.minecraft.nbt.NBTTagCompound();m.saveNBTData(saved);
        var restored=machine(HeavyProbe.class);restored.loadNBTData(saved);
        check(number(restored,"turbineEfficiency")==125&&number(restored,"idleTicks")==19,"actual Heavy NBT round trip preserves heat and idle fraction");
        restored.mStartUpCheck=1;restored.onPostTick(restored.getBaseMetaTileEntity(),2);
        check(number(restored,"turbineEfficiency")==125&&restored.mStartUpCheck==0,"zero startup check tick still freezes heat");
        restored.mMachine=true;restored.onPostTick(restored.getBaseMetaTileEntity(),3);
        check(number(restored,"turbineEfficiency")==115&&number(restored,"idleTicks")==0,"first operable idle tick resumes normal cooldown exactly");
        var mega=machine(MegaProbe.class);
        var mn=new net.minecraft.nbt.NBTTagCompound();mn.setInteger("mEfficiency",25000);mn.setInteger("mMaxProgresstime",1);mn.setInteger("mEfficiencyIncrease",1);mn.setInteger("mCasingTier",6);mn.setInteger("mMaterialTier",3);mn.setInteger("mSteamType",1);mn.setInteger("mSingularityMode",1);mn.setInteger("mSingularityModeTicks",4000);
        mn.setLong("mFullBaseEUt",1024);mn.setInteger("mEUt",1024);
        mega.loadNBTData(mn);
        for(int tick=1;tick<=100;tick++)mega.onPostTick(mega.getBaseMetaTileEntity(),tick);
        System.out.println("MEGA STARTUP="+mega.mStartUpCheck+" eff="+mega.mEfficiency+" formed="+mega.mMachine);
        check(mega.mEfficiency==25000,"Mega real GT startup preserves loaded efficiency");
        mega.onPostTick(mega.getBaseMetaTileEntity(),101);
        System.out.println("MEGA FIRST RUN eff="+mega.mEfficiency+" max="+mega.mMaxProgresstime+" steam="+mega.mSteamType);
        check(mega.mEfficiency==25000,"Mega loaded heat survives the first real NO_FUEL check");
        int paidOutputs=mega.outputs;
        for(int tick=102;tick<=126;tick++)mega.onPostTick(mega.getBaseMetaTileEntity(),tick);
        check(mega.mEfficiency==25000,"Mega bounded restore window covers native idle decay while input recovers");
        for(int tick=127;tick<=200;tick++)mega.onPostTick(mega.getBaseMetaTileEntity(),tick);
        check(mega.mEfficiency==25000,"Mega protects exactly 100 loaded ticks after GT startup");
        check(mega.outputs==paidOutputs,"recovery preserves heat without producing more unpaid energy");
        mega.onPostTick(mega.getBaseMetaTileEntity(),201);
        check(mega.mEfficiency==24000,"Mega original native idle decay resumes immediately after expiry");
        mega.mEfficiency=25000;mega.stopMachine(gregtech.api.util.shutdown.ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        check(mega.mEfficiency==25000,"Mega retains its existing structure-incomplete protection");
        mega.stopMachine(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
        check(mega.mEfficiency==0,"Mega real player stop still clears GT efficiency");
        var paid=machine(MegaProbe.class);paid.loadNBTData(mn);paid.mStartUpCheck=0;paid.mMachine=true;
        paid.onPostTick(paid.getBaseMetaTileEntity(),1);
        paid.mPowerParameter=2;paid.mSingularityMode=0;
        net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("supercriticalsteam"));
        var source=(com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput)unsafe.allocateInstance(com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput.class);
        set(source,"watchers",new ArrayList<>());set(source,"mInventory",new net.minecraft.item.ItemStack[3]);set(source,"mFluidCapacity",512000);
        final boolean[] modified={false};
        var sourceTile=(gregtech.api.interfaces.tileentity.IGregTechTileEntity)Proxy.newProxyInstance(source.getClass().getClassLoader(),new Class<?>[]{gregtech.api.interfaces.tileentity.IGregTechTileEntity.class},(p,method,args)->{
            switch(method.getName()) {
                case "getMetaTileEntity":return source;
                case "isServerSide":case "isAllowedToWork":return true;
                case "hasInventoryBeenModified":return modified[0];
                // Same boundary flag as GT CommonBaseMetaTileEntity.markDirty(), without a live world.
                case "markDirty":modified[0]=true;return null;
            }
            Class<?> r=method.getReturnType();if(r==boolean.class)return false;if(r==int.class)return 0;if(r==long.class)return 0L;return null;
        });
        source.setBaseMetaTileEntity(sourceTile);
        check(paid.addToMachineList(sourceTile,0),"production pressure-hatch registration accepts the source");
        check(source.fill(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.getFluid("supercriticalsteam"),500_000),true)==500_000,"real pressure-hatch fill marks the tank modified within its native capacity");
        var stock=source.getFluid();
        source.onPostTick(sourceTile,1);
        paid.onPostTick(paid.getBaseMetaTileEntity(),2);
        check(stock.amount<500_000,"native hatch change push triggers real controller payment after temporary no fuel");
        check(number(paid,"mLoadRecoveryTicks")==0,"only scheduled paid success immediately consumes the one-time load recovery");
        System.out.println("PAYMENT actualNativeDrain="+(500_000-stock.amount)+" recovery="+number(paid,"mLoadRecoveryTicks"));
        stock.amount=0;
        paid.onPostTick(paid.getBaseMetaTileEntity(),3);
        System.out.println("PAID RECHECK eff="+paid.mEfficiency+" recovery="+number(paid,"mLoadRecoveryTicks")+" max="+paid.mMaxProgresstime+" stock="+source.getFluidAmount());
        check(paid.mEfficiency<25000,"subsequent no fuel after paid recovery uses original 500 decay");
        int afterPaid=paid.mEfficiency;paid.onPostTick(paid.getBaseMetaTileEntity(),4);
        check(paid.mEfficiency==afterPaid-1000,"subsequent native idle decay is unchanged after paid recovery");
        var stopped=machine(MegaProbe.class);stopped.loadNBTData(mn);stopped.mStartUpCheck=0;stopped.mMachine=true;
        stopped.stopMachine(gregtech.api.util.shutdown.ShutDownReasonRegistry.NONE);
        stopped.onPostTick(stopped.getBaseMetaTileEntity(),1);
        check(stopped.mEfficiency==0&&number(stopped,"mLoadRecoveryTicks")==0,"explicit player stop during recovery cannot resurrect heat");
        var disabled=machine(MegaProbe.class);disabled.loadNBTData(mn);disabled.mStartUpCheck=0;disabled.mMachine=true;disabled.mMaxProgresstime=0;
        switches.get(disabled.getBaseMetaTileEntity())[1]=false;disabled.onPostTick(disabled.getBaseMetaTileEntity(),1);
        check(disabled.mEfficiency==24000&&number(disabled,"mLoadRecoveryTicks")==0,"disabled controller immediately cancels recovery and keeps normal cooling");
        var cold=machine(MegaProbe.class);var coldN=(net.minecraft.nbt.NBTTagCompound)mn.copy();coldN.setInteger("mEfficiency",0);coldN.setInteger("mMaxProgresstime",0);
        cold.loadNBTData(coldN);cold.mStartUpCheck=0;cold.mMachine=true;cold.onPostTick(cold.getBaseMetaTileEntity(),1);
        check(cold.mEfficiency==0&&number(cold,"mLoadRecoveryTicks")==0,"zero-efficiency legacy saves gain no free heat or recovery");
        var broken=machine(MegaProbe.class);broken.loadNBTData(mn);broken.mStartUpCheck=0;
        broken.onPostTick(broken.getBaseMetaTileEntity(),1);
        check(broken.mEfficiency==25000&&broken.outputs==0&&broken.mMaxProgresstime==0,"unvalidated structure pauses production and never uses recovery to bypass GT validation");
        var kpa=machine(KpaProbe.class);var kn=new net.minecraft.nbt.NBTTagCompound();kn.setInteger("mEfficiency",10000);kn.setInteger("mEUt",-128);kn.setInteger("mMaterialTier",2);kn.setLong("boostModeUntil",24000);
        kpa.loadNBTData(kn);for(int tick=1;tick<=100;tick++)kpa.onPostTick(kpa.getBaseMetaTileEntity(),tick);
        check(kpa.mEfficiency==10000,"KPA startup retains standard GT efficiency");
        var ks=new net.minecraft.nbt.NBTTagCompound();kpa.saveNBTData(ks);
        check(ks.getInteger("mEfficiency")==10000&&ks.getLong("boostModeUntil")==24000,"KPA actual NBT retains efficiency and existing boost deadline");
        kpa.mStartUpCheck=0;kpa.mMachine=true;kpa.onPostTick(kpa.getBaseMetaTileEntity(),101);
        check(kpa.mEfficiency==9000,"KPA resumes its existing standard idle decay after startup");
        System.out.println("PASS real GT265 startup and NBT checks="+checks);
    }
}

class MegaProbe extends com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray {
    int outputs;
    MegaProbe(){super("test");}
    @Override public boolean checkStructure(boolean force,gregtech.api.interfaces.tileentity.IGregTechTileEntity tile){mMachine=true;return true;}
    @Override public boolean addEnergyOutput(long energy){outputs++;return true;}
    @Override public boolean polluteEnvironment(int pollution){return true;}
}

class KpaProbe extends com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray {
    KpaProbe(){super("test");}
    @Override public boolean checkStructure(boolean force,gregtech.api.interfaces.tileentity.IGregTechTileEntity tile){return true;}
}

class HeavyProbe extends com.miaokatze.gtsr.common.machine.MTEHeavySteamTurbine {
    HeavyProbe(){super("test");}
    @Override public boolean checkStructure(boolean force,gregtech.api.interfaces.tileentity.IGregTechTileEntity tile){return true;}
}
