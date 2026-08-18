package com.miaokatze.gtsr.common.machine.base;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.api.progress.IGTSRProgressProvider;
import com.miaokatze.gtsr.common.gui.MTEGTSRRedstoneHatchGui;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.render.TextureFactory;

/**
 * 红石仓：可安装在任何多方块机器（含 GTSR 机器与 GT5U 原生机器）上的通用红石信号输出仓。
 * 通过结构校验 mixin（{@code StructureCheckerMixin}）绑定控制器后，按所选词条（机器进度词条或
 * GT 标准键 efficiency/power_output/power_consumption/working）读取数值，与阈值比较后
 * 按 updateInterval 周期向六向输出强红石信号；支持反向（inverted）逻辑。
 * 材质与输出机制同 GT5U pH 传感仓（MTEHatchPHSensor）。
 */
public class MTEGTSRRedstoneHatch extends MTEHatch {

    /** GT5U 标准词条键（机器词条不存在同名键时补充进下拉列表） */
    private static final String[] STANDARD_ENTRY_KEYS = { "efficiency", "power_output", "power_consumption",
        "working" };

    /** 标准词条键 → 显示名 lang 键 */
    private static final Map<String, String> STANDARD_ENTRY_DISPLAY_KEYS = new LinkedHashMap<>();

    static {
        STANDARD_ENTRY_DISPLAY_KEYS.put("efficiency", "gtsr.gui.redstone_hatch.entry.efficiency");
        STANDARD_ENTRY_DISPLAY_KEYS.put("power_output", "gtsr.gui.redstone_hatch.entry.power_output");
        STANDARD_ENTRY_DISPLAY_KEYS.put("power_consumption", "gtsr.gui.redstone_hatch.entry.power_consumption");
        STANDARD_ENTRY_DISPLAY_KEYS.put("working", "gtsr.gui.redstone_hatch.entry.working");
    }

    /** 材质复用 pH 传感仓（材质与红石输出机制同 pH 传感仓） */
    private static final IIconContainer TEXTURE_FONT = Textures.BlockIcons.OVERLAY_HATCH_PH_SENSOR;
    private static final IIconContainer TEXTURE_FONT_GLOW = Textures.BlockIcons.OVERLAY_HATCH_PH_SENSOR_GLOW;

    // NBT 持久化字段
    private String entryKey = "";
    private double threshold = 0;
    private boolean inverted = false;
    private int updateInterval = 20;

    // 瞬态字段（不持久化）
    private boolean isOn = false;
    private IMetaTileEntity controllerMeta;
    private long nextUpdateTick = 0;

