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
                CriticalMachineState state=CriticalProtectionContract.state(world,31,facing);
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
                CriticalGeometry.Voxel port=CriticalGeometry.optionalWorkPorts(state).get(0);
                int hx=state.blockWorldX(port),hy=state.blockWorldY(port.y),hz=state.blockWorldZ(port);
                MTEHatchInputBus bus=(MTEHatchInputBus)CriticalProtectionContract.unsafe.allocateInstance(MTEHatchInputBus.class);
                tile(world,bus,hx,hy,hz,facing);world.blocks.put(CriticalProtectionContract.key(hx,hy,hz),shared);
                check(rows,shared.getBlockHardness(world,hx,hy,hz)==5F&&entity.func_145774_a(explosion,world,hx,hy,hz,shared,Float.MAX_VALUE),
                    "Actual GT manual chamber remains maintainable "+facing);
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
