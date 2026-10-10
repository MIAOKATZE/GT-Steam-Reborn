package com.miaokatze.gtsr.common.critical;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;

/** Paginated upper-machine hints. Projection never places foundation, loom or machine blocks. */
public final class CriticalProjection {

    private static final int PAGE_SIZE = 1024;

    private CriticalProjection() {}

    public static void construct(TileEntityCriticalController controller, ItemStack trigger, boolean hintsOnly) {
        World world = controller.getWorldObj();
        if (!hintsOnly || world == null || !world.isRemote) return;
        List<CriticalGeometry.Voxel> voxels = CriticalGeometry.getModel(controller.getKind().key).voxels;
        int pages = Math.max(1, (voxels.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(pages - 1, Math.max(0, trigger == null ? 0 : trigger.stackSize - 1));
        int end = Math.min(voxels.size(), (page + 1) * PAGE_SIZE);
        for (int i = page * PAGE_SIZE; i < end; i++) {
            CriticalGeometry.Voxel voxel = voxels.get(i);
            StructureLibAPI.hintParticle(
                world,
                controller.getOriginX() + voxel.x,
                controller.getOriginY() + voxel.y,
                controller.getOriginZ() + voxel.z,
                voxel.block,
                voxel.metadata);
        }
    }

    public static String[] description() {
        return new String[] { StatCollector.translateToLocal("gtsr.critical.projection.loom_only"),
            StatCollector.translateToLocal("gtsr.critical.projection.pages") };
    }
}
