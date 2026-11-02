package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.Explosion;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.common.blocks.BlockMachines;

/** Called only inside the probe's real LaunchClassLoader/Mixin environment, after transformation is enabled. */
public final class CriticalV31ProtectionMixinRuntime {
    static void setDeclared(Class<?> owner,Object target,String name,Object value)throws Exception {
        Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static void check(JsonArray rows,boolean pass,String name) {
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("pass",pass);rows.add(row);
        if(!pass)throw new AssertionError(name);
    }
    static WitnessTile tile(CriticalProtectionContract.SparseWorld world,MetaTileEntity meta,int x,int y,int z,
        ForgeDirection facing)throws Exception {
        WitnessTile tile=(WitnessTile)CriticalProtectionContract.unsafe.allocateInstance(WitnessTile.class);
        tile.setWorldObj(world);tile.xCoord=x;tile.yCoord=y;tile.zCoord=z;
        setDeclared(MetaTileEntity.class,meta,"mBaseMetaTileEntity",tile);
        tile.setMetaTileEntity(meta);setDeclared(BaseMetaTileEntity.class,tile,"mFacing",facing);
        world.tiles.put(CriticalProtectionContract.key(x,y,z),tile);return tile;
    }
    public static JsonObject exercise()throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        CriticalProtectionContract.unsafe=(sun.misc.Unsafe)f.get(null);
        CriticalProtectionContract.materials();
        JsonArray rows=new JsonArray();Block previous=GregTechAPI.sBlockMachines;
        Block shared=(Block)CriticalProtectionContract.unsafe.allocateInstance(BlockMachines.class);
        shared.setHardness(5);GregTechAPI.sBlockMachines=shared;
        try {
            for(ForgeDirection facing:new ForgeDirection[]{ForgeDirection.NORTH,ForgeDirection.EAST,ForgeDirection.SOUTH,ForgeDirection.WEST}) {
                CriticalProtectionContract.SparseWorld world=CriticalProtectionContract.world();
                CriticalMachineState state=CriticalProtectionContract.state(world,33,facing);
                List<CriticalGeometry.Voxel> cells=CriticalProtectionContract.geometry(state);
                CriticalGeometry.Voxel controller=cells.stream().filter(v->CriticalStructures.isController(v)&&v.z!=47).findFirst().orElseThrow();
                int x=state.blockWorldX(controller),y=state.blockWorldY(controller.y),z=state.blockWorldZ(controller);
                MTECriticalMachine main=(MTECriticalMachine)CriticalProtectionContract.unsafe.allocateInstance(MTECriticalMachine.class);
                setDeclared(MTECriticalMachine.class,main,"presetKind",CriticalMachineKind.SUN);
                WitnessTile tile=tile(world,main,x,y,z,state.getControllerFacing(false));
                world.blocks.put(CriticalProtectionContract.key(x,y,z),shared);
                ((BitSet)CriticalProtectionContract.field(state,"placed")).set(cells.indexOf(controller));
                CriticalWorldData data=CriticalProtectionContract.claim(state);
                check(rows,shared.getBlockHardness(world,x,y,z)==-1F,"Actual transformed shared Block getter protects paid controller "+facing);
                check(rows,!shared.removedByPlayer(world,null,x,y,z,true),
                    "Actual GT willHarvest callback rejects paid controller before deferred harvest "+facing);
                check(rows,!shared.removedByPlayer(world,null,x,y,z,false),
                    "Actual GT ordinary removal rejects paid controller "+facing);
                check(rows,world.getBlock(x,y,z)==shared&&world.getTileEntity(x,y,z)==tile,
                    "Rejected player removal preserves paid controller and tile "+facing);
                CriticalGeometry.Voxel material=cells.stream().filter(v->!CriticalLoomStructure.isLoomVoxel(v)
                    && !CriticalStructures.isController(v)&&v.block instanceof CriticalMaterials.MaterialBlock).findFirst().orElseThrow();
                int mx=state.blockWorldX(material),my=state.blockWorldY(material.y),mz=state.blockWorldZ(material);
                CriticalProtectionContract.put(state,material,material.block);
                ((BitSet)CriticalProtectionContract.field(state,"placed")).set(cells.indexOf(material));
                data.updateProtection(state);
                check(rows,!material.block.removedByPlayer(world,null,mx,my,mz,true),
                    "Actual Block willHarvest callback rejects paid material "+facing);
                check(rows,!material.block.removedByPlayer(world,null,mx,my,mz),
                    "Actual legacy Block callback rejects paid material "+facing);
                int manualX=mx+1000;world.blocks.put(CriticalProtectionContract.key(manualX,my,mz),material.block);
                check(rows,material.block.getBlockHardness(world,manualX,my,mz)==5F
                    &&material.block.removedByPlayer(world,null,manualX,my,mz,true)&&world.isAirBlock(manualX,my,mz),
                    "Same manual material keeps normal hardness and actual player removal "+facing);
                check(rows,shared.getExplosionResistance(null,world,x,y,z,x,y,z)==Float.MAX_VALUE,
                    "Actual transformed GT coordinate resistance "+facing);
                ProbeEntity entity=(ProbeEntity)CriticalProtectionContract.unsafe.allocateInstance(ProbeEntity.class);
                Explosion explosion=new Explosion(world,entity,x,y,z,Float.MAX_VALUE);
                check(rows,!entity.func_145774_a(explosion,world,x,y,z,shared,Float.MAX_VALUE),
                    "Actual transformed Entity can-destroy rejects paid controller "+facing);
                shared.onBlockExploded(world,x,y,z,explosion);
                check(rows,world.getBlock(x,y,z)==shared&&world.getTileEntity(x,y,z)==tile,
                    "Actual transformed GT callback stops before energy explosion and deletion "+facing);
                if(facing==ForgeDirection.NORTH) {
                    Explosion raycast=new Explosion(world,entity,x+.5,y+.5,z+.5,8F);
                    raycast.doExplosionA();
                    check(rows,!raycast.affectedBlockPositions.contains(new ChunkPosition(x,y,z)),
                        "Actual Explosion A ray collection excludes protected GT controller");
                    raycast.isSmoking=true;raycast.doExplosionB(false);
                    check(rows,world.getBlock(x,y,z)==shared&&world.getTileEntity(x,y,z)==tile,
                        "Actual Explosion A followed by B retains protected GT controller");
                    int blastX=x+1001;Block ordinary=new OrdinaryBlastBlock();
                    world.blocks.put(CriticalProtectionContract.key(blastX,y,z),ordinary);
                    Explosion destruction=new Explosion(world,null,blastX+.5,y+.5,z+.5,Float.MAX_VALUE);
                    destruction.affectedBlockPositions.add(new ChunkPosition(x,y,z));
                    destruction.affectedBlockPositions.add(new ChunkPosition(blastX,y,z));
                    net.minecraftforge.event.world.ExplosionEvent.Detonate detonate=new net.minecraftforge.event.world.ExplosionEvent.Detonate(
                        world,destruction,new java.util.ArrayList<Entity>());
                    new CriticalRuntime.StructureChangeListener().onExplosion(detonate);
                    check(rows,destruction.affectedBlockPositions.size()==1&&destruction.affectedBlockPositions.contains(new ChunkPosition(blastX,y,z)),
                        "Actual Forge Detonate listener removes paid coordinate and keeps ordinary coordinate at arbitrary power");
                    destruction.isSmoking=true;destruction.doExplosionB(false);
                    check(rows,world.isAirBlock(blastX,y,z)&&world.getBlock(x,y,z)==shared&&world.getTileEntity(x,y,z)==tile,
                        "Actual Explosion B deletes ordinary block while protected GT controller remains");
                }
                int ordinaryX=x+1000;world.blocks.put(CriticalProtectionContract.key(ordinaryX,y,z),shared);
                check(rows,shared.getBlockHardness(world,ordinaryX,y,z)==5F,"Shared ordinary GT block keeps its original hardness "+facing);
                check(rows,shared.getExplosionResistance(null,world,ordinaryX,y,z,ordinaryX,y,z)==10F,
                    "Shared ordinary GT block keeps its original resistance "+facing);
                check(rows,entity.func_145774_a(explosion,world,ordinaryX,y,z,shared,Float.MAX_VALUE),
                    "Actual Entity hook still permits ordinary GT block "+facing);
                check(rows,shared.removedByPlayer(world,null,ordinaryX,y,z,true)
                    &&shared.removedByPlayer(world,null,ordinaryX,y,z,false)&&world.isAirBlock(ordinaryX,y,z),
                    "Same manual GT block still permits deferred and ordinary player removal "+facing);
                CriticalGeometry.Voxel port=CriticalGeometry.optionalWorkPorts(state).get(0);
                int hx=state.blockWorldX(port),hy=state.blockWorldY(port.y),hz=state.blockWorldZ(port);
                MTEHatchInputBus bus=(MTEHatchInputBus)CriticalProtectionContract.unsafe.allocateInstance(MTEHatchInputBus.class);
                tile(world,bus,hx,hy,hz,facing);world.blocks.put(CriticalProtectionContract.key(hx,hy,hz),shared);
                check(rows,shared.getBlockHardness(world,hx,hy,hz)==5F&&entity.func_145774_a(explosion,world,hx,hy,hz,shared,Float.MAX_VALUE),
                    "Actual GT manual chamber remains maintainable "+facing);
                CriticalBlockProtection.beginOwnedWrite(world,mx,my,mz);
                try {material.block.breakBlock(world,mx,my,mz,material.block,material.metadata);}
                finally {CriticalBlockProtection.endOwnedWrite(world,mx,my,mz);}
                check(rows,material.block.getBlockHardness(world,mx,my,mz)==-1F,
                    "Exact internal owned write does not revoke material ownership "+facing);
                BitSet paidBefore=(BitSet)((BitSet)CriticalProtectionContract.field(state,"placed")).clone();
                CriticalBlockProtection.beginOwnedWrite(world,mx+1,my,mz);
                try {material.block.breakBlock(world,mx,my,mz,material.block,material.metadata);}
                finally {CriticalBlockProtection.endOwnedWrite(world,mx+1,my,mz);}
                world.setBlockToAir(mx,my,mz);
                CriticalProtectionContract.put(state,material,material.block);
                java.lang.reflect.Method matches=CriticalMachineState.class.getDeclaredMethod("matches",CriticalGeometry.Voxel.class);
                matches.setAccessible(true);
                check(rows,!(Boolean)matches.invoke(state,material),
                    "Hard break then same-material manual replacement cannot restore construction identity "+facing);
                check(rows,material.block.getBlockHardness(world,mx,my,mz)==5F,
                    "Hard-broken paid cell manually replaced by same block keeps manual hardness "+facing);
                long fingerprint=((Number)CriticalProtectionContract.field(state,"jobFingerprint")).longValue();
                check(rows,data.isPlacementInvalidated(state.getStructureId(),fingerprint,material.positionKey())
                    &&paidBefore.equals(CriticalProtectionContract.field(state,"placed")),
                    "Hard break invalidates physical identity while retaining paid history "+facing);
                check(rows,!data.placementInvalidationsMatch(state.getStructureId(),fingerprint^1),
                    "Lost ownership remains bound to the original model fingerprint "+facing);
                data.clearPlacementInvalidation(state.getStructureId(),fingerprint^1,material.positionKey());
                check(rows,data.isPlacementInvalidated(state.getStructureId(),fingerprint,material.positionKey()),
                    "Different model cannot clear retained ownership loss "+facing);
                NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);
                CriticalWorldData restored=new CriticalWorldData();restored.readFromNBT(saved);
                world.perWorldStorage.setData("gtsr_critical_regions",restored);data=restored;
                check(rows,data.isPlacementInvalidated(state.getStructureId(),fingerprint,material.positionKey())
                    &&!(Boolean)matches.invoke(state,material),"World NBT preserves loss across owner reload and same-block replacement "+facing);
                NBTTagCompound legacy=(NBTTagCompound)saved.copy();
                legacy.getTagList("regions",10).getCompoundTagAt(0).removeTag("invalidatedPlacements");
                CriticalWorldData oldData=new CriticalWorldData();oldData.readFromNBT(legacy);
                check(rows,!oldData.isPlacementInvalidated(state.getStructureId(),fingerprint,material.positionKey()),
                    "Old world NBT without provenance loss preserves its existing compatibility boundary "+facing);
                check(rows,world.setBlockToAir(x,y,z),"Internal world teardown still removes the protected controller "+facing);
                data.release(state.getStructureId());
            }
        } finally {GregTechAPI.sBlockMachines=previous;}
        JsonObject result=new JsonObject();result.addProperty("status","PASS");result.add("checks",rows);
        result.addProperty("sharedBlockClass",BlockMachines.class.getName());
        result.addProperty("mode","Real transformed Block/Entity/GT classes; sparse World and allocated native GT base/meta relations; no FML server lifecycle");
        return result;
    }
    public static final class WitnessTile extends BaseMetaTileEntity {
        @Override public void doEnergyExplosion(){throw new AssertionError("Protected GT callback reached the energy explosion before the protection guard");}
    }
    public static final class OrdinaryBlastBlock extends Block {
        public OrdinaryBlastBlock(){super(Material.rock);}
        @Override public boolean canDropFromExplosion(Explosion explosion){return false;}
    }
    public static final class ProbeEntity extends Entity {
        public ProbeEntity(World world){super(world);}
        @Override protected void entityInit(){}
        @Override protected void readEntityFromNBT(NBTTagCompound n){}
        @Override protected void writeEntityToNBT(NBTTagCompound n){}
    }
}
