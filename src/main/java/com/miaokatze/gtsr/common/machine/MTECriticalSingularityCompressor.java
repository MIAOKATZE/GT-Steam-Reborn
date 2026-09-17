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
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.event.GTSRSingularityDimSolidifyEvent;
import com.miaokatze.gtsr.common.event.GTSRSingularityDimTearEvent;
import com.miaokatze.gtsr.common.event.GTSRSingularityOverlimitEvent;
import com.miaokatze.gtsr.common.event.GTSRSingularityStructCollapseEvent;
import com.miaokatze.gtsr.common.gui.MTECriticalSingularityCompressorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import bartworks.common.loaders.ItemRegistry;
import bartworks.system.material.Werkstoff;
import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
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

    /** T6 超限上限（%）：>=1000 触发装置超限爆炸链（v1.1 §2.4） */
    private static final double OVERLIMIT_CAP = 1000.0d;
    /** 结构崩解失稳阈值（%）：>100 触发结构崩解爆炸链 */
    private static final double INSTABILITY_COLLAPSE_THRESHOLD = 100.0d;
    /** 维度撕裂爆炸阈值（%）：>100 触发维度撕裂爆炸链（紫色 rogue） */
    private static final double TEAR_THRESHOLD = 100.0d;
    /** 撕裂积累条件（v1.1 §2.6）：失稳 > 50 且超限 > 800（同条件机内奇点变紫） */
    private static final double TEAR_INSTABILITY_GATE = 50.0d;
    private static final double TEAR_OVERLIMIT_GATE = 800.0d;
    /** UU 物质流体双名（CMA:1325-1329 同款：GTNH 注册名 ic2uumatter 优先，回退旧名 uumatter） */
    private static final String UU_MATTER_FLUID_NAME = "ic2uumatter";
    private static final String UU_MATTER_FLUID_NAME_LEGACY = "uumatter";

    private static IStructureDefinition<MTECriticalSingularityCompressor> STRUCTURE_DEFINITION;
    private static Block TIER2_FRAME_BLOCK;
    private static Integer TIER2_FRAME_META;
    private static Block TIER2_GLASS_BLOCK;

    public MTECriticalSingularityCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTECriticalSingularityCompressor(String aName) {
        super(aName);
        registerProgressEntries();
    }

    /** 注册终端数值词条（顺序 = GUI 显示顺序；热量 mHeat 口径 0-1，显示 ×100） */
    private void registerProgressEntries() {
        registerEntry(
            "temperature",
            "gtsr.gui.critical_singularity_compressor.heat",
            "%.2f%%",
            EnumChatFormatting.RED,
            () -> mHeat * 100.0d);
        // T6 超限/失稳/撕裂词条（全部 .showZero()：0% 为安全态，GUI/红石仓监控需常显，v1.20.22 零值默认隐藏约定）
        registerEntry(
            GTSRProgressEntry
                .of(
                    "overlimit",
                    "gtsr.gui.critical_singularity_compressor.overlimit",
                    "%.2f%%",
                    EnumChatFormatting.RED,
                    () -> mOverlimit)
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of(
                    "instability",
                    "gtsr.gui.critical_singularity_compressor.instability",
                    "%.2f%%",
                    EnumChatFormatting.GOLD,
                    () -> mInstability)
                .showZero());
        registerEntry(
            GTSRProgressEntry
                .of(
                    "tear",
                    "gtsr.gui.critical_singularity_compressor.tear",
                    "%.2f%%",
                    EnumChatFormatting.LIGHT_PURPLE,
                    () -> mTear)
                .showZero());
    }

    // region T6 超限模式（v1.1 机制不变，v1.2 适配）：三值全实装，三爆炸链首触即止

    /** 本秒是否实扣过 UU 物质（蠕变豁免：无 UU 时撕裂每秒 +0.1）；仅服务端秒级管线内读写 */
    private boolean mUUConsumedThisSecond = false;

    @Override
    protected boolean hasOverlimitMechanics() {
        return true;
    }

    @Override
    protected boolean hasTearMechanics() {
        return true;
    }

    /** CESS 热量乘数：(1 + 超限/100)(1 + 撕裂/100)（与 SSE 对称：两者均无失稳项，v1.2 §4-35） */
    @Override
    protected double getHeatMultiplier() {
        return (1.0d + mOverlimit / 100.0d) * (1.0d + mTear / 100.0d);
    }

    /**
     * 三流体消耗：pyrotheum 提超限 + cryotheum 降温/超扣停机（基类共享实现）+ UU 物质降撕裂/超扣停机。
     */
    @Override
    protected void consumeOverlimitFluids() {
        mUUConsumedThisSecond = false;
        consumePyrotheumAndCryotheum();
        // UU 物质（双名探测）：全量消耗 → 撕裂 -0.001x；超扣（减少量 > 当前撕裂）→ clamp 0 + 停机 + 聊天「维度固化→停机」
        FluidStack uuProbe = FluidRegistry.getFluidStack(UU_MATTER_FLUID_NAME, 1);
        if (uuProbe == null) uuProbe = FluidRegistry.getFluidStack(UU_MATTER_FLUID_NAME_LEGACY, 1);
        int uu = drainAllOfFluid(uuProbe);
        if (uu > 0) {
            mUUConsumedThisSecond = true;
            double reduction = 0.001d * uu;
            if (reduction > mTear) {
                mTear = 0.0d;
                GTSRSingularityDimSolidifyEvent event = newMachineEvent(GTSRSingularityDimSolidifyEvent::new);
                event.setBroadcast(true)
                    .sendChat(); // 聊天先于停机发送
                getBaseMetaTileEntity().disableWorking();
            } else {
                mTear -= reduction;
            }
        }
    }

    /**
     * 撕裂积累（v1.1 §2.6）：工作中且失稳>50 且超限>800 → 每秒 +失稳/100+超限/1000；
     * 蠕变：工作中且撕裂>0 且本秒未实扣 UU → 每秒 +0.1。（停机衰减与爆炸链由基类管线统一处理）
     */
    @Override
    protected void accumulateTear(boolean working) {
        if (working && mInstability > TEAR_INSTABILITY_GATE && mOverlimit > TEAR_OVERLIMIT_GATE) {
            mTear += mInstability / 100.0d + mOverlimit / 1000.0d;
        }
        if (working && mTear > 0.0d && !mUUConsumedThisSecond) {
            mTear += 0.1d; // 蠕变
        }
    }

    /** 撕裂积累条件成立（失稳>50 且超限>800）：机内奇点 spec 颜色 gray→purple 的同一状态源 */
    private boolean isTearAccumulating() {
        return mInstability > TEAR_INSTABILITY_GATE && mOverlimit > TEAR_OVERLIMIT_GATE;
    }

    /**
     * 三爆炸链（顺序首触即止，v1.1 §2.7）：装置超限（>=1000%）→ 结构崩解（失稳>100%）→ 维度撕裂（>100%，紫色 rogue）。
     * 装置超限/结构崩解黑色 rogue `20 5 5 1200 0 black 60`（60 秒自毁）；维度撕裂紫色 rogue `50 20 10 24000 0 purple 100`（20 分钟）。
     */
    @Override
    protected boolean checkOverlimitExplosionChain() {
        if (mOverlimit >= OVERLIMIT_CAP) {
            detonateRunawaySingularity(
                newMachineEvent(GTSRSingularityOverlimitEvent::new),
                40.0d,
                80.0d,
                5.0d,
                4800,
                0,
                "black",
                60.0d);
            return true;
        }
        if (mInstability > INSTABILITY_COLLAPSE_THRESHOLD) {
            detonateRunawaySingularity(
                newMachineEvent(GTSRSingularityStructCollapseEvent::new),
                40.0d,
                80.0d,
                5.0d,
                4800,
                0,
                "black",
                60.0d);
            return true;
        }
        if (mTear > TEAR_THRESHOLD) {
            detonateRunawaySingularity(
                newMachineEvent(GTSRSingularityDimTearEvent::new),
                90.0d,
                240.0d,
                15.0d,
                24000,
                0,
                "black",
                100.0d);
            return true;
        }
        return false;
    }

    // endregion

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
        return 0.005d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 5000L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return false;
    }

    // v1.10.59 仅接受致密态变体
    @Override
    protected boolean isDenseSteamOnly() {
        return true;
    }

    // v1.10.59 单级机器不显示等级
    @Override
    public boolean isHideTierInGui() {
        return true;
    }

    // NEI 展示用伪合成表：实际处理逻辑在 checkProcessing()，本 map 仅用于 NEI 显示。
    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.criticalSingularityCompressorRecipes;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return GTSRItemList.CriticalSteamEntangledSingularity.get(1);
    }

    // 奇点模式预留出口：输入总线可放置但非必需，后续接入奇点模式时恢复强制并接通消费逻辑
    @Override
    protected boolean requiresInputBus() {
        return false;
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
                return block == BlocksGTSR.runawaySingularity || block.isAir(world, x, y, z);
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
        // T6 状态化颜色（v1.1 §2.6）：撕裂积累条件成立 → purple，解除 → gray；
        // 颜色随 spec 变化由基类自愈分支（参数比对不符即 setParams 重应用）自动落到机内奇点
        String color = isTearAccumulating() ? "purple" : "gray";
        return new EntanglementSpec(off.get0(), off.get1(), off.get2(), 9.0D, 0.0D, 0.0D, -1, -1, color, 40.0D);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(keyPrefix + "type"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc_2"))
            .addInfo(StatCollector.translateToLocal(keyPrefix + "desc7"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2_2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc8"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc3_2"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"));
        // T6 三值机制说明（用户拍板追加）：在既有描述之后、结构段之前追加，键未配置时零输出
        addOverlimitTooltipLines(tt, keyPrefix);
        tt.addSeparator()
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列
            .beginStructureBlock(27, 21, 27, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.critical_singularity_compressor.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1);
        tt.addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1);
        tt.addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1);
        tt.addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(keyPrefix + "desc6"))
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "tier1_blocks"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
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
