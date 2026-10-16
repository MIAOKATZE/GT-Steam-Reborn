package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.Block;

/** Real GT205 controller/hatches; Unsafe skips registry constructors only. */
public final class ProbeSupport {
    public static final sun.misc.Unsafe U;
    public static int checks;
    static { try {Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);U=(sun.misc.Unsafe)f.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);} }
    public static void bootstrap()throws Exception {
        Thread.currentThread().setName("Server thread");
        Field side=cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");side.setAccessible(true);side.set(null,cpw.mods.fml.relauncher.Side.SERVER);
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="gregtech";metadata.name="GregTech";metadata.version=System.getProperty("probe.gt","5.09.54.205");
        Map<String,cpw.mods.fml.common.ModContainer> mods=new HashMap<>();mods.put("gregtech",new cpw.mods.fml.common.DummyModContainer(metadata));set(loader,"namedMods",mods);set(loader,"mods",new ArrayList<>(mods.values()));set(loader,"modController",new cpw.mods.fml.common.LoadController(loader));
        net.minecraft.init.Bootstrap.func_151354_b();
        net.minecraft.client.Minecraft mc=(net.minecraft.client.Minecraft)U.allocateInstance(net.minecraft.client.Minecraft.class);
        Field f=net.minecraft.client.Minecraft.class.getDeclaredField("theMinecraft");f.setAccessible(true);f.set(null,mc);
        set(mc,"mcResourceManager",new net.minecraft.client.resources.SimpleReloadableResourceManager(new net.minecraft.client.resources.data.IMetadataSerializer()));
        Field proxy=gregtech.GTMod.class.getField("proxy");if(proxy.get(null)==null)proxy.set(null,U.allocateInstance(proxy.getType()));
    }
    public static Field field(Object o,String n)throws Exception {
        for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(n);
    }
    public static void set(Object o,String n,Object v)throws Exception {field(o,n).set(o,v);}
    public static Object get(Object o,String n)throws Exception {return field(o,n).get(o);}
    public static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    public static Object call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    public static BaseMetaTileEntity attach(MetaTileEntity m)throws Exception {
        BaseMetaTileEntity b=(BaseMetaTileEntity)U.allocateInstance(BaseMetaTileEntity.class);
        set(m,"mBaseMetaTileEntity",b);b.setMetaTileEntity(m);b.setWorldObj(world());return b;
    }
    public static MTECriticalMachine host(CriticalMachineState s)throws Exception {
        MTECriticalMachine m=(MTECriticalMachine)U.allocateInstance(MTECriticalMachine.class);
        set(m,"state",s);set(m,"presetKind",s.getKind());
        set(m,"hatchEnergySpent",new IdentityHashMap<>());set(m,"portSnapshot",new LinkedHashMap<>());
        set(m,"meItemsSnapshot",new ItemStack[0]);set(m,"meFluidsSnapshot",new net.minecraftforge.fluids.FluidStack[0]);
        set(m,"meSnapshotTick",Long.MIN_VALUE);set(m,"hatchDiscoveryTick",Long.MIN_VALUE);
        set(m,"mInventory",new ItemStack[2]);
        for(Class<?> c=m.getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(List.class.isAssignableFrom(f.getType())&&!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);if(f.get(m)==null)f.set(m,f.getType().isInterface()?new ArrayList<>():f.getType().getDeclaredConstructor().newInstance());}
        BaseMetaTileEntity b=attach(m);s.bindHost(b);return m;
    }
    public static CriticalMachineState state(CriticalMachineKind kind)throws Exception {
        CriticalMachineState s=new CriticalMachineState();set(s,"config",new CriticalConfiguration(CriticalTier.T3,kind,0,0,0));return s;
    }
    @SuppressWarnings("unchecked") public static void add(MTECriticalMachine m,String field,Object hatch)throws Exception {((List<Object>)get(m,field)).add(hatch);}
    public static MTEHatchInputBus bus(ItemStack... inputs)throws Exception {
        MTEHatchInputBus b=(MTEHatchInputBus)U.allocateInstance(MTEHatchInputBus.class);attach(b);set(b,"mInventory",Arrays.copyOf(inputs,inputs.length+1));return b;
    }
    public static MTEHatchEnergy energy(int tier,long eu)throws Exception {
        MTEHatchEnergy h=(MTEHatchEnergy)U.allocateInstance(MTEHatchEnergy.class);BaseMetaTileEntity b=attach(h);set(h,"mTier",(byte)tier);set(b,"mStoredEnergy",eu);return h;
    }
    public static String source(Class<?> c){return c.getProtectionDomain().getCodeSource()==null?"unknown":c.getProtectionDomain().getCodeSource().getLocation().toString();}
    public static SparseWorld world()throws Exception {
        SparseWorld w=(SparseWorld)U.allocateInstance(SparseWorld.class);w.playerEntities=new ArrayList<>();w.chunk=(net.minecraft.world.chunk.Chunk)U.allocateInstance(net.minecraft.world.chunk.Chunk.class);
        Field f=World.class.getDeclaredField("perWorldStorage");f.setAccessible(true);f.set(w,new net.minecraft.world.storage.MapStorage(null));
        set(w,"provider",new WorldProviderSurface());w.provider.dimensionId=0;return w;
    }
    public static final class SparseWorld extends World {
        long time;net.minecraft.world.chunk.Chunk chunk;
        SparseWorld(){super(null,"unused",(WorldProvider)null,null,null);}
        @Override public long getTotalWorldTime(){return time;}
        @Override public net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int x,int z){return chunk;}
        @Override public net.minecraft.world.chunk.Chunk getChunkFromBlockCoords(int x,int z){return chunk;}
        @Override public long getWorldTime(){return time;}
        @Override public void setWorldTime(long t){time=t;}
        @Override public void markBlockForUpdate(int x,int y,int z){}
        @Override public TileEntity getTileEntity(int x,int y,int z){return null;}
        @Override public Block getBlock(int x,int y,int z){return net.minecraft.init.Blocks.air;}
        @Override public boolean isAirBlock(int x,int y,int z){return true;}
        @Override public boolean setBlock(int x,int y,int z,Block b,int meta,int flags){return true;}
        @Override public boolean setBlockToAir(int x,int y,int z){return true;}
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity t){}
        @Override public void func_147453_f(int x,int y,int z,Block b){}
        @Override public boolean blockExists(int x,int y,int z){return false;}
        @Override protected IChunkProvider createChunkProvider(){throw new UnsupportedOperationException();}
        @Override protected int func_152379_p(){throw new UnsupportedOperationException();}
        @Override public Entity getEntityByID(int id){throw new UnsupportedOperationException();}
    }
}
