// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.miaokatze.gtsr.client.machinevisual.TungstenSteelGearboxSprite;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Dyes;
import gregtech.common.blocks.MaterialCasings;

/** A native-rendered gearbox with a steel gear recess and robust tungstensteel casing. */
public final class BlockTungstenSteelGearbox extends Block {

    public BlockTungstenSteelGearbox() {
        super(MaterialCasings.INSTANCE);
        setBlockName("TungstenSteelGearbox");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("wrench", 2);
        setStepSound(soundTypeMetal);
    }

    @Override
    public int getRenderType() {
        return GregTechAPI.sBlockCasings2.getRenderType();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        blockIcon = TungstenSteelGearboxSprite.register(register);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderColor(int meta) {
        return Dyes.MACHINE_METAL.toInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int colorMultiplier(IBlockAccess world, int x, int y, int z) {
        return getRenderColor(0);
    }
}
