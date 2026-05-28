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
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
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
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
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
    private final List<MTESteamCoolingHatch> mSteamCoolingHatches = new ArrayList<>();
    private final List<MTEPressureSteamCoolingHatch> mPressureCoolingHatches = new ArrayList<>();
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
    }

    private static final int MAX_EFFICIENCY_STEAM = 15000;
    private static final int MAX_EFFICIENCY_HP_STEAM = 20000;

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mCasingTier, val -> mCasingTier = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mTheoreticalEUt, val -> mTheoreticalEUt = val));
        screenElements
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSteamConsumption, val -> mSteamConsumption = val));

        screenElements.widget(
            TextWidget.dynamicString(
                () -> EnumChatFormatting.GOLD + "EU/t: "
                    + EnumChatFormatting.AQUA
                    + GTUtility.formatNumbers(
                        (long) (getVoltage() * 4 * (getStackLayers() + 1) * (getMaxEfficiencyLimit(false) / 10000.0)))
                    + EnumChatFormatting.GRAY
                    + " (HP: "
                    + EnumChatFormatting.RED
                    + GTUtility.formatNumbers(
                        (long) (getVoltage() * 8 * (getStackLayers() + 1) * (getMaxEfficiencyLimit(true) / 10000.0)))
                    + EnumChatFormatting.GRAY
                    + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Steam: "
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(calcSteamConsumption(false))
                        + " L/t"
                        + EnumChatFormatting.GRAY
                        + " (HP: "
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(calcSteamConsumption(true))
                        + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Savings: "
                        + EnumChatFormatting.GREEN
                        + String.format("%.0f%%", (0.05 * getStackLayers() + (mGearTier > 1 ? 0.05 : 0)) * 100))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Stacks: "
                        + EnumChatFormatting.AQUA
                        + (1 + mStackCount)
                        + " group(s)"
                        + EnumChatFormatting.GRAY
                        + " ("
                        + (mStackCount == 0 ? "baseline" : "+" + mStackCount + " extra")
                        + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Eff: "
                        + (mEfficiency >= getMaxEfficiencyLimit(true) ? EnumChatFormatting.LIGHT_PURPLE
                            : mEfficiency >= 10000 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                        + String.format("%.1f%%", mEfficiency / 100.0)
                        + (mEfficiency >= getMaxEfficiencyLimit(true) ? " MAX" : ""))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Output: "
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
                            { "E           E", "  E BBBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
                                " BEEEEEEEEEB ", " BEEEEDEEEEB ", " BEEEEEEEEEB ", "  BEEEEEEEB  ", "  BBEEEEEBB  ",
                                " E BBEEEBB E ", "  E  BBB  E  ", "E           E" },
                            { "EEEBBBBBBBEEE", "E BBBBBBBBB E", "EBB       BBE", "BB         BB", "BB         BB",
                                "BB         BB", "BB    D    BB", "BB         BB", "BB         BB", "BB         BB",
                                "EBB       BBE", "E BBBBBBBBB E", "EEEBBBBBBEE E" },
                            { "E  BBB~BBB  E", "  BBBBCBBBB  ", " BBBDBCBDBBB ", "BBBCDCDDCBBB", "BBBBCDCDCBBBB",
                                "BBBBBCCCBBBBB", "BBCCCCDCCCCBB", "BBBBBCCCBBBBB", "BBBBCDCDCBBBB", "BBBCDCDDCBBB",
                                " BBBDBCBDBBB ", "  BBBBBBBBB  ", "E  BBDDBB   E" },
                            { "E  BBBBBBB  E", "  BBBBBBBBB  ", " BBBBBBBBBBB ", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB", "BBBBBBBBBBBBB",
                                " BBBBBBBBBBB ", "  BBBBBBBBB  ", "E  BBBBBB   E" } }))
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
                            { "E           E", "  E BBBB  E  ", " E BBEEEBB E ", "  BBEEEEEBB  ", "  BEEEEEEEB  ",
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
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings1, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 1),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0),
        Pair.of(GregTechAPI.sBlockCasings8, 6),
        Pair.of(GregTechAPI.sBlockCasings8, 7));

    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings2, 14),
        Pair.of(GregTechAPI.sBlockCasings2, 15));

    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 3), Pair.of(GregTechAPI.sBlockCasings2, 4));

    private static final List<Pair<Block, Integer>> FRAME_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Aluminium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.StainlessSteel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Palladium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Iridium.mMetaItemSubID));

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings1 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4) {
            if (meta == 1) return 3;
            if (meta == 2) return 4;
            if (meta == 0) return 5;
        }
        if (block == GregTechAPI.sBlockCasings8) {
            if (meta == 6) return 6;
            if (meta == 7) return 7;
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

    private boolean hasPressureSteamHatch() {
        return !mPressureSteamInputs.isEmpty();
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
        mSteamCoolingHatches.clear();
        mPressureCoolingHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE, 6, 0, 5)) return false;

        for (int i = 0; i < 2; i++) {
            int cOffset = 9 + i * 4;
            if (!checkPiece(STRUCTURE_PIECE_STACK, 6, 0, cOffset)) break;
            mStackCount++;
        }

        if (mCasingTier <= 0 || mCasingTier > 7) return false;

        int capC = 7 + mStackCount * 4;
        if (!checkPiece(STRUCTURE_PIECE_CAP, 6, 0, capC)) return false;

        boolean hasInput = !mInputHatches.isEmpty() || hasPressureSteamHatch();
        boolean hasOutput = !mOutputHatches.isEmpty() || hasSteamCoolingHatch() || hasPressureCoolingHatch();
        if (!hasInput || !hasOutput || mDynamoHatches.isEmpty()) return false;

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
     * 最大效率上限 (含管道升级奖励).
     * mPipeTier: 1=钢(无奖励), 2=钛(+10%), 3=钨钢(+20%)
     */
    private int getMaxEfficiencyLimit(boolean isHP) {
        int base = isHP ? MAX_EFFICIENCY_HP_STEAM : MAX_EFFICIENCY_STEAM;
        if (mPipeTier > 1) {
            float pipeBonus = 0.1f * (mPipeTier - 1);
            return (int) (base * (1f + pipeBonus));
        }
        return base;
    }

    private long getVoltage() {
        return GTValues.V[mCasingTier > 0 ? mCasingTier : 1];
    }

    private float getCustomEfficiency() {
        int eff = mEfficiency;
        if (eff <= 0) return 0.0f;
        return eff / 10000.0f;
    }

    private long calcSteamConsumption(boolean isHP) {
        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float savings = 0.05f * stackLayers + (mGearTier > 1 ? 0.05f : 0f);
        if (isHP) {
            long baseEU = voltage * 8 * n;
            return (long) (baseEU * Math.max(0, 1 - savings));
        } else {
            long baseEU = voltage * 4 * n;
            return (long) (baseEU * Math.max(0, 1 - savings) / 0.5f);
        }
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> tFluids = getStoredFluids();
        if (tFluids.isEmpty() && mPressureSteamInputs.isEmpty()) {
            mEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float efficiency = getCustomEfficiency();
        float savings = 0.05f * stackLayers + (mGearTier > 1 ? 0.05f : 0f);

        boolean hasSuperheated = false;
        boolean hasNormalSteam = false;
        for (FluidStack fs : tFluids) {
            if (GTModHandler.isSuperHeatedSteam(fs)) hasSuperheated = true;
            else if (GTModHandler.isAnySteam(fs)) hasNormalSteam = true;
        }

        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.amount > 0) {
                if (GTModHandler.isSuperHeatedSteam(fs)) hasSuperheated = true;
                else if (GTModHandler.isAnySteam(fs)) hasNormalSteam = true;
            }
        }

        long generatedEUt;
        long steamConsumption;

        if (hasSuperheated) {
            generatedEUt = (long) (voltage * 8 * n * efficiency);
            steamConsumption = (long) (voltage * 8 * n * Math.max(0, 1 - savings));
            FluidStack superheated = getSuperheatedSteam();
            if (superheated == null || superheated.amount < steamConsumption) {
                if (hasNormalSteam) {
                    generatedEUt = (long) (voltage * 4 * n * efficiency);
                    long steamBaseEU = (long) (voltage * 4 * n * Math.max(0, 1 - savings));
                    steamConsumption = (long) (steamBaseEU / 0.5f);
                    this.mTheoreticalEUt = (int) generatedEUt;
                    this.mSteamConsumption = (int) steamConsumption;
                    depleteSteam((int) steamConsumption);
                    int waterOutput = condenseSteam((int) steamConsumption);
                    outputCoolingWater(waterOutput);
                } else {
                    mEUt = 0;
                    mTheoreticalEUt = 0;
                    mSteamConsumption = 0;
                    mEfficiency = Math.max(0, mEfficiency - 500);
                    return CheckRecipeResultRegistry.NO_FUEL_FOUND;
                }
            } else {
                this.mTheoreticalEUt = (int) generatedEUt;
                this.mSteamConsumption = (int) steamConsumption;
                depleteSuperheatedSteam((int) steamConsumption);
                outputCoolingSteam((int) steamConsumption);
            }
        } else if (hasNormalSteam) {
            generatedEUt = (long) (voltage * 4 * n * efficiency);
            long steamBaseEU = (long) (voltage * 4 * n * Math.max(0, 1 - savings));
            steamConsumption = (long) (steamBaseEU / 0.5f);
            this.mTheoreticalEUt = (int) generatedEUt;
            this.mSteamConsumption = (int) steamConsumption;
            depleteSteam((int) steamConsumption);
            int waterOutput = condenseSteam((int) steamConsumption);
            outputCoolingWater(waterOutput);
        } else {
            mEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int difference = (int) (generatedEUt - mEUt);
        int maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mEUt = (int) generatedEUt;
        }

        mMaxProgresstime = 1;
        int maxEff = getMaxEfficiencyLimit(hasSuperheated);
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
        return aTotal;
    }

    private FluidStack getSuperheatedSteam() {
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isSuperHeatedSteam(fs)) {
                return fs;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs) && fs.amount > 0) {
                return fs;
            }
        }
        return null;
    }

    private boolean depleteSteam(int amount) {
        int remaining = amount;
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) {
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
            if (fs != null && GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.drain(canDrain, true);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private boolean depleteSuperheatedSteam(int amount) {
        int remaining = amount;
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isSuperHeatedSteam(fs)) {
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
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.drain(canDrain, true);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
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
        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float eff = getCustomEfficiency();
        float savings = 0.05f * stackLayers + (mGearTier > 1 ? 0.05f : 0f);

        long maxEUt = (long) (voltage * 4 * n * (getMaxEfficiencyLimit(false) / 10000.0));
        long maxHPEUt = (long) (voltage * 8 * n * (getMaxEfficiencyLimit(true) / 10000.0));
        long steamCons = calcSteamConsumption(false);
        long hpSteamCons = calcSteamConsumption(true);

        return new String[] {
            EnumChatFormatting.GOLD + "EU/t: "
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(maxEUt)
                + EnumChatFormatting.GRAY
                + " (HP: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(maxHPEUt)
                + ")",
            EnumChatFormatting.GOLD + "Steam: "
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(steamCons)
                + " L/t"
                + EnumChatFormatting.GRAY
                + " (HP: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(hpSteamCons)
                + ")",
            EnumChatFormatting.GOLD + "Savings: " + EnumChatFormatting.GREEN + String.format("%.0f%%", savings * 100),
            EnumChatFormatting.GOLD + "Efficiency: "
                + (eff >= 2.0f ? EnumChatFormatting.LIGHT_PURPLE
                    : eff >= 1.0f ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                + String.format("%.1f%%", eff * 100)
                + (eff >= 2.0f ? " MAX" : "") };
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
            || addSteamCoolingToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureCoolingToMachineList(tTileEntity, aBaseCasingIndex)
            || addInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addOutputToMachineList(tTileEntity, aBaseCasingIndex)
            || addDynamoToMachineList(tTileEntity, aBaseCasingIndex);
    }

    private void updateAllHatchTextures() {
        int textureIndex = getCasingTextureIndex();
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
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
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, 6, 0, 5);
        int tTotalHeight = Math.max(9, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 9, 17));
        int extraStacks = (tTotalHeight - 9) / 4;
        for (int i = 0; i < extraStacks; i++) {
            int cOffset = 9 + i * 4;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, 6, 0, cOffset);
        }
        int capC = 7 + extraStacks * 4;
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, 6, 0, capC);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE, stackSize, 6, 0, 5, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(9, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 9, 17));
        int extraStacks = (tTotalHeight - 9) / 4;
        for (int i = 0; i < extraStacks; i++) {
            int cOffset = 9 + i * 4;
            built = survivalBuildPiece(
                STRUCTURE_PIECE_STACK,
                stackSize,
                6,
                0,
                cOffset,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        int capC = 7 + extraStacks * 4;
        return survivalBuildPiece(STRUCTURE_PIECE_CAP, stackSize, 6, 0, capC, elementBudget, env, false, true);
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
        return 20000;
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
            EnumChatFormatting.GRAY + "Extra Stacks: 0 ~ 2 (9~17 total height)" };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info2"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info3"))
            .addInfo(EnumChatFormatting.AQUA + "Titanium Pipe: Max Efficiency +10%")
            .addInfo(EnumChatFormatting.AQUA + "Tungstensteel Pipe: Max Efficiency +20%")
            .addInfo(EnumChatFormatting.AQUA + "Titanium Gearbox: Steam Savings +5%")
            .addInfo(EnumChatFormatting.GRAY + "Stack Groups: 0~2 additional (each +4 layers)")
            .beginStructureBlock(13, 9, 13, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.controller"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.input"), 1)
            .addDynamoHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.dynamo"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.output"), 1)
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.cooling_hatch"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.pressure_cooling_hatch"))
            .toolTipFinisher();
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
