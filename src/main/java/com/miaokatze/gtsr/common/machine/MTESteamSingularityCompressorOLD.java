package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Tier 1 steam entanglement machine. */
public class MTESteamSingularityCompressorOLD extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 8;
    private static final int DEPTH_OFF_SET = 2;

    private static IStructureDefinition<MTESteamSingularityCompressorOLD> STRUCTURE_DEFINITION;

    private int mCasingTierB = -1;
    private int mCasingTierC = -1;
    private int mCasingTierD = -1;
    private int mCasingTierE = -1;
    private int mCasingTierF = -1;

    public MTESteamSingularityCompressorOLD(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamSingularityCompressorOLD(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamSingularityCompressorOLD(mName);
    }

    @Override
    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.old.singularity_compressor.";
    }

    @Override
    protected int getRequiredTier() {
        return 1;
    }

    @Override
    protected double getHeatMax() {
        return 0.005d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 200000L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return GTSRItemList.SteamEntangledSingularity.get(1);
    }

    @Nullable
    private static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        return null;
    }

    @Nullable
    private static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        return null;
    }

    @Nullable
    private static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 1;
        return null;
    }

    @Nullable
    private static Integer getGlassTier(Block block, int meta) {
        if (block == GTVersionCompat.getReinforcedGlassBlock() && meta == GTVersionCompat.getReinforcedGlassMeta()) {
            return 1;
        }
        return null;
    }

    private static IStructureDefinition<MTESteamSingularityCompressorOLD> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        List<Pair<Block, Integer>> casingTiers = new ArrayList<>();
        casingTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 0));
        List<Pair<Block, Integer>> pipeTiers = new ArrayList<>();
        pipeTiers.add(Pair.of(GregTechAPI.sBlockCasings2, 13));
        List<Pair<Block, Integer>> frameTiers = new ArrayList<>();
        frameTiers.add(Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID));
        List<Pair<Block, Integer>> glassTiers = new ArrayList<>();
        glassTiers.add(Pair.of(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()));

        return StructureDefinition.<MTESteamSingularityCompressorOLD>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] {
                        { " DBBBBBBBD ", "DB  EBE  BD", "B DEBDBED B", "B EEEEEEE B", "BEBEEEEEBEB", "BBDEEEEEDBB",
                            "BEBEEEEEBEB", "B EEEEEEE B", "B DEBDBED B", "DB   B   BD", " DBBBBBBBD " },
                        { " F       F ", "FD  EBE  DF", "  BEFBFEB  ", "  E     E  ", " EF     FE ", " BB     BB ",
                            " EF     FE ", "  E     E  ", "  BEFBFEB  ", "FD  EBE  DF", " F       F " },
                        { " F       F ", "FD  E E  DF", "  BEFBFEB  ", "  EB B BE  ", " EF     FE ", "  BB   BB  ",
                            " EF     FE ", "  EB B BE  ", "  BEFBFEB  ", "FD  E E  DF", " F       F " },
                        { " F       F ", "F   E E   F", "  CEFEFEC  ", "  ED D DE  ", " EF     FE ", "  ED   DE  ",
                            " EF     FE ", "  ED D DE  ", "  CEFEFEC  ", "F   E E  DF", " F       F " },
                        { " F       F ", "F   E E   F", "  CEFEFEC  ", "  EB B BE  ", " EF BBB FE ", "  EBB BBE  ",
                            " EF BBB FE ", "  EB B BE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                        { " F       F ", "F   E E   F", "   EFEFE   ", "  E     E  ", " EF EEE FE ", "  E E E E  ",
                            " EF EEE FE ", "  E     E  ", "   EFEFE   ", "F   E E   F", " F       F " },
                        { " F       F ", "F   E E   F", "  CEFEFEC  ", "  EB B BE  ", " EF BBB FE ", "  EBB BBE  ",
                            " EF BBB FE ", "  EB B BE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                        { " F       F ", "F   E E   F", "  CEFEFEC  ", "  ED D DE  ", " EF     FE ", "  ED   DE  ",
                            " EF     FE ", "  ED D DE  ", "  CEFEFEC  ", "F   E E   F", " F       F " },
                        { " F       F ", "FD  E E  DF", "  BEF~FEB  ", "  EB B BE  ", " EF     FE ", "  BB   BB  ",
                            " EF     FE ", "  EB B BE  ", "  BEFBFEB  ", "FD  E E  DF", " F       F " },
                        { " F       F ", "FD  EBE  DF", "  BEFBFEB  ", "  ED D DE  ", " EF     FE ", " BBD   DBB ",
                            " EF     FE ", "  ED D DE  ", "  BEFBFEB  ", "FD  EBE  DF", " F       F " },
                        { " DBBBBBBBD ", "DB  EBE  BD", "B DEBDBED B", "B EEEEEEE B", "BEBEEEEEBEB", "BBDEEEEEDBB",
                            "BEBEEEEEBEB", "B EEEEEEE B", "B DEBDBED B", "DB  EBE  BD", " DBBBBBBBD " } }))
            .addElement(
                'B',
                ofChain(
                    ofBlocksTiered(
                        MTESteamSingularityCompressorOLD::getCasingTier,
                        casingTiers,
                        -1,
                        (t, tier) -> t.mCasingTierB = tier,
                        t -> t.mCasingTierB),
                    buildHatchAdder(MTESteamSingularityCompressorOLD.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityCompressorOLD.class)
                        .atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityCompressorOLD.class)
                        .atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTESteamSingularityCompressorOLD.class)
                        .atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement(
                'C',
                ofBlocksTiered(
                    MTESteamSingularityCompressorOLD::getPipeTier,
                    pipeTiers,
                    -1,
                    (t, tier) -> t.mCasingTierC = tier,
                    t -> t.mCasingTierC))
            .addElement(
                'D',
                ofBlocksTiered(
                    MTESteamSingularityCompressorOLD::getCasingTier,
                    casingTiers,
                    -1,
                    (t, tier) -> t.mCasingTierD = tier,
                    t -> t.mCasingTierD))
            .addElement(
                'E',
                ofBlocksTiered(
                    MTESteamSingularityCompressorOLD::getGlassTier,
                    glassTiers,
                    -1,
                    (t, tier) -> t.mCasingTierE = tier,
                    t -> t.mCasingTierE))
            .addElement(
                'F',
                ofBlocksTiered(
                    MTESteamSingularityCompressorOLD::getFrameTier,
                    frameTiers,
                    -1,
                    (t, tier) -> t.mCasingTierF = tier,
                    t -> t.mCasingTierF))
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IStructureDefinition<MTESingularityMachineBase> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return (IStructureDefinition<MTESingularityMachineBase>) (IStructureDefinition<?>) STRUCTURE_DEFINITION;
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingTierB = -1;
        mCasingTierC = -1;
        mCasingTierD = -1;
        mCasingTierE = -1;
        mCasingTierF = -1;
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        int tier = mCasingTierB;
        if (mCasingTierC != tier || mCasingTierD != tier
            || mCasingTierE != tier
            || mCasingTierF != tier
            || tier != getRequiredTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mTier = tier;

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty())
            || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.old.remove_soon"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.old.singularity_compressor.new_mechanism"))
            .addMachineType(StatCollector.translateToLocal(keyPrefix + "type"))
            .addInfo(StatCollector.translateToLocal(keyPrefix + "desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"));
        tt.addSeparator()
            .beginStructureBlock(11, 11, 11, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1)
            .addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier1_blocks"))
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

    @Override
    public CheckRecipeResult checkProcessing() {
        // 老版线性机制：无配方周期，处理全部在 onPostTick 完成（每秒线性增长），
        // 参照 MTESingularityDrillingHub.checkProcessing 的 NO_RECIPE 写法。
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || aTick % 20 != 0
            || !mMachine
            || !aBaseMetaTileEntity.isAllowedToWork()) return;
        FluidStack hot = FluidRegistry.getFluidStack("ic2superheatedsteam", 320000);
        if (hot != null && GTSRHatchFluidAccess.depleteFluidAcross(getSteamInputHatches(), hot) >= 320000) {
            mHeat += 0.0008d; // 过热：320,000 L/s → +0.08%/s（老版线性）
        } else {
            FluidStack normal = Materials.Steam.getGas(80000);
            if (GTSRHatchFluidAccess.depleteFluidAcross(getSteamInputHatches(), normal) >= 80000) {
                mHeat += 0.0002d; // 普通：80,000 L/s → +0.02%/s（老版线性）
            }
        }
        if (mHeat >= 1.0d) {
            if (canOutputSingularity()) {
                addOutputPartial(getAggregationOutput());
                mHeat = 0.0d;
            }
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // Old mode keys are intentionally ignored; this machine is permanently tier 1 aggregation.
        mTier = 1;
    }
}
