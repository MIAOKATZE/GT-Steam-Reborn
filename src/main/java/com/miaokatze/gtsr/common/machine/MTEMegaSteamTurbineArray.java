package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
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

import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
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

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";
    private static final int BASE_TOTAL_HEIGHT = 9;
    private static final int STACK_LAYER_HEIGHT = 4;
    public static final int MAX_EXTRA_STACKS = 4;

    private static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION = null;

    private int mCasingAmount = 0;
    public int mStackCount = 0;
    public int mCasingTier = -1;
    public int mPipeTier = -1;
    public int mGearTier = -1;
    private int mFrameTier = -1;
    private int excessWater = 0;

    public int mTheoreticalEUt = 0;
    public int mSteamConsumption = 0;

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
    private enum MegaSteamTurbineArrayHatchElement implements IHatchElement<MTEMegaSteamTurbineArray> {

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
            return GTUtility.translate(translationKey);
        }

        @Override
        public String getDescriptionLangKey() {
            return translationKey;
        }
    }

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
    }

    public enum SteamType {

        NONE(0, 0, 0, ""),
        STEAM(0.5f, 0.5f, 15000, "gtsr.gui.steam_type.normal"),
        DENSE_STEAM(0.5f, 500, 20000, "gtsr.gui.steam_type.dense"),
        SH_STEAM(1.0f, 1.0f, 20000, "gtsr.gui.steam_type.superheated"),
        DENSE_SH_STEAM(1.0f, 1000, 25000, "gtsr.gui.steam_type.dense_superheated"),
        SC_STEAM(1.0f, 1.0f, 25000, "gtsr.gui.steam_type.supercritical"),
        DENSE_SC_STEAM(1.0f, 1000, 30000, "gtsr.gui.steam_type.dense_supercritical");

        public final float steamEffFactor;
        public final float euPerL;
        public final int maxEfficiency;
        public final String nameKey;

        SteamType(float steamEffFactor, float euPerL, int maxEfficiency, String nameKey) {
            this.steamEffFactor = steamEffFactor;
            this.euPerL = euPerL;
            this.maxEfficiency = maxEfficiency;
            this.nameKey = nameKey;
        }

        boolean isDense() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == DENSE_SC_STEAM;
        }

        public boolean requiresHighTier() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == SC_STEAM || this == DENSE_SC_STEAM;
        }
    }

    private static final SteamType[] STEAM_TYPE_PRIORITY = { SteamType.DENSE_SC_STEAM, SteamType.SC_STEAM,
        SteamType.DENSE_SH_STEAM, SteamType.DENSE_STEAM, SteamType.SH_STEAM, SteamType.STEAM };

    public SteamType mSteamType = SteamType.NONE;

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEMegaSteamTurbineArrayGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mCasingTier, val -> mCasingTier = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mTheoreticalEUt, val -> mTheoreticalEUt = val));
        screenElements
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSteamConsumption, val -> mSteamConsumption = val));
        screenElements.widget(
            new FakeSyncWidget.IntegerSyncer(() -> mSteamType.ordinal(), val -> mSteamType = SteamType.values()[val]));

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
                        + NumberFormatUtil.formatNumber(Math.abs((long) mEUt * mEfficiency / 10000))
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
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEMegaSteamTurbineArray>builder()
                .addShape(
                    STRUCTURE_PIECE_BASE,
                    transpose(
                        new String[][] {
                            { "EEEEBBBBBEEEE", "E EBBBBBBBE E", "EEB       BEE", "EB BBBBBBB BE", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", "EB BBBBBBB BE",
                                "EEB       BEE", "E EBBBBBBBE E", "EEEEBBBBBEEEE" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB BCCCCCB BB",
                                "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCCCCCB BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E           E", "  E  BBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", "  BEEEEEEEB  ", "  BBEEEEEBB  ",
                                " E BBEEEBB E ", "  E  BBB  E  ", "E           E" },
                            { "EEEBBBBBBBEEE", "E BBBBBBBBB E", "EBB       BBE", "BB         BB", "BB         BB",
                                "BB         BB", "BB    D    BB", "BB         BB", "BB         BB", "BB         BB",
                                "EBB       BBE", "E BBBBBBBBB E", "EEEBBBBBBBEEE" },
                            { "E  BBB~BBB  E", "  BBBBCBBBB  ", " BBBDBCBDBBB ", "BBBCDCDDCBBB", "BBBBCDCDCBBBB",
                                "BBBBBCCCBBBBB", "BBCCCCDCCCCBB", "BBBBBCCCBBBBB", "BBBBCDCDCBBBB", "BBBCDCDDCBBB",
                                " BBBDBCBDBBB ", "  BBBBBBBBB  ", "E  BBBBBBB  E" },
                            { "E  BBBBBBB  E", "  BBBBBBBBB  ", " BBBBBBBBBBB ", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                " BBBBBBBBBBB ", "  BBBBBBBBB  ", "E  BBBBBBB  E" } }))
                .addShape(
                    STRUCTURE_PIECE_STACK,
                    transpose(
                        new String[][] {
                            { "EEEEBBBBBEEEE", "E EBBBBBBBE E", "EEB       BEE", "EB BBBBBBB BE", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", "EB BBBBBBB BE",
                                "EEB       BEE", "E EBBBBBBBE E", "EEEEBBBBBEEEE" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB DCCCCCD BB",
                                "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCDDDCD BB", "BB DCCCCCD BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E   BBBBB   E", "  EBBBBBBBE  ", " EB       BE ", " B BBBBBBB B ", "BB BCCCCCB BB",
                                "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCBBBCB BB", "BB BCCCCCB BB", " B BBBBBBB B ",
                                " EB       BE ", "  EBBBBBBBE  ", "E   BBBBB   E" },
                            { "E           E", "  E  BBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", "  BEEEEEEEB  ", "  BBEEEEEBB  ",
                                " E BBEEEBB E ", "  E  BBB  E  ", "E           E" } }))
                .addShape(
                    STRUCTURE_PIECE_CAP,
                    transpose(
                        new String[][] {
                            { "             ", "             ", "    CCBCC    ", "    BBCBB    ", "  CBBBCBBBC  ",
                                "  CBBBBBBBC  ", "  BCCBBBCCB  ", "  CBBBBBBBC  ", "  CBBBCBBBC  ", "    BBCBB    ",
                                "    CCBCC    ", "             ", "             " },
                            { "             ", "    BBBBB    ", "   BBEEEBB   ", "  BBEEEEEBB  ", " BBEEEEEEEBB ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", " BBEEEEEEEBB ", "  BBEEEEEBB  ",
                                "   BBEEEBB   ", "    BBBBB    ", "             " } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTEMegaSteamTurbineArray::onCasingAdded,
                            ofBlocksTiered(
                                MTEMegaSteamTurbineArray::getCasingTier,
                                getAllowedCasings(),
                                -1,
                                (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                                t -> t.mCasingTier)),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .atLeast(MegaSteamTurbineArrayHatchElement.PressureSteamInput)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .atLeast(MegaSteamTurbineArrayHatchElement.OverpressureInput)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .atLeast(MegaSteamTurbineArrayHatchElement.CoolingHatch)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(2)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputBus)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(Dynamo)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEMegaSteamTurbineArray::getPipeTier,
                            PIPE_CASINGS,
                            -1,
                            (t, tier) -> t.mPipeTier = tier,
                            t -> t.mPipeTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEMegaSteamTurbineArray::getGearTier,
                            GEAR_CASINGS,
                            -1,
                            (t, tier) -> t.mGearTier = tier,
                            t -> t.mGearTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEMegaSteamTurbineArray::getFrameTier,
                            getFrameCasings(),
                            -1,
                            (t, tier) -> t.mFrameTier = tier,
                            t -> t.mFrameTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    // 延迟初始化：避免在 MTE 构造时（sAfterGTPreload 遍历期间）触发 WerkstoffLoader 类加载，
    // 否则 Werkstoff 构造函数会向正在遍历的 sAfterGTPreload 列表添加 Runnable，导致
    // ConcurrentModificationException。WerkstoffLoader 在 bartworks preInit 中完成初始化后，
    // getStructureDefinition() 首次调用时才安全地引用 WerkstoffLoader.BWBlockCasings。
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> ALLOWED_CASINGS = null;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> getAllowedCasings() {
        if (ALLOWED_CASINGS == null) {
            ALLOWED_CASINGS = ImmutableList.of(
                Pair.of(GregTechAPI.sBlockCasings2, 0), // Tier 1 - Steel
                Pair.of(GregTechAPI.sBlockCasings1, 2), // Tier 2 - Stainless Steel
                Pair.of(GregTechAPI.sBlockCasings4, 1), // Tier 3 - Titanium
                Pair.of(GregTechAPI.sBlockCasings4, 2), // Tier 4 - Tungstensteel
                Pair.of(GregTechAPI.sBlockCasings4, 0), // Tier 5 - Chrome
                Pair.of(GregTechAPI.sBlockCasings8, 6), // Tier 6 - Advanced Rhodium Palladium
                Pair.of(GregTechAPI.sBlockCasings8, 7), // Tier 7 - Advanced Iridium
                Pair.of(GregTechAPI.sBlockCasings4, 14), // Tier 8 - Mining Osmiridium (UV)
                Pair.of(GregTechAPI.sBlockReinforced, 11), // Tier 9 - Reinforced Machine Casing (UHV)
                Pair.of(GregTechAPI.sBlockReinforced, 10), // Tier 10 - Naquadah Reinforced (UEV)
                Pair.of(GregTechAPI.sBlockCasings8, 3), // Tier 11 - Mining Black Plutonium (UIV)
                Pair.of(GregTechAPI.sBlockCasings8, 10)); // Tier 12 - Radiant Naquadah Alloy (UMV)
        }
        return ALLOWED_CASINGS;
    }

    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 3), Pair.of(GregTechAPI.sBlockCasings2, 4));

    // FRAME_CASINGS 与 ALLOWED_CASINGS 一样延迟初始化：等级 6 框架需要解析
    // WerkstoffLoader.RhodiumPlatedPalladium 材质并使用 BW 框架方块 bw.frames，
    // 不能在 MTE 类加载时触发 WerkstoffLoader 类加载。
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> FRAME_CASINGS = null;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<Pair<Block, Integer>> getFrameCasings() {
        if (FRAME_CASINGS == null) {
            FRAME_CASINGS = ImmutableList.of(
                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID), // 1
                Pair.of(GregTechAPI.sBlockFrames, Materials.Aluminium.mMetaItemSubID), // 2
                Pair.of(GregTechAPI.sBlockFrames, Materials.StainlessSteel.mMetaItemSubID), // 3
                Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID), // 4
                Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID), // 5
                Pair.of(getTier6FrameBlock(), getTier6FrameMeta()), // 6
                Pair.of(GregTechAPI.sBlockFrames, Materials.Iridium.mMetaItemSubID), // 7
                Pair.of(GregTechAPI.sBlockFrames, Materials.Osmium.mMetaItemSubID), // 8 - UV
                Pair.of(GregTechAPI.sBlockFrames, Materials.Neutronium.mMetaItemSubID), // 9 - UHV
                Pair.of(GregTechAPI.sBlockFrames, Materials.Bedrockium.mMetaItemSubID), // 10 - UEV
                Pair.of(GregTechAPI.sBlockFrames, 397), // 11 - Infinity (UIV)
                Pair.of(GregTechAPI.sBlockFrames, 588)); // 12 - UMV (SpaceTime)
        }
        return FRAME_CASINGS;
    }

    private static Block TIER6_FRAME_BLOCK;
    private static Integer TIER6_FRAME_META;

    private static Block getTier6FrameBlock() {
        if (TIER6_FRAME_BLOCK == null) {
            TIER6_FRAME_BLOCK = GregTechAPI.sBlockFramesBW;
            if (TIER6_FRAME_BLOCK == null) TIER6_FRAME_BLOCK = GameRegistry.findBlock("gregtech", "bw.frames");
        }
        return TIER6_FRAME_BLOCK;
    }

    private static int getTier6FrameMeta() {
        if (TIER6_FRAME_META == null) {
            Werkstoff werkstoff = WerkstoffLoader.RhodiumPlatedPalladium;
            TIER6_FRAME_META = werkstoff != null ? (int) werkstoff.getmID() : 88;
        }
        return TIER6_FRAME_META;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings1 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4) {
            if (meta == 1) return 3;
            if (meta == 2) return 4;
            if (meta == 0) return 5;
            if (meta == 14) return 8;
        }
        if (block == GregTechAPI.sBlockCasings8) {
            if (meta == 3) return 11; // Mining Black Plutonium (UIV)
            if (meta == 6) return 6;
            if (meta == 7) return 7;
            if (meta == 10) return 12; // Radiant Naquadah Alloy (UMV)
        }
        if (block == GregTechAPI.sBlockReinforced) {
            if (meta == 11) return 9; // Reinforced Machine Casing (UHV)
            if (meta == 10) return 10; // Naquadah Reinforced (UEV)
        }
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 14) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 4) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames) {
            if (meta == Materials.Steel.mMetaItemSubID) return 1;
            if (meta == Materials.Aluminium.mMetaItemSubID) return 2;
            if (meta == Materials.StainlessSteel.mMetaItemSubID) return 3;
            if (meta == Materials.Titanium.mMetaItemSubID) return 4;
            if (meta == Materials.TungstenSteel.mMetaItemSubID) return 5;
            if (meta == Materials.Iridium.mMetaItemSubID) return 7;
            if (meta == Materials.Osmium.mMetaItemSubID) return 8;
            if (meta == Materials.Neutronium.mMetaItemSubID) return 9;
            if (meta == Materials.Bedrockium.mMetaItemSubID) return 10;
            if (meta == 397) return 11; // Infinity (UIV)
            if (meta == 588) return 12; // SpaceTime
        }
        if (block == getTier6FrameBlock() && meta == getTier6FrameMeta()) return 6;
        return null;
    }

    private void onCasingAdded() {
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
        return GTValues.V[mCasingTier > 0 ? mCasingTier : 1];
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
     * 循环超限芯片：蒸汽效率因子按蒸汽家族内叠加，致密族只叠加致密族（致密蒸汽冷却产物本就是蒸馏水）。
     * 未激活（芯片未装或叠加层不足）或 NONE 时返回类型自身因子。
     * 因子值以 SteamType.steamEffFactor 字段为单一事实来源，不在此硬编码。
     */
    public float getEffectiveSteamEffFactor(SteamType type) {
        if (!isCycleOverlimitActive() || type == SteamType.NONE) {
            return type.steamEffFactor;
        }
        return switch (type) {
            case STEAM -> SteamType.STEAM.steamEffFactor;
            case SH_STEAM -> SteamType.SH_STEAM.steamEffFactor + SteamType.STEAM.steamEffFactor;
            case SC_STEAM -> SteamType.SC_STEAM.steamEffFactor + SteamType.SH_STEAM.steamEffFactor
                + SteamType.STEAM.steamEffFactor;
            case DENSE_STEAM -> SteamType.DENSE_STEAM.steamEffFactor;
            case DENSE_SH_STEAM -> SteamType.DENSE_SH_STEAM.steamEffFactor + SteamType.DENSE_STEAM.steamEffFactor;
            case DENSE_SC_STEAM -> SteamType.DENSE_SC_STEAM.steamEffFactor + SteamType.DENSE_SH_STEAM.steamEffFactor
                + SteamType.DENSE_STEAM.steamEffFactor;
            default -> type.steamEffFactor;
        };
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
            / type.euPerL);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> tFluids = getStoredFluids();
        if (tFluids.isEmpty() && mPressureSteamInputs.isEmpty() && mOverpressureInputs.isEmpty()) {
            mEUt = 0;
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

        for (SteamType type : STEAM_TYPE_PRIORITY) {
            if (!availableTypes.contains(type)) continue;
            // 基础 EU/t: 不含当前效率，由父类 onRunningTick 统一乘 mEfficiency/10000 后输出
            // 当前理论 EU/t = baseEUt × efficiency
            long base = (long) (voltage * mPowerParameter * groupCount * powerMult * getEffectiveSteamEffFactor(type));
            long eu = (long) (base * efficiency);
            long consumption = (long) (voltage * mPowerParameter
                * groupCount
                * powerMult
                * Math.max(0, 1 - savings)
                * getEffectiveSteamEffFactor(type)
                / type.euPerL);
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
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mSteamType = SteamType.NONE;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        // v1.10.8：饱和钳制到 int 范围——tier 11+（UIV/UMV）baseEUt 可达 13B+，
        // 原 (int) 强转溢出为负导致父类 onRunningTick 走耗电分支/输出异常。
        this.mTheoreticalEUt = (int) Math.min(Integer.MAX_VALUE, generatedEUt);
        this.mSteamConsumption = (int) Math.min(Integer.MAX_VALUE, steamConsumption);
        this.mSteamType = selectedType;

        depleteSteamByType(selectedType, mSteamConsumption);
        outputCoolingProduct(selectedType, mSteamConsumption);

        // mEUt 存储基础 EU/t，平滑插值也基于基础值（钳制防溢出）
        long baseClamped = Math.min(Integer.MAX_VALUE, baseEUt);
        int difference = (int) (baseClamped - mEUt);
        int maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mEUt = (int) baseClamped;
        }

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
        if (fs == null || fs.amount <= 0) return SteamType.NONE;
        if (isDenseSupercriticalSteamFluid(fs)) return SteamType.DENSE_SC_STEAM;
        if (isSupercriticalSteamFluid(fs)) return SteamType.SC_STEAM;
        if (isDenseSuperheatedSteamFluid(fs)) return SteamType.DENSE_SH_STEAM;
        if (isDenseSteamFluid(fs)) return SteamType.DENSE_STEAM;
        if (GTModHandler.isSuperHeatedSteam(fs)) return SteamType.SH_STEAM;
        if (GTModHandler.isAnySteam(fs)) return SteamType.STEAM;
        return SteamType.NONE;
    }

    private static boolean isDenseSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSteam.mGas;
    }

    private static boolean isDenseSuperheatedSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSuperheatedSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSuperheatedSteam.mGas;
    }

    private static boolean isSupercriticalSteamFluid(FluidStack fs) {
        if (fs == null) return false;
        Fluid scFluid = FluidRegistry.getFluid("supercriticalsteam");
        return scFluid != null && fs.getFluid() == scFluid;
    }

    private static boolean isDenseSupercriticalSteamFluid(FluidStack fs) {
        if (fs == null || Materials.DenseSupercriticalSteam.mGas == null) return false;
        return fs.getFluid() == Materials.DenseSupercriticalSteam.mGas;
    }

    // v1.10.61：求和 long 化——跨仓同型蒸汽累加可超 int max（2.147B L）溢出为负，
    // 与 checkProcessing 门控（consumption 为 long）配套，避免永久假 NO_FUEL。
    private long getTotalSteamAmount(SteamType type) {
        long total = 0;
        for (FluidStack fs : getStoredFluids()) {
            if (classifyFluid(fs) == type) total += fs.amount;
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) total += fs.amount;
        }
        for (MTEOverpressureTurbineInputHatch hatch : mOverpressureInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) total += fs.amount;
        }
        return total;
    }

    private boolean depleteSteamByType(SteamType type, int amount) {
        int remaining = amount;
        // v1.10.8：mInputHatches 段改用 GTSRHatchFluidAccess.depleteFluidAcross 跨仓按需取流
        // （原 getStoredFluids 聚合对 ME 仓按流体去重，多 ME 仓供汽欠计）
        for (FluidStack fs : getStoredFluids()) {
            if (classifyFluid(fs) == type) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    remaining -= GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(fs, canDrain));
                }
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && classifyFluid(fs) == type) {
                int canDrain = Math.min(fs.amount, remaining);
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
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.consumeSteam(canDrain);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private void outputCoolingProduct(SteamType type, int consumedAmount) {
        // 循环超限芯片：过热/超临界蒸汽不再输出冷却蒸汽或次级蒸汽，全部冷凝为蒸馏水（蒸汽循环回收）
        boolean overlimit = isCycleOverlimitActive();
        if (consumedAmount <= 0) return;
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
                    outputCoolingSteam(consumedAmount);
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
                    FluidStack denseSteam = Materials.DenseSteam.getGas(consumedAmount);
                    if (denseSteam != null) addOutput(denseSteam);
                }
                break;
            }
            case SC_STEAM: {
                // 循环超限：超临界蒸汽直接冷凝为蒸馏水（原输出 ic2superheatedsteam）
                if (overlimit) {
                    outputCoolingWater(condenseSteam(consumedAmount));
                } else {
                    FluidStack shSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", consumedAmount);
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
                    FluidStack denseSHSteam = Materials.DenseSuperheatedSteam.getGas(consumedAmount);
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
                + NumberFormatUtil.formatNumber(mTheoreticalEUt)
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
            .beginVariableStructureBlock(5, 5, 13, 13, 9, 25, true)
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
            .toolTipFinisher(
                EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                    + "GT-Steam-Reborn");
        return tt;
    }

    private int getCasingTextureIndex() {
        return CasingTierTextureHelper.getTextureIndex(mCasingTier, SOLID_STEEL_CASING_INDEX);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mCasingTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mCasingTier;
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
