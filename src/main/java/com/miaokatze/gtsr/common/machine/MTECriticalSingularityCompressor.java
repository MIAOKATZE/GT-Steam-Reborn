package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTECriticalSingularityCompressorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.loader.BlockLoader;

import bartworks.common.loaders.ItemRegistry;
import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/** Tier 2 steam entanglement machine. */
public class MTECriticalSingularityCompressor extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 13;
    private static final int VERTICAL_OFF_SET = 10;
    private static final int DEPTH_OFF_SET = 2;

    private static IStructureDefinition<MTECriticalSingularityCompressor> STRUCTURE_DEFINITION;
    private static Block TIER2_FRAME_BLOCK;
    private static Integer TIER2_FRAME_META;
    private static Block TIER2_GLASS_BLOCK;

    public MTECriticalSingularityCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECriticalSingularityCompressor(String aName) {
        super(aName);
    }

    @Override
    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.critical_singularity_compressor.";
    }

    @Override
    public String getGuiKeyPrefix() {
        return "gtsr.gui.critical_singularity_compressor.";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECriticalSingularityCompressor(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 2;
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
    protected double getHeatMax() {
        return 0.002d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 1000L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return true;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return GTSRItemList.CriticalSteamEntangledSingularity.get(1);
    }

    @Override
    protected boolean requiresInputBus() {
        return true;
    }

    @Override
    protected boolean shouldRenderEntanglementSingularity(IGregTechTileEntity aBaseMetaTileEntity) {
        // 奇点模式：结构成型即渲染失控奇点（停止工作/软锤关闭也渲染，结构破坏才消失）
        return mMachine;
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

    // Shape: canonical — Y slices (top -> bottom); each row = depth line (front face first);
    // each char = horizontal axis (left -> right, seen from the machine front)
    // 注册：.addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))，旋转由 StructureLib ExtendedFacing 自动处理
    private static final String[][] SHAPE_MAIN = {
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "           GGGGG           ", "          GGGGGGG          ", "         GGGAAAGGG         ",
            "         GGAAAAAGG         ", "         GGAAAAAGG         ", "         GGAAAAAGG         ",
            "         GGGAAAGGG         ", "          GGGGGGG          ", "           GGGGG           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "           GGGGG           ", "         GGGGGGGGG         ",
            "        GGGFFFFFGGG        ", "        GGF-----FGG        ", "       GGF-------FGG       ",
            "       GGF-------FGG       ", "       GGF-------FGG       ", "       GGF-------FGG       ",
            "       GGF-------FGG       ", "        GGF-----FGG        ", "        GGGFFFFFGGG        ",
            "         GGGGGGGGG         ", "           GGGGG           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "           GGGGG           ",
            "         GGGGGGGGG         ", "       GGGGFFFFFGGGG       ", "       GGFF-----FFGG       ",
            "      GGF---------FGG      ", "      GGF---------FGG      ", "     GGF-----------FGG     ",
            "     GGF-----------FGG     ", "     GGF-----------FGG     ", "     GGF-----------FGG     ",
            "     GGF-----------FGG     ", "      GGF---------FGG      ", "      GGF---------FGG      ",
            "       GGFF-----FFGG       ", "       GGGGFFFFFGGGG       ", "         GGGGGGGGG         ",
            "           GGGGG           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "           EEEEE           ", "         EEGGGGGEE         ",
            "       EEGGGGGGGGGEE       ", "      EGGGG-----GGGGE      ", "      EGG---------GGE      ",
            "     EGG-----------GGE     ", "     EGG-----------GGE     ", "    EGG-------------GGE    ",
            "    EGG-------------GGE    ", "    EGG-------------GGE    ", "    EGG-------------GGE    ",
            "    EGG-------------GGE    ", "     EGG-----------GGE     ", "     EGG-----------GGE     ",
            "      EGG---------GGE      ", "      EGGGG-----GGGGE      ", "       EEGGGGGGGGGEE       ",
            "         EEGGGGGEE         ", "           EEEEE           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "           GGGGG           ", "         GGFFFFFGG         ",
            "       GGFF-----FFGG       ", "      GFF---------FFG      ", "      GF-----------FG      ",
            "     GF-------------FG     ", "     GF-------------FG     ", "    GF---------------FG    ",
            "    GF---------------FG    ", "    GF---------------FG    ", "    GF---------------FG    ",
            "    GF---------------FG    ", "     GF-------------FG     ", "     GFF------------FG     ",
            "      GF-----------FG      ", "      GFFF--------FFG      ", "       GGFF-----FFGG       ",
            "         GGFFFFFGG         ", "           GGGGG           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "            CCC            ", "            CCC            ",
            "           EEEEE           ", "         EEGGGGGEE         ", "       EEGG-----GGEE       ",
            "      EGG---------GGE      ", "     EG-------------GE     ", "     EG-------------GE     ",
            "    EG---------------GE    ", "    EG---------------GE    ", "   EG-----------------GE   ",
            " CCEG-----------------GECC ", " CCEG-----------------GECC ", " CCEG-----------------GECC ",
            "   EG-----------------GE   ", "    EG---------------GE    ", "    EG---------------GE    ",
            "     EG-------------GE     ", "     EG-------------GE     ", "      EGG---------GGE      ",
            "       EEGG-----GGEE       ", "         EEGGGGGEE         ", "           EEEEE           ",
            "            CCC            ", "            CCC            ", "                           " },
        { "            CCC            ", "            EEE            ", "            EEE            ",
            "           GGGGG           ", "         GGFFFFFGG         ", "       GGFF-----FFGG       ",
            "      GFF---------FFG      ", "     GF-------------FG     ", "     GF-------------FG     ",
            "    GF---------------FG    ", "    GF---------------FG    ", "   GF-----------------FG   ",
            "CEEGF-----------------FGEEC", "CEEGF-----------------FGEEC", "CEEGF-----------------FGEEC",
            "   GF-----------------FG   ", "    GF---------------FG    ", "    GF---------------FG    ",
            "     GF-------------FG     ", "     GF-------------FG     ", "      GFF---------FFG      ",
            "       GGFF-----FFGG       ", "         GGFFFFFGG         ", "           GGGGG           ",
            "            EEE            ", "            EEE            ", "            CCC            " },
        { "           HHHHH           ", "                           ", "           EGGGE           ",
            "         EEGGGGGEE         ", "       EEGG-----GGEE       ", "      EGG---------GGE      ",
            "     EG-------------GE     ", "    EG---------------GE    ", "    EG---------------GE    ",
            "   EG-----------------GE   ", "   EG-----------------GE   ", "H GG-------------------GE H",
            "H GG-------------------GG H", "H GG-------------------GG H", "H GG-------------------GG H",
            "H GG-------------------GE H", "   EG-----------------GE   ", "   EG-----------------GE   ",
            "    EG---------------GE    ", "    EG---------------GE    ", "     EG-------------GE     ",
            "      EGG---------GGE      ", "       EEGG-----GGEE       ", "         EEGGGGGEE         ",
            "           EGGGE           ", "                           ", "           HHHHH           " },
        { "          H     H          ", "                           ", "           GAGAG           ",
            "         GGB---BGG         ", "       GG--B---B--GG       ", "      G----B---B----G      ",
            "     GB----DDDDD----BG     ", "    G--B-DDD---DDD-B--G    ", "    G---DD-------DD---G    ",
            "   G---DD---------DD---G   ", "H  G---D-----------D---G  H", "  GBBBDD-----------DDBBBG  ",
            "  A---D-------------D---A  ", "  A---D-------------D---A  ", "  A---D-------------D---A  ",
            "  GBBBDD-----------DDBBBG  ", "H  G---D-----------D---G  H", "   G---DD---------DD---G   ",
            "    G---DD-------DD---G    ", "    G--B-DDD---DDD-B--G    ", "     GB----DDDDD----BG     ",
            "      G----B---B----G      ", "       GG--B---B--GG       ", "         GGB---BGG         ",
            "           GAAAG           ", "                           ", "          H     H          " },
        { "         CH     HC         ", "         CE     EC         ", "       CCEGAAGAAGECC       ",
            "      CEEGG-----GGEEC      ", "     CEGG---------GGEC     ", "    CEG-------------GEC    ",
            "   CEG---------------GEC   ", "  CEG-----------------GEC  ", "  CEG-----------------GEC  ",
            "CCEG-------------------GECC", "HEGG-------------------GGEH", "  A---------------------A  ",
            "  A---------------------A  ", "  A---------------------A  ", "  A---------------------A  ",
            "  A---------------------A  ", "HEGG-------------------GGEH", "CCEG-------------------GECC",
            "  CEG-----------------GEC  ", "  CEG-----------------GEC  ", "   CEG---------------GEC   ",
            "    CEG-------------GEC    ", "     CEGG---------GGEC     ", "      CEEGG-----GGEEC      ",
            "       CCEGAAAAAGECC       ", "         CE     EC         ", "         CH     HC         " },
        { "         CH     HC         ", "       CCEE     EECC       ", "      CEEGGGG~GGGGEEC      ",
            "     CEGGGG-----GGGGEC     ", "    CEGGG---------GGGEC    ", "   CEGG-------------GGEC   ",
            "  CEGG---------------GGEC  ", " CEGG-----------------GGEC ", " CEGG-----------------GGEC ",
            "CEGG-------------------GGEC", "HEGG-------------------GGEH", "  A---------------------A  ",
            "  A---------------------A  ", "  A----------I----------A  ", "  A---------------------A  ",
            "  A---------------------A  ", "HEGG-------------------GGEH", "CEGG-------------------GGEC",
            " CEGG-----------------GGEC ", " CEGG-----------------GEEC ", "  CEGG---------------GEEC  ",
            "   CEGG-------------GGEC   ", "    CEGGG---------GGGEC    ", "     CEGGGG-----GGGGEC     ",
            "      CEEGGAAAAAGGEEC      ", "       CCEE     EECC       ", "         CH     HC         " },
        { "         CH     HC         ", "         CE     EC         ", "       CCEGAAGAAGECC       ",
            "      CEEGG-----GGEEC      ", "     CEGG---------GGEC     ", "    CEG-------------GEC    ",
            "   CEG---------------GEC   ", "  CEG-----------------GEC  ", "  CEG-----------------GEC  ",
            "CCEG-------------------GECC", "HEGG-------------------GGEH", "  A---------------------A  ",
            "  A---------------------A  ", "  A---------------------A  ", "  A---------------------A  ",
            "  A---------------------A  ", "HEGG-------------------GGEH", "CCEG-------------------GECC",
            "  CEG-----------------GEC  ", "  CEG-----------------GEC  ", "   CEG---------------GEC   ",
            "    CEG-------------GEC    ", "     CEGG---------GGEC     ", "      CEEGG-----GGEEC      ",
            "       CCEGAAAAAGECC       ", "         CE     EC         ", "         CH     HC         " },
        { "          H     H          ", "                           ", "           GAGAG           ",
            "         GGB---BGG         ", "       GG--B---B--GG       ", "      G----B---B----G      ",
            "     GB----DDDDD---B-G     ", "    G--B-DDD---DDDB---G    ", "    G---DD-------DD---G    ",
            "   G---DD---------DD---G   ", "H  G---D-----------D---G  H", "  GBBBDD-----------DDBBBG  ",
            "  A---D-------------D---A  ", "  A---D-------------D---A  ", "  A---D-------------D---A  ",
            "  GBBBDD-----------DDBBBG  ", "H  G---D-----------D---G  H", "   G---DD---------DD---G   ",
            "    G---DD-------DD---G    ", "    G--B-DDD---DDD-B--G    ", "     GB----DDDDD----BG     ",
            "      G----B---B----G      ", "       GG--B---B--GG       ", "         GGB---BGG         ",
            "           GAAAG           ", "                           ", "          H     H          " },
        { "           HHHHH           ", "                           ", "           EGGGE           ",
            "         EEGGGGGEE         ", "       EEGG-----GGEE       ", "      EGG---------GGE      ",
            "     EG-------------GE     ", "    EG---------------GE    ", "    EG---------------GE    ",
            "   EG-----------------GE   ", "   EG-----------------GE   ", "H GG-------------------GE H",
            "H GG-------------------GG H", "H GG-------------------GG H", "H GG-------------------GG H",
            "H GG-------------------GE H", "   EG-----------------GE   ", "   EG-----------------GE   ",
            "    EG---------------GE    ", "    EG---------------GE    ", "     EG-------------GE     ",
            "      EGG---------GGE      ", "       EEGG-----GGEE       ", "         EEGGGGGEE         ",
            "           EGGGE           ", "                           ", "           HHHHH           " },
        { "            CCC            ", "            EEE            ", "            EEE            ",
            "           GGGGG           ", "         GGFFFFFGG         ", "       GGFF-----FFGG       ",
            "      GFF---------FFG      ", "     GF-------------FG     ", "     GF-------------FG     ",
            "    GF---------------FG    ", "    GF---------------FG    ", "   GF-----------------FG   ",
            "CEEGF-----------------FGEEC", "CEEGF-----------------FGEEC", "CEEGF-----------------FGEEC",
            "   GF-----------------FG   ", "    GF---------------FG    ", "    GF---------------FG    ",
            "     GF-------------FG     ", "     GF-------------FG     ", "      GFF---------FFG      ",
            "       GGFF-----FFGG       ", "         GGFFFFFGG         ", "           GGGGG           ",
            "            EEE            ", "            EEE            ", "            CCC            " },
        { "                           ", "            CCC            ", "            CCC            ",
            "           EEEEE           ", "         EEGGGGGEE         ", "       EEGG-----GGEE       ",
            "      EGG---------GGE      ", "     EG-------------GE     ", "     EG-------------GE     ",
            "    EG---------------GE    ", "    EG---------------GE    ", "   EG-----------------GE   ",
            " CCEG-----------------GECC ", " CCEG-----------------GECC ", " CCEG-----------------GECC ",
            "   EG-----------------GE   ", "    EG---------------GE    ", "    EG---------------GE    ",
            "     EG-------------GE     ", "     EG-------------GE     ", "      EGG---------GGE      ",
            "       EEGG-----GGEE       ", "         EEGGGGGEE         ", "           EEEEE           ",
            "            CCC            ", "            CCC            ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "           GGGGG           ", "         GGFFFFFGG         ",
            "       GGFF-----FFGG       ", "      GFF---------FFG      ", "      GF-----------FG      ",
            "     GF-------------FG     ", "     GF-------------FG     ", "    GF---------------FG    ",
            "    GF---------------FG    ", "    GF---------------FG    ", "    GF---------------FG    ",
            "    GF---------------FG    ", "     GF-------------FG     ", "     GF-------------FG     ",
            "      GF-----------FG      ", "      GFF---------FFG      ", "       GGFF-----FFGG       ",
            "         GGFFFFFGG         ", "           GGGGG           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "           EEEEE           ", "         EEGGGGGEE         ",
            "       EEGGGGGGGGGEE       ", "      EGGGG-----GGGGE      ", "      EGG---------GGE      ",
            "     EGG-----------GGE     ", "     EGG-----------GGE     ", "    EGG-------------GGE    ",
            "    EGG-------------GGE    ", "    EGG-------------GGE    ", "    EGG-------------GGE    ",
            "    EGG-------------GGE    ", "     EGG-----------GGE     ", "     EGG-----------GGE     ",
            "      EGG---------GGE      ", "      EGGGG-----GGGGE      ", "       EEGGGGGGGGGEE       ",
            "         EEGGGGGEE         ", "           EEEEE           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "           GGGGG           ",
            "         GGGGGGGGG         ", "       GGGGFFFFFGGGG       ", "       GGFF-----FFGG       ",
            "      GGF---------FGG      ", "      GGF---------FGG      ", "     GGF-----------FGG     ",
            "     GGF-----------FGG     ", "     GGF-----------FGG     ", "     GGF-----------FGG     ",
            "     GGF-----------FGG     ", "      GGF---------FGG      ", "      GGF---------FGG      ",
            "       GGFF-----FFGG       ", "        GGGFFFFFGGGG       ", "         GGGGGGGGG         ",
            "           GGGGG           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "           GGGGG           ", "         GGGGGGGGG         ",
            "        GGGFFFFFGGG        ", "        GGF-----FGG        ", "       GGF-------FGG       ",
            "       GGF-------FGG       ", "       GGF-------FGG       ", "       GGF-------FGG       ",
            "       GGF-------FGG       ", "        GGF-----FGG        ", "        GGGFFFFFGGG        ",
            "         GGGGGGGGG         ", "           GGGGG           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "           GGGGG           ", "          GGGGGGG          ", "         GGGAAAGGG         ",
            "         GGAAAAAGG         ", "         GGAAAAAGG         ", "         GGAAAAAGG         ",
            "         GGGAAAGGG         ", "          GGGGGGG          ", "           GGGGG           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " } };

    private static IStructureDefinition<MTECriticalSingularityCompressor> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
        // 失控节点定位位（导出为泥土）：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）。
        // noPlacement()：构建/全息投影不放置奇点方块——该位保持空气，奇点仅由机器运行时惰性生成
        IStructureElementCheckOnly<MTECriticalSingularityCompressor> singularityLocator = new IStructureElementCheckOnly<MTECriticalSingularityCompressor>() {

            @Override
            public boolean check(MTECriticalSingularityCompressor t, World world, int x, int y, int z) {
                // 结构判定：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）；
                // CheckOnly 不放置：构建/全息投影保持空气，奇点仅由机器运行时惰性生成
                Block block = world.getBlock(x, y, z);
                return block == BlockLoader.blockRunawaySingularity || block.isAir(world, x, y, z);
            }
        };

        return StructureDefinition.<MTECriticalSingularityCompressor>builder()
            .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
            // ' ' = skip — handled by StructureLib addShape, no addElement needed
            // '~' = controller — handled by StructureLib addShape, no addElement needed
            .addElement('-', isAir())
            .addElement('A', ofBlock(getTier2GlassBlock(), 3))
            .addElement('B', ofBlock(getTier2FrameBlock(), getTier2FrameMeta()))
            .addElement('C', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings"), 6))
            .addElement('D', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings"), 15))
            .addElement('E', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings4"), 6))
            .addElement('F', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings4"), 7))
            .addElement(
                'G',
                ofChain(
                    ofBlock(GregTechAPI.sBlockCasings8, 6),
                    buildHatchAdder(MTECriticalSingularityCompressor.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTECriticalSingularityCompressor.class)
                        .atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTECriticalSingularityCompressor.class)
                        .atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTECriticalSingularityCompressor.class)
                        .atLeast(SingularityHatchElement.SteamOutputHatch)
                        .casingIndex(casingIndex)
                        .hint(2)
                        .build()))
            .addElement('H', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockframes"), 70))
            .addElement('I', singularityLocator)
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

        if ((mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty())
            || mOutputBusses.isEmpty()) {
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
    @Nullable
    protected EntanglementSpec getEntanglementSpec() {
        // I 定位块：形状偏移 (a+0, b+0, c+11)（I 字符在 slice10 行13 列13，控制器在 slice10 行2 列13），
        // 经 ExtendedFacing 换算世界偏移（与 checkPiece 同源映射）
        Vec3Impl off = getExtendedFacing().getWorldOffset(new Vec3Impl(0, 0, 11));
        return new EntanglementSpec(off.get0(), off.get1(), off.get2(), 10.0D, 0.0D, 0.0D, -1, -1, "gray");
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = super.createTooltip();
        tt.addSeparator()
            .beginStructureBlock(27, 27, 21, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1);
        if (requiresInputBus()) {
            tt.addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1);
        }
        tt.addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1);
        if (requiresOutputHatch()) {
            tt.addOutputHatch(StatCollector.translateToLocal(keyPrefix + "output_hatch"), 1);
        }
        tt.addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(keyPrefix + "desc6"))
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
        return processAggregationCycle();
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTECriticalSingularityCompressorGui(this);
    }
}
