package com.miaokatze.gtsr.common.machine;

import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.BASE_TOTAL_HEIGHT;
import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.SOLID_STEEL_CASING_INDEX;
import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.STACK_LAYER_HEIGHT;
import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.STRUCTURE_PIECE_BASE;
import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.STRUCTURE_PIECE_CAP;
import static com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef.STRUCTURE_PIECE_STACK;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.util.CasingTierTextureHelper;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTEMegaSteamTurbineArrayGui;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESingularityModeMachineBase;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.turbine.MegaSteamTurbineStructureDef;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes;
import com.miaokatze.gtsr.common.machine.turbine.SteamTurbineSteamTypes.SteamType;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTUtilityClient;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoMulti;

public class MTEMegaSteamTurbineArray extends MTESingularityModeMachineBase<MTEMegaSteamTurbineArray>
    implements IConstructable, ISurvivalConstructable {

    public static final int MAX_EXTRA_STACKS = 4;

    // 结构定义与等级映射域（三件 shape、惰性 casing 表、casingTier/pipeTier/gearTier/frameTier、
    // 结构 piece/高度/底材常量）已单源至 common/machine/turbine/MegaSteamTurbineStructureDef
    // （O2-04/A01 段 2 外移；建造入口与偏移算式留本类）。

    private int mCasingAmount = 0;
    public int mStackCount = 0;
    public int mCasingTier = -1;
    public int mPipeTier = -1;
    public int mGearTier = -1;
    public int mFrameTier = -1;
    private int excessWater = 0;

    public int mTheoreticalEUt = 0;
    public int mSteamConsumption = 0;
    /** 全精度基础 EU/t：tier 10+ 临界+芯片下基数超 int max，int mEUt 饱和会把 ×5 增幅压成约 2×；不持久化，首 tick 重算 */
    public long mFullBaseEUt = 0;

    /** 全局功率参数：10/8/6/4/2，对应 100%/80%/60%/40%/20% */
    public int mPowerParameter = 10;
    // 奇点模式字段（mSingularityMode/mSingularityModeTicks）与持续时间常量
    // SINGULARITY_DURATION_TICKS 由父类 MTESingularityModeMachineBase 提供
    private static final int[] POWER_PARAMETERS = { 10, 8, 6, 4, 2 };
    /** 蒸汽纠缠奇点模式：效率上限 +100% */
    private static final int SINGULARITY_EFFICIENCY_BONUS = 10000; // +100%
    /** 蒸汽纠缠奇点模式：蒸汽节省 +15% */
    private static final float SINGULARITY_SAVINGS_BONUS = 0.15f; // +15%
    /** 蒸汽纠缠奇点模式：功率翻倍 */
    private static final int SINGULARITY_POWER_MULTIPLIER = 2; // 功率翻倍
    /** 临界奇点模式：功率×5 */
    private static final int CRITICAL_POWER_MULTIPLIER = 5; // 功率×5
    /** 临界奇点模式：效率上限 +200% */
    private static final int CRITICAL_EFFICIENCY_BONUS = 20000; // +200%
    /** 临界奇点模式：蒸汽节省 +20% */
    private static final float CRITICAL_SAVINGS_BONUS = 0.20f; // +20%

    /**
     * 奇点模式功率倍率：mode 2=×5、mode 1=×2、mode 0=×1。
     * 临界模式功率×5，蒸汽纠缠模式功率翻倍，普通模式无加成。
     */
    private int getSingularityPowerMult() {
        return mSingularityMode == 2 ? CRITICAL_POWER_MULTIPLIER
            : mSingularityMode == 1 ? SINGULARITY_POWER_MULTIPLIER : 1;
    }

    /**
     * 奇点模式效率上限加成：mode 2=+200%、mode 1=+100%、mode 0=无。
     */
    private int getSingularityEfficiencyBonus() {
        return mSingularityMode == 2 ? CRITICAL_EFFICIENCY_BONUS
            : mSingularityMode == 1 ? SINGULARITY_EFFICIENCY_BONUS : 0;
    }

    /**
     * 奇点模式蒸汽节省加成：mode 2=+20%、mode 1=+15%、mode 0=无。
     */
    private float getSingularitySavingsBonus() {
        return mSingularityMode == 2 ? CRITICAL_SAVINGS_BONUS : mSingularityMode == 1 ? SINGULARITY_SAVINGS_BONUS : 0f;
    }

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    private final List<MTEOverpressureTurbineInputHatch> mOverpressureInputs = new ArrayList<>();
    private final List<MTESteamCoolingHatch> mSteamCoolingHatches = new ArrayList<>();
    private final List<MTEPressureSteamCoolingHatch> mPressureCoolingHatches = new ArrayList<>();
    private final ArrayList<MTEHatchDynamoMulti> eDynamoMulti = new ArrayList<>();
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();

    /**
     * Local hatch elements for the mega steam turbine array.
     * <p>
     * {@code mteBlacklist()} excludes the specific hatch classes from the NEI hatch item filter,
     * so the generic input/output/dynamo hatch placeholders are shown instead of these custom hatches.
     */
    public enum MegaSteamTurbineArrayHatchElement implements IHatchElement<MTEMegaSteamTurbineArray> {

        PressureSteamInput("GTSR.HatchElement.PressureSteamInput",
            MTEMegaSteamTurbineArray::addPressureSteamToMachineList, MTEHatchPressureSteamInput.class) {

            @Override
            public long count(MTEMegaSteamTurbineArray t) {
                return t.mPressureSteamInputs.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchPressureSteamInput.class);
            }
        },

        OverpressureInput("GTSR.HatchElement.OverpressureInput",
            MTEMegaSteamTurbineArray::addOverpressureInputToMachineList, MTEOverpressureTurbineInputHatch.class) {

            @Override
            public long count(MTEMegaSteamTurbineArray t) {
                return t.mOverpressureInputs.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEOverpressureTurbineInputHatch.class);
            }
        },

        CoolingHatch("GTSR.HatchElement.CoolingHatch", MTEMegaSteamTurbineArray::addCoolingHatchToMachineList,
            MTEPressureSteamCoolingHatch.class, MTESteamCoolingHatch.class) {

            @Override
            public long count(MTEMegaSteamTurbineArray t) {
                return t.mSteamCoolingHatches.size() + t.mPressureCoolingHatches.size();
            }

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEPressureSteamCoolingHatch.class, MTESteamCoolingHatch.class);
            }
        };

        private final String translationKey;
        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEMegaSteamTurbineArray> adder;

        @SafeVarargs
        MegaSteamTurbineArrayHatchElement(String translationKey, IGTHatchAdder<MTEMegaSteamTurbineArray> adder,
            Class<? extends IMetaTileEntity>... mteClasses) {
            this.translationKey = translationKey;
            this.mteClasses = ImmutableList.copyOf(mteClasses);
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEMegaSteamTurbineArray> adder() {
            return adder;
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal(translationKey);
        }

        @Override
        public String getDescriptionLangKey() {
            return translationKey;
        }
    }

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
        registerProgressEntries();
    }

    /**
     * 注册终端数值词条（顺序 = GUI 显示顺序；文本行由 GUI 保留）。
     * 效率行颜色/「(Max)」后缀随值变化，颜色内嵌于 formatter（词条颜色 RESET 不干扰）。
     */
    private void registerProgressEntries() {
        registerEntryCustom(
            "power_output",
            "gtsr.gui.turbine_array.eu_t",
            EnumChatFormatting.AQUA,
            () -> (long) (getVoltage() * mPowerParameter
                * getGroupCount()
                * getSingularityPowerMult()
                * (getMaxEfficiencyLimit(mSteamType) / 10000.0)
                * getEffectiveSteamEffFactor(mSteamType)),
            value -> NumberFormatUtil.formatNumber((long) value) + " EU/t");
        registerEntryCustom(
            "steam_output",
            "gtsr.gui.turbine_array.steam",
            EnumChatFormatting.AQUA,
            () -> calcSteamConsumption(mSteamType),
            value -> NumberFormatUtil.formatNumber((long) value) + " L/t");
        registerEntry(
            "steam_savings",
            "gtsr.gui.turbine_array.savings",
            "%.0f%%",
            EnumChatFormatting.GREEN,
            () -> getSteamSavings() * 100);
        registerEntryCustom(
            "efficiency",
            "gtsr.gui.turbine_array.efficiency",
            EnumChatFormatting.RESET,
            () -> mEfficiency / 100.0d,
            value -> (mEfficiency >= getMaxEfficiencyLimit(mSteamType) ? EnumChatFormatting.LIGHT_PURPLE
                : mEfficiency >= 10000 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                + String.format("%.1f%%", value)
                + (mEfficiency >= getMaxEfficiencyLimit(mSteamType)
                    ? StatCollector.translateToLocal("gtsr.gui.turbine_array.max")
                    : ""));
        registerEntry(
            "max_efficiency",
            "gtsr.gui.turbine_array.max_efficiency",
            "%.1f%%",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> getMaxEfficiencyLimit(mSteamType) / 100.0d);
        registerEntryCustom(
            "base_output",
            "gtsr.gui.turbine_array.output",
            EnumChatFormatting.GREEN,
            () -> Math.abs(mFullBaseEUt * mEfficiency / 10000),
            value -> NumberFormatUtil.formatNumber((long) value) + " EU/t");
        registerEntry(
            "power_parameter",
            "gtsr.gui.turbine_array.power_param",
            "%.0f%%",
            EnumChatFormatting.AQUA,
            () -> mPowerParameter * 10);
    }

    // 蒸汽类型域（SteamType 枚举、PRIORITY、分类/总量/因子换算）已单源至
    // common/machine/turbine/SteamTurbineSteamTypes（O2-04/A01 段 1 外移，纯函数零状态）。

    public SteamType mSteamType = SteamType.NONE;

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMegaSteamTurbineArrayGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        // GUI 分区私有方法化（O2-04/A01 段 3）：12 个闭包按分区拆方法，行序与闭包体逐字保留。
        addMachineStateSyncers(screenElements);
        addSteamTypeRow(screenElements);
        addPowerRow(screenElements);
        addSteamConsumptionRow(screenElements);
        addStatRows(screenElements);
        addSingularityCountdownRow(screenElements);
        addChipStateRow(screenElements);
    }

    /**
     * 同步器组：mCasingTier/mStackCount/mTheoreticalEUt/mSteamConsumption/mSteamType 客户端镜像。
     */
    private void addMachineStateSyncers(DynamicPositionedColumn screenElements) {
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mCasingTier, val -> mCasingTier = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mTheoreticalEUt, val -> mTheoreticalEUt = val));
        screenElements
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSteamConsumption, val -> mSteamConsumption = val));
        screenElements.widget(
            new FakeSyncWidget.IntegerSyncer(() -> mSteamType.ordinal(), val -> mSteamType = SteamType.values()[val]));
    }

    /**
     * 蒸汽类型行：高阶类型紫色标注 + (Tier 6+) 后缀。
     */
    private void addSteamTypeRow(DynamicPositionedColumn screenElements) {
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.steam_type")
                            + (mSteamType.requiresHighTier() ? EnumChatFormatting.LIGHT_PURPLE
                                : EnumChatFormatting.YELLOW)
                            + StatCollector.translateToLocal(mSteamType.nameKey)
                            + (mSteamType.requiresHighTier() ? EnumChatFormatting.GRAY + " (Tier 6+)" : ""))
                    .setTextAlignment(Alignment.CenterLeft)
                    .setDefaultColor(COLOR_TEXT_WHITE.get())
                    .setEnabled(w -> mMachine));
    }

    /**
     * 功率行：EU/t = voltage × powerParameter × groupCount × powerMult × effLimit × effFactor。
     */
    private void addPowerRow(DynamicPositionedColumn screenElements) {
        screenElements.widget(TextWidget.dynamicString(() -> {
            int powerMult = getSingularityPowerMult();
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.eu_t")
                + EnumChatFormatting.AQUA
                + NumberFormatUtil.formatNumber(
                    (long) (getVoltage() * mPowerParameter
                        * getGroupCount()
                        * powerMult
                        * (getMaxEfficiencyLimit(mSteamType) / 10000.0)
                        * getEffectiveSteamEffFactor(mSteamType)))
                + " EU/t";
        })
            .setTextAlignment(Alignment.CenterLeft)
            .setDefaultColor(COLOR_TEXT_WHITE.get())
            .setEnabled(w -> mMachine));
    }

    /**
     * 蒸汽消耗行：calcSteamConsumption L/t。
     */
    private void addSteamConsumptionRow(DynamicPositionedColumn screenElements) {
        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.steam")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(calcSteamConsumption(mSteamType))
                        + " L/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));
    }

    /**
     * 节省/编组/效率/上限/输出/功率参数六行：纯读数行组。
     */
    private void addStatRows(DynamicPositionedColumn screenElements) {
        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.savings")
                        + EnumChatFormatting.GREEN
                        + String.format("%.0f%%", getSteamSavings() * 100))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.stacks")
                        + EnumChatFormatting.AQUA
                        + (1 + mStackCount)
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.groups")
                        + EnumChatFormatting.GRAY
                        + " ("
                        + (mStackCount == 0 ? StatCollector.translateToLocal("gtsr.gui.turbine_array.baseline")
                            : "+" + mStackCount + StatCollector.translateToLocal("gtsr.gui.turbine_array.extra"))
                        + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.efficiency")
                        + (mEfficiency >= getMaxEfficiencyLimit(mSteamType) ? EnumChatFormatting.LIGHT_PURPLE
                            : mEfficiency >= 10000 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                        + String.format("%.1f%%", mEfficiency / 100.0)
                        + (mEfficiency >= getMaxEfficiencyLimit(mSteamType)
                            ? StatCollector.translateToLocal("gtsr.gui.turbine_array.max")
                            : ""))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.turbine_array.max_efficiency")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + String.format("%.1f%%", getMaxEfficiencyLimit(mSteamType) / 100.0))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.output")
                        + EnumChatFormatting.GREEN
                        + NumberFormatUtil.formatNumber(Math.abs(mFullBaseEUt * mEfficiency / 10000))
                        + " EU/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.power_param")
                        + EnumChatFormatting.AQUA
                        + (mPowerParameter * 10)
                        + "%")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));
    }

    /**
     * 奇点模式倒计时行：蒸汽纠缠（1）与临界（2）共用倒计时，前缀文案区分。
     */
    private void addSingularityCountdownRow(DynamicPositionedColumn screenElements) {
        screenElements.widget(TextWidget.dynamicString(() -> {
            if (mSingularityMode == 0) {
                return EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.gui.turbine_array.singularity_mode")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.gui.turbine_array.singularity_off");
            }
            // 临界模式（2）与蒸汽纠缠模式（1）共用倒计时显示，前缀文案区分
            String prefixKey = mSingularityMode == 2 ? "gtsr.gui.turbine_array.singularity_critical"
                : "gtsr.gui.turbine_array.singularity_mode";
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal(prefixKey)
                + EnumChatFormatting.LIGHT_PURPLE
                + (mSingularityModeTicks / 20)
                + "s";
        })
            .setTextAlignment(Alignment.CenterLeft)
            .setDefaultColor(COLOR_TEXT_WHITE.get())
            .setEnabled(w -> mMachine));
    }

    /**
     * 循环超限芯片状态行：未安装 / 已安装但叠加层不足 / 已激活。
     */
    private void addChipStateRow(DynamicPositionedColumn screenElements) {
        // 循环超限芯片状态：未安装 / 已安装但叠加层不足 / 已激活（控制器槽物品客户端可读，mStackCount 经 IntegerSyncer 同步）
        screenElements.widget(TextWidget.dynamicString(() -> {
            if (!hasCycleOverlimitChip()) {
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_missing");
            }
            if (!isCycleOverlimitActive()) {
                return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                    + EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_needs_stacks");
            }
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_state")
                + EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("gtsr.gui.turbine_array.chip_active");
        })
            .setTextAlignment(Alignment.CenterLeft)
            .setDefaultColor(COLOR_TEXT_WHITE.get())
            .setEnabled(w -> mMachine));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEMegaSteamTurbineArray(mName);
    }

    @Override
    public IStructureDefinition<MTEMegaSteamTurbineArray> getStructureDefinition() {
        return MegaSteamTurbineStructureDef.definition();
    }

    /**
     * 结构元素计数回调（伴生类 builder 的 onElementPass 方法引用目标）。
     */
    public void onCasingAdded() {
        mCasingAmount++;
    }

    private boolean addPressureSteamToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mPressureSteamInputs.add(hatch);
            return true;
        }
        return false;
    }

    private boolean addOverpressureInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEOverpressureTurbineInputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mOverpressureInputs.add(hatch);
            return true;
        }
        return false;
    }

    private boolean hasPressureSteamHatch() {
        return !mPressureSteamInputs.isEmpty() || !mOverpressureInputs.isEmpty();
    }

    private boolean hasSteamCoolingHatch() {
        return !mSteamCoolingHatches.isEmpty();
    }

    private boolean hasPressureCoolingHatch() {
        return !mPressureCoolingHatches.isEmpty();
    }

    private boolean addCoolingHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mPressureCoolingHatches.add(hatch);
        }
        if (mte instanceof MTESteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mSteamCoolingHatches.add(hatch);
        }
        return false;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingAmount = 0;
        mStackCount = 0;
        mCasingTier = -1;
        mPipeTier = -1;
        mGearTier = -1;
        mFrameTier = -1;
        mPressureSteamInputs.clear();
        mOverpressureInputs.clear();
        mSteamCoolingHatches.clear();
        mPressureCoolingHatches.clear();
        eDynamoMulti.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE, 6, 5, 0, errors)) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            int bOffset = 9 + i * 4;
            if (!checkPiece(STRUCTURE_PIECE_STACK, 6, bOffset, 0)) break;
            mStackCount++;
        }

        if (mCasingTier <= 0 || mCasingTier > 12 || mFrameTier != mCasingTier) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        int capB = 7 + mStackCount * 4;
        if (!checkPiece(STRUCTURE_PIECE_CAP, 6, capB, 0, errors)) {
            return;
        }

        // v1.9.39 修复：hasInput 计入 mDualInputHatches——样板输入仓（MTEHatchCraftingInputME/Slave，
        // implements IDualInputHatch）经 addInputBusToMachineList 重定向到 mDualInputHatches，
        // 此前漏检导致放了输入总线/样板仓仍提示无输入。ME 流体输入仓（MTEHatchInputME）非
        // IDualInputHatch，正常进 mInputHatches，由 getStoredFluids 特判与 depleteInput 支持。
        boolean hasInput = !mInputHatches.isEmpty() || !mInputBusses.isEmpty()
            || !mDualInputHatches.isEmpty()
            || hasPressureSteamHatch();
        boolean hasOutput = !mOutputHatches.isEmpty() || hasSteamCoolingHatch() || hasPressureCoolingHatch();
        if (!hasInput || !hasOutput || (mDynamoHatches.isEmpty() && eDynamoMulti.isEmpty())) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateAllHatchTextures();
    }

    /**
     * 追加叠加层数 (用于发电公式中的 n).
     * mStackCount = 追加叠加组数 (不含 BASE 内嵌的 1 组基线);
     * 每组 = 4 层 (L5~L2).
     * n = getStackLayers() + 1 = mStackCount * 4 + 1
     *
     * 例: 0 个追加组 → stackLayers=0, n=1, savings=0%
     * 1 个追加组 → stackLayers=4, n=5, savings=20%
     *
     * @see checkProcessing() 公式: EU/t = voltage × multiplier × n × efficiency
     */
    private int getStackLayers() {
        return mStackCount * 4;
    }

    /**
     * 获取叠加组数（含基线）。
     * 用于蒸汽节省和发电公式计算。
     * 组数 = mStackCount + 1（基线算1组）
     */
    public int getGroupCount() {
        return mStackCount + 1;
    }

    /**
     * 最大效率上限 (含所有加成，加算).
     * 基准: 10000 = 100%效率，1% = 100
     * - 每额外组+10%效率上限 = +1000
     * - 高级Gear额外+5%效率上限 = +500
     * - 钛管+10%效率上限 = +1000，钨钢管+25%效率上限 = +2500
     * - 机器等级每提高一级(等级-1)，增加5%效率上限 = +500
     * - 奇点模式：蒸汽纠缠+100% = +10000，临界+200% = +20000
     */
    public int getMaxEfficiencyLimit(SteamType type) {
        int base = type.maxEfficiency;
        // 效率上限加成（加算）：每额外组+1000，高级Gear+500，钛管+1000，钨钢管+2500，机器等级每级+500
        int bonus = 1000 * mStackCount + (mGearTier > 1 ? 500 : 0)
            + (mPipeTier == 2 ? 1000 : mPipeTier == 3 ? 2500 : 0)
            + 500 * (mCasingTier - 1);
        // 奇点模式效率上限加成：蒸汽纠缠+100%、临界+200%（三态 helper 统一取用）
        bonus += getSingularityEfficiencyBonus();
        return base + bonus;
    }

    /**
     * 计算蒸汽节省率。
     * 基础节省率随功率参数变化：10→20%，8→15%，6→10%，4→5%，2→0%。
     * 叠加 Stack/Gear/Pipe 加成，奇点模式下：蒸汽纠缠额外 +15%、临界额外 +20%。
     */
    public float getSteamSavings() {
        // 基础节省率 = 20% - (10 - power) / 2 * 5%
        float baseSavings = 0.20f - ((10 - mPowerParameter) / 2) * 0.05f;
        if (baseSavings < 0) baseSavings = 0;
        // 结构加成
        float bonus = 0.05f * mStackCount + (mGearTier > 1 ? 0.025f : 0f)
            + (mPipeTier == 2 ? 0.025f : mPipeTier == 3 ? 0.075f : 0f);
        // 奇点模式蒸汽节省加成：蒸汽纠缠+15%、临界+20%（三态 helper 统一取用）
        bonus += getSingularitySavingsBonus();
        return Math.min(1.0f, baseSavings + bonus);
    }

    public long getVoltage() {
        // 客户端 GUI 构建期（DoubleSyncValue 构造即求值词条）可能带着未同步/越界的 mCasingTier
        // （GT5U 自定义数据通道仅 7 位 &0x7F，见 onValueUpdate/getUpdateData），索引硬钳制防 AIOOBE
        int tier = Math.min(mCasingTier > 0 ? mCasingTier : 1, GTValues.V.length - 1);
        return GTValues.V[tier];
    }

    private float getCustomEfficiency() {
        int eff = mEfficiency;
        if (eff <= 0) return 0.0f;
        return eff / 10000.0f;
    }

    /**
     * 是否安装了蒸汽轮机循环超限芯片（读控制器槽）。
     */
    private boolean hasCycleOverlimitChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.SteamTurbineCycleOverlimitChip.isStackEqual(stack, true, true);
    }

    /**
     * 循环超限是否激活：芯片已安装且叠加层数达到上限（MAX_EXTRA_STACKS）。
     */
    public boolean isCycleOverlimitActive() {
        return hasCycleOverlimitChip() && mStackCount >= MAX_EXTRA_STACKS;
    }

    /**
     * 循环超限芯片：仅提升输出侧蒸汽转换倍率，蒸汽家族内叠加，致密族只叠加致密族。
     * 纯函数单源在 SteamTurbineSteamTypes（因子值以 SteamType.steamEffFactor 字段为单一事实来源）。
     */
    public float getEffectiveSteamEffFactor(SteamType type) {
        return SteamTurbineSteamTypes.effectiveSteamEffFactor(type, isCycleOverlimitActive());
    }

    /**
     * 输出倍率配套的等效 EU/L。将芯片倍率同时应用到分母，使同一蒸汽类型的消耗保持不变。
     */
    private float getEffectiveSteamEuPerL(SteamType type) {
        return SteamTurbineSteamTypes.effectiveSteamEuPerL(type, isCycleOverlimitActive());
    }

    public long calcSteamConsumption(SteamType type) {
        if (type == SteamType.NONE) return 0;
        int groupCount = getGroupCount();
        long voltage = getVoltage();
        float savings = getSteamSavings();
        int powerMult = getSingularityPowerMult();
        return (long) (voltage * mPowerParameter
            * groupCount
            * powerMult
            * Math.max(0, 1 - savings)
            * getEffectiveSteamEffFactor(type)
            / getEffectiveSteamEuPerL(type));
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> tFluids = getStoredFluids();
        if (tFluids.isEmpty() && mPressureSteamInputs.isEmpty() && mOverpressureInputs.isEmpty()) {
            mEUt = 0;
            mFullBaseEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mSteamType = SteamType.NONE;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int groupCount = getGroupCount();
        long voltage = getVoltage();
        float efficiency = getCustomEfficiency();
        float savings = getSteamSavings();
        int powerMult = getSingularityPowerMult();

        EnumSet<SteamType> availableTypes = EnumSet.noneOf(SteamType.class);
        for (FluidStack fs : tFluids) {
            SteamType type = classifyFluid(fs);
            if (type != SteamType.NONE) availableTypes.add(type);
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.amount > 0) {
                SteamType type = classifyFluid(fs);
                if (type != SteamType.NONE) availableTypes.add(type);
            }
        }
        for (MTEOverpressureTurbineInputHatch hatch : mOverpressureInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.amount > 0) {
                SteamType type = classifyFluid(fs);
                if (type != SteamType.NONE) availableTypes.add(type);
            }
        }

        boolean canUseHighTier = mCasingTier >= 6;
        if (!canUseHighTier) {
            availableTypes.remove(SteamType.DENSE_STEAM);
            availableTypes.remove(SteamType.DENSE_SH_STEAM);
            availableTypes.remove(SteamType.SC_STEAM);
            availableTypes.remove(SteamType.DENSE_SC_STEAM);
        }

        SteamType selectedType = SteamType.NONE;
        long baseEUt = 0;
        long generatedEUt = 0;
        long steamConsumption = 0;

        for (SteamType type : SteamTurbineSteamTypes.PRIORITY) {
            if (!availableTypes.contains(type)) continue;
            // 基础 EU/t: 不含当前效率，由 onRunningTick 覆写（mFullBaseEUt long 路径）统一乘 mEfficiency/10000 后输出
            // 当前理论 EU/t = baseEUt × efficiency
            long base = (long) (voltage * mPowerParameter * groupCount * powerMult * getEffectiveSteamEffFactor(type));
            long eu = (long) (base * efficiency);
            long consumption = (long) (voltage * mPowerParameter
                * groupCount
                * powerMult
                * Math.max(0, 1 - savings)
                * getEffectiveSteamEffFactor(type)
                / getEffectiveSteamEuPerL(type));
            // v1.10.61：门控 long 化——UIV/UMV 级（V[11]=33.6M、V[12]=134M）+ 3 组/临界模式时
            // 蒸汽消耗 > int max，int 求和溢出为负导致永久假 NO_FUEL（见 getTotalSteamAmount）
            long totalAvailable = getTotalSteamAmount(type);
            if (totalAvailable >= consumption) {
                selectedType = type;
                baseEUt = base;
                generatedEUt = eu;
                steamConsumption = consumption;
                break;
            }
        }

        if (selectedType == SteamType.NONE) {
            mEUt = 0;
            mFullBaseEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mSteamType = SteamType.NONE;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        // v1.10.8：饱和钳制到 int 范围——tier 11+（UIV/UMV）baseEUt 可达 13B+，
        // 原 (int) 强转溢出为负导致父类 onRunningTick 走耗电分支/输出异常。
        this.mTheoreticalEUt = (int) Math.min(Integer.MAX_VALUE, generatedEUt);
        // v1.10.88-r2：mSteamConsumption 字段保 int 仅作显示/NBT 旧档兼容（饱和钳制）；
        // 实扣与冷却产物改传未钳制 long——原 int 钳位使 UIV/UMV+临界档门控按 10B 存量放行、
        // 每 tick 实扣却截断至 2.147B（虚省约 4.7 倍）
        this.mSteamConsumption = (int) Math.min(Integer.MAX_VALUE, steamConsumption);
        this.mSteamType = selectedType;

        depleteSteamByType(selectedType, steamConsumption);
        outputCoolingProduct(selectedType, steamConsumption);

        // mFullBaseEUt 存储全精度基础 EU/t，平滑插值在 long 域进行（公式不变：每 tick 1% 或 10）；
        // int mEUt 保留钳制派生值仅供父类内部使用，实际输出走 onRunningTick 覆写的 long 路径
        long difference = baseEUt - mFullBaseEUt;
        long maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mFullBaseEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mFullBaseEUt = baseEUt;
        }
        mEUt = (int) Math.min(Integer.MAX_VALUE, mFullBaseEUt);

        mMaxProgresstime = 1;
        int maxEff = getMaxEfficiencyLimit(selectedType);
        // v1.10.8：换低限蒸汽类型时钳制回落（防止高限类型残留效率超限运行）
        if (mEfficiency > maxEff) {
            mEfficiency = maxEff;
        }
        if (mEfficiency < 10000) {
            mEfficiencyIncrease = 10;
        } else if (mEfficiency < maxEff) {
            mEfficiencyIncrease = 1;
        } else {
            mEfficiencyIncrease = 0;
        }

        return CheckRecipeResultRegistry.GENERATING;
    }

    /**
     * 螺丝刀右击循环切换全局功率参数：10 → 8 → 6 → 4 → 2 → 10。
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        int idx = 0;
        for (int i = 0; i < POWER_PARAMETERS.length; i++) {
            if (POWER_PARAMETERS[i] == mPowerParameter) {
                idx = i;
                break;
            }
        }
        mPowerParameter = POWER_PARAMETERS[(idx + 1) % POWER_PARAMETERS.length];
        if (aPlayer.worldObj.isRemote) return;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.gui.turbine_array.power_param") + ": "
                + EnumChatFormatting.AQUA
                + (mPowerParameter * 10)
                + "%");
    }

    /**
     * 奇点模式倒计时与续杯检查已上移父类统一处理，此处仅转发 super。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // 奇点模式计时/消耗已由 MTESingularityModeMachineBase.onPostTick 统一处理
    }

    /**
     * 全精度输出：父类 onRunningTick 以 int mEUt 输出（tier 10+ 被钳到 2^31-1，临界 ×5 增幅饱和成约 2×），
     * 改用 mFullBaseEUt（long）输出；路由与既有规则不变（dynamo 吞吐不足即爆炸）。
     */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (mFullBaseEUt > 0) {
            addEnergyOutput(mFullBaseEUt * mEfficiency / 10000);
            return true;
        }
        return super.onRunningTick(aStack);
    }

    /**
     * Waila「当前发电」修正：父类 getWailaNBTData 用 int mEUt（钳制值）写 energyUsage
     * （MTEMultiBlockBase.java:2709-2710），tier 10+ 临界+芯片下被钳在 2^31-1×eff；
     * 以全精度基数覆写该标签——仅显示口径，不影响实际输出路径。
     */
    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        if (mFullBaseEUt > 0 && getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isActive()) {
            tag.setLong("energyUsage", -(mFullBaseEUt * mEfficiency / 10000));
        }
    }

    public long getMaximumOutput() {
        long aTotal = 0;
        for (MTEHatchDynamo aDynamo : GTUtility.validMTEList(mDynamoHatches)) {
            aTotal += aDynamo.maxAmperesOut() * aDynamo.maxEUOutput();
        }
        for (MTEHatchDynamoMulti aExoticDynamo : GTUtility.validMTEList(eDynamoMulti)) {
            aTotal += aExoticDynamo.maxAmperesOut() * aExoticDynamo.maxEUOutput();
        }
        return aTotal;
    }

    @Override
    public boolean addEnergyOutputMultipleDynamos(long aEU, boolean aAllowMixedVoltageDynamos) {
        long totalOutput = 0;
        for (MTEHatchDynamo aDynamo : GTUtility.validMTEList(mDynamoHatches)) {
            totalOutput += aDynamo.maxAmperesOut() * aDynamo.maxEUOutput();
        }
        for (MTEHatchDynamoMulti aDynamo : GTUtility.validMTEList(eDynamoMulti)) {
            totalOutput += aDynamo.maxAmperesOut() * aDynamo.maxEUOutput();
        }
        if (totalOutput < aEU) {
            // 输出仓总吞吐不足 -> 与原版 GT5U 一致直接爆炸
            explodeMultiblock();
            return false;
        }
        long injected = 0;
        for (MTEHatchDynamo aDynamo : GTUtility.validMTEList(mDynamoHatches)) {
            injected = injectEnergyIntoDynamo(aEU, injected, aDynamo);
        }
        for (MTEHatchDynamoMulti aDynamo : GTUtility.validMTEList(eDynamoMulti)) {
            injected = injectEnergyIntoDynamo(aEU, injected, aDynamo);
        }
        return injected > 0;
    }

    private long injectEnergyIntoDynamo(long aEU, long injected, MTEHatchDynamo aDynamo) {
        long leftToInject = aEU - injected;
        long aVoltage = aDynamo.maxEUOutput();
        long aAmpsToInject = leftToInject / aVoltage;
        long aRemainder = leftToInject - (aAmpsToInject * aVoltage);
        long ampsOnCurrentHatch = Math.min(aDynamo.maxAmperesOut(), aAmpsToInject);
        aDynamo.getBaseMetaTileEntity()
            .increaseStoredEnergyUnits(aVoltage * ampsOnCurrentHatch, false);
        injected += aVoltage * ampsOnCurrentHatch;
        if (aRemainder > 0 && ampsOnCurrentHatch < aDynamo.maxAmperesOut()) {
            aDynamo.getBaseMetaTileEntity()
                .increaseStoredEnergyUnits(aRemainder, false);
            injected += aRemainder;
        }
        return injected;
    }

    private SteamType classifyFluid(FluidStack fs) {
        return SteamTurbineSteamTypes.classifyFluid(fs);
    }

    // v1.10.61：求和 long 化——跨仓同型蒸汽累加可超 int max（2.147B L）溢出为负，
    // 与 checkProcessing 门控（consumption 为 long）配套，避免永久假 NO_FUEL。
    private long getTotalSteamAmount(SteamType type) {
        return SteamTurbineSteamTypes
            .totalSteamAmount(type, getStoredFluids(), mPressureSteamInputs, mOverpressureInputs);
    }

    // v1.10.88-r2：形参与 remaining 升 long——STEAM/SH_STEAM（euPerL=1）tier11+ 临界档实扣可越 int max；
    // Math.min(fs.amount, remaining) 结果恒 <= fs.amount，(int) 回收无损失
    private boolean depleteSteamByType(SteamType type, long amount) {
        long remaining = amount;
        // v1.10.8：mInputHatches 段改用 GTSRHatchFluidAccess.depleteFluidAcross 跨仓按需取流
        // （原 getStoredFluids 聚合对 ME 仓按流体去重，多 ME 仓供汽欠计）
        for (FluidStack fs : getStoredFluids()) {
            if (classifyFluid(fs) == type) {
                int canDrain = (int) Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    remaining -= GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(fs, canDrain));
                }
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) {
                int canDrain = (int) Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.drain(canDrain, true);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        for (MTEOverpressureTurbineInputHatch hatch : mOverpressureInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) {
                int canDrain = (int) Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.consumeSteam(canDrain);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    // v1.10.88-r2：consumedAmount 升 long 与实扣口径对齐；蒸馏水走 condenseSteam(long) 全精度，
    // int 基输出 API（冷却蒸汽/次级蒸汽 FluidStack）按饱和值钳制
    private void outputCoolingProduct(SteamType type, long consumedAmount) {
        // 循环超限芯片：过热/超临界蒸汽不再输出冷却蒸汽或次级蒸汽，全部冷凝为蒸馏水（蒸汽循环回收）
        boolean overlimit = isCycleOverlimitActive();
        if (consumedAmount <= 0) return;
        int saturatedAmount = (int) Math.min(Integer.MAX_VALUE, consumedAmount);
        switch (type) {
            case STEAM: {
                int waterOutput = condenseSteam(consumedAmount);
                outputCoolingWater(waterOutput);
                break;
            }
            case DENSE_STEAM: {
                // v1.10.61：致密族换算 long 化——consumedAmount×1000 在消耗 >2.147M L/t 时 int 溢出
                long equivalentSteam = (long) consumedAmount * 1000;
                int waterOutput = condenseSteam(equivalentSteam);
                outputCoolingWater(waterOutput);
                break;
            }
            case SH_STEAM: {
                // 循环超限：过热蒸汽直接冷凝为蒸馏水（原输出冷却蒸汽）
                if (overlimit) {
                    outputCoolingWater(condenseSteam(consumedAmount));
                } else {
                    outputCoolingSteam(saturatedAmount);
                }
                break;
            }
            case DENSE_SH_STEAM: {
                // 循环超限：致密过热蒸汽按 1L:1000L 换算后冷凝为蒸馏水（原输出 DenseSteam）
                if (overlimit) {
                    // v1.10.61：致密族换算 long 化——consumedAmount×1000 可超 int max（见 condenseSteam）
                    long equivalentSteam = (long) consumedAmount * 1000;
                    outputCoolingWater(condenseSteam(equivalentSteam));
                } else {
                    FluidStack denseSteam = Materials.DenseSteam.getGas(saturatedAmount);
                    if (denseSteam != null) addOutput(denseSteam);
                }
                break;
            }
            case SC_STEAM: {
                // 循环超限：超临界蒸汽直接冷凝为蒸馏水（原输出 ic2superheatedsteam）
                if (overlimit) {
                    outputCoolingWater(condenseSteam(consumedAmount));
                } else {
                    FluidStack shSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", saturatedAmount);
                    if (shSteam != null) addOutput(shSteam);
                }
                break;
            }
            case DENSE_SC_STEAM: {
                // 循环超限：致密超临界蒸汽按 1L:1000L 换算后冷凝为蒸馏水（原输出 DenseSuperheatedSteam）
                if (overlimit) {
                    // v1.10.61：致密族换算 long 化——consumedAmount×1000 可超 int max（见 condenseSteam）
                    long equivalentSteam = (long) consumedAmount * 1000;
                    outputCoolingWater(condenseSteam(equivalentSteam));
                } else {
                    FluidStack denseSHSteam = Materials.DenseSuperheatedSteam.getGas(saturatedAmount);
                    if (denseSHSteam != null) addOutput(denseSHSteam);
                }
                break;
            }
            default:
                break;
        }
    }

    // v1.10.61：condenseSteam long 化——致密族 equivalentSteam（consumedAmount×1000）可超 int max；
    // excessWater 保持 int 字段（每次取模后 <160 不溢出），返回水量 clamp 回 int 防御。
    private int condenseSteam(long steam) {
        long total = excessWater + steam;
        excessWater = (int) (total % GTValues.STEAM_PER_WATER);
        long water = total / GTValues.STEAM_PER_WATER;
        return (int) Math.min(Integer.MAX_VALUE, water);
    }

    private void outputCoolingWater(int waterAmount) {
        if (waterAmount <= 0) return;
        boolean pushedToCoolingHatch = false;
        for (MTESteamCoolingHatch hatch : mSteamCoolingHatches) {
            int pushed = hatch.pushCoolingWater(waterAmount);
            if (pushed > 0) {
                waterAmount -= pushed;
                pushedToCoolingHatch = true;
            }
            if (waterAmount <= 0) return;
        }
        if (!pushedToCoolingHatch || waterAmount > 0) {
            addOutput(GTModHandler.getDistilledWater(waterAmount));
        }
    }

    private void outputCoolingSteam(int steamAmount) {
        if (steamAmount <= 0) return;
        boolean pushedToCoolingHatch = false;
        for (MTEPressureSteamCoolingHatch hatch : mPressureCoolingHatches) {
            int pushed = hatch.pushCoolingSteam(steamAmount);
            if (pushed > 0) {
                steamAmount -= pushed;
                pushedToCoolingHatch = true;
            }
            if (steamAmount <= 0) return;
        }
        if (!pushedToCoolingHatch || steamAmount > 0) {
            addOutput(gregtech.api.enums.Materials.Steam.getGas(steamAmount));
        }
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.type"));

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        String tierText = switch (mCasingTier) {
            case 1 -> "LV (Steel)";
            case 2 -> "MV (Stainless Steel)";
            case 3 -> "HV (Titanium)";
            case 4 -> "EV (Tungstensteel)";
            case 5 -> "IV (Chrome)";
            case 6 -> "LuV (Rhodium Palladium)";
            case 7 -> "ZPM (Iridium)";
            case 8 -> "UV (Osmiridium)";
            case 9 -> "UHV (Neutronium)";
            case 10 -> "UEV";
            case 11 -> "UIV (Naquadah Alloy)";
            case 12 -> "UMV";
            default -> "Unknown";
        };
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText);

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey));

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.turbine_array.stack_layers")
                + EnumChatFormatting.GOLD
                + getStackLayers());

        String steamType = mSteamType != SteamType.NONE ? StatCollector.translateToLocal(mSteamType.nameKey)
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + (mSteamType.requiresHighTier() ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.YELLOW)
                + steamType);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.turbine_array.output_power")
                + EnumChatFormatting.GREEN
                + NumberFormatUtil.formatNumber(mFullBaseEUt * mEfficiency / 10000)
                + " EU/t");

        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("excessWater", excessWater);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mTheoreticalEUt", mTheoreticalEUt);
        aNBT.setInteger("mSteamConsumption", mSteamConsumption);
        // v1.10.86：持久化全精度基数——父类持久化 mEUt（钳制值），若 mFullBaseEUt 缺失，
        // 重载后首个运行 tick 经 super 满额输出一次，随后 checkProcessing 从 0 平滑爬坡（大基数 ~46s 蒸汽照耗）
        aNBT.setLong("mFullBaseEUt", mFullBaseEUt);
        aNBT.setInteger("mEfficiency", mEfficiency);
        aNBT.setInteger("mPowerParameter", mPowerParameter);
        // 奇点模式字段（mSingularityMode/mSingularityModeTicks）由父类 saveNBTData 持久化
        // v1.10.9：持久化蒸汽类型——修复重进存档后 mSteamType 为 NONE 导致
        // getMaxEfficiency 返回 10000、父类钳制把存档效率砍到 100% 的 bug。
        aNBT.setInteger("mSteamType", mSteamType.ordinal());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        excessWater = aNBT.getInteger("excessWater");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mTheoreticalEUt = aNBT.getInteger("mTheoreticalEUt");
        mSteamConsumption = aNBT.getInteger("mSteamConsumption");
        mFullBaseEUt = aNBT.getLong("mFullBaseEUt");
        mEfficiency = aNBT.getInteger("mEfficiency");
        mPowerParameter = aNBT.hasKey("mPowerParameter") ? aNBT.getInteger("mPowerParameter") : 10;
        // 整数键 mSingularityMode（0/1/2）与旧整数键 mSingularityModeLevel（无 mSingularityMode 键时）
        // 由父类 loadNBTData 兼容读取；此处仅迁移 v1.10.39 及以前的 boolean 键 mSingularityMode
        // 存档（true=普通蒸汽纠缠奇点模式 → mode 1）。以键类型 NBTTagByte 区分新旧档，避免覆盖 int 档。
        if (aNBT.getTag("mSingularityMode") instanceof NBTTagByte) {
            mSingularityMode = aNBT.getBoolean("mSingularityMode") ? 1 : 0;
        }
        // v1.10.9：恢复蒸汽类型（旧档无键 → NONE，由 getMaxEfficiency 的 NONE 兜底处理）
        mSteamType = aNBT.hasKey("mSteamType")
            ? SteamType.values()[Math.max(0, Math.min(aNBT.getInteger("mSteamType"), SteamType.values().length - 1))]
            : SteamType.NONE;
    }

    @Override
    public void stopMachine(@Nonnull ShutDownReason reason) {
        int savedEfficiency = mEfficiency;
        super.stopMachine(reason);
        if (reason == ShutDownReasonRegistry.STRUCTURE_INCOMPLETE) {
            mEfficiency = savedEfficiency;
        }
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tTileEntity, int aBaseCasingIndex) {
        return addPressureSteamToMachineList(tTileEntity, aBaseCasingIndex)
            || addOverpressureInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addCoolingHatchToMachineList(tTileEntity, aBaseCasingIndex)
            || addInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addOutputToMachineList(tTileEntity, aBaseCasingIndex)
            || addDynamoToMachineList(tTileEntity, aBaseCasingIndex);
    }

    @Override
    public boolean addInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        // Exclude MTEOverpressureTurbineInputHatch - it has its own adder
        if (aTileEntity != null && aTileEntity.getMetaTileEntity() instanceof MTEOverpressureTurbineInputHatch) {
            return false;
        }
        return super.addInputToMachineList(aTileEntity, aBaseCasingIndex);
    }

    @Override
    public boolean addDynamoToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHatchDynamo) {
            ((MTEHatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            return mDynamoHatches.add((MTEHatchDynamo) aMetaTileEntity);
        } else if (aMetaTileEntity instanceof MTEHatchDynamoMulti) {
            ((MTEHatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            return eDynamoMulti.add((MTEHatchDynamoMulti) aMetaTileEntity);
        }
        return false;
    }

    private void updateAllHatchTextures() {
        int textureIndex = getCasingTextureIndex();
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            hatch.updateTexture(textureIndex);
        }
        for (MTEOverpressureTurbineInputHatch hatch : mOverpressureInputs) {
            hatch.updateTexture(textureIndex);
        }
        for (MTESteamCoolingHatch hatch : mSteamCoolingHatches) {
            hatch.updateTexture(textureIndex);
        }
        for (MTEPressureSteamCoolingHatch hatch : mPressureCoolingHatches) {
            hatch.updateTexture(textureIndex);
        }
        for (var inputHatch : GTUtility.validMTEList(mInputHatches)) {
            inputHatch.updateTexture(textureIndex);
        }
        for (var outputHatch : GTUtility.validMTEList(mOutputHatches)) {
            outputHatch.updateTexture(textureIndex);
        }
        // v1.9.41 修复：补 mInputBusses（atLeast(InputBus) 元素注册）与 mDualInputHatches（样板仓），
        // 此前 12 级分级下 tier≥2 时输入总线/样板仓底材停滞钢材质
        for (var inputBus : GTUtility.validMTEList(mInputBusses)) {
            inputBus.updateTexture(textureIndex);
        }
        if (mDualInputHatches != null) {
            for (var dualHatch : mDualInputHatches) {
                if (dualHatch != null) {
                    dualHatch.updateTexture(textureIndex);
                }
            }
        }
        for (var dynamoHatch : GTUtility.validMTEList(mDynamoHatches)) {
            dynamoHatch.updateTexture(textureIndex);
        }
        for (var exoticDynamoHatch : GTUtility.validMTEList(eDynamoMulti)) {
            exoticDynamoHatch.updateTexture(textureIndex);
        }
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, 6, 5, 0);
        int extraStacks = getConstructExtraStackCount(stackSize);
        for (int i = 0; i < extraStacks; i++) {
            int bOffset = BASE_TOTAL_HEIGHT + i * STACK_LAYER_HEIGHT;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, 6, bOffset, 0);
        }
        int capB = BASE_TOTAL_HEIGHT - 2 + extraStacks * STACK_LAYER_HEIGHT;
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, 6, capB, 0);
    }

    private static int getConstructExtraStackCount(ItemStack stackSize) {
        int totalHeight = Math.max(
            BASE_TOTAL_HEIGHT,
            GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, BASE_TOTAL_HEIGHT, 25));
        return Math.min(MAX_EXTRA_STACKS, (totalHeight - BASE_TOTAL_HEIGHT) / STACK_LAYER_HEIGHT);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE, stackSize, 6, 5, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        int extraStacks = getConstructExtraStackCount(stackSize);
        for (int i = 0; i < extraStacks; i++) {
            int bOffset = BASE_TOTAL_HEIGHT + i * STACK_LAYER_HEIGHT;
            built = survivalBuildPiece(
                STRUCTURE_PIECE_STACK,
                stackSize,
                6,
                bOffset,
                0,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        int capB = BASE_TOTAL_HEIGHT - 2 + extraStacks * STACK_LAYER_HEIGHT;
        return survivalBuildPiece(STRUCTURE_PIECE_CAP, stackSize, 6, capB, 0, elementBudget, env, false, true);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    public String getMachineType() {
        return "Mega Steam Turbine Array";
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        // v1.10.8 修复：动态返回当前蒸汽类型的效率上限（原硬编码 30000 使父类
        // MTEMultiBlockBase:782-787 的 Math.min 把任何 SteamType 的效率永久截断在 300%，
        // 与 getMaxEfficiencyLimit（可 >30000，含奇点加成）双上限错位）。
        // v1.10.9 修复：NONE 时返回不产生钳制的值（Integer.MAX_VALUE）——旧存档无 mSteamType
        // 键加载后为 NONE，若返回 10000 会把存档的 >100% 效率在首个运行 tick 被父类钳制到 100%。
        // 钳制表达式 min(mEfficiency+inc, getMaxEfficiency() - 0) 对 MAX_VALUE 安全（取左值）。
        return mSteamType != SteamType.NONE ? getMaxEfficiencyLimit(mSteamType) : Integer.MAX_VALUE;
    }

    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.GRAY + "BASE (7 layers, L8~L2): Controller + 1 baseline stack",
            EnumChatFormatting.GRAY + "STACK (4 layers, L5~L2): Repeatable, each +4 layers",
            EnumChatFormatting.GRAY + "CAP (2 layers, L1~L0): Top cover",
            EnumChatFormatting.GRAY + "Extra Stacks: 0 ~ 4 (9~25 total height)",
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.height_channel") };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.desc2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.desc3"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.desc4"))
            .addSeparator()
            .addInfo(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.tier_system"))
            .addInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.steam_progression"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.stacking_desc"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.stacking_desc_2"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.stacking_desc_3"))
            .addSeparator()
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.formula"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.formula_2"))
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.power_param"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.power_param_2"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.power_param_3"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.singularity_mode"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.singularity_mode_2"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.singularity_mode_3"))
            .addInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.cycle_overlimit_chip"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.cycle_overlimit_chip_2"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.cycle_overlimit_chip_3"))
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列。
            // 六参序 (wmin,wmax,hmin,hmax,lmin,lmax)=(13,13,9,25,13,13)：h 为堆叠可变维 [9,25]（BASE=9+4×4 层）；
            // 截面 13×13（三件套 shape 13 行×13 字符，buildPiece 中心锚 6），旧参首位 5 为 beta-2 时代误值，本次一并修正。
            .beginVariableStructureBlock(13, 13, 9, 25, 13, 13, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.ctrl"))
            .addStructureInfo("")
            // beta-1 兼容：GTStructureChannels.STRUCTURE_HEIGHT 与 MultiblockTooltipBuilder.addSubChannel
            // 均为 GT5U 5.09.54（beta-2）新增，beta-1 (5.09.52) 仅有 addSubChannelUsage 且无此通道。
            // 此行原用于 tooltip 显示高度堆叠通道，删去不影响功能（下方 extra_stack_layers 文本已说明）。
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.multi_tier"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.turbine_array.hatches_1"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.turbine_array.hatches_2"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.turbine_array.hatches_3"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.turbine_array.hatches_4"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.turbine_array.extra_stack_layers")
                    + EnumChatFormatting.GOLD
                    + "1-4"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.each_tier")
                    + ")")
            .addStructureHint("gtsr.tooltip.turbine_array.hint_pipe")
            .addStructureHint("gtsr.tooltip.turbine_array.hint_gear")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    private int getCasingTextureIndex() {
        return CasingTierTextureHelper.getTextureIndex(mCasingTier, SOLID_STEEL_CASING_INDEX);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        // 通道仅 7 位（&0x7F）：0=未成形（归一回 -1 保持语义），1..12=等级
        mCasingTier = aValue > 0 ? aValue : -1;
    }

    @Override
    public byte getUpdateData() {
        // 未成形 -1 经 7 位通道会被掩成 127 反噬客户端（getVoltage 越界）：必须钳非负
        return (byte) Math.max(0, mCasingTier);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        setTurbineOverlay();
    }

    protected void setTurbineOverlay() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile.isServerSide()) return;

        IIconContainer[] tTextures;
        if (tile.isActive()) tTextures = getTurbineTextureActive();
        else if (mMachine) tTextures = getTurbineTextureFull();
        else tTextures = getTurbineTextureEmpty();

        GTUtilityClient.setTurbineOverlay(
            tile.getWorld(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            getExtendedFacing(),
            tTextures,
            overlayTickets);
    }

    public IIconContainer[] getTurbineTextureActive() {
        return Textures.BlockIcons.TURBINE_NEW_ACTIVE;
    }

    public IIconContainer[] getTurbineTextureFull() {
        return Textures.BlockIcons.TURBINE_NEW;
    }

    public IIconContainer[] getTurbineTextureEmpty() {
        return Textures.BlockIcons.TURBINE_NEW_EMPTY;
    }

    @Override
    public void onTextureUpdate() {
        setTurbineOverlay();
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (getBaseMetaTileEntity().isClientSide()) GTUtilityClient.clearTurbineOverlay(overlayTickets);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                aActive ? TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5)
                    : TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW5) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }
}
