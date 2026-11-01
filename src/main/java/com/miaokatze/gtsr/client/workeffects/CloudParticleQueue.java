package com.miaokatze.gtsr.client.workeffects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.client.machinevisual.MachineVisualClient;
import com.miaokatze.gtsr.common.machine.tcds.TcdsParticleFx;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Cell;
import com.miaokatze.gtsr.common.workeffects.WorkEffectDefinition.Emitter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** One client-tick cloud budget, shared by thermal outlets, the three selected stacks and the original TCDS chain. */
@SideOnly(Side.CLIENT)
public final class CloudParticleQueue {

    public static final int MAX_OWNERS = 256;
    public static final int MAX_PER_OWNER = 4;
    public static final int STANDARD_BUDGET = 128;
    public static final int LOW_BUDGET = 32;
    private static final double RANGE_SQ = 64D * 64D;
    private static final Map<IMetaTileEntity, Request> REQUESTS = new LinkedHashMap<>();
    private static World requestWorld;
    private static int nextOwner;
    private static long clientTicks;
    private static long emitted;
    private static long dropped;

    private static final class Request {

        private final IMetaTileEntity owner;
        private final ExtendedFacing facing;
        private final List<Emitter> emitters;
        private final int count;
        private final float scale;

        private Request(IMetaTileEntity owner, ExtendedFacing facing, List<Emitter> emitters, int count, float scale) {
            this.owner = owner;
            this.facing = facing;
            this.emitters = emitters;
            this.count = count;
            this.scale = scale;
        }

        private IMetaTileEntity owner() {
            return owner;
        }

        private ExtendedFacing facing() {
            return facing;
        }

        private List<Emitter> emitters() {
            return emitters;
        }

        private int count() {
            return count;
        }

        private float scale() {
            return scale;
        }
    }

    /** Repeated submissions by the same owner replace that tick's request; they never append another emission pool. */
    public static void submit(IMetaTileEntity owner, ExtendedFacing facing, List<Emitter> emitters, int count,
        float scale) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world != requestWorld) {
            REQUESTS.clear();
            requestWorld = world;
            nextOwner = 0;
        }
        if (MachineVisualClient.quality() == MachineVisualClient.Quality.OFF || mc.gameSettings.particleSetting == 2
            || count <= 0
            || emitters.isEmpty()
            || !isLive(owner, world)) return;
        IGregTechTileEntity base = owner.getBaseMetaTileEntity();
        if (mc.renderViewEntity == null
            || mc.renderViewEntity.getDistanceSq(base.getXCoord() + .5D, base.getYCoord() + .5D, base.getZCoord() + .5D)
                > RANGE_SQ)
            return;
        if (!REQUESTS.containsKey(owner) && REQUESTS.size() >= MAX_OWNERS) {
            dropped += count;
            return;
        }
        REQUESTS.put(owner, new Request(owner, facing, emitters, Math.min(MAX_PER_OWNER, count), scale));
    }

    public static boolean isLive(IMetaTileEntity owner, World world) {
        if (owner == null || world == null) return false;
        IGregTechTileEntity base = owner.getBaseMetaTileEntity();
        return base != null && base.getWorld() == world
            && world.blockExists(base.getXCoord(), base.getYCoord(), base.getZCoord())
            && world.getTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord()) == base
            && base.getMetaTileEntity() == owner;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        TcdsParticleFx.retainLiveWorld(world);
        clientTicks++;
        if (world == null || world != requestWorld
            || mc.isGamePaused()
            || mc.gameSettings.particleSetting == 2
            || MachineVisualClient.quality() == MachineVisualClient.Quality.OFF) {
            REQUESTS.clear();
            requestWorld = world;
            return;
        }
        List<Request> requests = new ArrayList<>(REQUESTS.values());
        REQUESTS.clear();
        int size = requests.size();
        if (size == 0) return;
        int budget = mc.gameSettings.particleSetting == 1
            || MachineVisualClient.quality() == MachineVisualClient.Quality.LOW ? LOW_BUDGET : STANDARD_BUDGET;
        int start = Math.floorMod(nextOwner, size);
        int visited = 0;
        // One particle per owner per round prevents one large/early machine consuming the entire allowance.
        for (int round = 0; round < MAX_PER_OWNER && budget > 0; round++) {
            for (int index = 0; index < size && budget > 0; index++) {
                Request request = requests.get((start + index) % size);
                if (round >= request.count()) continue;
                visited = index + 1;
                budget--;
                if (spawn(request, world)) emitted++;
                else dropped++;
            }
        }
        nextOwner = (start + Math.max(1, visited)) % size;
    }

    private static boolean spawn(Request request, World world) {
        if (!isLive(request.owner(), world)) return false;
        Emitter emitter = request.emitters()
            .get(
                world.rand.nextInt(
                    request.emitters()
                        .size()));
        IGregTechTileEntity base = request.owner()
            .getBaseMetaTileEntity();
        Vec3Impl offset = offset(request.facing(), emitter.cell());
        int x = base.getXCoord() + offset.get0();
        int y = base.getYCoord() + offset.get1();
        int z = base.getZCoord() + offset.get2();
        if (!isAir(world, x, y, z)) return false;
        if (emitter.above() != null) {
            Vec3Impl above = offset(request.facing(), emitter.above());
            if (!isAir(
                world,
                base.getXCoord() + above.get0(),
                base.getYCoord() + above.get1(),
                base.getZCoord() + above.get2())) return false;
        }
        // Jitter and upward velocity remain in world space, after the StructureLib ABC transform.
        double px = x + .5D + (world.rand.nextDouble() - .5D) * .8D;
        double py = y + .5D + emitter.centerYOffset();
        double pz = z + .5D + (world.rand.nextDouble() - .5D) * .8D;
        if (request.scale() == 1F) {
            world.spawnParticle("cloud", px, py, pz, 0D, .3D, 0D);
        } else {
            EntityCloudFX fx = new EntityCloudFX(world, px, py, pz, 0D, .3D, 0D);
            fx.multipleParticleScaleBy(request.scale());
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }
        return true;
    }

    private static Vec3Impl offset(ExtendedFacing facing, Cell cell) {
        return facing.getWorldOffset(new Vec3Impl(cell.a(), cell.b(), cell.c()));
    }

    private static boolean isAir(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.isAirBlock(x, y, z);
    }

    /** Read-only counters for the actual client trace; no game run has been inferred from their existence. */
    public static String diagnostics() {
        return "ticks=" + clientTicks + ",emitted=" + emitted + ",dropped=" + dropped + ",queued=" + REQUESTS.size();
    }
}
