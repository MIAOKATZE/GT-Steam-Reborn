package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
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
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTUtilityClient;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.misc.GTStructureChannels;
import tectech.thing.metaTileEntity.hatch.MTEHatchDynamoMulti;

public class MTEMegaSteamTurbineArray extends MTEEnhancedMultiBlockBase<MTEMegaSteamTurbineArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";

    private static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION;

    private int mCasingAmount = 0;
    private int mStackCount = 0;
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mGearTier = -1;
    private int mFrameTier = -1;
    private int excessWater = 0;

    private int mTheoreticalEUt = 0;
    private int mSteamConsumption = 0;

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    private final List<MTEOverpressureTurbineInputHatch> mOverpressureInputs = new ArrayList<>();
    private final List<MTESteamCoolingHatch> mSteamCoolingHatches = new ArrayList<>();
    private final List<MTEPressureSteamCoolingHatch> mPressureCoolingHatches = new ArrayList<>();
    private final ArrayList<MTEHatchDynamoMulti> eDynamoMulti = new ArrayList<>();
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
    }

    private enum SteamType {

        NONE(0, 0, 0, ""),
        STEAM(0.5f, 0.5f, 15000, "gtsr.gui.steam_type.normal"),
        DENSE_STEAM(0.5f, 500, 20000, "gtsr.gui.steam_type.dense"),
        SH_STEAM(1.0f, 1.0f, 20000, "gtsr.gui.steam_type.superheated"),
        DENSE_SH_STEAM(1.0f, 1000, 25000, "gtsr.gui.steam_type.dense_superheated"),
        SC_STEAM(1.0f, 1.0f, 25000, "gtsr.gui.steam_type.supercritical"),
        DENSE_SC_STEAM(1.0f, 1000, 30000, "gtsr.gui.steam_type.dense_supercritical");

        final float steamEffFactor;
        final float euPerL;
        final int maxEfficiency;
        final String nameKey;

        SteamType(float steamEffFactor, float euPerL, int maxEfficiency, String nameKey) {
            this.steamEffFactor = steamEffFactor;
            this.euPerL = euPerL;
            this.maxEfficiency = maxEfficiency;
            this.nameKey = nameKey;
        }

        boolean isDense() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == DENSE_SC_STEAM;
        }

        boolean requiresHighTier() {
            return this == DENSE_STEAM || this == DENSE_SH_STEAM || this == SC_STEAM || this == DENSE_SC_STEAM;
        }
    }

    private static final SteamType[] STEAM_TYPE_PRIORITY = { SteamType.DENSE_SC_STEAM, SteamType.SC_STEAM,
        SteamType.DENSE_SH_STEAM, SteamType.DENSE_STEAM, SteamType.SH_STEAM, SteamType.STEAM };

    private SteamType mSteamType = SteamType.NONE;

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

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.eu_t")
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(
                            (long) (getVoltage() * 8
                                * getGroupCount()
                                * (getMaxEfficiencyLimit(mSteamType) / 10000.0)
                                * mSteamType.steamEffFactor)))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.steam")
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(calcSteamConsumption(mSteamType))
                        + " L/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.turbine_array.savings")
                        + EnumChatFormatting.GREEN
                        + String.format(
                            "%.0f%%",
                            (0.05 * mStackCount + (mGearTier > 1 ? 0.025 : 0)
                                + (mPipeTier == 2 ? 0.025 : mPipeTier == 3 ? 0.075 : 0)) * 100))
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
                        + GTUtility.formatNumbers(Math.abs(mEUt))
                        + " EU/t")
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
                        onElementPass(
                            MTEMegaSteamTurbineArray::onCasingAdded,
                            ofBlocksTiered(
                                MTEMegaSteamTurbineArray::getCasingTier,
                                ALLOWED_CASINGS,
                                -1,
                                (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                                t -> t.mCasingTier)),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .adder(MTEMegaSteamTurbineArray::addPressureSteamToMachineList)
                            .hatchClass(MTEHatchPressureSteamInput.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .adder(MTEMegaSteamTurbineArray::addOverpressureInputToMachineList)
                            .hatchClass(MTEOverpressureTurbineInputHatch.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .adder(MTEMegaSteamTurbineArray::addSteamCoolingToMachineList)
                            .hatchClass(MTESteamCoolingHatch.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .adder(MTEMegaSteamTurbineArray::addPressureCoolingToMachineList)
                            .hatchClass(MTEPressureSteamCoolingHatch.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(Dynamo)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
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
                            FRAME_CASINGS,
                            -1,
                            (t, tier) -> t.mFrameTier = tier,
                            t -> t.mFrameTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> ALLOWED_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 0), // Tier 1 - Steel
        Pair.of(GregTechAPI.sBlockCasings1, 2), // Tier 2 - Stainless Steel
        Pair.of(GregTechAPI.sBlockCasings4, 1), // Tier 3 - Titanium
        Pair.of(GregTechAPI.sBlockCasings4, 2), // Tier 4 - Tungstensteel
        Pair.of(GregTechAPI.sBlockCasings4, 0), // Tier 5 - Chrome
        Pair.of(GregTechAPI.sBlockCasings8, 6), // Tier 6 - Advanced Rhodium Palladium
        Pair.of(GregTechAPI.sBlockCasings8, 7), // Tier 7 - Advanced Iridium
        Pair.of(GregTechAPI.sBlockCasings4, 14), // Tier 8 - Mining Osmiridium (UV)
        Pair.of(WerkstoffLoader.BWBlockCasings, 31895), // Tier 9 - Bolted Neutronium (UHV)
        Pair.of(GregTechAPI.sBlockReinforced, 10), // Tier 10 - Naquadah Reinforced (UEV)
        Pair.of(WerkstoffLoader.BWBlockCasings, 32091), // Tier 11 - Bolted Naquadah Alloy (UIV)
        Pair.of(GregTechAPI.sBlockCasings8, 10)); // Tier 12 - Radiant Naquadah Alloy (UMV)

    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 3), Pair.of(GregTechAPI.sBlockCasings2, 4));

    private static final List<Pair<Block, Integer>> FRAME_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID), // 1
        Pair.of(GregTechAPI.sBlockFrames, Materials.Aluminium.mMetaItemSubID), // 2
        Pair.of(GregTechAPI.sBlockFrames, Materials.StainlessSteel.mMetaItemSubID), // 3
        Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID), // 4
        Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID), // 5
        Pair.of(GregTechAPI.sBlockFrames, Materials.Palladium.mMetaItemSubID), // 6
        Pair.of(GregTechAPI.sBlockFrames, Materials.Iridium.mMetaItemSubID), // 7
        Pair.of(GregTechAPI.sBlockFrames, Materials.Osmium.mMetaItemSubID), // 8 - UV
        Pair.of(GregTechAPI.sBlockFrames, Materials.Neutronium.mMetaItemSubID), // 9 - UHV
        Pair.of(GregTechAPI.sBlockFrames, Materials.Bedrockium.mMetaItemSubID), // 10 - UEV
        Pair.of(GregTechAPI.sBlockFrames, Materials.BlackPlutonium.mMetaItemSubID), // 11 - UIV
        Pair.of(GregTechAPI.sBlockFrames, 588)); // 12 - UMV (SpaceTime)

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
            if (meta == 6) return 6;
            if (meta == 7) return 7;
            if (meta == 10) return 12; // Radiant Naquadah Alloy (UMV)
        }
        if (block == WerkstoffLoader.BWBlockCasings) {
            if (meta == 31895) return 9; // Bolted Neutronium (UHV)
            if (meta == 32091) return 11; // Bolted Naquadah Alloy (UIV)
        }
        if (block == GregTechAPI.sBlockReinforced) {
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
            if (meta == Materials.Palladium.mMetaItemSubID) return 6;
            if (meta == Materials.Iridium.mMetaItemSubID) return 7;
            if (meta == Materials.Osmium.mMetaItemSubID) return 8;
            if (meta == Materials.Neutronium.mMetaItemSubID) return 9;
            if (meta == Materials.Bedrockium.mMetaItemSubID) return 10;
            if (meta == Materials.BlackPlutonium.mMetaItemSubID) return 11;
            if (meta == 588) return 12; // SpaceTime
        }
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

    private boolean addSteamCoolingToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTESteamCoolingHatch hatch && !(mte instanceof MTEPressureSteamCoolingHatch)) {
            hatch.updateTexture(aBaseCasingIndex);
            mSteamCoolingHatches.add(hatch);
            return true;
        }
        return false;
    }

    private boolean addPressureCoolingToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mPressureCoolingHatches.add(hatch);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
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

        if (!checkPiece(STRUCTURE_PIECE_BASE, 6, 5, 0)) return false;

        for (int i = 0; i < 4; i++) {
            int bOffset = 9 + i * 4;
            if (!checkPiece(STRUCTURE_PIECE_STACK, 6, bOffset, 0)) break;
            mStackCount++;
        }

        if (mCasingTier <= 0 || mCasingTier > 12) return false;

        int capB = 7 + mStackCount * 4;
        if (!checkPiece(STRUCTURE_PIECE_CAP, 6, capB, 0)) return false;

        boolean hasInput = !mInputHatches.isEmpty() || hasPressureSteamHatch();
        boolean hasOutput = !mOutputHatches.isEmpty() || hasSteamCoolingHatch() || hasPressureCoolingHatch();
        if (!hasInput || !hasOutput || (mDynamoHatches.isEmpty() && eDynamoMulti.isEmpty())) return false;

        updateAllHatchTextures();
        return true;
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
    private int getGroupCount() {
        return mStackCount + 1;
    }

    /**
     * 最大效率上限 (含所有加成，加算).
     * 基准: 10000 = 100%效率，1% = 100
     * - 每额外组+10%效率上限 = +1000
     * - 高级Gear额外+5%效率上限 = +500
     * - 钛管+10%效率上限 = +1000，钨钢管+25%效率上限 = +2500
     * - 机器等级每提高一级(等级-1)，增加5%效率上限 = +500
     */
    private int getMaxEfficiencyLimit(SteamType type) {
        int base = type.maxEfficiency;
        // 效率上限加成（加算）：每额外组+1000，高级Gear+500，钛管+1000，钨钢管+2500，机器等级每级+500
        int bonus = 1000 * mStackCount + (mGearTier > 1 ? 500 : 0)
            + (mPipeTier == 2 ? 1000 : mPipeTier == 3 ? 2500 : 0)
            + 500 * (mCasingTier - 1);
        return base + bonus;
    }

    private long getVoltage() {
        return GTValues.V[mCasingTier > 0 ? mCasingTier : 1];
    }

    private float getCustomEfficiency() {
        int eff = mEfficiency;
        if (eff <= 0) return 0.0f;
        return eff / 10000.0f;
    }

    private long calcSteamConsumption(SteamType type) {
        if (type == SteamType.NONE) return 0;
        int groupCount = getGroupCount();
        long voltage = getVoltage();
        // 蒸汽节省: 每额外组+5%，高级Gear额外+2.5%，钛管+2.5%，钨钢管+7.5%
        float savings = 0.05f * mStackCount + (mGearTier > 1 ? 0.025f : 0f)
            + (mPipeTier == 2 ? 0.025f : mPipeTier == 3 ? 0.075f : 0f);
        return (long) (voltage * 8 * groupCount * Math.max(0, 1 - savings) * type.steamEffFactor / type.euPerL);
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
        // 蒸汽节省: 每额外组+5%，高级Gear额外+2.5%，钛管+2.5%，钨钢管+7.5%
        float savings = 0.05f * mStackCount + (mGearTier > 1 ? 0.025f : 0f)
            + (mPipeTier == 2 ? 0.025f : mPipeTier == 3 ? 0.075f : 0f);

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
        long generatedEUt = 0;
        long steamConsumption = 0;

        for (SteamType type : STEAM_TYPE_PRIORITY) {
            if (!availableTypes.contains(type)) continue;
            // 发电公式: EU/t = V[tier] × 8 × steamEffFactor × n × efficiency
            // n = groupCount
            long eu = (long) (voltage * 8 * groupCount * efficiency * type.steamEffFactor);
            long consumption = (long) (voltage * 8
                * groupCount
                * Math.max(0, 1 - savings)
                * type.steamEffFactor
                / type.euPerL);
            int totalAvailable = getTotalSteamAmount(type);
            if (totalAvailable >= consumption) {
                selectedType = type;
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

        this.mTheoreticalEUt = (int) generatedEUt;
        this.mSteamConsumption = (int) steamConsumption;
        this.mSteamType = selectedType;

        depleteSteamByType(selectedType, (int) steamConsumption);
        outputCoolingProduct(selectedType, (int) steamConsumption);

        int difference = (int) (generatedEUt - mEUt);
        int maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mEUt = (int) generatedEUt;
        }

        mMaxProgresstime = 1;
        int maxEff = getMaxEfficiencyLimit(selectedType);
        if (mEfficiency < 10000) {
            mEfficiencyIncrease = 10;
        } else if (mEfficiency < maxEff) {
            mEfficiencyIncrease = 1;
        } else {
            mEfficiencyIncrease = 0;
        }

        return CheckRecipeResultRegistry.GENERATING;
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
        // Try standard dynamos first
        if (!mDynamoHatches.isEmpty()) {
            if (super.addEnergyOutputMultipleDynamos(aEU, aAllowMixedVoltageDynamos)) {
                return true;
            }
        }
        // Try exotic (multi-amp) dynamos
        if (!eDynamoMulti.isEmpty()) {
            for (MTEHatchDynamoMulti tHatch : GTUtility.validMTEList(eDynamoMulti)) {
                if (tHatch.maxEUOutput() * tHatch.maxAmperesOut() >= aEU) {
                    tHatch.setEUVar(
                        Math.min(
                            tHatch.maxEUStore(),
                            tHatch.getBaseMetaTileEntity()
                                .getStoredEU() + aEU));
                    return true;
                }
            }
        }
        return false;
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

    private int getTotalSteamAmount(SteamType type) {
        int total = 0;
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
        for (FluidStack fs : getStoredFluids()) {
            if (classifyFluid(fs) == type) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    depleteInput(new FluidStack(fs, canDrain));
                    remaining -= canDrain;
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
        if (consumedAmount <= 0) return;
        switch (type) {
            case STEAM: {
                int waterOutput = condenseSteam(consumedAmount);
                outputCoolingWater(waterOutput);
                break;
            }
            case DENSE_STEAM: {
                int equivalentSteam = consumedAmount * 1000;
                int waterOutput = condenseSteam(equivalentSteam);
                outputCoolingWater(waterOutput);
                break;
            }
            case SH_STEAM: {
                outputCoolingSteam(consumedAmount);
                break;
            }
            case DENSE_SH_STEAM: {
                FluidStack denseSteam = Materials.DenseSteam.getGas(consumedAmount);
                if (denseSteam != null) addOutput(denseSteam);
                break;
            }
            case SC_STEAM: {
                FluidStack shSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", consumedAmount);
                if (shSteam != null) addOutput(shSteam);
                break;
            }
            case DENSE_SC_STEAM: {
                FluidStack denseSHSteam = Materials.DenseSuperheatedSteam.getGas(consumedAmount);
                if (denseSHSteam != null) addOutput(denseSHSteam);
                break;
            }
            default:
                break;
        }
    }

    private int condenseSteam(int steam) {
        excessWater += steam;
        int water = excessWater / GTValues.STEAM_PER_WATER;
        excessWater %= GTValues.STEAM_PER_WATER;
        return water;
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
                + GTUtility.formatNumbers(mTheoreticalEUt)
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
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        excessWater = aNBT.getInteger("excessWater");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mTheoreticalEUt = aNBT.getInteger("mTheoreticalEUt");
        mSteamConsumption = aNBT.getInteger("mSteamConsumption");
        mEfficiency = aNBT.getInteger("mEfficiency");
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
            || addSteamCoolingToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureCoolingToMachineList(tTileEntity, aBaseCasingIndex)
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
        int tTotalHeight = Math.max(9, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 9, 25));
        int extraStacks = (tTotalHeight - 9) / 4;
        for (int i = 0; i < extraStacks; i++) {
            int bOffset = 9 + i * 4;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, 6, bOffset, 0);
        }
        int capB = 7 + extraStacks * 4;
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, 6, capB, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE, stackSize, 6, 5, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(9, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 9, 25));
        int extraStacks = (tTotalHeight - 9) / 4;
        for (int i = 0; i < extraStacks; i++) {
            int bOffset = 9 + i * 4;
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
        int capB = 7 + extraStacks * 4;
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
        return 30000;
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
            EnumChatFormatting.GRAY + "Extra Stacks: 0 ~ 4 (9~25 total height)" };
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
            .addSeparator()
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.formula"))
            .beginStructureBlock(5, 6, 5, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.input_hatch"), 1)
            .addDynamoHatch(StatCollector.translateToLocal("gtsr.tooltip.turbine_array.dynamo"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.turbine_array.multi_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 38, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 8, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 8, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 12, false)
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
                EnumChatFormatting.AQUA + "GT"
                    + EnumChatFormatting.GREEN
                    + "-"
                    + EnumChatFormatting.GOLD
                    + "Steam"
                    + EnumChatFormatting.RED
                    + "-"
                    + EnumChatFormatting.BLUE
                    + "Reborn");
        return tt;
    }

    private int getCasingTextureIndex() {
        switch (mCasingTier) {
            case 1:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            case 2:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 2);
            case 3:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 1);
            case 4:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2);
            case 5:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
            case 6:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
            case 7:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 7);
            case 8:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 14);
            case 9:
                return GTUtility.getCasingTextureIndex(WerkstoffLoader.BWBlockCasings, 31895);
            case 10:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockReinforced, 10);
            case 11:
                return GTUtility.getCasingTextureIndex(WerkstoffLoader.BWBlockCasings, 32091);
            case 12:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 10);
            default:
                return SOLID_STEEL_CASING_INDEX;
        }
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
