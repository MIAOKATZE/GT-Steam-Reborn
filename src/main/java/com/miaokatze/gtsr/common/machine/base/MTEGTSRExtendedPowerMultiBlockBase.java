package com.miaokatze.gtsr.common.machine.base;

import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.common.api.progress.GTSRProgressBar;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.api.progress.IGTSRProgressProvider;

import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;

/** GT long-EU multiblock base with the shared GTSR progress display delegation. */
public abstract class MTEGTSRExtendedPowerMultiBlockBase<T extends MTEGTSRExtendedPowerMultiBlockBase<T>>
    extends MTEExtendedPowerMultiBlockBase<T> implements IGTSRProgressProvider {

    /** GTSR 进度显示基础设施：子类机器构造时用 registerEntry(...) 注册词条，GUI 终端统一渲染 */
    protected final GTSRProgressBar progressBar = new GTSRProgressBar();

    protected MTEGTSRExtendedPowerMultiBlockBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    protected MTEGTSRExtendedPowerMultiBlockBase(String aName) {
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

}
