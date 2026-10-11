package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** 通用控制器调试棒。Alt 右击打开目标设置，右击应用，Shift 右击反向或取消。 */
public class SingularityTuningWand extends Item {

    /** 手持 NBT：词条选择游标（0=超限 1=失稳 2=撕裂；无键视为 0；服务端权威写） */
    private static final String NBT_ENTRY = "gtsrWandEntry";
    /** 手持 NBT：增量档位索引（0..DELTA_STEPS.length-1；无键视为 0） */
    private static final String NBT_DELTA = "gtsrWandDelta";

    /** 词条游标数（超限/失稳/撕裂；UI 按钮数与包校验共用口径） */
    public static final int ENTRY_COUNT = 3;
    /** 增量档位表（正增量；GUI 标签与右击应用共用，顺序即档位索引） */
    public static final double[] DELTA_STEPS = { 1, 5, 10, 25, 50, 100, 500, 1000 };

    public SingularityTuningWand() {
        super();
        setUnlocalizedName("SingularityTuningWand");
        setCreativeTab(CreativeTabManager.CREATIVE_TAB);
        setTextureName("gtsr:SingularityTuningWand");
        setMaxStackSize(1);
    }

    /** Custom interaction packets own application; consume vanilla controller GUI activation. */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        return world.getTileEntity(x, y, z) instanceof IGregTechTileEntity;
    }

    /** Shift+右击空气（客户端）打开选择界面；其余场景交还原有使用路径。 */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote && player.isSneaking()) {
            openAirSelectorGui(stack);
        }
        return stack;
    }

    @SideOnly(Side.CLIENT)
    private void openAirSelectorGui(ItemStack stack) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        // A canceled block interaction can fall through to tryUseItem; it is not an air click.
        if (mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) return;
        mc.displayGuiScreen(
            new com.miaokatze.gtsr.client.gui.wand.GuiTuningWandSelector(
                getSelectedEntry(stack),
                getSelectedDeltaIndex(stack)));
    }

    /** 读词条游标（0..2；无键/越界回退 0=超限） */
    public static int getSelectedEntry(ItemStack stack) {
        if (stack.stackTagCompound == null) {
            return 0;
        }
        int entry = stack.stackTagCompound.getInteger(NBT_ENTRY);
        return entry >= 0 && entry < ENTRY_COUNT ? entry : 0;
    }

    /** 写词条游标（stackTagCompound 惰性创建；钳位 0..2；服务端权威写） */
    public static void setSelectedEntry(ItemStack stack, int entry) {
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
        stack.stackTagCompound.setInteger(NBT_ENTRY, Math.max(0, Math.min(ENTRY_COUNT - 1, entry)));
    }

    /** 读增量档位索引（0..DELTA_STEPS.length-1；无键/越界回退 0） */
    public static int getSelectedDeltaIndex(ItemStack stack) {
        if (stack.stackTagCompound == null) {
            return 0;
        }
        int index = stack.stackTagCompound.getInteger(NBT_DELTA);
        return index >= 0 && index < DELTA_STEPS.length ? index : 0;
    }

    /** 写增量档位索引（stackTagCompound 惰性创建；钳位；服务端权威写） */
    public static void setSelectedDeltaIndex(ItemStack stack, int index) {
        if (stack.stackTagCompound == null) {
            stack.stackTagCompound = new NBTTagCompound();
        }
        stack.stackTagCompound.setInteger(NBT_DELTA, Math.max(0, Math.min(DELTA_STEPS.length - 1, index)));
    }

    /** 词条游标 → lang 键尾段（overlimit/instability/tear） */
    public static String entryNameKey(int entry) {
        switch (entry) {
            case 1:
                return "instability";
            case 2:
                return "tear";
            default:
                return "overlimit";
        }
    }

    /** lang 键尾段 → 增量档位标签（+1/+5/…/+1000） */
    public static String deltaLabel(int index) {
        return "+" + (long) DELTA_STEPS[Math.max(0, Math.min(DELTA_STEPS.length - 1, index))];
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.tuning_wand.desc.1"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tuning_wand.desc.2"));
        list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tuning_wand.desc.3"));
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.tuning_wand.desc.4"));
        String entry = StatCollector.translateToLocal("gtsr.wand.entry." + entryNameKey(getSelectedEntry(stack)));
        list.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal(
                "gtsr.wand.debug.family.singularity") + ": " + entry + " " + deltaLabel(getSelectedDeltaIndex(stack)));
    }
}
