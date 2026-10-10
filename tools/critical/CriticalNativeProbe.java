package audit;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import com.miaokatze.gtsr.common.critical.*;
import com.miaokatze.gtsr.common.critical.recipe.*;

/** Tests candidate jar inside a complete GTNH205 dedicated server. No production class overlays. */
@Mod(modid="criticalnative12115", name="Critical native audit", version="1", dependencies="after:gregtech;after:gtsr")
public final class CriticalNativeProbe {
    static net.minecraft.block.Block receiverBlock;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        receiverBlock=new ReceiverBlock();cpw.mods.fml.common.registry.GameRegistry.registerBlock(receiverBlock,"native_energy_receiver");cpw.mods.fml.common.registry.GameRegistry.registerTileEntity(Receiver.class,"criticalnative.energy.receiver");
    }
    static net.minecraft.block.material.Material receiverMaterial() {
        try{try{return (net.minecraft.block.material.Material)field(net.minecraft.block.material.Material.class,"iron");}catch(Exception named){return (net.minecraft.block.material.Material)field(net.minecraft.block.material.Material.class,"field_151573_f");}}catch(Exception error){throw new RuntimeException(error);}
    }
    public static class ReceiverBlock extends net.minecraft.block.BlockContainer {
        public ReceiverBlock(){super(receiverMaterial());}
        public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World world,int meta){return new Receiver();}
        public net.minecraft.tileentity.TileEntity func_149915_a(net.minecraft.world.World world,int meta){return new Receiver();}
    }
    public static class Receiver extends net.minecraft.tileentity.TileEntity implements gregtech.api.interfaces.tileentity.IEnergyConnected {
        static Receiver active;long received,lastStepAmps,maxStepAmps;int calls;boolean onlyUp=true;
        public Receiver(){active=this;}
        public long injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection side,long voltage,long amperage){onlyUp &= side==net.minecraftforge.common.util.ForgeDirection.UP;if(!inputEnergyFrom(side))return 0;received+=voltage*amperage;lastStepAmps+=amperage;calls++;return amperage;}
        public boolean inputEnergyFrom(net.minecraftforge.common.util.ForgeDirection side){return side==net.minecraftforge.common.util.ForgeDirection.UP;}
        public boolean outputsEnergyTo(net.minecraftforge.common.util.ForgeDirection side){return false;}
        public byte getColorization(){return -1;}
        public byte setColorization(byte color){return -1;}
    }
    boolean done;
    TileEntityCriticalController natural;
    int naturalTicks;
    Object dimensionClock,primaryClock;long dimensionClockStart,primaryClockStart;int clockTicks;
    JsonArray checks = new JsonArray();
    void check(String name, boolean pass, String detail) {
        JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("pass", pass); row.addProperty("detail", detail);
        checks.add(row); System.out.println("CRITICAL_CHECK " + row);
    }
    static Object field(Object obj, String name) throws Exception {
        for (Class<?> t = obj instanceof Class ? (Class<?>)obj : obj.getClass(); t != null; t=t.getSuperclass()) {
            try { Field f=t.getDeclaredField(name); f.setAccessible(true); return f.get(obj instanceof Class ? null : obj); }
            catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }
    static Object call(Object obj, String names, Object...args) throws Exception {
        for(String name:names.split("\\|")) for(Class<?> t=obj instanceof Class?(Class<?>)obj:obj.getClass();t!=null;t=t.getSuperclass()) for(Method m:t.getDeclaredMethods()) {
            if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
            boolean fits=true; Class<?>[] types=m.getParameterTypes();
            for(int i=0;i<types.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])&&!(types[i].isPrimitive()&&args[i] instanceof Number)&&!(types[i]==boolean.class&&args[i] instanceof Boolean))fits=false;
            if(fits){m.setAccessible(true);return m.invoke(obj instanceof Class?null:obj,args);}
        }
        throw new NoSuchMethodException(names);
    }
    static ItemStack copy(ItemStack item)throws Exception{return (ItemStack)call(item,"copy|func_77946_l");}
    static void increment(ItemStack item)throws Exception{try {Field f=ItemStack.class.getField("stackSize");f.setInt(item,f.getInt(item)+1);}catch(NoSuchFieldException e){Field f=ItemStack.class.getField("field_77994_a");f.setInt(item,f.getInt(item)+1);}}
    static Object packetNbt(Object packet)throws Exception {
        for(Field f:packet.getClass().getDeclaredFields())if(NBTTagCompound.class.isAssignableFrom(f.getType())){f.setAccessible(true);return f.get(packet);}
        throw new NoSuchFieldException("packet NBTTagCompound field");
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) { FMLCommonHandler.instance().bus().register(this); }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if(done) {
            if(natural==null) {
                if(dimensionClock!=null&&++clockTicks>=40) {
                    try {
                        long dt=((Number)call(dimensionClock,"getWorldTime|func_72820_D")).longValue()-dimensionClockStart;
                        long pt=((Number)call(primaryClock,"getWorldTime|func_72820_D")).longValue()-primaryClockStart;
                        check("night.non-overworld-disable-natural-time-resumes",dt>=39&&dt<=42,"40 automatic server ticks; dimension delta="+dt);
                        check("night.primary-world-natural-time-continues",pt>=39&&pt<=42,"40 automatic server ticks; primary delta="+pt);
                    }catch(Exception e){check("night.dimension-natural-observation",false,e.toString());}
                    dimensionClock=null;finish();
                }
                return;
            }
            naturalTicks++;
            if(natural.getBatchId().isEmpty()||naturalTicks>600) {
                check("natural.controller-cycle",natural.getBatchId().isEmpty()&&naturalTicks<=600&&outputCount(natural)==32,"automatic world ticks="+naturalTicks+" output="+outputCount(natural)+" status="+natural.getStatus());
                try {
                    Object world=call(FMLCommonHandler.instance().getMinecraftServerInstance(),"worldServerForDimension|func_71218_a",0);
                    natural=restore(world,natural);for(int i=0;i<10;i++)advance(natural);
                    check("batch.postdelivery-nbt-no-duplicate",natural.getBatchId().isEmpty()&&outputCount(natural)==32&&slot(natural,0)==null,"delivered single-TE snapshot restored, ten further production ticks");
                    if(Boolean.getBoolean("critical.final"))finalLifecycle(world,natural);
                }catch(Exception error){check("final.lifecycle-execution",false,error.toString());error.printStackTrace();}
                natural=null;if(dimensionClock==null)finish();
            }
            return;
        }
        done=true;
        try { tests(); } catch (Throwable error) { check("execution", false, error.toString()); error.printStackTrace(); }
        if(natural==null)finish();
    }
    void tests() throws Exception {
        String binding=String.valueOf(CriticalConfiguration.class.getProtectionDomain().getCodeSource().getLocation());
        check("production.binding", binding.contains("production-gtsr.jar"), binding);
        String gt=String.valueOf(gregtech.api.recipe.RecipeMaps.class.getProtectionDomain().getCodeSource().getLocation());
        check("GT5U205.binding", gt.contains("5.09.54.205"), gt);
        check("kind.count", CriticalMachineKind.values().length==9, Arrays.toString(CriticalMachineKind.values()));
        check("tier.count", CriticalTier.values().length==3, Arrays.toString(CriticalTier.values()));
        for (int a=0; a<=8; a++) for (int b=0; b<=8-a; b++) for (int c=0; c<=8-a-b; c++) {
            CriticalConfiguration cfg=new CriticalConfiguration(CriticalTier.T1, CriticalMachineKind.PROCESSING,a,b,c);
            long expected=(8193L*(10-c)+9)/10;
            check("plugin."+a+"."+b+"."+c, cfg.parallelMultiplier()==(1<<a) && cfg.duration(401)==Math.max(1,(401+(1<<b)-1)/(1<<b)) && cfg.energy(8193)==expected,
                "parallel="+cfg.parallelMultiplier()+" duration="+cfg.duration(401)+" eu="+cfg.energy(8193)+" expected="+expected);
        }
        boolean reject=false; try { new CriticalConfiguration(CriticalTier.T1,CriticalMachineKind.SOLAR,3,3,3); } catch(IllegalArgumentException e) { reject=true; }
        check("plugin.overflow-rejected",reject,"9 plugins");
        regions(); recipes(); controller();
    }
    void regions() throws Exception {
        CriticalWorldData data=new CriticalWorldData();
        String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString(), c=UUID.randomUUID().toString();
        check("region.negative-cross-chunk",data.acquire(a,"owner-a",-19,70,-33)&&data.owns(a,-19,70,-33),"[-19,61) x [-33,47), crosses chunk boundaries");
        check("region.overlap",!data.acquire(b,"owner-b",60,90,46),"79-block offset, overlap one block; separate height remains exclusive");
        check("region.adjacent",data.acquire(b,"owner-b",61,70,-33),"80-block offset accepted");
        check("night.first-lock",data.claimNight(a)&&!data.claimNight(b),"first claimant wins");
        NBTTagCompound n=new NBTTagCompound(); call(data,"writeToNBT|func_76187_b",n); CriticalWorldData restored=new CriticalWorldData(); call(restored,"readFromNBT|func_76184_a",n);
        check("region.nbt",restored.owns(a,-19,70,-33)&&restored.owns(b,61,70,-33),n.toString());
        check("night.nbt",restored.holdsNight(a)&&!restored.claimNight(b),"lock persists");
        restored.release(a);
        check("region.release",restored.acquire(c,"owner-c",-19,70,-33),"released area available");
        check("night.release",restored.claimNight(b),"release owner also releases night lock");
    }
    void recipes() throws Exception {
        Method list=CriticalRecipes.class.getDeclaredMethod("recipes"); list.setAccessible(true);
        Collection<?> specs=(Collection<?>)list.invoke(null); Set<CriticalMachineKind> kinds=new HashSet<CriticalMachineKind>();
        for(Object spec:specs) {
            String id=(String)field(spec,"id"); CriticalMachineKind kind=(CriticalMachineKind)field(spec,"kind"); kinds.add(kind);
            ItemStack[] in=(ItemStack[])field(spec,"in"); FluidStack[] fin=(FluidStack[])field(spec,"fin");
            for(CriticalTier tier:CriticalTier.values()) {
                CriticalConfiguration cfg=new CriticalConfiguration(tier,kind,0,0,0); RecipeMatch match=CriticalRecipes.match(cfg,in,fin);
                check("recipe."+id+"."+tier,match!=null&&match.id.equals(id)&&match.durationTicks==400,"real registered item/fluid inputs, match="+(match==null?"null":match.id));
            }
            if(in.length>0) {
                ItemStack[] extra=new ItemStack[in.length]; for(int i=0;i<in.length;i++)extra[i]=copy(in[i]); increment(extra[0]);
                check("recipe.excess."+id,!CriticalRecipes.exactItems(extra,in,1),"one extra input rejected");
                ItemStack[] tagged=new ItemStack[in.length]; for(int i=0;i<in.length;i++)tagged[i]=copy(in[i]);
                NBTTagCompound tag=new NBTTagCompound();call(tag,"setString|func_74778_a","audit","different");call(tagged[0],"setTagCompound|func_77982_d",tag);
                check("recipe.nbt."+id,!CriticalRecipes.exactItems(tagged,in,1),"different input NBT rejected");
            }
            if(fin.length>0) {
                FluidStack[] extra=new FluidStack[fin.length]; for(int i=0;i<fin.length;i++)extra[i]=fin[i].copy(); extra[0].amount++;
                check("recipe.fluid-excess."+id,!CriticalRecipes.exactFluids(extra,fin,1),"one extra mB rejected");
            }
        }
        check("recipe.nine-kinds",kinds.size()==9,"registered recipes="+specs.size()+" kinds="+kinds);
    }
    int outputCount(TileEntityCriticalController tile) {
        int n=0;
        for(int i=18;i<36;i++) {
            ItemStack stack;try{stack=slot(tile,i);}catch(Exception e){throw new RuntimeException(e);}
            if(stack!=null)try{n+=((Number)field(stack,"stackSize")).intValue();}catch(Exception first){try{n+=((Number)field(stack,"field_77994_a")).intValue();}catch(Exception ignored){}}
        }
        return n;
    }
    static void advance(TileEntityCriticalController tile)throws Exception{if(Receiver.active!=null)Receiver.active.lastStepAmps=0;call(tile,"updateEntity|func_145845_h");if(Receiver.active!=null)Receiver.active.maxStepAmps=Math.max(Receiver.active.maxStepAmps,Receiver.active.lastStepAmps);}
    static ItemStack slot(TileEntityCriticalController tile,int i)throws Exception{return (ItemStack)call(tile,"getStackInSlot|func_70301_a",i);}
    static void put(TileEntityCriticalController tile,int i,ItemStack stack)throws Exception{call(tile,"setInventorySlotContents|func_70299_a",i,stack);}
    NBTTagCompound save(TileEntityCriticalController tile)throws Exception{NBTTagCompound n=new NBTTagCompound();call(tile,"writeToNBT|func_145841_b",n);return n;}
    TileEntityCriticalController restore(Object world,TileEntityCriticalController old)throws Exception {
        NBTTagCompound n=save(old);TileEntityCriticalController copy=new TileEntityCriticalController();call(copy,"readFromNBT|func_145839_a",n);
        call(world,"setTileEntity|func_147455_a",-19,70,-33,copy);return copy;
    }
    void feed(TileEntityCriticalController tile,ItemStack[] costs)throws Exception {
        for(int i=0;i<18;i++)put(tile,i,null);int slot=0;
        for(ItemStack source:costs) {
            int left=((Number)field(source,hasField(source,"stackSize")?"stackSize":"field_77994_a")).intValue();
            while(left>0){if(slot>=18)throw new IllegalStateException("stage needs >18 input slots");ItemStack stack=copy(source);int amount=Math.min(64,left);Field f=stack.getClass().getField(hasField(stack,"stackSize")?"stackSize":"field_77994_a");f.setInt(stack,amount);put(tile,slot++,stack);left-=amount;}
        }
    }
    static boolean hasField(Object target,String name){try{target.getClass().getField(name);return true;}catch(Exception e){return false;}}
    List<Object> geometry(CriticalTier tier,CriticalMachineKind kind)throws Exception {
        Class<?> g=Class.forName("com.miaokatze.gtsr.common.critical.CriticalGeometry");List<Object> all=new ArrayList<Object>((Collection<?>)call(g,"getLoom"));all.addAll((Collection<?>)call(g,"get",tier,kind));return all;
    }
    void voxel(Object world,Object v,boolean air)throws Exception {
        int x=-59+((Number)field(v,"x")).intValue(),y=70+((Number)field(v,"y")).intValue(),z=-65+((Number)field(v,"z")).intValue();
        if(air)call(world,"setBlockToAir|func_147468_f",x,y,z);else call(world,"setBlock|func_147465_d",x,y,z,field(v,"block"),field(v,"metadata"),2);
    }
    void clearOutput(TileEntityCriticalController tile)throws Exception {
        for(int i=18;i<36;i++)put(tile,i,null);while(tile.drain(net.minecraftforge.common.util.ForgeDirection.DOWN,Integer.MAX_VALUE,true)!=null){}
    }
    void finalLifecycle(Object world,TileEntityCriticalController tile)throws Exception {
        Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);call(player,"setPosition|func_70107_b",-18.5,70.5,-32.5);
        List<Object> all=geometry(tile.getTier(),tile.getKind());Object damaged=all.get(0);voxel(world,damaged,true);advance(tile);
        check("repair.owner-start",(Boolean)call(tile,"repairStructure",player),tile.getStatus());
        feed(tile,CriticalConstructionCosts.forStage(tile.getConfiguration(),0,1));tile.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,tile.getTier().recipeVoltage(),4096);
        int ticks=0;while(tile.getState()==4&&ticks++<100)advance(tile);
        check("repair.real-materials-complete",tile.getState()==2,"repair production ticks="+ticks+" status="+tile.getStatus());
        clearOutput(tile);
        Object stick;try{stick=field(net.minecraft.init.Items.class,"stick");}catch(Exception alias){stick=field(net.minecraft.init.Items.class,"field_151055_y");}
        for(int i=18;i<36;i++)put(tile,i,new ItemStack((net.minecraft.item.Item)stick,64));
        check("dismantle.owner-start",tile.startDismantle((net.minecraft.entity.player.EntityPlayer)player),tile.getStatus());
        ticks=0;while(tile.getState()==3&&ticks++<all.size()/128+all.size()/512+20)advance(tile);
        check("dismantle.refund-full-blocked",tile.getState()==3&&tile.getCursor()==all.size()&&tile.getStatus().contains("空间"),"ticks="+ticks+" status="+tile.getStatus());
        NBTTagCompound paidRefund=save(tile);long refundSeed=((Number)call(paidRefund,"getLong|func_74763_f","seed")).longValue();String refundJournal=call(paidRefund,"getTagList|func_150295_c","pendingItems",10).toString();
        Object lagged=all.get(all.size()-1);voxel(world,lagged,false);tile=restore(world,tile);
        for(int i=0;i<all.size()/512+3;i++)advance(tile);
        check("dismantle.old-world-block-leading-te-pauses",tile.getState()==3&&tile.getCursor()==all.size(),"leading completed cursor and restored old world block; status="+tile.getStatus());
        int lx=-59+((Number)field(lagged,"x")).intValue(),ly=70+((Number)field(lagged,"y")).intValue(),lz=-65+((Number)field(lagged,"z")).intValue();Object dirt;try{dirt=field(net.minecraft.init.Blocks.class,"dirt");}catch(Exception alias){dirt=field(net.minecraft.init.Blocks.class,"field_150346_d");}
        call(world,"setBlock|func_147465_d",lx,ly,lz,dirt,0,2);
        check("dismantle.foreign-conflict-recovery-refused",!(Boolean)call(tile,"repairStructure",player)&&call(world,"getBlock|func_147439_a",lx,ly,lz)==dirt,"owner recovery refuses foreign replacement and keeps it");
        voxel(world,lagged,false);boolean reconciled=(Boolean)call(tile,"repairStructure",player);
        check("dismantle.owner-reconcile-rewinds-cursor",reconciled&&tile.getCursor()<all.size(),"matching owned old block reconciliation cursor="+tile.getCursor());
        ticks=0;while(tile.getState()==3&&ticks++<all.size()/128+all.size()/512+20)advance(tile);
        NBTTagCompound afterReconcile=save(tile);
        check("dismantle.reconcile-refund-journal-unchanged",refundSeed==((Number)call(afterReconcile,"getLong|func_74763_f","seed")).longValue()&&refundJournal.equals(call(afterReconcile,"getTagList|func_150295_c","pendingItems",10).toString())&&tile.getState()==3,"old residual re-deleted, full output still blocks same paid refund journal");
        tile=restore(world,tile);clearOutput(tile);for(int i=0;i<all.size()/512+12&&tile.getState()!=0;i++)advance(tile);
        int refund=outputCount(tile);check("dismantle.refund-restore-deliver-once",tile.getState()==0&&refund>0,"refund count="+refund+" state="+tile.getState());
        for(int i=0;i<5;i++)advance(tile);check("dismantle.idle-no-duplicate",outputCount(tile)==refund,"five further ticks refund="+outputCount(tile));clearOutput(tile);
        Method specs=CriticalRecipes.class.getDeclaredMethod("recipes");specs.setAccessible(true);Collection<?> recipes=(Collection<?>)specs.invoke(null);
        for(CriticalTier tier:CriticalTier.values())for(CriticalMachineKind kind:CriticalMachineKind.values()) {
            boolean configured=tile.configure((net.minecraft.entity.player.EntityPlayer)player,tier,kind,0,0,0);
            boolean started=tile.startConstruction((net.minecraft.entity.player.EntityPlayer)player);
            check("matrix.fixture.claim."+tier+"."+kind,configured&&started,tile.getStatus());if(!started)throw new IllegalStateException("fixture claim failed "+tier+kind);
            List<Object> voxels=geometry(tier,kind);for(Object v:voxels)voxel(world,v,false);
            // Explicit fixture: production start establishes real owner/region/job/fingerprint; geometry is placed directly.
            // READY and placement journal are native NBT fixture setup, not a claim of paid construction for these 27 cases.
            NBTTagCompound setup=save(tile);call(setup,"setInteger|func_74768_a","state",2);call(setup,"setInteger|func_74768_a","stage",5);
            call(setup,"setLong|func_74772_a","energy",0L); // Each explicitly seeded matrix fixture starts with an empty energy buffer.
            java.util.BitSet placed=new java.util.BitSet();placed.set(0,voxels.size());call(setup,"setByteArray|func_74773_a","placed",placed.toByteArray());
            call(tile,"readFromNBT|func_145839_a",setup);for(int i=0;i<voxels.size()/512+3;i++)advance(tile);
            for(Object spec:recipes)if(field(spec,"kind")==kind) {
                String id=(String)field(spec,"id");ItemStack[] in=(ItemStack[])field(spec,"in");FluidStack[] fin=(FluidStack[])field(spec,"fin");
                clearOutput(tile);feed(tile,in);for(FluidStack fluid:fin)check("matrix.fill."+tier+"."+id,tile.fill(net.minecraftforge.common.util.ForgeDirection.UP,fluid,true)==fluid.amount,"actual input fluid="+fluid);
                RecipeMatch match=CriticalRecipes.match(tile.getConfiguration(),in,fin);if(match==null)throw new IllegalStateException("matrix recipe missing "+id);
                if(match.euPerTick>0)tile.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,tier.recipeVoltage(),4096);
                long energyBefore=tile.getEnergy();advance(tile);String batch=tile.getBatchId();
                check("matrix.start."+tier+"."+id,!batch.isEmpty()&&tile.getCycleDuration()==match.durationTicks,"actual production batch="+batch+" D="+tile.getCycleDuration());
                ticks=0;while(!tile.getBatchId().isEmpty()&&ticks++<match.durationTicks+5)advance(tile);
                ItemStack[] actualItems=new ItemStack[18];for(int i=0;i<18;i++)actualItems[i]=slot(tile,i+18);
                FluidStack[] actualFluids=new FluidStack[8];net.minecraftforge.fluids.FluidTankInfo[] info=tile.getTankInfo(net.minecraftforge.common.util.ForgeDirection.DOWN);for(int i=0;i<8;i++)actualFluids[i]=info[i+8].fluid;
                long expectedEnergy=energyBefore-match.euPerTick*match.durationTicks+match.generatedEu;
                long pendingEnergy=((Number)call(save(tile),"getLong|func_74763_f","pendingGenerated")).longValue();
                check("matrix.finish."+tier+"."+id,tile.getBatchId().isEmpty()&&CriticalRecipes.exactItems(actualItems,match.itemOutputs,1)&&CriticalRecipes.exactFluids(actualFluids,match.fluidOutputs,1)&&tile.getEnergy()+pendingEnergy==expectedEnergy,"ticks="+ticks+" energy="+tile.getEnergy()+" pendingGenerated="+pendingEnergy+" expected conserved="+expectedEnergy+" items="+outputCount(tile));
                for(int i=0;i<5;i++)advance(tile);pendingEnergy=((Number)call(save(tile),"getLong|func_74763_f","pendingGenerated")).longValue();
                check("matrix.once."+tier+"."+id,CriticalRecipes.exactItems(actualItems,match.itemOutputs,1)&&tile.getBatchId().isEmpty()&&pendingEnergy==0&&tile.getEnergy()==expectedEnergy,"five production idle ticks; generated journal transferred once; energy="+tile.getEnergy());
            }
            clearOutput(tile);tile.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,tier.recipeVoltage(),4096);
            if(!tile.startDismantle((net.minecraft.entity.player.EntityPlayer)player))throw new IllegalStateException("matrix dismantle failed "+tile.getStatus());
            ticks=0;while(tile.getState()!=0&&ticks++<voxels.size()/128+voxels.size()/512+20)advance(tile);
            check("matrix.fixture.release."+tier+"."+kind,tile.getState()==0,"dismantle ticks="+ticks+" status="+tile.getStatus());if(tile.getState()!=0)throw new IllegalStateException("matrix area unreleased");clearOutput(tile);
        }
        supplementary(world,player);
    }
    TileEntityCriticalController directFixture(Object world,Object player,int x,int z,CriticalMachineKind kind)throws Exception {
        int ox=x-40,oz=z-32;Object provider=call(world,"getChunkProvider|func_72863_F");
        for(int cx=ox>>4;cx<=(ox+79)>>4;cx++)for(int cz=oz>>4;cz<=(oz+79)>>4;cz++)call(provider,"loadChunk|func_73158_c",cx,cz);
        call(world,"setBlockToAir|func_147468_f",x,70,z);call(world,"setBlock|func_147465_d",x,70,z,CriticalRuntime.controller,0,2);TileEntityCriticalController tile=(TileEntityCriticalController)call(world,"getTileEntity|func_147438_o",x,70,z);
        call(player,"setPosition|func_70107_b",x+.5,70.5,z+.5);
        if(!tile.configure((net.minecraft.entity.player.EntityPlayer)player,CriticalTier.T1,kind,0,0,0)||!tile.startConstruction((net.minecraft.entity.player.EntityPlayer)player))throw new IllegalStateException("supplement claim failed "+tile.getStatus());
        List<Object> voxels=geometry(CriticalTier.T1,kind);for(Object v:voxels)call(world,"setBlock|func_147465_d",ox+((Number)field(v,"x")).intValue(),70+((Number)field(v,"y")).intValue(),oz+((Number)field(v,"z")).intValue(),field(v,"block"),field(v,"metadata"),2);
        NBTTagCompound setup=save(tile);call(setup,"setInteger|func_74768_a","state",2);call(setup,"setInteger|func_74768_a","stage",5);java.util.BitSet placed=new java.util.BitSet();placed.set(0,voxels.size());call(setup,"setByteArray|func_74773_a","placed",placed.toByteArray());call(tile,"readFromNBT|func_145839_a",setup);
        for(int i=0;i<voxels.size()/512+3;i++)advance(tile);return tile;
    }
    void supplementary(Object world,Object player)throws Exception {
        TileEntityCriticalController first=directFixture(world,player,-19,-33,CriticalMachineKind.SOLAR);
        TileEntityCriticalController second=directFixture(world,player,81,-33,CriticalMachineKind.SOLAR);
        Object journal=call(CriticalWorldData.class,"get",world);
        first.fill(net.minecraftforge.common.util.ForgeDirection.UP,gregtech.api.enums.Materials.Water.getFluid(1000),true);second.fill(net.minecraftforge.common.util.ForgeDirection.UP,gregtech.api.enums.Materials.Water.getFluid(1000),true);
        advance(first);advance(second);
        check("night.actual-controller-first-start",(Boolean)call(journal,"holdsNight",first.getStructureId())&&!first.getBatchId().isEmpty()&&second.getBatchId().isEmpty()&&second.getStatus().contains("先启动"),"first="+first.getStatus()+" competitor="+second.getStatus());
        call(player,"setPosition|func_70107_b",-18.5,70.5,-32.5);first.setEnabled((net.minecraft.entity.player.EntityPlayer)player,false);advance(second);
        check("night.actual-controller-disable-release",(Boolean)call(journal,"holdsNight",second.getStructureId())&&!second.getBatchId().isEmpty(),"second batch now="+second.getBatchId());
        call(second,"onChunkUnload");check("night.actual-controller-unload-release",!(Boolean)call(journal,"holdsNight",second.getStructureId()),"production onChunkUnload releases night ownership");
        first.setEnabled((net.minecraft.entity.player.EntityPlayer)player,true);advance(first);check("night.actual-controller-reacquire",(Boolean)call(journal,"holdsNight",first.getStructureId()),"enabled first resumes committed escrow batch");
        first.setEnabled((net.minecraft.entity.player.EntityPlayer)player,false);call(player,"setPosition|func_70107_b",81.5,70.5,-32.5);second.setEnabled((net.minecraft.entity.player.EntityPlayer)player,false);
        TileEntityCriticalController sun=directFixture(world,player,181,-33,CriticalMachineKind.SUN);
        sun.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,sun.getTier().recipeVoltage(),4096);long charged=sun.getEnergy();
        sun.fill(net.minecraftforge.common.util.ForgeDirection.UP,gregtech.api.enums.Materials.Deuterium.getGas(1000),true);sun.fill(net.minecraftforge.common.util.ForgeDirection.UP,gregtech.api.enums.Materials.Tritium.getGas(1000),true);advance(sun);
        net.minecraftforge.fluids.FluidTankInfo[] fullInputs=sun.getTankInfo(net.minecraftforge.common.util.ForgeDirection.UP);
        check("energy.full-buffer-generation-refuses-no-consume",sun.getBatchId().isEmpty()&&fullInputs[0].fluid!=null&&fullInputs[0].fluid.amount==1000&&fullInputs[1].fluid!=null&&fullInputs[1].fluid.amount==1000&&sun.getEnergy()==charged,"actual full SUN energy buffer refuses batch and preserves both fuel fluids");
        call(world,"setBlock|func_147465_d",181,69,-33,receiverBlock,0,2);Object raw=call(world,"getTileEntity|func_147438_o",181,69,-33);
        check("energy.actual-receiver-world-tile",raw instanceof Receiver,"actual helper BlockContainer TE="+raw);if(!(raw instanceof Receiver))throw new IllegalStateException("receiver not in native world");Receiver receiver=(Receiver)raw;
        advance(sun);
        check("energy.idle-16A-budget",receiver.lastStepAmps==16,"actual idle production tick accepted amps="+receiver.lastStepAmps);
        for(int i=0;i<50&&sun.getBatchId().isEmpty();i++)advance(sun);
        advance(sun);check("energy.working-16A-budget",receiver.lastStepAmps==16&&!sun.getBatchId().isEmpty(),"actual input-commit working tick accepted amps="+receiver.lastStepAmps);
        int ticks=0;while(!sun.getBatchId().isEmpty()&&ticks++<410)advance(sun);
        check("energy.delivery-16A-budget",receiver.lastStepAmps<=16&&receiver.maxStepAmps<=16,"delivery tick amps="+receiver.lastStepAmps+" max per production updateEntity="+receiver.maxStepAmps);
        for(int i=0;i<80;i++)advance(sun);
        check("energy.generated-down-transfer-conservation",receiver.received==charged+209715200L&&sun.getEnergy()==0&&receiver.onlyUp&&receiver.calls>0,"native SUN emitEnergy to physical DOWN neighbor via receiver.UP: received="+receiver.received+" initialCharge="+charged+" controller="+sun.getEnergy()+" calls="+receiver.calls);
        long received=receiver.received;for(int i=0;i<20;i++)advance(sun);check("energy.idle-no-duplicate-generation",receiver.received==received&&sun.getBatchId().isEmpty(),"twenty empty-input production ticks, received="+receiver.received);
        call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",17,-3);call(world,"setBlock|func_147465_d",281,70,-33,CriticalRuntime.controller,0,2);
        TileEntityCriticalController capacityTile=(TileEntityCriticalController)call(world,"getTileEntity|func_147438_o",281,70,-33);call(player,"setPosition|func_70107_b",281.5,70.5,-32.5);
        capacityTile.configure((net.minecraft.entity.player.EntityPlayer)player,CriticalTier.T3,CriticalMachineKind.BATTERY,0,0,0);capacityTile.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,CriticalTier.T3.recipeVoltage(),4096);long full=capacityTile.getEnergy();
        boolean downgraded=capacityTile.configure((net.minecraft.entity.player.EntityPlayer)player,CriticalTier.T1,CriticalMachineKind.BATTERY,0,0,0);
        check("energy.charged-tier-downgrade-rejected",!downgraded&&capacityTile.getTier()==CriticalTier.T3&&capacityTile.getEnergy()==full,"charged T3 energy="+full+" downgradeAccepted="+downgraded);
        TileEntityCriticalController restored=new TileEntityCriticalController();call(restored,"readFromNBT|func_145839_a",save(capacityTile));call(world,"setTileEntity|func_147455_a",281,70,-33,restored);
        check("energy.downgrade-rejection-nbt-no-loss",restored.getTier()==CriticalTier.T3&&restored.getEnergy()==full,"native TE restore energy="+restored.getEnergy()+" expected="+full);
        dimensionNight(world);
    }
    void dimensionNight(Object main)throws Exception {
        int dim=com.miaokatze.gtsr.config.Config.prosperityDimId;net.minecraftforge.common.DimensionManager.initDimension(dim);
        Object world=call(FMLCommonHandler.instance().getMinecraftServerInstance(),"worldServerForDimension|func_71218_a",dim);if(world==null)throw new IllegalStateException("registered prosperity world absent "+dim);
        Object provider=call(world,"getChunkProvider|func_72863_F");for(int cx=-4;cx<=1;cx++)for(int cz=-5;cz<=0;cz++)call(provider,"loadChunk|func_73158_c",cx,cz);
        // Dedicated copy only: clear target geometry positions in the natural dimension before normal owner/job claim.
        for(Object voxel:geometry(CriticalTier.T1,CriticalMachineKind.SOLAR))voxel(world,voxel,true);
        Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);
        long before=((Number)call(main,"getWorldTime|func_72820_D")).longValue();TileEntityCriticalController solar=directFixture(world,player,-19,-33,CriticalMachineKind.SOLAR);
        long local=((Number)call(world,"getWorldTime|func_72820_D")).longValue(),after=((Number)call(main,"getWorldTime|func_72820_D")).longValue();float angle=((Number)call(world,"getCelestialAngle|func_72826_c",0F)).floatValue();
        check("night.non-overworld-local-clock-and-angle",Math.floorMod(local,24000L)==18000L&&Math.abs(angle-.5F)<.001F,"actual registered prosperity dim="+dim+" native worldTime="+local+" celestialAngle="+angle);
        check("night.non-overworld-does-not-write-primary-clock",before==after,"primary world time before="+before+" after="+after);
        solar.setEnabled((net.minecraft.entity.player.EntityPlayer)player,false);dimensionClock=world;primaryClock=main;dimensionClockStart=((Number)call(world,"getWorldTime|func_72820_D")).longValue();primaryClockStart=after;clockTicks=0;
    }
    void controller()throws Exception {
        Object server=FMLCommonHandler.instance().getMinecraftServerInstance();Object world=call(server,"worldServerForDimension|func_71218_a",0);
        Object provider=call(world,"getChunkProvider|func_72863_F");
        for(int x=-4;x<=1;x++)for(int z=-5;z<=0;z++)call(provider,"loadChunk|func_73158_c",x,z);
        check("controller.registered",CriticalRuntime.controller!=null,"block="+CriticalRuntime.controller);
        call(world,"setBlock|func_147465_d",-19,70,-33,CriticalRuntime.controller,0,2);
        Object raw=call(world,"getTileEntity|func_147438_o",-19,70,-33);
        if(Boolean.getBoolean("critical.final")) {
            float resistance=((Number)call(CriticalRuntime.controller,"getExplosionResistance|func_149638_a",new Object[]{null})).floatValue();
            check("controller.exact-blast-resistance",Math.abs(resistance-10000F)<0.1F,"native getExplosionResistance="+resistance);
        }
        check("controller.actual-world-tile",raw instanceof TileEntityCriticalController,"class="+(raw==null?"null":raw.getClass()));
        if(!(raw instanceof TileEntityCriticalController))return;
        TileEntityCriticalController tile=(TileEntityCriticalController)raw;
        Object player=call(Class.forName("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);
        call(player,"setPosition|func_70107_b",-18.5,70.5,-32.5);
        check("controller.configure",tile.configure((net.minecraft.entity.player.EntityPlayer)player,CriticalTier.T1,CriticalMachineKind.PROCESSING,0,0,0),tile.getStatus());
        check("controller.negative-origin",tile.getOriginX()==-59&&tile.getOriginZ()==-65,"origin="+tile.getOriginX()+","+tile.getOriginZ());
        Class<?> geometry=Class.forName("com.miaokatze.gtsr.common.critical.CriticalGeometry");
        List<Object> voxels=new ArrayList<Object>((Collection<?>)call(geometry,"getLoom"));voxels.addAll((Collection<?>)call(geometry,"get",CriticalTier.T1,CriticalMachineKind.PROCESSING));
        if(Boolean.getBoolean("critical.final")) {
            Set<Object> checked=new HashSet<Object>();
            for(Object voxel:voxels) {Object block=field(voxel,"block");if(!checked.add(block))continue;
                float resistance=((Number)call(block,"getExplosionResistance|func_149638_a",new Object[]{null})).floatValue();
                check("materials.exact-blast-resistance."+checked.size(),Math.abs(resistance-10000F)<0.1F,"native registered block="+block+" getExplosionResistance="+resistance);
            }
        }
        Object placed=voxels.get(0);int px=-59+((Number)field(placed,"x")).intValue(),py=70+((Number)field(placed,"y")).intValue(),pz=-65+((Number)field(placed,"z")).intValue();
        call(world,"setBlock|func_147465_d",px,py,pz,field(placed,"block"),field(placed,"metadata"),2);
        check("construction.direct-block-not-free",!tile.startConstruction((net.minecraft.entity.player.EntityPlayer)player)&&tile.getState()==0,tile.getStatus());
        call(world,"setBlockToAir|func_147468_f",px,py,pz);
        boolean started=tile.startConstruction((net.minecraft.entity.player.EntityPlayer)player);
        check("construction.start",started,tile.getStatus());if(!started)return;
        advance(tile);check("construction.missing-material",tile.getCursor()==0&&tile.getStage()==0,tile.getStatus());
        int[] counts=new int[5];for(Object voxel:voxels)counts[((Number)field(voxel,"stage")).intValue()]++;
        feed(tile,CriticalConstructionCosts.forStage(tile.getConfiguration(),0,counts[0]));advance(tile);
        check("construction.no-energy",tile.getCursor()==0&&tile.getStage()==0,tile.getStatus());
        if(Boolean.getBoolean("critical.final")) {
            Object journal=call(CriticalWorldData.class,"get",world);NBTTagCompound journalTag=new NBTTagCompound();call(journal,"writeToNBT|func_76187_b",journalTag);
            Object regions=call(journalTag,"getTagList|func_150295_c","regions",10);Object region=call(regions,"getCompoundTagAt|func_150305_b",0);
            call(region,"setString|func_74778_a","jobId",UUID.randomUUID().toString());call(journal,"readFromNBT|func_76184_a",journalTag);
            String originalJob=tile.getJobId();int originalCursor=tile.getCursor();advance(tile);
            check("construction.cross-file-task-mismatch-pauses",tile.getCursor()==originalCursor&&tile.getStatus().contains("身份"),tile.getStatus());
            boolean recovered=(Boolean)call(tile,"repairStructure",player);
            check("construction.owner-explicit-task-recovery",recovered&&tile.getCursor()==originalCursor&&originalJob.equals(tile.getJobId()),"owner recovery preserves cursor and job; status="+tile.getStatus());
        }
        tile.injectEnergyUnits(net.minecraftforge.common.util.ForgeDirection.UP,CriticalTier.T1.recipeVoltage(),4096);
        advance(tile);int before=tile.getCursor(),stage=tile.getStage();String job=tile.getJobId();
        if(Boolean.getBoolean("critical.final")) {
            Object packet=call(tile,"getDescriptionPacket|func_145844_m");Object tag=packetNbt(packet);boolean keys=true;
            for(String key:new String[]{"stage","cursor","jobTotal","reason","working","structureId","batchId"})keys &= (Boolean)call(tag,"hasKey|func_74764_b",key);
            check("network.description-packet-fields",keys&&((Number)call(tag,"getInteger|func_74762_e","cursor")).intValue()==tile.getCursor()&&((Number)call(tag,"getInteger|func_74762_e","stage")).intValue()==tile.getStage(),"production descriptionPacket native NBT cursor="+before+" stage="+stage);
            List<Object> guarded=new ArrayList<Object>(((Map<?,?>)field(CriticalMaterials.class,"STATIC")).values());guarded.addAll(((Map<?,?>)field(CriticalMaterials.class,"PROXY")).values());
            call(provider,"loadChunk|func_73158_c",6,6);int index=0;
            for(Object block:guarded) {
                boolean canPlace=(Boolean)call(block,"canPlaceBlockAt|func_149742_c",world,100,70,100);
                Object item=call(net.minecraft.item.Item.class,"getItemFromBlock|func_150898_a",block);
                boolean directPlaced=(Boolean)call(item,"placeBlockAt",new ItemStack((net.minecraft.block.Block)block),(net.minecraft.entity.player.EntityPlayer)player,world,100,70,100,1,0.5F,0.5F,0.5F,0);
                check("material.direct-place-denied."+(++index),!canPlace&&!directPlaced&&(Boolean)call(world,"isAirBlock|func_147437_c",100,70,100),"registered item="+item.getClass().getName()+" blockCanPlace="+canPlace+" itemPlace="+directPlaced);
            }
        }
        tile=restore(world,tile);
        check("construction.nbt-cursor",tile.getCursor()==before&&tile.getStage()==stage&&job.equals(tile.getJobId()),"cursor="+before+" stage="+stage);
        int lastFunded=0,ticks=0;boolean placeBudget=true;
        while(tile.getState()==1&&ticks++<10000) {
            if(tile.getStage()!=lastFunded&&tile.getStage()<5){lastFunded=tile.getStage();feed(tile,CriticalConstructionCosts.forStage(tile.getConfiguration(),lastFunded,counts[lastFunded]));}
            int previousStage=tile.getStage(),previousCursor=tile.getCursor();advance(tile);
            if(previousStage==tile.getStage())placeBudget &= tile.getCursor()-previousCursor<=128;
        }
        check("construction.placement-budget",placeBudget,"actual cursor advances at most128 within a stage, production ticks="+ticks);
        check("construction.real-blocks-ready",tile.getState()==2,"ticks="+ticks+" voxels="+voxels.size()+" status="+tile.getStatus());if(tile.getState()!=2)return;
        Method specs=CriticalRecipes.class.getDeclaredMethod("recipes");specs.setAccessible(true);Object wire=null;
        for(Object spec:(Collection<?>)specs.invoke(null))if("processing_wire".equals(field(spec,"id")))wire=spec;
        if(wire==null)throw new IllegalStateException("wire recipe absent");
        ItemStack[] inputs=(ItemStack[])field(wire,"in");
        Object stick;try{stick=field(net.minecraft.init.Items.class,"stick");}catch(Exception alias){stick=field(net.minecraft.init.Items.class,"field_151055_y");}
        for(int i=18;i<36;i++)put(tile,i,new ItemStack((net.minecraft.item.Item)stick,64));
        feed(tile,inputs);for(int i=0;i<voxels.size()/512+3;i++)advance(tile);
        check("batch.output-full-no-consume",tile.getBatchId().isEmpty()&&slot(tile,0)!=null,tile.getStatus());
        for(int i=18;i<36;i++)put(tile,i,null);
        for(int i=0;i<voxels.size()/512+3&&tile.getBatchId().isEmpty();i++)advance(tile);
        check("batch.commit-input-escrow",!tile.getBatchId().isEmpty()&&slot(tile,0)==null,"duration="+tile.getCycleDuration()+" progress="+tile.getCycleTicks());
        String batch=tile.getBatchId();int progress=tile.getCycleTicks();tile=restore(world,tile);
        check("batch.nbt-restore",batch.equals(tile.getBatchId())&&progress==tile.getCycleTicks()&&slot(tile,0)==null,"same single TE snapshot");
        List<?> loom=(List<?>)call(geometry,"getLoom");Object v=loom.get(0);
        int x=-59+((Number)field(v,"x")).intValue(),y=70+((Number)field(v,"y")).intValue(),z=-65+((Number)field(v,"z")).intValue();
        call(world,"setBlockToAir|func_147468_f",x,y,z);advance(tile);
        check("loom.missing-stops-batch",tile.getCycleTicks()==progress&&!tile.isWorking(),tile.getStatus());
        call(world,"setBlock|func_147465_d",x,y,z,field(v,"block"),field(v,"metadata"),2);
        for(int i=18;i<36;i++)put(tile,i,new ItemStack((net.minecraft.item.Item)stick,64));
        for(int i=0;i<voxels.size()/512+tile.getCycleDuration()+10;i++)advance(tile);
        check("batch.commit-output-blocked",!tile.getBatchId().isEmpty()&&tile.getCycleTicks()==tile.getCycleDuration()&&slot(tile,0)==null,"committed input remains escrow; completed output waits; status="+tile.getStatus());
        batch=tile.getBatchId();tile=restore(world,tile);
        for(int i=0;i<voxels.size()/512+3;i++)advance(tile);
        check("batch.blocked-nbt-restore",batch.equals(tile.getBatchId())&&slot(tile,0)==null,"full-output single TE restore retains batch identity and consumed input");
        for(int i=18;i<36;i++)put(tile,i,null);advance(tile);
        check("batch.unblock-one-delivery",tile.getBatchId().isEmpty()&&outputCount(tile)==32,"exactly32 copper wires delivered after opening output");
        for(int i=0;i<5;i++)advance(tile);
        check("batch.unblock-idle-no-duplicate",outputCount(tile)==32&&tile.getBatchId().isEmpty(),"five idle production ticks");
        for(int i=18;i<36;i++)put(tile,i,null);feed(tile,inputs);advance(tile);
        check("natural.fresh-batch",!tile.getBatchId().isEmpty()&&tile.getCycleTicks()==1,"second independent genuine batch starts before automatic ticks");
        // Leave the new batch completion to actual world ticks.
        natural=tile;naturalTicks=0;
    }
    void finish() {
        boolean pass=true; for(JsonElement check:checks)pass &= check.getAsJsonObject().get("pass").getAsBoolean();
        JsonObject result=new JsonObject(); result.addProperty("status",pass?"PASS":"FAIL"); result.add("checks",checks);
        result.addProperty("scope","Complete GTNH205 dedicated server; candidate production jar binding and registered resources. Configuration arithmetic, native NBT region/night lock tests. Actual negative-coordinate world-backed processing T1 construction, actual staged resources/energy, cursor NBT restore, full output refusal, batch input escrow restore, permanent loom removal pause, completed-output blocked NBT restore and exactly-once unblocking, second independent batch natural world tick completion. Construction and blocked-output matrix are accelerated by calling production updateEntity on the server thread. Natural completion uses only automatic world ticks. Does not yet exhaust nine controller kinds, three construction tiers, actual process restart, dynamic proxy direct placement or scan budget instrumentation.");
        if(Boolean.getBoolean("critical.final"))result.addProperty("scope","Complete GTNH205 dedicated server bound to final candidate. One fully paid PROCESSING T1 41767-voxel construction; actual production construction/updateEntity, input/output escrow native NBT restores, blocked delivery exactly-once, job UUID mismatch owner recovery, real missing-block repair, full-output dismantle refund restore, exact blast resistance10000, production descriptionPacket fields and all static/proxy ItemBlock direct placement denial. Additional 9kind x3tier world-backed controller matrix uses explicitly seeded native NBT READY fixtures and directly placed real production geometry after normal owner/region/job claim; these 27 fixtures do not claim paid construction. All15 registered recipes x3tier traverse real production TE batch consumption, energy and item/fluid output lifecycle, accelerated on server thread. Separate fresh PROCESSING T1 batch completes with automatic world ticks. No process restart/crash/fsync durability or runtime scan512 instrumentation claim; placement128 is measured by actual cursor deltas.");
        try {
            Files.write(Paths.get(System.getProperty("critical.receipt")),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
            call(FMLCommonHandler.instance().getMinecraftServerInstance(),"initiateShutdown|func_71263_m");
        } catch(Exception error) { error.printStackTrace(); }
    }
}
