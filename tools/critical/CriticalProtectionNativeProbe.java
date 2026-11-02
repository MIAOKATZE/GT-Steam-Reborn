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

    /** Real World/Chunk break callbacks, including a picked controller's original NBT placed back by hand. */
    @SuppressWarnings("unchecked")
    public static JsonArray runOwnership216(WorldServer world)throws Exception {
        JsonArray checks=new JsonArray();
        net.minecraft.entity.player.EntityPlayer player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        com.mojang.authlib.GameProfile profile=(com.mojang.authlib.GameProfile)call(player,"getGameProfile|func_146103_bH");
        Block gt=gregtech.api.GregTechAPI.sBlockMachines;
        int ox=32008,oy=100,oz=32015;
        List<int[]> cleanup=new ArrayList<int[]>();CriticalMachineState state=null;CriticalWorldData data=CriticalWorldData.get(world);
        Throwable failure=null;
        try {
            block(world,ox,oy,oz,gt,0);cleanup.add(new int[]{ox,oy,oz});
            Object owner=call(world,"getTileEntity|func_147438_o",ox,oy,oz);
            call(owner,"setInitialValuesAsNBT",null,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_LOOM.ID);
            call(owner,"setOwnerUuid",profile.getId());call(owner,"setOwnerName",profile.getName());
            Object machine=call(owner,"getMetaTileEntity");state=(CriticalMachineState)call(machine,"getLocalCriticalState");
            NBTTagCompound n=new NBTTagCompound();
            call(n,"setInteger|func_74768_a","geometryVersion",2);call(n,"setInteger|func_74768_a","v2LayoutRevision",33);
            call(n,"setBoolean|func_74757_a","loomController",true);call(n,"setBoolean|func_74757_a","loomJournalOwner",true);
            call(n,"setString|func_74778_a","structureId",UUID.randomUUID().toString());
            call(n,"setString|func_74778_a","ownerUuid",profile.getId().toString());call(n,"setString|func_74778_a","jobId","ownership216");
            call(n,"setInteger|func_74768_a","state",2);
            call(n,"setTag|func_74782_a","config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,0,0,0).write());
            state.readFromNBT(n);call(state,"bindHost",owner);
            List<CriticalGeometry.Voxel> all=(List<CriticalGeometry.Voxel>)call(state,"allVoxels");
            set(state,"jobFingerprint",field(state,"modelFingerprint"));
            CriticalGeometry.Voxel material=null,main=null;
            for(CriticalGeometry.Voxel voxel:all) {
                if(controller(voxel)&&!CriticalLoomStructure.isLoomVoxel(voxel))main=voxel;
                else if(!CriticalLoomStructure.isLoomVoxel(voxel)&&voxel.block instanceof CriticalMaterials.MaterialBlock&&material==null)material=voxel;
                if(material!=null&&main!=null)break;
            }
            check(checks,material!=null&&main!=null,"Native ownership fixture has paid material and main controller");
            check(checks,data.acquire(state.getStructureId(),profile.getId().toString(),state.getOriginX(),state.getOriginY(),state.getOriginZ(),
                state.getRegionSize(),state.getRegionHeight(),ox,oy,oz),"Native ownership region acquired");
            check(checks,data.bindTask(state.getStructureId(),profile.getId().toString(),state.getJobId(),state.getOriginX(),state.getOriginY(),state.getOriginZ()),
                "Native ownership task bound");
            int mx=state.blockWorldX(material),my=state.blockWorldY(material.y),mz=state.blockWorldZ(material);
            int cx=state.blockWorldX(main),cy=state.blockWorldY(main.y),cz=state.blockWorldZ(main);
            call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",mx>>4,mz>>4);
            call(call(world,"getChunkProvider|func_72863_F"),"loadChunk|func_73158_c",cx>>4,cz>>4);
            cleanup.add(new int[]{mx,my,mz});cleanup.add(new int[]{cx,cy,cz});
            check(checks,(Boolean)call(state,"placeOwnedBlock",material)&&(Boolean)call(state,"placeOwnedBlock",main),
                "Native actual owned placement installs material and controller");call(data,"updateProtection",state);
            BitSet paidBefore=(BitSet)((BitSet)field(state,"placed")).clone();
            check(checks,hardness(material.block,world,mx,my,mz)==-1F&&hardness(gt,world,cx,cy,cz)==-1F,
                "Native owned placement establishes both hardness guards");
            check(checks,(Boolean)call(world,"setBlockMetadataWithNotify|func_72921_c",mx,my,mz,material.metadata^1,2)
                &&!(Boolean)call(state,"matches",material),"Native metadata-only mutation invalidates owned material");
            call(world,"setBlockMetadataWithNotify|func_72921_c",mx,my,mz,material.metadata,2);
            check(checks,!(Boolean)call(state,"matches",material)&&hardness(material.block,world,mx,my,mz)==5F,
                "Native manually restored metadata does not recover ownership");
            call(material.block,"removedByPlayer",world,player,mx,my,mz,false);
            check(checks,(Boolean)call(state,"placeOwnedBlock",material),"Native owned placement restores metadata-damaged material");
            set(state,"structureValid",true);
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",mx,my,mz),"Native direct World hard break removes paid material");
            check(checks,!state.isGeometryValidated(),"Native loaded owner loses valid geometry immediately after hard break");
            block(world,mx,my,mz,material.block,material.metadata);
            check(checks,!(Boolean)call(state,"matches",material)&&hardness(material.block,world,mx,my,mz)==5F,
                "Native same material placed by hand remains invalid and removable");
            check(checks,(Boolean)call(material.block,"removedByPlayer",world,player,mx,my,mz,false),"Native invalid manual replacement can be cleared normally");
            check(checks,(Boolean)call(state,"placeOwnedBlock",material)&&(Boolean)call(state,"matches",material)
                &&hardness(material.block,world,mx,my,mz)==-1F,"Native actual owned reinstallation restores material identity");
            Object original=call(world,"getTileEntity|func_147438_o",cx,cy,cz);NBTTagCompound picked=new NBTTagCompound();
            call(original,"writeToNBT|func_145841_b",picked);set(state,"structureValid",true);
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",cx,cy,cz)&&!state.isGeometryValidated(),
                "Native GT overriding break callback immediately invalidates the loaded owner");
            block(world,cx,cy,cz,gt,0);Object manual=call(world,"getTileEntity|func_147438_o",cx,cy,cz);
            call(manual,"setInitialValuesAsNBT",picked,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_SUN.ID);
            call(manual,"setOwnerUuid",profile.getId());call(manual,"setOwnerName",profile.getName());
            call(manual,"setFrontFacing",state.getControllerFacing(false));
            check(checks,!(Boolean)call(state,"matches",main)&&hardness(gt,world,cx,cy,cz)>0F,
                "Native same controller with original picked NBT cannot restore paid identity");
            check(checks,!((CriticalMachineState)call(call(manual,"getMetaTileEntity"),"getCriticalState")).isGeometryValidated(),
                "Native picked main cannot reuse a stale validated owner cache");
            check(checks,paidBefore.equals(field(state,"placed")),"Native hard breaks preserve the paid placement and refund history");
        } catch(Exception|Error problem) {failure=problem;throw problem;}
        finally {
            try {
                for(int i=cleanup.size()-1;i>=0;i--) {
                    int[] position=cleanup.get(i);CriticalBlockProtection.beginOwnedWrite(world,position[0],position[1],position[2]);
                    try {call(world,"setBlockToAir|func_147468_f",position[0],position[1],position[2]);}
                    finally {CriticalBlockProtection.endOwnedWrite(world,position[0],position[1],position[2]);}
                }
                if(state!=null)data.release(state.getStructureId());
            } catch(Exception|Error problem) {if(failure==null)throw problem;failure.addSuppressed(problem);}
        }
        return checks;
    }

    /** One current layout, real charged GT and GraviSuite+Neo tools, no explosion/rotation matrix. */
    @SuppressWarnings("unchecked")
    public static JsonArray runProtection216(WorldServer world)throws Exception {
        JsonArray checks=new JsonArray();
        net.minecraft.entity.player.EntityPlayer player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        com.mojang.authlib.GameProfile profile=(com.mojang.authlib.GameProfile)call(player,"getGameProfile|func_146103_bH");
        Object oldHeld=call(player,"getHeldItem|func_70694_bm");
        boolean oldSneaking=(Boolean)call(player,"isSneaking|func_70093_af");
        CriticalMachineState state=new CriticalMachineState();
        NBTTagCompound n=new NBTTagCompound();
        call(n,"setInteger|func_74768_a","geometryVersion",2);call(n,"setInteger|func_74768_a","v2LayoutRevision",33);
        call(n,"setBoolean|func_74757_a","loomController",true);call(n,"setBoolean|func_74757_a","loomJournalOwner",true);
        call(n,"setString|func_74778_a","structureId",UUID.randomUUID().toString());
        call(n,"setString|func_74778_a","ownerUuid","protection216");call(n,"setString|func_74778_a","jobId","protection216");
        call(n,"setInteger|func_74768_a","state",1);
        call(n,"setTag|func_74782_a","config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SUN,0,0,0).write());
        state.readFromNBT(n);state.worldObj=world;state.xCoord=30008;state.yCoord=100;state.zCoord=30015;
        List<CriticalGeometry.Voxel> all=(List<CriticalGeometry.Voxel>)call(state,"allVoxels");
        set(state,"jobFingerprint",field(state,"modelFingerprint"));BitSet paid=(BitSet)field(state,"placed");
        CriticalGeometry.Voxel controller=null,material=null,manual=null;
        for(CriticalGeometry.Voxel voxel:all) {
            if(controller(voxel)&&!CriticalLoomStructure.isLoomVoxel(voxel))controller=voxel;
            else if(!CriticalLoomStructure.isLoomVoxel(voxel)&&voxel.block instanceof CriticalMaterials.MaterialBlock) {
                if(material==null)material=voxel;else if(manual==null)manual=voxel;
            }
            if(controller!=null&&material!=null&&manual!=null)break;
        }
        check(checks,controller!=null&&material!=null&&manual!=null,"Current layout has main controller, paid material and unpaid manual cell");
        List<int[]> placed=new ArrayList<int[]>();CriticalWorldData data=CriticalWorldData.get(world);Throwable failure=null;
        try {
            call(player,"setSneaking|func_70095_a",false);
            Block gt=gregtech.api.GregTechAPI.sBlockMachines;
            int cx=state.blockWorldX(controller),cy=state.blockWorldY(controller.y),cz=state.blockWorldZ(controller);
            block(world,cx,cy,cz,gt,0);placed.add(new int[]{cx,cy,cz});Object tile=call(world,"getTileEntity|func_147438_o",cx,cy,cz);
            call(tile,"setInitialValuesAsNBT",null,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_SUN.ID);
            call(tile,"setOwnerUuid",profile.getId());call(tile,"setOwnerName",profile.getName());
            call(tile,"setFrontFacing",state.getControllerFacing(false));paid.set(all.indexOf(controller));
            float unclaimedGtHardness=hardness(gt,world,cx,cy,cz);
            check(checks,unclaimedGtHardness>0F,"Native GT controller has ordinary hardness before claim");
            int mx=state.blockWorldX(material),my=state.blockWorldY(material.y),mz=state.blockWorldZ(material);
            block(world,mx,my,mz,material.block,material.metadata);placed.add(new int[]{mx,my,mz});paid.set(all.indexOf(material));
            check(checks,data.acquire(state.getStructureId(),"protection216",state.getOriginX(),state.getOriginY(),state.getOriginZ(),
                state.getRegionSize(),state.getRegionHeight(),state.xCoord,state.yCoord,state.zCoord),"Native exact protection region acquired");
            check(checks,data.bindTask(state.getStructureId(),"protection216",state.getJobId(),state.getOriginX(),state.getOriginY(),state.getOriginZ()),
                "Native exact protection job bound");call(data,"updateProtection",state);
            check(checks,hardness(gt,world,cx,cy,cz)==-1F&&hardness(material.block,world,mx,my,mz)==-1F,
                "Native paid main controller and material hardness -1");
            call(gt,"onBlockHarvested|func_149681_a",world,cx,cy,cz,0,player);
            check(checks,!(Boolean)call(gt,"removedByPlayer",world,player,cx,cy,cz,true)
                &&!(Boolean)call(gt,"removedByPlayer",world,player,cx,cy,cz,false)
                &&call(world,"getTileEntity|func_147438_o",cx,cy,cz)==tile,"Native GT pre-harvest and both removal modes retain controller tile");
            check(checks,!(Boolean)call(material.block,"removedByPlayer",world,player,mx,my,mz,true)
                &&!(Boolean)call(material.block,"removedByPlayer",world,player,mx,my,mz),"Native both Forge material removal overloads refuse paid cell");
            net.minecraft.item.ItemStack gtTool=gregtech.api.enums.ItemList.Tool_Vajra.get(1);
            net.minecraft.item.Item graviItem=(net.minecraft.item.Item)field(Class.forName("gravisuite.GraviSuite"),"vajra");
            net.minecraft.item.ItemStack graviTool=new net.minecraft.item.ItemStack(graviItem);
            NBTTagCompound toolNbt=new NBTTagCompound();call(toolNbt,"setInteger|func_74768_a","toolMode",1);
            call(graviTool,"setTagCompound|func_77982_d",toolNbt);
            check(checks,Arrays.stream(graviItem.getClass().getDeclaredMethods()).anyMatch(method->method.getName().contains("gravisuiteneo$checkHardness")),
                "Native GraviSuite item has the actual Neo hardness transform");
            int ux=state.blockWorldX(manual),uy=state.blockWorldY(manual.y),uz=state.blockWorldZ(manual);
            int gx=cx+3,gy=cy,gz=cz;
            for(net.minecraft.item.ItemStack tool:new net.minecraft.item.ItemStack[]{gtTool,graviTool}) {
                String name=tool.getItem().getClass().getName();
                ic2.api.item.ElectricItem.manager.charge(tool,Double.MAX_VALUE,Integer.MAX_VALUE,true,false);
                check(checks,ic2.api.item.ElectricItem.manager.canUse(tool,10000),"Native actual Vajra is charged: "+name);
                call(player,"setCurrentItemOrArmor|func_70062_b",0,tool);
                int before=world.loadedEntityList.size();
                call(tool.getItem(),"onItemUse|func_77648_a",tool,player,world,cx,cy,cz,1,.5F,.5F,.5F);
                call(tool.getItem(),"onItemUse|func_77648_a",tool,player,world,mx,my,mz,1,.5F,.5F,.5F);
                check(checks,at(world,cx,cy,cz)==gt&&call(world,"getTileEntity|func_147438_o",cx,cy,cz)==tile
                    &&at(world,mx,my,mz)==material.block&&world.loadedEntityList.size()==before,
                    "Native actual Vajra preserves paid controller/material and creates no drops: "+name);
                block(world,ux,uy,uz,material.block,material.metadata);placed.add(new int[]{ux,uy,uz});
                check(checks,hardness(material.block,world,ux,uy,uz)==5F&&!CriticalBlockProtection.protects(world,ux,uy,uz),
                    "Native unpaid manual material inside claimed region keeps hardness5: "+name);
                call(tool.getItem(),"onItemUse|func_77648_a",tool,player,world,ux,uy,uz,1,.5F,.5F,.5F);
                check(checks,(Boolean)call(world,"isAirBlock|func_147437_c",ux,uy,uz),"Native same Vajra still removes manual material: "+name);
                block(world,gx,gy,gz,gt,0);placed.add(new int[]{gx,gy,gz});Object manualTile=call(world,"getTileEntity|func_147438_o",gx,gy,gz);
                call(manualTile,"setInitialValuesAsNBT",null,(short)com.miaokatze.gtsr.common.api.enums.MetaTileEntityID.CRITICAL_SUN.ID);
                call(manualTile,"setOwnerUuid",profile.getId());call(manualTile,"setOwnerName",profile.getName());
                call(manualTile,"setFrontFacing",state.getControllerFacing(false));
                check(checks,hardness(gt,world,gx,gy,gz)==unclaimedGtHardness&&!CriticalBlockProtection.protects(world,gx,gy,gz)
                    &&(Boolean)call(gt,"removedByPlayer",world,player,gx,gy,gz,true),"Native manual GT controller permits deferred player removal: "+name);
                call(tool.getItem(),"onItemUse|func_77648_a",tool,player,world,gx,gy,gz,1,.5F,.5F,.5F);
                check(checks,(Boolean)call(world,"isAirBlock|func_147437_c",gx,gy,gz),"Native same Vajra still removes manual GT controller: "+name);
            }
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",mx,my,mz),"Native internal teardown still removes paid material");
            check(checks,(Boolean)call(world,"setBlockToAir|func_147468_f",cx,cy,cz),"Native internal teardown still removes paid controller");
        } catch(Exception|Error problem) {
            failure=problem;throw problem;
        } finally {
            try {
                call(player,"setCurrentItemOrArmor|func_70062_b",0,oldHeld);call(player,"setSneaking|func_70095_a",oldSneaking);
                for(int[] position:placed)call(world,"setBlockToAir|func_147468_f",position[0],position[1],position[2]);
                data.release(state.getStructureId());
            } catch(Exception|Error cleanup) {
                if(failure==null)throw cleanup;
                failure.addSuppressed(cleanup);
            }
        }
        return checks;
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
                if(kind==0&&!b.transparent&&!b.animated||kind==1&&b.transparent||kind==2&&b.animated){cells.add(v);break;}
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