    public MTEGTSRRedstoneHatch(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 1, 0, "GTSR Redstone Hatch.");
    }

    public MTEGTSRRedstoneHatch(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public boolean allowGeneralRedstoneOutput() {
        return true;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        openGui(aPlayer);
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.redstone_hatch.desc"),
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.redstone_hatch.output"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.redstone_hatch.gui"),
            GTSRUtils.getAddedByLine() };
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        entryKey = aNBT.getString("mEntryKey");
        threshold = aNBT.getDouble("mThreshold");
        inverted = aNBT.getBoolean("mInverted");
        updateInterval = aNBT.getInteger("mUpdateInterval");
        super.loadNBTData(aNBT);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setString("mEntryKey", entryKey);
        aNBT.setDouble("mThreshold", threshold);
        aNBT.setBoolean("mInverted", inverted);
        aNBT.setInteger("mUpdateInterval", updateInterval);
        super.saveNBTData(aNBT);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && aTick >= nextUpdateTick) {
            // 按 updateInterval 周期重算 isOn 并输出（更新频率语义）
            nextUpdateTick = aTick + updateInterval;
            validateController();
            final double value = getCurrentValue();
            isOn = !Double.isNaN(value) && (value > threshold) ^ inverted;
            for (final ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                aBaseMetaTileEntity.setStrongOutputRedstoneSignal(side, (byte) (isOn ? 15 : 0));
            }
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
    }

    /** 校验控制器有效性：base tile 存活且 metaTE 仍等于控制器，否则清空（结构被破坏/控制器消失时关闭输出） */
    private void validateController() {
        if (controllerMeta == null) return;
        IGregTechTileEntity base = controllerMeta.getBaseMetaTileEntity();
        if (base == null || base.isDead() || base.getMetaTileEntity() != controllerMeta) {
            controllerMeta = null;
        }
    }

    // ============================================================
    // 词条来源（只读机器实际存在的词条）
    // ============================================================

    /**
     * 可用词条键列表：控制器为 IGTSRProgressProvider 时收集其 getProgressEntries() 的 internalKey（顺序保留）；
     * 再补充 GT5U 标准词条键（机器词条中不存在同名键时）。
     */
    public List<String> getAvailableEntryKeys() {
        List<String> keys = new ArrayList<>();
        if (controllerMeta instanceof IGTSRProgressProvider) {
            List<GTSRProgressEntry> entries = ((IGTSRProgressProvider) controllerMeta).getProgressEntries();
            if (entries != null) {
                for (GTSRProgressEntry entry : entries) {
                    keys.add(entry.getInternalKey());
                }
            }
        }
        for (String standardKey : STANDARD_ENTRY_KEYS) {
            if (!keys.contains(standardKey)) {
                keys.add(standardKey);
            }
        }
        return keys;
    }

    /**
     * 词条显示名 lang 键：机器词条经 progressBar.getDisplayKey；标准键映射到
     * gtsr.gui.redstone_hatch.entry.* 系列；未知键返回 null。
     */
    public String getEntryDisplayKey(String internalKey) {
        if (internalKey == null) return null;
        if (controllerMeta instanceof IGTSRProgressProvider) {
            String displayKey = ((IGTSRProgressProvider) controllerMeta).getDisplayKey(internalKey);
            if (displayKey != null) return displayKey;
        }
        return STANDARD_ENTRY_DISPLAY_KEYS.get(internalKey);
    }

    /**
     * 读取词条值：控制器 null → NaN；机器词条（hasEntry）→ getEntryValue；
     * 标准键 → 控制器 cast 为 MTEMultiBlockBase 读取（efficiency 0-100、power_output/consumption EU/t、
     * working 0/1）；非 MTEMultiBlockBase 控制器的标准键返回 NaN。
     */
    public double readValue(String internalKey) {
        if (controllerMeta == null) return Double.NaN;
        if (controllerMeta instanceof IGTSRProgressProvider) {
            IGTSRProgressProvider provider = (IGTSRProgressProvider) controllerMeta;
            if (provider.hasEntry(internalKey)) {
                return provider.getEntryValue(internalKey);
            }
        }
        if (!(controllerMeta instanceof MTEMultiBlockBase)) return Double.NaN;
        MTEMultiBlockBase controller = (MTEMultiBlockBase) controllerMeta;
        switch (internalKey) {
            case "efficiency":
                return controller.mEfficiency / 100.0;
            case "power_output":
                return controller.mEUt > 0 ? controller.mEUt : 0;
            case "power_consumption":
                return controller.mEUt < 0 ? -controller.mEUt : 0;
            case "working":
                return controllerMeta.getBaseMetaTileEntity()
                    .isActive() ? 1 : 0;
            default:
                return Double.NaN;
        }
    }

    /** 当前所选词条的实时值 */
    public double getCurrentValue() {
        return readValue(entryKey);
    }

    // ============================================================
    // 控制器绑定（StructureCheckerMixin 调用）
    // ============================================================

    public void setController(IMetaTileEntity mte) {
        this.controllerMeta = mte;
    }

    /**
     * 结构施加底材：由 StructureCheckerMixin 在结构校验访问本仓时调用，索引取自本仓位置的结构
     * 期望方块。解析失败时由调用方决定回退策略，不在仓室内部猜测控制器材质。
     */
    public void applyStructureTexture(int casingIndex) {
        updateTexture(casingIndex);
    }

    public IMetaTileEntity getController() {
        return controllerMeta;
    }

    // ============================================================
    // Getter / Setter（GUI 同步与 mixin 注册用）
    // ============================================================

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    // ============================================================
    // 外观与 GUI
    // ============================================================

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEGTSRRedstoneHatch(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(TEXTURE_FONT), TextureFactory.builder()
            .addIcon(TEXTURE_FONT_GLOW)
            .glow()
            .build() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(TEXTURE_FONT) };
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        return new MTEGTSRRedstoneHatchGui(this).build(data, syncManager, uiSettings);
    }
}
