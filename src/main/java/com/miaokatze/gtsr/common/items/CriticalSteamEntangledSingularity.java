package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.util.CriticalSingularityTexture;
import com.miaokatze.gtsr.common.util.SingularityDropExplosion;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;

public class CriticalSteamEntangledSingularity extends Item {

    public CriticalSteamEntangledSingularity() {
        super();
        setUnlocalizedName("CriticalSteamEntangledSingularity");
        setCreativeTab(GregTechAPI.TAB_GREGTECH);
        setTextureName("gtsr:CriticalSteamEntangledSingularity");
        setMaxStackSize(64);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        // 纹理由 CriticalSingularityTexture 在缝合前注入 (TextureMap.setTextureEntry)，
        // 物品不依赖磁盘上的 PNG 文件。
        this.itemIcon = CriticalSingularityTexture.registerIcon(iconRegister);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.critical_singularity.desc"));
        list.add(
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.steam_entangled_singularity.danger"));
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        SingularityDropExplosion.updateCriticalSingularity(entityItem);
        return false;
    }
}
