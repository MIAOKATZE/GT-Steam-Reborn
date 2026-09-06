package com.miaokatze.gtsr.common.machine.base;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressBar;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.api.progress.IGTSRProgressProvider;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

/**
 * GTSR 自有多方块基类：扩展 GT5U MTEEnhancedMultiBlockBase，统一接入 GTSR 进度显示基础设施
 * （IGTSRProgressProvider + 组合 GTSRProgressBar）。Enhanced 直系 GTSR 机器改挂本类后，
 * 子类机器在构造时调用 registerEntry(...) 注册词条，GUI 终端统一渲染。
 * 接口与便捷方法签名与 {@link MTESingularityModeMachineBase} 完全一致（子类统一调用 registerEntry(...)）。
 */
public abstract class MTEGTSRMultiBlockBase<T extends MTEGTSRMultiBlockBase<T>> extends MTEEnhancedMultiBlockBase<T>
    implements IGTSRProgressProvider {

    /** GTSR 进度显示基础设施：子类机器构造时用 registerEntry(...) 注册词条，GUI 终端统一渲染 */
    protected final GTSRProgressBar progressBar = new GTSRProgressBar();

    protected MTEGTSRMultiBlockBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEGTSRMultiBlockBase(String aName) {
        super(aName);
    }

    // region GTSR 进度显示基础设施（IGTSRProgressProvider）
    // 词条注册/查询全部委托 progressBar；子类机器在构造时调用 registerEntry(...) 注册显示口径

    @Override
    public List<GTSRProgressEntry> getProgressEntries() {
        return progressBar.getProgressEntries();
    }

    @Override
    public boolean hasEntry(String internalKey) {
        return progressBar.hasEntry(internalKey);
    }

    @Override
    public double getEntryValue(String internalKey) {
        return progressBar.getEntryValue(internalKey);
    }

    @Override
    public String getDisplayKey(String internalKey) {
        return progressBar.getDisplayKey(internalKey);
    }

    /** 注册词条（直接实例）；同 internalKey 重复注册后者覆盖 */
    protected final void registerEntry(GTSRProgressEntry entry) {
        progressBar.registerEntry(entry);
    }

    /** 注册词条（默认格式化）：format 为 String.format(Locale.ENGLISH, format, value) 模板 */
    protected final void registerEntry(String internalKey, String displayKey, String format, EnumChatFormatting color,
        DoubleSupplier valueSupplier) {
        registerEntry(GTSRProgressEntry.of(internalKey, displayKey, format, color, valueSupplier));
    }

    /** 注册词条（自定义格式化，如带条件后缀） */
    protected final void registerEntryCustom(String internalKey, String displayKey, EnumChatFormatting color,
        DoubleSupplier valueSupplier, DoubleFunction<String> formatter) {
        registerEntry(GTSRProgressEntry.ofCustom(internalKey, displayKey, color, valueSupplier, formatter));
    }

    // endregion

    // region [GT-compat] beta-1/2 NBT 描述包同步垫片（beta-3 迁 writeToStream/readFromStream 的兼容层）

    /**
     * [GT-compat] GT5U 2.9.0 beta-3（PR7821）把 MTE 客户端同步从 NBT 描述包
     * （getDescriptionData/onDescriptionPacket）迁到 writeToStream/readFromStream 并删除了
     * MTEEnhancedMultiBlockBase 的这两个方法，子类（MTEHubArrayBase）旧 NBT 路径的 super 调用
     * 由此失去编译期落点。本垫片按 beta-2 MTEEnhancedMultiBlockBase 原实现（:552-589）复刻：
     * beta-1/2 运行时经虚分派覆写父类同名方法承住 super 链（root IMetaTileEntity 默认实现
     * 返回 null/空方法体，无祖先键可丢）；beta-3 下父类方法已删，本方法为不覆写任何父类方法的
     * 合法死代码（故无 @Override）。
     *
     * 对 beta-2 原实现的可达性差异（所用符号在 beta-1/2/3 三版本均已核实存在）：
     * - center 为 private final 字段：经 public getCenter()（返回可变 Vector3f）读写，行为等价；
     * - mExtendedFacing 为 private 字段：读经 public getExtendedFacing()，写经 public
     * setExtendedFacing(...)（ExtendedFacing.of 返回缓存实例，其引用比较去重仍生效）；
     * - structureRadius 为 private 且三版本均无 setter：radius 键两侧一致省略——beta-1/2 客户端
     * 结构半径不再经描述包同步，仅影响客户端活动音循环音量档（createSoundLoop 的 setVolume
     * 缩放退回 1f 基线）与 radius 变化触发 restartActivitySound，服务端半径逻辑不受影响。
     */
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = new NBTTagCompound();
        data.setFloat("centerX", getCenter().x);
        data.setFloat("centerY", getCenter().y);
        data.setFloat("centerZ", getCenter().z);
        data.setByte(
            "eRotation",
            (byte) getExtendedFacing().getRotation()
                .getIndex());
        data.setByte(
            "eFlip",
            (byte) getExtendedFacing().getFlip()
                .getIndex());
        return data;
    }

    /**
     * [GT-compat] onDescriptionPacket 垫片，与 {@link #getDescriptionData()} 成对（beta-1/2 运行时
     * 覆写父类、beta-3 下死代码）。读侧与写侧严格对称（centerX/Y/Z float×3 + eRotation/eFlip
     * byte×2），radius 键两侧一致省略。与 beta-2 原实现的唯一语义差异：朝向落地经
     * setExtendedFacing 而非直接字段赋值，值变化时客户端会顺带走 issueTextureUpdate/状态复位
     * 分支（无服务端副作用，详见 getDescriptionData 注释）。
     */
    public void onDescriptionPacket(NBTTagCompound data) {
        getCenter().set(data.getFloat("centerX"), data.getFloat("centerY"), data.getFloat("centerZ"));
        setExtendedFacing(
            ExtendedFacing.of(
                getBaseMetaTileEntity().getFrontFacing(),
                Rotation.byIndex(data.getByte("eRotation")),
                Flip.byIndex(data.getByte("eFlip"))));
    }

    // endregion
}
