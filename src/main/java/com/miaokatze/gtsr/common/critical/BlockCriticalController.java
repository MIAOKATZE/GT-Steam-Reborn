package com.miaokatze.gtsr.common.critical;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.terminal.AggregatorGuiHandler;

/** Permanent terminal: a funded structure cannot be mined to erase its ownership or journal. */
public final class BlockCriticalController extends BlockContainer {

    public BlockCriticalController() {
        super(Material.iron);
        setBlockName("criticalController");
        setBlockTextureName("gtsr:critical/loom_controller");
        setHardness(500F);
        setResistance(10000F);
        setHarvestLevel("pickaxe", 3);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityCriticalController();
    }

    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity entity) {
        return 10000F;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        net.minecraft.item.ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof com.gtnewhorizon.structurelib.item.ItemConstructableTrigger) {
            com.gtnewhorizon.structurelib.alignment.constructable.ConstructableUtility
                .handle(held, player, world, x, y, z, side);
            return true;
        }
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof TileEntityCriticalController controller
            && controller.canUse(player)) {
            Object mod = AggregatorGuiHandler.modInstance();
            if (mod != null) player.openGui(mod, 1, world, x, y, z);
        }
        return true;
    }

    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityCriticalController controller
            && (!controller.canUse(player) || !controller.canRemove())) return false;
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, net.minecraft.entity.Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(net.minecraft.world.Explosion explosion) {
        return false;
    }

    @Override
    public void onBlockExploded(World world, int x, int y, int z, net.minecraft.world.Explosion explosion) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityCriticalController controller && !controller.canRemove()) return;
        super.onBlockExploded(world, x, y, z, explosion);
    }
}
