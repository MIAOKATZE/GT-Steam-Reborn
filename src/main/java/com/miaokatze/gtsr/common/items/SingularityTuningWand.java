package com.miaokatze.gtsr.common.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.register.CreativeTabManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 奇点调谐棒（管理员调试物品，无配方无 NEI 途径；交互口径对齐 Outpost 前哨调节棒）：
 * <ul>
 * <li>非潜行右击奇点机器控制器（{@code onItemUseFirst} 服务端权威，消费交互、不开机器 GUI）：
 * 按当前选择（手持 NBT {@code gtsrWandEntry}/{@code gtsrWandDelta}）给该机器对应词条数值
 * 加上增量——超限/失稳/撕裂三选一；撕裂仅 CESS 支持，SSE 反馈「不支持撕裂」；减量钳位 ≥0；
 * 聊天区反馈词条与新值（两位小数百分比口径）；</li>
 * <li>Shift+右击空气（客户端）：打开选择界面（词条三选一 + 增量八档），每次变更即时经
 * {@code WandNet} 包由服务端校验持械后写手持 NBT 并同步。</li>
 * </ul>
 */
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

    /**
     * 服务端权威：非潜行右击奇点机器控制器 → 应用当前选择的词条增量（消费交互，跳过控制器
     * 原 GUI 激活路径）；潜行右击方块与非奇点目标均不消费（交还原交互）。
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote || player.isSneaking()) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity gte)
            || !(gte.getMetaTileEntity() instanceof MTESingularityMachineBase machine)) {
            return false;
        }
        int entry = getSelectedEntry(stack);
        double delta = DELTA_STEPS[getSelectedDeltaIndex(stack)];
        String entryName = StatCollector.translateToLocal("gtsr.wand.entry." + entryNameKey(entry));
        if (entry == 2 && !(machine instanceof MTECriticalSingularityCompressor)) {
            GTUtility
                .sendChatToPlayer(player, EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.wand.no_tear"));
            return true;
        }
        double current = readEntry(machine, entry);
        double updated = Math.max(0.0d, current + delta);
        writeEntry(machine, entry, updated);
        if (machine.getBaseMetaTileEntity() != null) {
            machine.getBaseMetaTileEntity()
                .issueTileUpdate();
        }
        GTUtility.sendChatToPlayer(
            player,
            String.format(
                "%s%s %+.0f → %s%.2f%%",
                EnumChatFormatting.AQUA,
                entryName,
                delta,
                EnumChatFormatting.WHITE,
                updated));
        return true;
    }

    /** Shift+右击空气（客户端）打开选择界面；其余场景交还原有使用路径。 */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote && player.isSneaking()) {
            openSelectorGui(stack);
        }
        return stack;
    }

    @SideOnly(Side.CLIENT)
    private void openSelectorGui(ItemStack stack) {
        net.minecraft.client.Minecraft.getMinecraft()
            .displayGuiScreen(
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

    /** 词条游标 → 基类字段读（0=超限 1=失稳 2=撕裂） */
    private static double readEntry(MTESingularityMachineBase machine, int entry) {
        switch (entry) {
            case 1:
                return machine.mInstability;
            case 2:
                return machine.mTear;
            default:
                return machine.mOverlimit;
        }
    }

    /** 词条游标 → 基类字段写（0=超限 1=失稳 2=撕裂；调用方已保证撕裂仅 CESS） */
    private static void writeEntry(MTESingularityMachineBase machine, int entry, double value) {
        switch (entry) {
            case 1:
                machine.mInstability = value;
                break;
            case 2:
                machine.mTear = value;
                break;
            default:
                machine.mOverlimit = value;
                break;
        }
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
            EnumChatFormatting.WHITE + StatCollector
                .translateToLocal("gtsr.wand.current") + " " + entry + " " + deltaLabel(getSelectedDeltaIndex(stack)));
    }
}
