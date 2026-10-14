package audit;

import java.lang.reflect.*;
import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.*;
import com.miaokatze.gtsr.common.critical.*;

/** Call run(real WorldServer) from a private native fixture; never installs its own event listeners. */
public final class CriticalProtectionNativeProbe {
    static Object field(Object target,String name)throws Exception {
        for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class?null:target);
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    static void set(Object target,String name,Object value)throws Exception {
        for(String alias:name.split("\\|"))for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(alias);f.setAccessible(true);f.set(target,value);return;
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    static Object call(Object target,String names,Object...args)throws Exception {
        for(String name:names.split("\\|"))for(Class<?> c=target instanceof Class?(Class<?>)target:target.getClass();c!=null;c=c.getSuperclass())
            for(Method m:c.getDeclaredMethods()) {
                if(!m.getName().equals(name)||m.getParameterTypes().length!=args.length)continue;
                boolean fits=true;Class<?>[] types=m.getParameterTypes();
                for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i])
                    &&!(types[i].isPrimitive()&&args[i] instanceof Number)&&!(types[i]==boolean.class&&args[i] instanceof Boolean))fits=false;
                if(fits){m.setAccessible(true);return m.invoke(target instanceof Class?null:target,args);}
            }
        throw new NoSuchMethodException(names);
    }
    static void check(JsonArray checks,boolean pass,String name) {
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);checks.add(row);
        if(!pass)throw new AssertionError(name);
    }
    static void block(World world,int x,int y,int z,Block block,int meta)throws Exception {
        call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",x>>4,z>>4);
        if(!(Boolean)call(world,"setBlock|func_147465_d",x,y,z,block,meta,2))throw new AssertionError("place "+x+","+y+","+z);
    }
    static float hardness(Block block,World world,int x,int y,int z)throws Exception {
        return ((Number)call(block,"getBlockHardness|func_149712_f",world,x,y,z)).floatValue();
    }
    static Block at(World world,int x,int y,int z)throws Exception {return (Block)call(world,"getBlock|func_147439_a",x,y,z);}
    static boolean controller(CriticalGeometry.Voxel voxel)throws Exception {
        Method m=CriticalStructures.class.getDeclaredMethod("isController",CriticalGeometry.Voxel.class);m.setAccessible(true);
        return (Boolean)m.invoke(null,voxel);
    }

    /** The caller owns lifecycle/receipt capture and should merge returned rows into its own receipt. */
    @SuppressWarnings("unchecked")
    public static JsonArray run(WorldServer world)throws Exception {
        JsonArray checks=new JsonArray();
        Object player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.EAST,ForgeDirection.SOUTH,ForgeDirection.WEST}) {
            NBTTagCompound n=new NBTTagCompound();
            call(n,"setInteger|func_74768_a","geometryVersion",2);call(n,"setInteger|func_74768_a","v2LayoutRevision",31);
            call(n,"setInteger|func_74768_a","structureFacing",facing.ordinal());
            call(n,"setBoolean|func_74757_a","loomController",true);call(n,"setBoolean|func_74757_a","loomJournalOwner",true);
            call(n,"setString|func_74778_a","structureId",UUID.randomUUID().toString());
            call(n,"setString|func_74778_a","ownerUuid","native-protection");call(n,"setString|func_74778_a","jobId","native-partial");
            call(n,"setInteger|func_74768_a","state",1);
            call(n,"setTag|func_74782_a","config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,0,0,0).write());
            CriticalMachineState s=new CriticalMachineState();s.readFromNBT(n);s.worldObj=world;
            s.xCoord=2104+facing.ordinal()*256;s.yCoord=100;s.zCoord=2095;
            List<CriticalGeometry.Voxel> all=(List<CriticalGeometry.Voxel>)call(s,"allVoxels");
            set(s,"jobFingerprint",field(s,"modelFingerprint"));BitSet paid=(BitSet)field(s,"placed");
            List<CriticalGeometry.Voxel> cells=new ArrayList<CriticalGeometry.Voxel>();
            for(int kind=0;kind<3;kind++)for(CriticalGeometry.Voxel v:all) {
                if(CriticalLoomStructure.isLoomVoxel(v)||controller(v)||!(v.block instanceof CriticalMaterials.MaterialBlock))continue;
                CriticalMaterials.MaterialBlock b=(CriticalMaterials.MaterialBlock)v.block;
                if(kind==0&&!b.transparent&&!b.proxy||kind==1&&b.transparent||kind==2&&b.proxy){cells.add(v);break;}
            }
            CriticalGeometry.Voxel controller=null;
            for(CriticalGeometry.Voxel v:all)if(v.material.equals("v2_main_controller")||v.group.equals("main_controller")) {controller=v;break;}
            if(controller==null)for(CriticalGeometry.Voxel v:all) {
                Method m=CriticalStructures.class.getDeclaredMethod("isController",CriticalGeometry.Voxel.class);m.setAccessible(true);
                if((Boolean)m.invoke(null,v)&&v.z!=47){controller=v;break;}
            }
            check(checks,controller!=null,"Native automatic controller cell exists "+facing);
            int cx=s.blockWorldX(controller),cy=s.blockWorldY(controller.y),cz=s.blockWorldZ(controller);
            Block gt=gregtech.api.GregTechAPI.sBlockMachines;
            block(world,cx,cy,cz,gt,0);Object tile=call(world,"getTileEntity|func_147438_o",cx,cy,cz);
            call(tile,"setInitialValuesAsNBT",null,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_SUN.ID);
            call(tile,"setFrontFacing",s.getControllerFacing(false));paid.set(all.indexOf(controller));
            for(CriticalGeometry.Voxel v:cells) {
                block(world,s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v),v.block,v.metadata);paid.set(all.indexOf(v));
            }
            CriticalWorldData data=CriticalWorldData.get(world);
            check(checks,data.acquire(s.getStructureId(),"native-protection",s.getOriginX(),s.getOriginY(),s.getOriginZ(),
                s.getRegionSize(),s.getRegionHeight(),s.xCoord,s.yCoord,s.zCoord),"Native exact region "+facing);
            check(checks,data.bindTask(s.getStructureId(),"native-protection",s.getJobId(),s.getOriginX(),s.getOriginY(),s.getOriginZ()),"Native exact job "+facing);
            call(data,"updateProtection",s);
            check(checks,hardness(gt,world,cx,cy,cz)==-1F,"Actual transformed shared GT getter -1 "+facing);
            Object oldHeld=call(player,"getHeldItem|func_70694_bm");
            try {
                call(player,"setCurrentItemOrArmor|func_70062_b",0,null);
                net.minecraftforge.event.entity.player.PlayerInteractEvent interact=new net.minecraftforge.event.entity.player.PlayerInteractEvent(
                    (net.minecraft.entity.player.EntityPlayer)player,
                    net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,cx,cy,cz,1,world);
                new CriticalRuntime.StructureChangeListener().onPlayerInteract(interact);
                check(checks,!interact.isCanceled(),"Paid GT ordinary right-click still reaches GUI "+facing);
                gregtech.api.objects.GTItemStack wrench=gregtech.api.GregTechAPI.sWrenchList.iterator().next();
                call(player,"setCurrentItemOrArmor|func_70062_b",0,wrench.toStack());
                interact=new net.minecraftforge.event.entity.player.PlayerInteractEvent(
                    (net.minecraft.entity.player.EntityPlayer)player,
                    net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,cx,cy,cz,1,world);
                new CriticalRuntime.StructureChangeListener().onPlayerInteract(interact);
                check(checks,interact.isCanceled(),"Paid GT wrench cannot rotate away from paid identity "+facing);
            } finally {call(player,"setCurrentItemOrArmor|func_70062_b",0,oldHeld);}
            Object explosion=new net.minecraft.world.Explosion(world,null,cx+.5,cy+.5,cz+.5,Float.MAX_VALUE);
            check(checks,((Number)call(gt,"getExplosionResistance",null,world,cx,cy,cz,(double)cx,(double)cy,(double)cz)).floatValue()==Float.MAX_VALUE,
                "Actual transformed GT resistance "+facing);
            check(checks,!(Boolean)call(player,"func_145774_a",explosion,world,cx,cy,cz,gt,Float.MAX_VALUE),"Actual transformed Entity can-destroy false "+facing);
            net.minecraft.world.Explosion raycast=new net.minecraft.world.Explosion(world,(Entity)player,cx+.5,cy+.5,cz+.5,8F);
            call(raycast,"doExplosionA|func_77278_a");
            check(checks,!new net.minecraftforge.event.world.ExplosionEvent.Detonate(world,raycast,new ArrayList<Entity>())
                .getAffectedBlocks().contains(new net.minecraft.world.ChunkPosition(cx,cy,cz)),"Actual Explosion A raycast excludes paid controller "+facing);
            call(raycast,"doExplosionB|func_77279_a",false);
            check(checks,at(world,cx,cy,cz)==gt,"Actual raycast and destruction retain paid controller "+facing);
            call(gt,"onBlockExploded",world,cx,cy,cz,explosion);
            check(checks,at(world,cx,cy,cz)==gt&&call(world,"getTileEntity|func_147438_o",cx,cy,cz)==tile,"Actual GT onBlockExploded retains controller before energy explosion "+facing);
            for(CriticalGeometry.Voxel v:cells)check(checks,hardness(v.block,world,s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v))==-1F,
                "Actual native material hardness "+facing+" "+v.group);
            int ux=cx+120,uy=cy,uz=cz;
            block(world,ux,uy,uz,gt,0);Object manual=call(world,"getTileEntity|func_147438_o",ux,uy,uz);
            call(manual,"setInitialValuesAsNBT",null,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_SUN.ID);
            check(checks,hardness(gt,world,ux,uy,uz)!=-1F,"Shared GT ordinary controller unchanged "+facing);
            check(checks,(Boolean)call(player,"func_145774_a",explosion,world,ux,uy,uz,gt,Float.MAX_VALUE),"Entity permits unrelated GT coordinate "+facing);
            CriticalGeometry.Voxel port=CriticalGeometry.optionalWorkPorts(s).get(0);
            int hx=s.blockWorldX(port),hy=s.blockWorldY(port.y),hz=s.blockWorldZ(port);
            Object hatchStack=gregtech.api.enums.ItemList.Hatch_Input_Bus_LV.get(1);
            int hatchId=((Number)call(hatchStack,"getItemDamage|func_77960_j")).intValue();
            block(world,hx,hy,hz,gt,0);Object hatch=call(world,"getTileEntity|func_147438_o",hx,hy,hz);
            call(hatch,"setInitialValuesAsNBT",null,(short)hatchId);
            check(checks,hardness(gt,world,hx,hy,hz)!=-1F&&!CriticalBlockProtection.protects(world,hx,hy,hz),"Native manual GT chamber remains maintainable "+facing);
            Block cobble=(Block)call(Block.class,"getBlockFromName|func_149684_b","minecraft:cobblestone");
            int bx=cx+2,by=cy+2,bz=cz+2;block(world,bx,by,bz,cobble,0);
            net.minecraftforge.event.world.ExplosionEvent.Detonate detonate=new net.minecraftforge.event.world.ExplosionEvent.Detonate(
                world,(net.minecraft.world.Explosion)explosion,new ArrayList<Entity>());
            List<net.minecraft.world.ChunkPosition> affected=detonate.getAffectedBlocks();
            affected.add(new net.minecraft.world.ChunkPosition(cx,cy,cz));affected.add(new net.minecraft.world.ChunkPosition(bx,by,bz));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(detonate);
            check(checks,affected.size()==1,"Actual Forge bus filters paid at arbitrary power "+facing);
            set(explosion,"isSmoking|field_82755_b",true);call(explosion,"doExplosionB|func_77279_a",false);
            check(checks,at(world,cx,cy,cz)==gt&&(Boolean)call(world,"isAirBlock|func_147437_c",bx,by,bz),
                "Actual Explosion B retains paid and destroys unrelated "+facing);
            int ax=ux+4,ay=uy+4,az=uz+4;block(world,ax,ay,az,CriticalRenderAnchors.BLOCK,0);
            call(CriticalRenderAnchors.BLOCK,"onBlockExploded",world,ax,ay,az,explosion);
            check(checks,at(world,ax,ay,az)==CriticalRenderAnchors.BLOCK,"Actual internal anchor callback resists explosion "+facing);
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",ax,ay,az),"Actual internal anchor world removal remains available "+facing);
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",cx,cy,cz),"Native internal teardown bypasses player/explosion protection "+facing);
            for(CriticalGeometry.Voxel v:cells)call(world,"setBlockToAir|func_147468_f",s.blockWorldX(v),s.blockWorldY(v.y),s.blockWorldZ(v));
            call(world,"setBlockToAir|func_147468_f",hx,hy,hz);
            call(world,"setBlockToAir|func_147468_f",ux,uy,uz);data.release(s.getStructureId());
        }
        return checks;
    }
}
