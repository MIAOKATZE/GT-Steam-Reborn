package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.gui.MTEDenseStateManipulatorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import bartworks.common.loaders.ItemRegistry;
import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Tier 2 dense steam compressor/decompressor（致密态蒸汽操控装置，就地升级自致密态操控机）。 */
public class MTEDenseStateManipulator extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 10;
    private static final int VERTICAL_OFF_SET = 8;
    private static final int DEPTH_OFF_SET = 1;

    private static final long DENSE_COMPRESSION_RATIO = 1000L;

    private static IStructureDefinition<MTEDenseStateManipulator> STRUCTURE_DEFINITION;
    private static Block TIER2_FRAME_BLOCK;
    private static Integer TIER2_FRAME_META;
    private static Block TIER2_GLASS_BLOCK;

    public static final int MODE_COMPRESS = 0;
    public static final int MODE_DECOMPRESS = 1;

    public int mMode = MODE_COMPRESS;
    private double mAccum = 0.0d;
    private int mAccumGrade = -1;

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    // Shape: GTUDK export — Y slices (top -> bottom); each row = depth line (front face first);
    // each char = horizontal axis (left -> right, seen from the machine front).
    // Controller '~' at (col 10, layer 8, row 1); runaway singularity 'F' at (10,5,10) and (24,5,10).
    private static final String[][] SHAPE_MAIN = {
        { "        EEEEE                 ", "      EEEEEEEEE               ", "    EEEEEEEEEEEEE             ",
            "   EEEEEEEEEEEEEEE            ", "  EEEEEEEEEEEEEEEEE           ", "  EEEEEEEEEEEEEEEEE   EEEEE   ",
            " EEEEEEEEEEEEEEEEEEE EEEEEEE  ", " EEEEEEEEEEEEEEEEEEEEEEEEEEEE ", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
            "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
            "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", " EEEEEEEEEEEEEEEEEEEEEEEEEEEE ", " EEEEEEEEEEEEEEEEEEE EEEEEEE  ",
            "  EEEEEEEEEEEEEEEEE   EEEEE   ", "  EEEEEEEEEEEEEEEEE           ", "   EEEEEEEEEEEEEEE            ",
            "    EEEEEEEEEEEEE             ", "      EEEEEEEEE               ", "        EEEEE                 " },
        { "                              ", "        EEEEE                 ", "      EEDDDDDEE               ",
            "    EEDD-----DDEE             ", "   EDD-----------E            ", "   ED------------E            ",
            "  ED--------------E   EEEEE   ", "  ED---B-----B----E  EDDDDDE  ", " ED----------------EE------DE ",
            " ED--------------------B-B-DE ", " ED------------------------DE ", " ED----------------DD--B-B-DE ",
            " ED---------------DEED-----DE ", "  ED---B-----B---DE  EDDDDDE  ", "  ED-------------DE   EEEEE   ",
            "   ED-----------DE            ", "   EDD---------DDE            ", "    EEDD-----DDEE             ",
            "      EEDDDDDEE               ", "        EEEEE                 ", "                              " },
        { "                              ", "        AAAAA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A----B-----B----A  A-----A  ", " A-----------------AA-------A ",
            " A---------------------B-B--A ", " A--------------------------A ", " A---------------------B-B--A ",
            " A-----------------AA-------A ", "  A----B-----B----A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        AAAAA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A----CCCCCCC----A  A-----A  ", " A-----C-----C-----AA-------A ",
            " A-----C-----C---------CCC--A ", " A-----C-----C---------C-C--A ", " A-----C-----C---------CCC--A ",
            " A-----C-----C-----AA-------A ", "  A----CCCCCCC----A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        AAAAA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A---------------A  A-----A  ", " A-----------------AA-------A ",
            " A--------------------------A ", " A--------------------------A ", " A--------------------------A ",
            " A-----------------AA-------A ", "  A---------------A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        AAAAA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A---------------A  A-----A  ", " A-----------------AA-------A ",
            " A--------------------------A ", " A--------F-------------F---A ", " A--------------------------A ",
            " A-----------------AA-------A ", "  A---------------A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        AAAAA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A---------------A  A-----A  ", " A-----------------AA-------A ",
            " A--------------------------A ", " A--------------------------A ", " A--------------------------A ",
            " A-----------------AA-------A ", "  A---------------A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        AEEEA                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A----CCCCCCC----A  A-----A  ", " A-----C-----C-----AA-------A ",
            " A-----C-----C---------CCC--A ", " A-----C-----C---------C-C--A ", " A-----C-----C---------CCC--A ",
            " A-----C-----C-----AA-------A ", "  A----CCCCCCC----A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        EE~EE                 ", "      AA-----AA               ",
            "    AA---------AA             ", "   A-------------A            ", "   A-------------A            ",
            "  A---------------A   AAAAA   ", "  A----B-----B----A  A-----A  ", " A-----------------AA-------A ",
            " A---------------------B-B--A ", " A--------------------------A ", " A---------------------B-B--A ",
            " A-----------------AA-------A ", "  A----B-----B----A  A-----A  ", "  A---------------A   AAAAA   ",
            "   A-------------A            ", "   A-------------A            ", "    AA---------AA             ",
            "      AA-----AA               ", "        AAAAA                 ", "                              " },
        { "                              ", "        EEEEE                 ", "      EEDDDDDEE               ",
            "    EEDD-----DDEE             ", "   EDD---------DDE            ", "   ED-----------DE            ",
            "  ED-------------DE   EEEEE   ", "  ED---B-----B---DE  EDDDDDE  ", " ED---------------DEED-----DE ",
            " ED----------------DD--B-B-DE ", " ED----------------DD------DE ", " ED----------------DD--B-B-DE ",
            " ED---------------DEED-----DE ", "  ED---B-----B---DE  EDDDDDE  ", "  ED-------------DE   EEEEE   ",
            "   ED-----------DE            ", "   EDD---------DDE            ", "    EEDD-----DDEE             ",
            "      EEDDDDDEE               ", "        EEEEE                 ", "                              " },
        { "        EEEEE                 ", "       EEEEEEEE               ", "    EEEEEEEEEEEEE             ",
            "   EEEEEEEEEEEEEEE            ", "  EEEEEEEEEEEEEEEEE           ", "  EEEEEEEEEEEEEEEEE   EEEEE   ",
            " EEEEEEEEEEEEEEEEEEE EEEEEEE  ", " EEEEEEEEEEEEEEEEEEEEEEEEEEEE ", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
            "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
            "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEE", " EEEEEEEEEEEEEEEEEEEEEEEEEEEE ", " EEEEEEEEEEEEEEEEEEE EEEEEEE  ",
            "  EEEEEEEEEEEEEEEEE   EEEEE   ", "  EEEEEEEEEEEEEEEEE           ", "   EEEEEEEEEEEEEEE            ",
            "    EEEEEEEEEEEEE             ", "      EEEEEEEEE               ", "        EEEEE                 " } };

    public MTEDenseStateManipulator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEDenseStateManipulator(String aName) {
        super(aName);
    }

    @Override
    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.dense_state_manipulator.";
    }

    @Override
    public String getGuiKeyPrefix() {
        return "gtsr.gui.dense_state_manipulator.";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEDenseStateManipulator(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 2;
    }

    @Override
    protected double getHeatMax() {
        return 0.0d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 1L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    // NEI 展示用伪合成表：实际处理逻辑在 checkProcessing()，本 map 仅用于 NEI 显示。
    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.denseStateManipulatorRecipes;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return null;
    }

    @Override
    protected boolean requiresOutputHatch() {
        return true;
    }

    @Override
    protected boolean requiresInputBus() {
        return true;
    }

    @Override
    protected boolean shouldRenderEntanglementSingularity(IGregTechTileEntity aBaseMetaTileEntity) {
        // v1.10.59：关机奇点消失；奇点模式倒计时未耗尽（模式持续中）时豁免，模式退出或结构破坏才消失
        return mMachine && (aBaseMetaTileEntity.isAllowedToWork() || mSingularityMode > 0);
    }

    @Override
    public boolean isDenseStateManipulator() {
        return true;
    }

    @Override
    public int getModeForGui() {
        return mMode;
    }

    @Override
    public boolean isHideTierInGui() {
        // v1.10.59：单级机器不显示等级
        return true;
    }

    @Override
    protected int getCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
    }

    @Override
    protected int getHatchCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(aActive ? OVERLAY_ON : OVERLAY_OFF) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    private static Block getTier2FrameBlock() {
        if (TIER2_FRAME_BLOCK == null) {
            TIER2_FRAME_BLOCK = GregTechAPI.sBlockFramesBW;
            if (TIER2_FRAME_BLOCK == null) TIER2_FRAME_BLOCK = GameRegistry.findBlock("gregtech", "bw.frames");
        }
        return TIER2_FRAME_BLOCK;
    }

    private static int getTier2FrameMeta() {
        if (TIER2_FRAME_META == null) {
            Werkstoff werkstoff = WerkstoffLoader.RhodiumPlatedPalladium;
            TIER2_FRAME_META = werkstoff != null ? (int) werkstoff.getmID() : 88;
        }
        return TIER2_FRAME_META;
    }

    private static Block getTier2GlassBlock() {
        if (TIER2_GLASS_BLOCK == null) {
            TIER2_GLASS_BLOCK = GameRegistry.findBlock("bartworks", "BW_TieredGlass");
            if (TIER2_GLASS_BLOCK == null) TIER2_GLASS_BLOCK = ItemRegistry.bw_realglas;
        }
        return TIER2_GLASS_BLOCK;
    }

    private static IStructureDefinition<MTEDenseStateManipulator> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
        // 失控节点定位位（导出为泥土）：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）。
        // noPlacement()：构建/全息投影不放置奇点方块——该位保持空气，奇点仅由机器运行时惰性生成
        IStructureElementCheckOnly<MTEDenseStateManipulator> singularityLocator = new IStructureElementCheckOnly<MTEDenseStateManipulator>() {

            @Override
            public boolean check(MTEDenseStateManipulator t, World world, int x, int y, int z) {
                // 结构判定：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）；
                // CheckOnly 不放置：构建/全息投影保持空气，奇点仅由机器运行时惰性生成
                Block block = world.getBlock(x, y, z);
                return block == BlocksGTSR.runawaySingularity || block.isAir(world, x, y, z);
            }
        };

        return StructureDefinition.<MTEDenseStateManipulator>builder()
            .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
            .addElement('A', ofBlock(getTier2GlassBlock(), 3))
            .addElement('B', ofBlock(getTier2FrameBlock(), getTier2FrameMeta()))
            .addElement('C', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings"), 15))
            .addElement('D', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings4"), 7))
            .addElement(
                'E',
                ofChain(
                    ofBlock(GregTechAPI.sBlockCasings8, 6),
                    buildHatchAdder(MTEDenseStateManipulator.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEDenseStateManipulator.class).atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEDenseStateManipulator.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEDenseStateManipulator.class).atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement('F', singularityLocator)
            .addElement('-', isAir())
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
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        if (mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (requiresInputBus() && mInputBusses.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (requiresOutputHatch() && mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    protected List<EntanglementSpec> getEntanglementSpecs() {
        // 两个失控奇点定位位：F1 形状偏移 (0,-3,9)、F2 (14,-3,9)，经 ExtendedFacing 换算世界偏移（与 checkPiece 同源映射）。
        // 左白（range 14，fxRadius 30）/右灰（range 8，fxRadius 10）双失控节点动画（nullplus 无电弧，吸积盘 3+辉光）。
        Vec3Impl left = getExtendedFacing().getWorldOffset(new Vec3Impl(0, -3, 9));
        Vec3Impl right = getExtendedFacing().getWorldOffset(new Vec3Impl(14, -3, 9));
        List<EntanglementSpec> list = new ArrayList<>();
        list.add(
            new EntanglementSpec(left.get0(), left.get1(), left.get2(), 14.0D, 0.0D, 0.0D, -1, -3, "white", 30.0D));
        list.add(
            new EntanglementSpec(right.get0(), right.get1(), right.get2(), 8.0D, 0.0D, 0.0D, -1, -3, "gray", 10.0D)); // attributeId=-3=ATTRIBUTE_NULL_PLUS（null
                                                                                                                      // 基础上无电弧无粒子，光片/辉光保留）
        return list;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = super.createTooltip();
        tt.addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc7"));
        tt.addSeparator()
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列
            .beginStructureBlock(30, 11, 21, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1);
        if (requiresInputBus()) {
            tt.addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1);
        }
        if (requiresOutputHatch()) {
            tt.addOutputHatch(StatCollector.translateToLocal(keyPrefix + "output_hatch"), 1);
        }
        tt.addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.desc6"))
            .addStructureInfo(
                EnumChatFormatting.DARK_GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.desc6_2"))
            .addStructureInfo(
                EnumChatFormatting.DARK_GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.desc6_3"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.tier1_blocks"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.dense_state_manipulator.tier1_blocks_2"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mMode == MODE_DECOMPRESS) return processDecompressionCycle();
        mMode = MODE_COMPRESS;
        return processCompressionCycle();
    }

    /**
     * 尝试进入奇点模式：临界奇点优先（与 CMA/MSTA 一致），失败再试普通奇点；
     * v1.10.8 教训——调用前必须确认有流体可吞噬（本方法只消耗奇点，不验证流体）。
     */
    private boolean enterSingularityModeIfPossible() {
        if (consumeSingularityFromInputBuses(1, getSingularityItemForMode(2))) {
            mSingularityMode = 2;
            mSingularityModeTicks = getSingularityDurationTicks();
            getBaseMetaTileEntity().markDirty();
            return true;
        }
        if (consumeSingularityFromInputBuses(1, getSingularityItemForMode(1))) {
            mSingularityMode = 1;
            mSingularityModeTicks = getSingularityDurationTicks();
            getBaseMetaTileEntity().markDirty();
            return true;
        }
        return false;
    }

    private CheckRecipeResult processCompressionCycle() {
        if (mAccum < 1.0d) {
            int grade = findHighestGrade(false);
            if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
            long amount = sumGrade(grade, false);
            if (amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            // v1.10.8：确认有流体可吞噬后才消耗奇点进入模式（原实现先扣燃料后验流体，
            // 无流体时燃料已扣、mFuelTicks=12000 空转 10 分钟）。
            if (mSingularityMode == 0) {
                if (!enterSingularityModeIfPossible()) return CheckRecipeResultRegistry.NO_RECIPE;
            }
            drainGrade(grade, false);
            // v1.10.8：等级切换保留已累积量（按原等级比例折算续算），
            // 原实现 mAccum=0 使混合等级输入永久零产出、流体与燃料纯消耗。
            if (mAccumGrade >= 0 && grade != mAccumGrade) {
                mAccum = mAccum * getGradeRatio(grade) / getGradeRatio(mAccumGrade);
            }
            mAccumGrade = grade;
            mAccum += (double) amount / DENSE_COMPRESSION_RATIO * getSingularityOutputCoefficient();
        } else if (mSingularityMode == 0) {
            // 输出阶段模式耗尽：工作上下文内重新进入（输出未达 1 单位、无流体可吞时也需维持模式）
            if (!enterSingularityModeIfPossible()) return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long output = (long) Math.floor(mAccum);
        if (output <= 0) {
            startCycle();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (mAccumGrade < 0) {
            mAccum = 0.0d;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        FluidStack dense = FluidRegistry
            .getFluidStack(DENSE_FLUID_NAMES[mAccumGrade], (int) Math.min(output, Integer.MAX_VALUE));
        if (dense == null || dense.amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        mAccum -= fillOutput(dense);
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult processDecompressionCycle() {
        if (mAccum < 1.0d) {
            int grade = findHighestDenseGrade();
            if (grade < 0) return CheckRecipeResultRegistry.NO_RECIPE;
            long amount = sumDenseGrade(grade);
            if (amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
            // v1.10.8：确认有流体可吞噬后才消耗奇点进入模式（同 compression）
            if (mSingularityMode == 0) {
                if (!enterSingularityModeIfPossible()) return CheckRecipeResultRegistry.NO_RECIPE;
            }
            drainDenseGrade(grade);
            // v1.10.8：等级切换保留已累积量（同 compression 折算续算）
            if (mAccumGrade >= 0 && grade != mAccumGrade) {
                mAccum = mAccum * getGradeRatio(grade) / getGradeRatio(mAccumGrade);
            }
            mAccumGrade = grade;
            mAccum += (double) amount * DENSE_COMPRESSION_RATIO * getSingularityOutputCoefficient();
        } else if (mSingularityMode == 0) {
            // 输出阶段模式耗尽：工作上下文内重新进入
            if (!enterSingularityModeIfPossible()) return CheckRecipeResultRegistry.NO_RECIPE;
        }

        long output = (long) Math.floor(mAccum);
        if (output <= 0) {
            startCycle();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        if (mAccumGrade < 0) {
            mAccum = 0.0d;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        FluidStack steam = FluidRegistry
            .getFluidStack(NORMAL_FLUID_NAMES[mAccumGrade], (int) Math.min(output, Integer.MAX_VALUE));
        if (steam == null || steam.amount <= 0) return CheckRecipeResultRegistry.NO_RECIPE;
        mAccum -= fillOutput(steam);
        startCycle();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** v1.10.59：普通奇点模式 -20% 输出损失（压缩与解压均生效），临界奇点模式无损。 */
    private double getSingularityOutputCoefficient() {
        return mSingularityMode == 1 ? 0.8d : 1.0d;
    }

    /** 等级能量折算基准（与 GRADE_COEF 一致：grade0=0.5、grade1=1.0、grade2=2.0）。 */
    private double getGradeRatio(int grade) {
        return GRADE_COEF[grade];
    }

    private int findHighestDenseGrade() {
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, false, true)) return grade;
        }
        return -1;
    }

    private long sumDenseGrade(int grade) {
        long amount = 0;
        FluidStack request = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (request == null) return 0;
        for (gregtech.api.metatileentity.implementations.MTEHatch hatch : getSteamInputHatches()) {
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(request)) amount += tank.fluid.amount;
            }
        }
        return amount;
    }

    private void drainDenseGrade(int grade) {
        FluidStack request = FluidRegistry.getFluidStack(DENSE_FLUID_NAMES[grade], 1);
        if (request == null) return;
        for (gregtech.api.metatileentity.implementations.MTEHatch hatch : getSteamInputHatches()) {
            // v1.10.60：对齐 v1.10.55 grade 族——直接 MAX_VALUE 实扣（原"模拟探测+实扣"两段
            // 依赖配方窗口位置，beta-1 窗口外模拟即真提取会双扣；直接实扣双版本等效，
            // "输入仓有多少消耗多少"设计语义不变）。
            FluidStack full = request.copy();
            full.amount = Integer.MAX_VALUE;
            hatch.drain(ForgeDirection.UNKNOWN, full, true);
        }
    }

    @Override
    protected boolean shouldDecayHeat() {
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (mTier < 2) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.compressor_mode.require_tier2");
            return;
        }
        mMode = mMode == MODE_COMPRESS ? MODE_DECOMPRESS : MODE_COMPRESS;
        // 切换压缩/解压重置奇点模式与累积量
        mSingularityMode = 0;
        mSingularityModeTicks = 0;
        mAccum = 0.0d;
        mAccumGrade = -1;
        String key = mMode == MODE_COMPRESS ? "gtsr.chat.compressor_mode.on.compress"
            : "gtsr.chat.compressor_mode.on.decompress";
        GTUtility.sendChatTrans(aPlayer, key);
        getBaseMetaTileEntity().markDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        // 奇点倒计时/消耗已由 MTESingularityModeMachineBase.onPostTick 统一处理——关机仅计时、工作才消耗
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mMode", mMode);
        aNBT.setDouble("mAccum", mAccum);
        aNBT.setInteger("mAccumGrade", mAccumGrade);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mMode = aNBT.getInteger("mMode") == MODE_DECOMPRESS ? MODE_DECOMPRESS : MODE_COMPRESS;
        // v1.10.59 旧档迁移：600s 单类型燃料 → 普通奇点模式（剩余 tick 直接继承）
        if (aNBT.hasKey("mFuelTicks") && aNBT.getInteger("mFuelTicks") > 0) {
            mSingularityMode = 1;
            mSingularityModeTicks = aNBT.getInteger("mFuelTicks");
        }
        mAccum = aNBT.getDouble("mAccum");
        mAccumGrade = aNBT.getInteger("mAccumGrade");
    }

    @Override
    public String[] getInfoData() {
        String tooltipKeyPrefix = getTooltipKeyPrefix();
        String guiKeyPrefix = getGuiKeyPrefix();
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(tooltipKeyPrefix + "type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        // v1.10.59：燃料行 → 奇点模式行（与 GUI 终端一致）；单级机器不再显示 tier 行
        String singularityValue = mSingularityMode == 0
            ? StatCollector.translateToLocal(guiKeyPrefix + "singularity_off")
            : (mSingularityMode == 2 ? StatCollector.translateToLocal(guiKeyPrefix + "singularity_critical")
                : StatCollector.translateToLocal(guiKeyPrefix + "singularity_steam"))
                + String.format(" %ds", mSingularityModeTicks / 20);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "singularity_mode")
                + EnumChatFormatting.RED
                + singularityValue
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTEDenseStateManipulatorGui(this);
    }
}
