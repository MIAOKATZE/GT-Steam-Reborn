package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

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
import com.miaokatze.gtsr.common.gui.MTEReinforcedBrickBlastFurnaceGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusInput;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchSteamBusOutput;

/**
 * 加固砖高炉（Reinforced Brick Blast Furnace）。
 * <p>
 * 一台纯固体钢/耐火砖结构的多方块高炉，使用 GT5U 砖高炉配方（{@link RecipeMaps#primitiveBlastRecipes}）。
 * 内置炉温机制：运行时炉温缓慢上升，闲置时炉温下降；炉温越高，并行数与配方速度越高。
 * 不需要维护仓、消声仓、空气输入或耐压蒸汽输入。
 */
public class MTEReinforcedBrickBlastFurnace extends MTEGTSRMultiBlockBase<MTEReinforcedBrickBlastFurnace>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";

    // 控制器在结构中的位置（对应转置后的坐标）。
    // 原始层定义中 '~' 位于第 4 层（下标 3）、第 1 行（下标 0）、第 2 列（下标 1）。
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 3;
    private static final int DEPTH_OFF_SET = 0;

    // 炉温变化速率：工作时 +0.01%/秒，闲置时 -1%/秒。
    private static final double TEMPERATURE_INCREMENT = 0.000005d;
    private static final double TEMPERATURE_DECREMENT = 0.0005d;

    // 炉温范围 [0.0, 1.0]。
    public double mFurnaceTemperature = 0.0d;

    private static IStructureDefinition<MTEReinforcedBrickBlastFurnace> STRUCTURE_DEFINITION = null;

    public MTEReinforcedBrickBlastFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTEReinforcedBrickBlastFurnace(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（炉温；状态/并行/速度为文本行保留在 GUI）
    private void registerProgressEntries() {
        registerEntry(
            "temperature",
            "gtsr.gui.reinforced_brick_blast_furnace.temperature",
            "%.1f%%",
            EnumChatFormatting.RED,
            () -> mFurnaceTemperature * 100.0d);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEReinforcedBrickBlastFurnace(mName);
    }

    public String getMachineType() {
        return "Reinforced Brick Blast Furnace";
    }

    protected int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        // v1.9.41 修复：补 mDualInputHatches（样板仓经自定义 adder 重定向至此）
        if (mDualInputHatches != null) {
            for (IDualInputHatch dualHatch : mDualInputHatches) {
                if (dualHatch != null) dualHatch.updateTexture(textureID);
            }
        }
    }

    /**
     * 添加输入总线到机器列表。
     * 由于 {@link MTEHatchSteamBusInput} 继承自 {@link MTEHatchInputBus}，因此普通输入总线与蒸汽板输入总线都会被接受。
     */
    // v1.9.39 修复：样板输入仓（MTEHatchCraftingInputME/Slave，implements IDualInputHatch）重定向到
    // mDualInputHatches（仿 GT5U addInputBusToMachineList）。此前裸 instanceof 会把样板仓收进
    // mInputBusses，随后被 GT5U getAllStoredInputs 对 CraftingInputME 的显式跳过逻辑忽略，
    // 导致样板输入静默失效（结构能成型、配方永不消耗）。
    public boolean addInputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof IDualInputHatch dualHatch) {
            dualHatch.updateTexture(aBaseCasingIndex);
            dualHatch.updateCraftingIcon(this.getMachineCraftingIcon());
            if (!mDualInputHatches.contains(dualHatch)) {
                mDualInputHatches.add(dualHatch);
            }
            return true;
        }
        if (aMetaTileEntity instanceof MTEHatchInputBus hatch) {
            hatch.mRecipeMap = getRecipeMap();
            hatch.updateTexture(aBaseCasingIndex);
            return mInputBusses.add(hatch);
        }
        return false;
    }

    /**
     * 添加输出总线到机器列表。
     * 由于 {@link MTEHatchSteamBusOutput} 继承自 {@link MTEHatchOutputBus}，因此普通输出总线与蒸汽板输出总线都会被接受。
     */
    public boolean addOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchOutputBus hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mOutputBusses.add(hatch);
        }
        return false;
    }

    /**
     * 用于结构匹配的仓室元素。
     * <p>
     * 自定义 {@link IHatchElement} 以使用本机器专用的 adder，同时通过 {@link #mteBlacklist()} 避免 StructureLib 在 NEI
     * 投影中将对应仓室渲染到每一个 'A' 位上。
     */
    private enum ReinforcedBrickBlastFurnaceHatchElement implements IHatchElement<MTEReinforcedBrickBlastFurnace> {

        InputBus(MTEReinforcedBrickBlastFurnace::addInputBusToMachineList, MTEHatchInputBus.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchInputBus.class);
            }
        },
        OutputBus(MTEReinforcedBrickBlastFurnace::addOutputBusToMachineList, MTEHatchOutputBus.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTEHatchOutputBus.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEReinforcedBrickBlastFurnace> adder;

        @SafeVarargs
        ReinforcedBrickBlastFurnaceHatchElement(IGTHatchAdder<MTEReinforcedBrickBlastFurnace> adder,
            Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEReinforcedBrickBlastFurnace> adder() {
            return adder;
        }

        @Override
        public long count(MTEReinforcedBrickBlastFurnace t) {
            return 0;
        }
    }

    @Override
    public IStructureDefinition<MTEReinforcedBrickBlastFurnace> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEReinforcedBrickBlastFurnace>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CXC", "CCC" }, { "CCC", "CXC", "CCC" }, { "CAC", "AXA", "CAC" },
                            { "A~A", "AXA", "AAA" }, { "BBB", "BBB", "BBB" } }))
                .addElement('~', onElementPass(x -> {}, ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .addElement(
                    'A',
                    ofChain(
                        // casing-first：NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        ofBlock(GregTechAPI.sBlockCasings2, 0),
                        buildHatchAdder(MTEReinforcedBrickBlastFurnace.class)
                            .atLeast(
                                ReinforcedBrickBlastFurnaceHatchElement.InputBus,
                                ReinforcedBrickBlastFurnaceHatchElement.OutputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build()))
                .addElement('B', ofBlock(GregTechAPI.sBlockCasings3, 14))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings4, 15))
                .addElement('X', isAir())
                .build();
        }
        return STRUCTURE_DEFINITION;
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
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        // v1.9.39 修复：样板输入仓计入输入判定（重定向后位于 mDualInputHatches，不再计入 mInputBusses）
        if (mInputBusses.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine) {
            boolean working = mMaxProgresstime > 0 || aBaseMetaTileEntity.isActive();
            if (working) {
                mFurnaceTemperature = Math.min(1.0d, mFurnaceTemperature + TEMPERATURE_INCREMENT);
            } else {
                mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
            }
        } else if (mStartUpCheck <= 0) {
            // 加载豁免期（GT5U mStartUpCheck 100 tick 内未检结构，"启动中....."阶段）冻结炉温，
            // 避免退出重进后结构检测延迟期间异常降温
            mFurnaceTemperature = Math.max(0.0d, mFurnaceTemperature - TEMPERATURE_DECREMENT);
        }
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic().setMaxParallelSupplier(this::getMaxParallelRecipes);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult result = super.checkProcessing();
        if (!result.wasSuccessful()) return result;

        double speedMultiplier = 1.0d + 0.5d * mFurnaceTemperature;
        mMaxProgresstime = Math.max(1, (int) (mMaxProgresstime / speedMultiplier));

        return result;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.primitiveBlastRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        int parallel = 1 + (int) Math.round(3.0d * mFurnaceTemperature);
        return Math.max(1, Math.min(4, parallel));
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    public int getTierRecipes() {
        return 0;
    }

    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return true;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return true;
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

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mFurnaceTemperature", mFurnaceTemperature);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mFurnaceTemperature = aNBT.getDouble("mFurnaceTemperature");
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        info.add(
            EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.temperature")
                + " "
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mFurnaceTemperature * 100.0d)
                + EnumChatFormatting.RESET);

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else if (mFurnaceTemperature > 0.0d && mFurnaceTemperature < 1.0d) {
            statusKey = "gtsr.gui.reinforced_brick_blast_furnace.status.heating";
            statusColor = EnumChatFormatting.YELLOW;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);

        info.add(
            EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.parallel")
                + " "
                + EnumChatFormatting.GOLD
                + getMaxParallelRecipes()
                + EnumChatFormatting.RESET);

        double speedMultiplier = 1.0d + 0.5d * mFurnaceTemperature;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.speed")
                + " "
                + EnumChatFormatting.GOLD
                + String.format("%.2fx", speedMultiplier)
                + EnumChatFormatting.RESET);

        return info.toArray(new String[0]);
    }

    /**
     * 返回自定义 GUI，通过 {@link MTEReinforcedBrickBlastFurnaceGui} 在终端区域显示炉温/状态/并行/速度。
     */
    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEReinforcedBrickBlastFurnaceGui(this);
    }

    /**
     * 在 GUI 中显示炉温、运行状态、并行数与运行速度，与其他项目机器保持一致。
     * <p>
     * 数据来源与 {@link #getInfoData()} 相同，仅展示层不同；通过 FakeSyncWidget 保证客户端实时同步。
     */
    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            // 炉温显示：黄色标签 + 红色百分比
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.temperature")
                        + " "
                        + EnumChatFormatting.RED
                        + String.format("%.1f%%", mFurnaceTemperature * 100.0d)
                        + EnumChatFormatting.RESET))
            // 运行状态显示：运行中(青)/升温中(黄)/待机中(灰)
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (mMaxProgresstime > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (mFurnaceTemperature > 0.0d && mFurnaceTemperature < 1.0d) {
                    statusKey = "gtsr.gui.reinforced_brick_blast_furnace.status.heating";
                    statusColor = EnumChatFormatting.YELLOW;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.GRAY;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey)
                    + EnumChatFormatting.RESET;
            }))
            // 并行数显示：黄色标签 + 金色数字
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + getMaxParallelRecipes()
                        + EnumChatFormatting.RESET))
            // 运行速度显示：黄色标签 + 金色倍率（1.00x ~ 1.50x）
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.reinforced_brick_blast_furnace.speed")
                        + " "
                        + EnumChatFormatting.GOLD
                        + String.format("%.2fx", 1.0d + 0.5d * mFurnaceTemperature)
                        + EnumChatFormatting.RESET))
            // 客户端同步：炉温与进度时间，确保 GUI 实时更新
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mFurnaceTemperature, val -> mFurnaceTemperature = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val));
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.type"))
            .addInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.desc"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.mechanism"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.parallel_hint"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.speed_hint"))
            .beginStructureBlock(3, 3, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.ctrl"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.reinforced_brick_blast_furnace.output_bus"), 1)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_firebox_casing"), 9, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_casing"), 11, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebricks"), 20, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.reinforced_brick_blast_furnace.hatch_steam")
            .toolTipFinisher(GTSRUtils.getAddedByLine());
        return tt;
    }
}
