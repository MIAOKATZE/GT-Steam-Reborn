package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofAnyWater;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
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
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.IShiftRightClickDecalcifiable;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.gui.MTELargeSolarOverpressureArrayGui;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;

public class MTELargeSolarOverpressureArray extends MTEEnhancedMultiBlockBase<MTELargeSolarOverpressureArray>
    implements IConstructable, ISurvivalConstructable, IShiftRightClickDecalcifiable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 4;
    private static final int DEPTH_OFF_SET = 1;

    // GTUDK 导出结构：27 列 × 7 层 × 30 行；'~' 控制器在 (列 3, 层 4, 行 1)；
    // b=slice（层，顶→底）、c=row（深度线，前面第一）、a=char（列，自左向右）
    private static final String[][] SHAPE_MAIN = {
        { "                           ", "  AAA                      ", " A---A                     ",
            " A---A                     ", " A---A                     ", "  AAA                      ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", "  AAA                      ", " A---A                     ",
            " A---A                     ", " A---A                     ", "  AAA                      ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           ",
            "                           ", "                           ", "                           " },
        { "                           ", " DAAAAAAAA           AAAAA ", " AFFFA                    A",
            " AFFFA                    A", " AFFFA                    A", " AAAA                   AA ",
            " A                         ", " A                      AA ", " A                        A",
            " A                        A", "                          A", "                        AA ",
            "                           ", "                        AA ", "                          A",
            "                          A", "                          A", "                        AA ",
            "                           ", "                        AA ", "                          A",
            "                          A", "                          A", "                        AA ",
            " A                         ", " A                      AA ", " A                        A",
            " A                        A", " A                        A", "  AAAAA              AAAAA " },
        { "                           ", " DAAAAAAAAAAAAAAAAAAAAAAAAD", " ACCCAGGGGGGGGGGGGGGGGGGGGA",
            " ACCCCGGGGGGGGGGGGGGGGGGGGA", " ACCCAGGGGGGGGGGGGGGGGGGGGA", " AAAAAAAAAAAAAAAAAAAAAAAAAD",
            " AGGGAD    D    D    D     ", " AGGGAAAAAAAAAAAAAAAAAAAAAD", " AGGGGGGGGGGGGGGGGGGGGGGGGA",
            " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGAAAAAAAAAAAAAAAAAAAAAD",
            " AGGGAD    D    D    D     ", " AGGGAAAAAAAAAAAAAAAAAAAAAD", " AGGGGGGGGGGGGGGGGGGGGGGGGA",
            " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGAAAAAAAAAAAAAAAAAAAAAD",
            " AGGGAD    D    D    D     ", " AGGGAAAAAAAAAAAAAAAAAAAAAD", " AGGGGGGGGGGGGGGGGGGGGGGGGA",
            " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGAAAAAAAAAAAAAAAAAAAAAD",
            " AGGGAD    D    D    D     ", " AGGGAAAAAAAAAAAAAAAAAAAAAD", " AGGGGGGGGGGGGGGGGGGGGGGGGA",
            " AGGGGGGGGGGGGGGGGGGGGGGGGA", " AGGGGGGGGGGGGGGGGGGGGGGGGA", " DAAAAAAAAAAAAAAAAAAAAAAAAD" },
        { "                           ", " DA~AAAAAAAAAAAAAAAAAAAAAAD", " ACCCAHHHHHHHHHHHHHHHHHHHHA",
            " ACCCCEEEEEEEEEEEEEEEEEEEEA", " ACCCAHHHHHHHHHHHHHHHHHHHHA", " AACAAAAAAAAAAAAAAAAAAAAAAD",
            " AHEHAD    D    D    D     ", " AHEHAAAAAAAAAAAAAAAAAAAAAD", " AHEHHHHHHHHHHHHHHHHHHHHHHA",
            " AHEEEEEEEEEEEEEEEEEEEEEEEA", " AHEHHHHHHHHHHHHHHHHHHHHHHA", " AHEHAAAAAAAAAAAAAAAAAAAAAD",
            " AHEHAD    D    D    D     ", " AHEHAAAAAAAAAAAAAAAAAAAAAD", " AHEHHHHHHHHHHHHHHHHHHHHHHA",
            " AHEEEEEEEEEEEEEEEEEEEEEEEA", " AHEHHHHHHHHHHHHHHHHHHHHHHA", " AHEHAAAAAAAAAAAAAAAAAAAAAD",
            " AHEHAD    D    D    D     ", " AHEHAAAAAAAAAAAAAAAAAAAAAD", " AHEHHHHHHHHHHHHHHHHHHHHHHA",
            " AHEEEEEEEEEEEEEEEEEEEEEEEA", " AHEHHHHHHHHHHHHHHHHHHHHHHA", " AHEHAAAAAAAAAAAAAAAAAAAAAD",
            " AHEHAD    D    D    D     ", " AHEHAAAAAAAAAAAAAAAAAAAAAD", " AHEHHHHHHHHHHHHHHHHHHHHHHA",
            " AHEEEEEEEEEEEEEEEEEEEEEEEA", " AHHHHHHHHHHHHHHHHHHHHHHHHA", " DAAAAAAAAAAAAAAAAAAAAAAAAD" },
        { "                           ", " DAAABBBBBBBBBBBBBBBBBBBBBD", " ACCCACCCCCCCCCCCCCCCCCCCCB",
            " ACCCCHHHHHHHHHHHHHHHHHHHHB", " ACCCACCCCCCCCCCCCCCCCCCCCB", " AACABBBBBBBBBBBBBBBBBBBBBD",
            " BCHCBD    D    D    D     ", " BCHCBBBBBBBBBBBBBBBBBBBBBD", " BCHCCCCCCCCCCCCCCCCCCCCCCB",
            " BCHHHHHHHHHHHHHHHHHHHHHHHB", " BCHCCCCCCCCCCCCCCCCCCCCCCB", " BCHCBBBBBBBBBBBBBBBBBBBBBD",
            " BCHCBD    D    D    D     ", " BCHCBBBBBBBBBBBBBBBBBBBBBD", " BCHCCCCCCCCCCCCCCCCCCCCCCB",
            " BCHH HHHHHHHHHHHHHHHHHHHHB", " BCHCCCCCCCCCCCCCCCCCCCCCCB", " BCHCBBBBBBBBBBBBBBBBBBBBBD",
            " BCHCBD    D    D    D     ", " BCHCBBBBBBBBBBBBBBBBBBBBBD", " BCHCHCCCCCCCCCCCCCCCCCCCCB",
            " BCHH HHHHHHHHHHHHHHHHHHHHB", " BCHCHCCCCCCCCCCCCCCCCCCCCB", " BCHCBBBBBBBBBBBBBBBBBBBBBD",
            " BCHCBD    D    D    D     ", " BCHCBBBBBBBBBBBBBBBBBBBBBD", " BCHCCCCCCCCCCCCCCCCCCCCCCB",
            " BCHHHHHHHHHHHHHHHHHHHHHHHB", " BCCCCCCCCCCCCCCCCCCCCCCCCB", " DBBBBBBBBBBBBBBBBBBBBBBBBD" },
        { " AAAA                      ", "ADAAAAAAAAAAAAAAAAAAAAAAAAD", "AAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAD",
            " AAAAAD    D    D    D     ", " AAAAAAAAAAAAAAAAAAAAAAAAAD", " AAAAAAAAAAAAAAAAAAAAAAAAAA",
            " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAD",
            " AAAAAD    D    D    D     ", " AAAAAAAAAAAAAAAAAAAAAAAAAD", " AAAAAAAAAAAAAAAAAAAAAAAAAA",
            " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAD",
            " AAAAAD    D    D    D     ", " AAAAAAAAAAAAAAAAAAAAAAAAAD", " AAAAAAAAAAAAAAAAAAAAAAAAAA",
            " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAD",
            " AAAAAD    D    D    D     ", " AAAAAAAAAAAAAAAAAAAAAAAAAD", " AAAAAAAAAAAAAAAAAAAAAAAAAA",
            " AAAAAAAAAAAAAAAAAAAAAAAAAA", " AAAAAAAAAAAAAAAAAAAAAAAAAA", " DAAAAAAAAAAAAAAAAAAAAAAAAD" } };

    // 顶面方块形状偏移缓存（静态惰性初始化，运行时扫描 SHAPE_MAIN 计算，勿硬编码数量）
    private static List<int[]> mTopSurfaceOffsets = null;
    // F 泥土粒子位形状偏移缓存（9 个）
    private static List<int[]> mParticleOffsets = null;

    private static IStructureDefinition<MTELargeSolarOverpressureArray> STRUCTURE_DEFINITION = null;
    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    protected int mSetTier = -1;
    public double mHeat = 0.0d;
    public double mCalcification = 0.0d;
    public long mRunningTicks = 0L;
    // 超压模式：mHeat 上限放开至 200%（升温速率 ×0.2、降温速率 ×2），产出随 mHeat 线性至 200%
    public boolean mOverpressure = false;
    // 缺水断电提示已发送标志：恢复开机（onEnableWorking）时清除，保证 gtsr.chat.no_water 只发一次
    private boolean mNoWaterNotified = false;
    protected boolean mIsHeating = false;
    protected boolean mIsOperating = false;
    protected int tierCasing = -1;
    protected int tierGlass = -1;
    protected int tierMetal = -1;
    protected int tierPipe = -1;
    protected int tierGear = -1;
    protected int tierFrame = -1;

    // 太阳可见比例（0~1）：顶面被遮挡时加热与产出按比例下降
    public double mSunRatio = 1.0d;
    private boolean needsWaterFill = false;

    // 钙化延迟统一为 1 小时；满垢后产出降至 1%，并每 10 分钟向所有者发送提醒
    private static final long CALCIFICATION_DELAY_TICKS = 3600L * 20;
    private static final long CALCIFICATION_WARN_INTERVAL_TICKS = 600L * 20;
    private long mCalcificationWarnTimer = 0L;

    private static final int STEAM_PER_WATER = 160;

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    public MTELargeSolarOverpressureArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeSolarOverpressureArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTELargeSolarOverpressureArray_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTELargeSolarOverpressureArray_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeSolarOverpressureArray(mName);
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

    public int getTier() {
        return mSetTier;
    }

    public boolean isBronze() {
        return mSetTier == 1;
    }

    public boolean isSteel() {
        return mSetTier == 2;
    }

    public boolean isNickel() {
        return mSetTier == 3;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getMetalTier(Block block, int meta) {
        if (block == Blocks.gold_block && meta == 0) return 1;
        if (block == GregTechAPI.sBlockMetal5 && meta == 4) return 2;
        if (block == GregTechAPI.sBlockMetal6 && meta == 10) return 3;
        return null;
    }

    @Nullable
    public static Integer getGlassTier(Block block, int meta) {
        if (block == Blocks.glass && meta == 0) return 1;
        // 防爆玻璃：通过兼容层自动适配 beta-1（IC2 blockAlloyGlass）与 beta-2（GT5U sBlockGlass1 meta 10）
        if (block == GTVersionCompat.getReinforcedGlassBlock() && meta == GTVersionCompat.getReinforcedGlassMeta())
            return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2 || mSetTier == 3) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateAllHatchTextures() {
        if (mSetTier <= 0) return;
        int textureID = getCasingTextureID();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch h) {
                h.updateTexture(textureID);
            }
        }

        if (mSetTier >= 2) {
            for (IMetaTileEntity hatch : mInputHatches) {
                if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch h) {
                    h.updateTexture(textureID);
                }
            }
        }
        // v1.10.6：样板仓（mDualInputHatches）纹理更新（InputHatch 元素可接受样板仓）
        for (IDualInputHatch dual : mDualInputHatches) {
            if (dual != null) dual.updateTexture(textureID);
        }
    }

    @Override
    public IStructureDefinition<MTELargeSolarOverpressureArray> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeSolarOverpressureArray>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
                .addElement(
                    'A',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            t -> {},
                            ofBlocksTiered(
                                MTELargeSolarOverpressureArray::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierCasing = tier,
                                (MTELargeSolarOverpressureArray t) -> t.tierCasing)),
                        buildHatchAdder(MTELargeSolarOverpressureArray.class).atLeast(OutputHatch, InputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'B',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getGearTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierGear = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierGear))
                .addElement(
                    'C',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getPipeTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierPipe = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierPipe))
                .addElement(
                    'D',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierFrame = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierFrame))
                .addElement(
                    'E',
                    ofChain(
                        // 水位：圆石位可被水替代（洗矿机同款机制），否则接受空气（注水前）
                        ofAnyWater(false),
                        isAir()))
                .addElement('F', isAir())
                .addElement(
                    'G',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getGlassTier,
                        ImmutableList.of(
                            Pair.of(Blocks.glass, 0),
                            // 防爆玻璃：通过兼容层自动适配 beta-1（IC2 blockAlloyGlass）与 beta-2（GT5U sBlockGlass1 meta 10）
                            Pair.of(
                                GTVersionCompat.getReinforcedGlassBlock(),
                                GTVersionCompat.getReinforcedGlassMeta())),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierGlass = Math.max(t.tierGlass, tier),
                        (MTELargeSolarOverpressureArray t) -> t.tierGlass))
                .addElement(
                    'H',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getMetalTier,
                        ImmutableList.of(
                            Pair.of(Blocks.gold_block, 0),
                            Pair.of(GregTechAPI.sBlockMetal5, 4),
                            Pair.of(GregTechAPI.sBlockMetal6, 10)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.tierMetal = tier,
                        (MTELargeSolarOverpressureArray t) -> t.tierMetal))
                .addElement('-', isAir())
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mSetTier = -1;
        tierCasing = -1;
        tierGlass = -1;
        tierMetal = -1;
        tierPipe = -1;
        tierGear = -1;
        tierFrame = -1;
        needsWaterFill = false;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        if (tierCasing == 1 && tierGear == 1 && tierPipe == 1 && tierFrame == 1 && tierGlass >= 1 && tierMetal == 1) {
            mSetTier = 1;
        } else if (tierCasing == 2 && tierGear == 2
            && tierPipe == 2
            && tierFrame == 2
            && tierGlass >= 1
            && tierMetal == 2) {
                mSetTier = 2;
            } else if (tierCasing == 2 && tierGear == 2
                && tierPipe == 2
                && tierFrame == 2
                && tierGlass >= 1
                && tierMetal == 3) {
                    mSetTier = 3;
                }

        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (!hasValidOutputHatchesForTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mInputHatches.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateAllHatchTextures();
        needsWaterFill = true;
        calculateSunRatio();
    }

    private boolean hasValidOutputHatchesForTier() {
        return !mOutputHatches.isEmpty();
    }

    // 顶面方块形状偏移列表：对每个 (列 a, 行 c) 取 slice 索引最小（最高）的非空格/非'-'/非'~' 字符位置，
    // 记录形状差 (a-3, b-4, c-1)（相对控制器），惰性缓存（共 694 个顶面位置，勿硬编码）。
    private static List<int[]> getTopSurfaceOffsets() {
        if (mTopSurfaceOffsets == null) {
            List<int[]> offsets = new ArrayList<>();
            for (int c = 0; c < SHAPE_MAIN[0].length; c++) {
                for (int a = 0; a < SHAPE_MAIN[0][c].length(); a++) {
                    for (int b = 0; b < SHAPE_MAIN.length; b++) {
                        char ch = SHAPE_MAIN[b][c].charAt(a);
                        if (ch == ' ' || ch == '-' || ch == '~') continue;
                        offsets.add(new int[] { a - HORIZONTAL_OFF_SET, b - VERTICAL_OFF_SET, c - DEPTH_OFF_SET });
                        break;
                    }
                }
            }
            mTopSurfaceOffsets = offsets;
        }
        return mTopSurfaceOffsets;
    }

    // F 泥土粒子位形状偏移（9 个）：(列 2-4, 层 2, 行 2-4) - (3, 4, 1)
    private static List<int[]> getParticleOffsets() {
        if (mParticleOffsets == null) {
            List<int[]> offsets = new ArrayList<>();
            for (int b = 0; b < SHAPE_MAIN.length; b++) {
                for (int c = 0; c < SHAPE_MAIN[b].length; c++) {
                    for (int a = 0; a < SHAPE_MAIN[b][c].length(); a++) {
                        if (SHAPE_MAIN[b][c].charAt(a) == 'F') {
                            offsets.add(new int[] { a - HORIZONTAL_OFF_SET, b - VERTICAL_OFF_SET, c - DEPTH_OFF_SET });
                        }
                    }
                }
            }
            mParticleOffsets = offsets;
        }
        return mParticleOffsets;
    }

    /**
     * 太阳可见比例：顶面各位置经 ExtendedFacing 换算世界偏移并叠加控制器坐标，
     * 用 canBlockSeeTheSky(wy + 1) 判定可见性（y+1 防止顶面方块自身遮挡；1.7.10 heightMap O(1) 查询）。
     */
    private void calculateSunRatio() {
        List<int[]> offsets = getTopSurfaceOffsets();
        if (offsets.isEmpty()) {
            mSunRatio = 1.0d;
            return;
        }
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base.getWorld();
        int cx = base.getXCoord();
        int cy = base.getYCoord();
        int cz = base.getZCoord();
        int visible = 0;
        for (int[] off : offsets) {
            Vec3Impl worldOff = getExtendedFacing().getWorldOffset(new Vec3Impl(off[0], off[1], off[2]));
            if (world.canBlockSeeTheSky(cx + worldOff.get0(), cy + worldOff.get1() + 1, cz + worldOff.get2())) {
                visible++;
            }
        }
        mSunRatio = (double) visible / offsets.size();
    }

    /**
     * 客户端：每 tick 按热量 mHeat 生成上升白色云朵粒子（仿砖高炉 vertical motion 0.3）。
     * 粒子期望数 = mHeat / 0.5：mHeat=50% → 1 个/tick（现状基准），100% → 2 个/tick，25% → 平均 0.5 个/tick
     * （小数部分用概率平滑）。
     */
    private void spawnCloudParticle() {
        if (mHeat <= 0.0d) return;
        double expected = mHeat / 0.5d;
        int n = (int) expected;
        if (getBaseMetaTileEntity().getWorld().rand.nextDouble() < expected - n) n++;
        for (int i = 0; i < n; i++) {
            spawnOneParticle();
        }
    }

    /** 在随机一个 F 泥土位生成单个上升白色云朵粒子 */
    private void spawnOneParticle() {
        List<int[]> offsets = getParticleOffsets();
        if (offsets.isEmpty()) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base.getWorld();
        int[] off = offsets.get(world.rand.nextInt(offsets.size()));
        Vec3Impl worldOff = getExtendedFacing().getWorldOffset(new Vec3Impl(off[0], off[1], off[2]));
        world.spawnParticle(
            "cloud",
            base.getXCoord() + worldOff.get0() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            base.getYCoord() + worldOff.get1() + 0.5D,
            base.getZCoord() + worldOff.get2() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) {
            // 有热量即渲染气体粒子动画（数量 ∝ mHeat，见 spawnCloudParticle）
            if (mHeat > 0.0d) {
                spawnCloudParticle();
            }
            return;
        }
        if (!mMachine || mSetTier <= 0) {
            // 结构失效时热量仍按衰减速度降至 0，保证客户端经 updateData 同步后停止粒子渲染
            mHeat -= getHeatDecreaseSpeed();
            if (mHeat < 0) mHeat = 0;
            return;
        }

        if (!aBaseMetaTileEntity.isAllowedToWork()) {
            mIsOperating = false;
            mCurrentSteamOutput = 0;
        }

        tickCalcificationWarning();

        World world = aBaseMetaTileEntity.getWorld();
        boolean isClearWeather = !world.isRaining() && !world.isThundering()
            || aBaseMetaTileEntity.getBiome().rainfall == 0.0F;
        boolean isDay = world.isDaytime();

        if (aTick % 200 == 0) {
            calculateSunRatio();
        }

        if (needsWaterFill && aTick % 20 == 0) {
            // 注水轴序：fillStructureWithWater 假定结构布局为 [深度][层][列]（洗矿机手写布局），
            // 本结构 GTUDK 布局为 [层][行][列]，须传 transpose 后的形状（数组=行/深度、串=层/垂直）。
            if (GTStructureUtility.fillStructureWithWater(
                aBaseMetaTileEntity,
                getExtendedFacing(),
                transpose(SHAPE_MAIN),
                HORIZONTAL_OFF_SET,
                VERTICAL_OFF_SET,
                DEPTH_OFF_SET,
                'E')) {
                needsWaterFill = false;
            }
        }

        if (aTick % 20 == 0) {
            // 超压模式热量跌破 100% 时自动退出（关闭后 mOverpressure 已为 false，提示仅触发一次）
            if (mOverpressure && mHeat < 1.0d) {
                mOverpressure = false;
                getBaseMetaTileEntity().markDirty();
                sendChatToOwner("gtsr.chat.overpressure.off");
            }

            boolean canHeat = isClearWeather && isDay && mSunRatio > 0;
            boolean wasHeating = mIsHeating;

            // 加载豁免期（GT5U mStartUpCheck 100 tick 内未检结构，"启动中....."阶段）：冻结热量，
            // 避免退出重进后结构检测延迟期间异常降温（与地热 mStructureGraceTicks/焦炉 mMachine 门控同语义）
            if (mStartUpCheck > 0) {
                mIsHeating = false;
            } else if (canHeat && aBaseMetaTileEntity.isAllowedToWork()) {
                // 超压模式升温速率 ×0.2
                mHeat += getHeatIncreaseSpeed() * mSunRatio * (mOverpressure ? 0.2d : 1.0d);
                if (mHeat > (mOverpressure ? 2.0d : 1.0d)) mHeat = mOverpressure ? 2.0d : 1.0d;
                mIsHeating = true;
            } else {
                // 超压模式降温速率 ×2
                mHeat -= getHeatDecreaseSpeed() * (mOverpressure ? 2.0d : 1.0d);
                if (mHeat < 0) mHeat = 0;
                mIsHeating = false;
            }

            if (wasHeating != mIsHeating) {
                aBaseMetaTileEntity.issueTextureUpdate();
            }
        }

        if (mMaxProgresstime > 0) {
            if (++mProgresstime >= mMaxProgresstime) {
                mProgresstime = 0;
                outputSteam();
            }
        } else {
            checkProcessing();
        }
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        if (!mMachine || mSetTier <= 0 || !getBaseMetaTileEntity().isAllowedToWork()) {
            mIsOperating = false;
            mCurrentSteamOutput = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // v1.10.60：水消耗路径重构——探测改 GTSRHatchFluidAccess.probeFluidAmountAcross（getTankInfo 安全模拟：
        // ME 输入仓窗口外每槽报真实可得量、普通仓报存量），实扣改 depleteFluidAcross（双版本 2 参 drain 语义一致，
        // 无 beta-1 首遍模拟即真提取的双扣问题）；删除样板仓 dual 合并探测与 depleteFluidFromDuals 兜底
        // （窗口外静默失效=免费流体）。蒸馏水优先：存在蒸馏水时优先消耗（与地热锅炉一致，避免钙化）。
        float solarBooster = calculateSolarBooster();
        int baseProduction = getBaseSteamProduction();
        int maxNeed = baseProduction / STEAM_PER_WATER;
        FluidStack distilledWant = GTModHandler.getDistilledWater(maxNeed);
        FluidStack waterWant = GTModHandler.getWater(maxNeed);
        boolean hasDistilledWater = GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, distilledWant) > 0;
        boolean hasWater = GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, waterWant) > 0;
        boolean hasWaterInSystem = hasDistilledWater || hasWater;

        if (hasWaterInSystem && mHeat > 0.01d) {
            long available = hasDistilledWater
                ? GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, distilledWant)
                : GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, waterWant);

            long calcificationDelayTicks = getCalcificationDelayTicks();
            long calcificationInterval = getCalcificationFullTime() / 100;

            // 超压 + 普通水：跳过 mRunningTicks 延迟门槛（立刻开始结垢）、增量 ×20（0.01→0.2，
            // 满垢时间 = 原满垢 1/20）；interval 周期调制保留，避免夜间/失败路径逐 tick 高速结垢；蒸馏水豁免保持
            if (mRunningTicks > (mOverpressure ? 0L : calcificationDelayTicks)
                && (mRunningTicks / 20) % calcificationInterval == 0
                && hasWater
                && !hasDistilledWater) {
                mCalcification += mOverpressure ? 0.2d : 0.01d;
                if (mCalcification > 1.0d) mCalcification = 1.0d;
            }

            // 加算口径（v1.10.51）：总倍率 = 1 + 太阳能额外增幅(solar-1) + 超压额外增幅(heat-1)
            // = solar + heat - 1；热量 200% 只贡献 +100% 额外增幅，叠加太阳能锅炉后最大 ×4.0（额外 +300%）
            double outputFactor = solarBooster + mHeat - 1.0d;

            int consumedWater = (int) (Math.min(available, maxNeed) * outputFactor
                * getCalcificationOutputFactor()
                * mSunRatio);

            if (consumedWater > 0) {
                FluidStack liquidToDeplete = hasDistilledWater ? GTModHandler.getDistilledWater(consumedWater)
                    : GTModHandler.getWater(consumedWater);

                if (GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, liquidToDeplete) >= consumedWater) {
                    int steamAmount = consumedWater * STEAM_PER_WATER;
                    mRunningTicks += 20;
                    mCurrentSteamOutput = steamAmount;

                    FluidStack outputSteam;
                    if (isNickel()) {
                        outputSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", steamAmount);
                    } else {
                        outputSteam = Materials.Steam.getGas(steamAmount);
                    }

                    super.mOutputFluids = new FluidStack[] { outputSteam };
                    super.mMaxProgresstime = 20;
                    super.mEfficiency = getMaxEfficiency(null);
                    mIsOperating = true;

                    return CheckRecipeResultRegistry.SUCCESSFUL;
                } else {
                    // 水消耗失败：断电停机 + 一次性提示，须手动重启（软锤/打开 GUI 点电源开关）
                    stopForMissingWater();
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
            }
        }

        // 系统内无水：断电停机，防止水恢复后自动复活（冷机 mHeat<=0.01 / 太阳被挡等未消耗场景不断电）
        if (!hasWaterInSystem) {
            stopForMissingWater();
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    /** 缺水断电：停机 + 一次性 gtsr.chat.no_water（mNoWaterNotified 防重复，恢复开机时清除） */
    private void stopForMissingWater() {
        mIsOperating = false;
        mCurrentSteamOutput = 0;
        if (!mNoWaterNotified) {
            mNoWaterNotified = true;
            sendChatToOwner("gtsr.chat.no_water");
        }
        getBaseMetaTileEntity().disableWorking();
    }

    /** 向在线所有者发送一条翻译键聊天消息（离线不发送，与 sendCalcificationWarning 同模式） */
    private void sendChatToOwner(String key) {
        UUID ownerUuid = getBaseMetaTileEntity().getOwnerUuid();
        if (ownerUuid == null) return;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP player && player.getUniqueID()
                .equals(ownerUuid)) {
                GTUtility.sendChatTrans(player, key);
                return;
            }
        }
    }

    private void outputSteam() {
        if (super.mOutputFluids != null && super.mOutputFluids.length > 0) {
            FluidStack outputSteam = super.mOutputFluids[0];
            if (outputSteam != null && outputSteam.amount > 0) {
                if (isNickel()) {
                    distributeSuperheatedSteamToOutputHatches(outputSteam.amount);
                } else {
                    distributeSteamToOutputHatches(outputSteam.amount);
                }
            }
        }
        super.mOutputFluids = null;
        mMaxProgresstime = 0;
        checkProcessing();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.solar_array.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc2"))
            .addInfo(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.solar_array.booster_label"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.solar_array.booster_content"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.calcification"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.calcification_d"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.clear_calcification_hint"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.BLUE + "Tier 1 "
                    + EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_bronze")
                    + EnumChatFormatting.GOLD
                    + " 120,000"
                    + EnumChatFormatting.GRAY
                    + " L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.base_output")
                    + EnumChatFormatting.GREEN
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.max_boosted_output")
                    + ": 480,000 L/s)")
            .addInfo(
                EnumChatFormatting.BLUE + "Tier 2 "
                    + EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_steel")
                    + EnumChatFormatting.GOLD
                    + " 180,000"
                    + EnumChatFormatting.GRAY
                    + " L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.base_output")
                    + EnumChatFormatting.GREEN
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.max_boosted_output")
                    + ": 720,000 L/s)")
            .addInfo(
                EnumChatFormatting.BLUE + "Tier 3 "
                    + EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_nickel")
                    + EnumChatFormatting.GOLD
                    + " 240,000"
                    + EnumChatFormatting.GRAY
                    + " L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.base_output")
                    + EnumChatFormatting.GREEN
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.max_boosted_output")
                    + ": 960,000 L/s)"
                    + EnumChatFormatting.GREEN
                    + " ("
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.superheated_steam")
                    + ")")
            .beginStructureBlock(30, 27, 7, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.solar_array.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.solar_array.input_hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.solar_array.steam_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.solar_array.three_tier"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc3"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc4"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc5"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.solar_array.desc6"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.casing"), 1306, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 281, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 255, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.metal"), 381, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.glass"), 381, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 113, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.water_block"), 130, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.particle_block"), 9, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.solar_array.air_block"), 18, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.overpressure.enable"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.overpressure.effects"))
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
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            ITexture frontOverlay = mIsHeating ? TextureFactory.of(OVERLAY_ON) : TextureFactory.of(OVERLAY_OFF);
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()), frontOverlay };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTELargeSolarOverpressureArrayGui(this);
    }

    @Override
    public boolean onShiftRightClick(EntityPlayer aPlayer, ForgeDirection side, float aX, float aY, float aZ) {
        if (!getBaseMetaTileEntity().isServerSide()) return true;
        if (mCalcification > 0.0d || mRunningTicks > 0L) {
            mCalcification = 0.0d;
            mRunningTicks = 0L;
            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.chat.calcification_cleared"));
            return true;
        }
        return false;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (mOverpressure) {
            mOverpressure = false;
            getBaseMetaTileEntity().markDirty();
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.overpressure.off");
        } else if (mHeat >= 1.0d) {
            mOverpressure = true;
            getBaseMetaTileEntity().markDirty();
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.overpressure.on");
        } else {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.overpressure.need_heat");
        }
    }

    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        // 恢复开机（软锤/打开 GUI 点电源开关）时清除缺水提示标志，下次缺水重新提示
        mNoWaterNotified = false;
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.heat")
                        + EnumChatFormatting.GOLD
                        + numberFormat.format(mHeat * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.solar_array.calcification")
                        + EnumChatFormatting.RED
                        + numberFormat.format(mCalcification * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.steam_output")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mCurrentSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.WHITE
                        + (isNickel() ? StatCollector.translateToLocal("gtsr.gui.solar_array.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.solar_array.steam"))
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.solar_array.solar_booster")
                        + EnumChatFormatting.GREEN
                        + numberFormat.format(calculateSolarBooster() * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mCalcification, val -> mCalcification = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSteamOutput, val -> mCurrentSteamOutput = val));
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        super.addUIWidgets(builder, buildContext);

        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0) {
                mCalcification = 0;
                mRunningTicks = 0;
            }
        })
            .setPlayClickSound(true)
            .setBackground(
                () -> new IDrawable[] { GTUITextures.BUTTON_STANDARD,
                    GTUITextures.OVERLAY_BUTTON_MACHINEMODE_WASHPLANT })
            .addTooltip(
                EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.solar_array.clear_calcification")
                    + EnumChatFormatting.RESET)
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setPos(new Pos2d(174, 91))
            .setSize(16, 16));
    }

    private float calculateSolarBooster() {
        float booster = 1.0f;

        ItemStack stack = getControllerSlot();
        if (stack != null) {
            if (ItemList.Machine_HP_Solar.isStackEqual(stack, false, false)) {
                booster += 2.0f * Math.min(stack.stackSize, 64) / 64.0f;
            } else if (ItemList.Machine_Bronze_Boiler_Solar.isStackEqual(stack, false, false)) {
                booster += 1.0f * Math.min(stack.stackSize, 64) / 64.0f;
            }
        }

        return Math.min(booster, 3.0f);
    }

    // v1.9.40 修复：蒸汽输出分配开放——蒸汽输出仓/耐压蒸汽输出仓优先，剩余量回退到全部流体输出仓
    // （含 ME 输出仓）。此前 instanceof 只认蒸汽输出仓类，普通/ME 输出仓放上结构成立但蒸汽静默丢失。
    private void distributeSteamToOutputHatches(int totalSteam) {
        FluidStack steam = Materials.Steam.getGas(totalSteam);
        fillSteamOutputHatches(steam, MTESteamOutputHatch.class);
        fillSteamOutputHatches(steam, MTEPressureSteamOutputHatch.class);
        fillRemainingOutputHatches(steam);
    }

    private void distributeSuperheatedSteamToOutputHatches(int totalSuperheatedSteam) {
        FluidStack superheatedSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", totalSuperheatedSteam);
        if (superheatedSteam == null) return;
        // 耐压蒸汽输出仓优先（MTESteamOutputHatch.canStoreFluid 只收普通蒸汽，不收超热蒸汽）
        fillSteamOutputHatches(superheatedSteam, MTEPressureSteamOutputHatch.class);
        fillRemainingOutputHatches(superheatedSteam);
    }

    private void fillSteamOutputHatches(FluidStack fluid, Class<? extends MTEHatchOutput> hatchClass) {
        for (IMetaTileEntity hatch : mOutputHatches) {
            if (fluid.amount <= 0) break;
            if (hatchClass.isInstance(hatch)) {
                int filled = ((MTEHatchOutput) hatch).fill(ForgeDirection.UNKNOWN, fluid.copy(), true);
                fluid.amount -= filled;
            }
        }
    }

    private void fillRemainingOutputHatches(FluidStack fluid) {
        for (IMetaTileEntity hatch : mOutputHatches) {
            if (fluid.amount <= 0) break;
            int filled = ((MTEHatchOutput) hatch).fill(ForgeDirection.UNKNOWN, fluid.copy(), true);
            fluid.amount -= filled;
        }
    }

    private int getBaseSteamProduction() {
        switch (mSetTier) {
            case 3:
                return 240000;
            case 2:
                return 180000;
            case 1:
            default:
                return 120000;
        }
    }

    private double getHeatIncreaseSpeed() {
        switch (mSetTier) {
            case 1:
                return 0.0006d;
            case 2:
                return 0.00048d;
            case 3:
                return 0.0002d;
            default:
                return 0.0006d;
        }
    }

    private double getHeatDecreaseSpeed() {
        switch (mSetTier) {
            case 1:
                return 0.0001d;
            case 2:
                return 0.00008d;
            case 3:
                return 0.00004d;
            default:
                return 0.0001d;
        }
    }

    private long getCalcificationDelayTicks() {
        return CALCIFICATION_DELAY_TICKS;
    }

    private long getCalcificationFullTime() {
        switch (mSetTier) {
            case 1:
                return 12L * 3600 * 20;
            case 2:
                return 4L * 3600 * 20;
            case 3:
                return 2L * 3600 * 20;
            default:
                return 12L * 3600 * 20;
        }
    }

    /**
     * 结垢产出系数：未结垢时 100%，完全结垢时降至 1%（线性递减）。
     */
    private double getCalcificationOutputFactor() {
        return Math.max(0.01d, 1.0d - 0.99d * mCalcification);
    }

    /**
     * 满垢后每 10 分钟向所有者玩家发送一次聊天窗提醒。
     */
    private void tickCalcificationWarning() {
        if (mCalcification >= 1.0d) {
            if (mCalcificationWarnTimer <= 0L) {
                mCalcificationWarnTimer = CALCIFICATION_WARN_INTERVAL_TICKS;
                sendCalcificationWarning();
            } else {
                mCalcificationWarnTimer--;
            }
        } else {
            mCalcificationWarnTimer = 0L;
        }
    }

    private void sendCalcificationWarning() {
        UUID ownerUuid = getBaseMetaTileEntity().getOwnerUuid();
        if (ownerUuid == null) return;
        for (Object o : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP player && player.getUniqueID()
                .equals(ownerUuid)) {
                GTUtility.sendChatToPlayer(
                    player,
                    EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.chat.calcification_full"));
                return;
            }
        }
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
            true,
            true);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setDouble("mCalcification", mCalcification);
        aNBT.setLong("mRunningTicks", mRunningTicks);
        aNBT.setLong("mCalcificationWarnTimer", mCalcificationWarnTimer);
        aNBT.setDouble("mSunRatio", mSunRatio);
        aNBT.setBoolean("mOverpressure", mOverpressure);
        aNBT.setBoolean("mNoWaterNotified", mNoWaterNotified);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mHeat = aNBT.getDouble("mHeat");
        mCalcification = aNBT.getDouble("mCalcification");
        mRunningTicks = aNBT.getLong("mRunningTicks");
        mCalcificationWarnTimer = aNBT.getLong("mCalcificationWarnTimer");
        mSunRatio = aNBT.getDouble("mSunRatio");
        mOverpressure = aNBT.getBoolean("mOverpressure");
        mNoWaterNotified = aNBT.getBoolean("mNoWaterNotified");
    }

    @Override
    public void onValueUpdate(byte aValue) {
        boolean oldHeating = mIsHeating;
        int oldTier = mSetTier;
        mIsHeating = (aValue & 0x01) != 0;
        // mSetTier 1~3 占 2 bit（bit1-2）；GT 事件通道会剥掉 bit7，故 mHeat 用 bit3-6 共 4 bit，
        // 编码 0~2.0（超压模式热量上限 200%），精度 2/15≈13.3%
        mSetTier = (aValue >> 1) & 0x03;
        mHeat = ((aValue >> 3) & 0x0F) / 15.0d * 2.0d;
        if (oldHeating != mIsHeating || oldTier != mSetTier) {
            getBaseMetaTileEntity().issueTextureUpdate();
        }
    }

    @Override
    public byte getUpdateData() {
        // 4 bit 编码 0~2.0（超压模式 mHeat 可达 2.0），位布局不变
        int heatQuantized = (int) Math.round(mHeat / 2.0d * 15.0);
        if (heatQuantized < 0) heatQuantized = 0;
        if (heatQuantized > 15) heatQuantized = 15;
        return (byte) ((heatQuantized << 3) | ((mSetTier <= 0 ? 0 : mSetTier) << 1) | (mIsHeating ? 0x01 : 0x00));
    }

    public int mCurrentSteamOutput = 0;

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.solar_array.type"));

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        String tierText = isNickel() ? StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_overpressure")
            : isSteel() ? StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_steel")
                : StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_bronze");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.solar_array.heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d));

        String statusKey;
        EnumChatFormatting statusColor;
        if (mIsOperating) {
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

        String steamOutputType = isNickel() ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.solar_array.steam_output")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + steamOutputType);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.solar_array.calcification")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mCalcification * 100.0d));

        float booster = calculateSolarBooster();
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.solar_array.solar_booster")
                + EnumChatFormatting.GOLD
                + String.format("x%.2f", booster));

        return info.toArray(new String[0]);
    }
}
