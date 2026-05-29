package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Iterator;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.util.UndergroundOilHelper;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.UndergroundOil;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTEVeinSteamPyrolyzer extends MTESteamMultiBase<MTEVeinSteamPyrolyzer> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 5;
    private static final int DEPTH_OFF_SET = 1;

    private static IStructureDefinition<MTEVeinSteamPyrolyzer> STRUCTURE_DEFINITION = null;

    private Fluid mLockedFluid = null;
    private final ArrayList<ChunkCoordIntPair> mVeinChunks = new ArrayList<>();
    private int mChunkRange = -1;

    protected int mCountCasing = 0;
    protected int mSetTier = -1;

    private boolean mApplyFluidIncrease = false;
    private String mLockedFluidName = "";
    private int mChipRangeBonus = 0;

    public MTEVeinSteamPyrolyzer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEVeinSteamPyrolyzer(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEVeinSteamPyrolyzer(mName);
    }

    @Override
    public String getMachineType() {
        return "Vein Steam Pyrolyzer";
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        return null;
    }

    @Nullable
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3) {
            if (meta == 13) return 1;
            if (meta == 14) return 2;
        }
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mSetTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mSetTier;
    }

    @Override
    public IStructureDefinition<MTEVeinSteamPyrolyzer> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEVeinSteamPyrolyzer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "       ", "       ", "       ", "   E   ", "       ", "       ", "       " },
                            { "       ", "       ", "       ", "   E   ", "       ", "       ", "       " },
                            { "       ", "       ", "   E   ", "  EDE  ", "   E   ", "       ", "       " },
                            { "       ", "       ", "   E   ", "  EDE  ", "   E   ", "       ", "       " },
                            { "       ", "       ", "   D   ", "  DDD  ", "   D   ", "       ", "       " },
                            { "       ", "   ~   ", "  DDD  ", " CDDDC ", "  DDD  ", "   C   ", "       " },
                            { "   B   ", "  DBD  ", " DDDDD ", "BBDDDBB", " DDDDD ", "  DBD  ", "   B   " } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTEVeinSteamPyrolyzer.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEVeinSteamPyrolyzer.class).atLeast(gregtech.api.enums.HatchElement.OutputBus)
                            .casingIndex(bronzeCasingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEVeinSteamPyrolyzer::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEVeinSteamPyrolyzer::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTEVeinSteamPyrolyzer t, Integer tier) -> t.mSetTier = tier,
                                        (MTEVeinSteamPyrolyzer t) -> t.mSetTier)))))
                .addElement(
                    'C',
                    onElementPass(
                        MTEVeinSteamPyrolyzer::onCasingAdded,
                        ofBlocksTiered(
                            MTEVeinSteamPyrolyzer::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTEVeinSteamPyrolyzer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEVeinSteamPyrolyzer t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEVeinSteamPyrolyzer::onCasingAdded,
                        ofBlocksTiered(
                            MTEVeinSteamPyrolyzer::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTEVeinSteamPyrolyzer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEVeinSteamPyrolyzer t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEVeinSteamPyrolyzer::onCasingAdded,
                        ofBlocksTiered(
                            MTEVeinSteamPyrolyzer::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTEVeinSteamPyrolyzer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEVeinSteamPyrolyzer t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCountCasing++;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCountCasing = 0;
        mSetTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (mSetTier <= 0) return false;
        if (mSteamInputFluids.size() < 1) return false;

        updateHatchTexture();
        mChipRangeBonus = getChipRangeBonus();
        return true;
    }

    private int getChipRangeBonus() {
        ItemStack controllerStack = getControllerSlot();
        if (controllerStack == null) return 0;
        if (GTSRItemList.VeinPyrolyzerChipT3.isStackEqual(controllerStack, true, true)) return 7;
        if (GTSRItemList.VeinPyrolyzerChipT2.isStackEqual(controllerStack, true, true)) return 3;
        if (GTSRItemList.VeinPyrolyzerChipT1.isStackEqual(controllerStack, true, true)) return 1;
        return 0;
    }

    @Override
    public void stopMachine(ShutDownReason aReason) {
        mApplyFluidIncrease = false;
        super.stopMachine(aReason);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity baseTE = getBaseMetaTileEntity();
        if (baseTE.getWorld().isRemote) return CheckRecipeResultRegistry.NO_RECIPE;

        int controllerChunkX = baseTE.getXCoord() >> 4;
        int controllerChunkZ = baseTE.getZCoord() >> 4;

        if (mLockedFluid == null) {
            FluidStack undergroundFluid = UndergroundOil.undergroundOilReadInformation(baseTE);
            if (undergroundFluid == null || undergroundFluid.getFluid() == null) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            mLockedFluid = undergroundFluid.getFluid();
            mLockedFluidName = mLockedFluid.getName();
        }

        int baseRange = 1;
        int chipBonus = getChipRangeBonus();
        mChipRangeBonus = chipBonus;
        int currentRange = baseRange + chipBonus;

        if (mChunkRange != currentRange) {
            mChunkRange = currentRange;
            mVeinChunks.clear();
        }

        if (mVeinChunks.isEmpty()) {
            FluidStack tOil = new FluidStack(mLockedFluid, 0);
            int xStart = Math.floorDiv(controllerChunkX, currentRange) * currentRange;
            int zStart = Math.floorDiv(controllerChunkZ, currentRange) * currentRange;
            for (int i = 0; i < currentRange; i++) {
                for (int j = 0; j < currentRange; j++) {
                    FluidStack tFluid = UndergroundOil.undergroundOil(baseTE.getWorld(), xStart + i, zStart + j, -1);
                    if (tFluid != null && tOil.isFluidEqual(tFluid) && tFluid.amount > 0) {
                        mVeinChunks.add(new ChunkCoordIntPair(xStart + i, zStart + j));
                    }
                }
            }
            if (mVeinChunks.isEmpty()) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }

        if (mApplyFluidIncrease) {
            mApplyFluidIncrease = false;
            int increasePerOp = mSetTier == 2 ? 25000 : 10000;
            int totalIncreased = 0;
            for (Iterator<ChunkCoordIntPair> it = mVeinChunks.iterator(); it.hasNext();) {
                ChunkCoordIntPair chunk = it.next();
                int actual = UndergroundOilHelper.increaseFluidAmount(
                    baseTE.getWorld(),
                    chunk.chunkXPos,
                    chunk.chunkZPos,
                    increasePerOp,
                    Integer.MAX_VALUE);
                totalIncreased += actual;
            }
            if (totalIncreased <= 0) {
                mVeinChunks.clear();
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
        }

        int steamPerTick = mSetTier == 2 ? 200 : 50;
        int workTime = mSetTier == 2 ? 3600 : 7200;
        lEUt = -steamPerTick;
        mMaxProgresstime = workTime;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        mApplyFluidIncrease = true;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 1;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " 500 L/s")
            .addInfo(
                EnumChatFormatting.GREEN + "Superheated Steam"
                    + EnumChatFormatting.GRAY
                    + " quadruples "
                    + EnumChatFormatting.GREEN
                    + "Speed"
                    + EnumChatFormatting.GRAY
                    + " and "
                    + EnumChatFormatting.AQUA
                    + "Steam Usage")
            .beginStructureBlock(5, 7, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + "Bronze"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "Steel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 14, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 18, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.vein_pyrolyzer.hint_bronze")
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

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (mLockedFluid != null) {
            aNBT.setString("mLockedFluid", mLockedFluid.getName());
        }
        aNBT.setInteger("mChunkRange", mChunkRange);
        aNBT.setInteger("mSetTier", mSetTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mLockedFluid")) {
            String fluidName = aNBT.getString("mLockedFluid");
            mLockedFluid = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
            if (mLockedFluid != null) mLockedFluidName = mLockedFluid.getName();
        }
        mChunkRange = aNBT.getInteger("mChunkRange");
        mSetTier = aNBT.getInteger("mSetTier");
    }

    private boolean hasSuperheatedSteamInHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.getFluid() != null
                && "ic2superheatedsteam".equals(
                    fs.getFluid()
                        .getName())
                && fs.amount > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> mLockedFluidName, val -> mLockedFluidName = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mChipRangeBonus, val -> mChipRangeBonus = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + (mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : StatCollector.translateToLocal("gtsr.gui.tier.bronze"))))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                        + EnumChatFormatting.YELLOW
                        + (hasSuperheatedSteamInHatch()
                            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal"))))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.chip")
                        + EnumChatFormatting.GREEN
                        + (mChipRangeBonus >= 7 ? "T3(+7)"
                            : mChipRangeBonus >= 3 ? "T2(+3)"
                                : mChipRangeBonus >= 1 ? "T1(+1)" : StatCollector.translateToLocal("gtsr.gui.none"))))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (mLockedFluidName.isEmpty()) {
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.target_fluid")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + StatCollector.translateToLocal("gtsr.gui.none");
                }
                Fluid fluid = FluidRegistry.getFluid(mLockedFluidName);
                String localName = fluid != null ? fluid.getLocalizedName(new FluidStack(fluid, 0)) : mLockedFluidName;
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.target_fluid")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + localName;
            }));
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        String steamType = hasSuperheatedSteamInHatch()
            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.vein_pyrolyzer.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType };
    }
}
