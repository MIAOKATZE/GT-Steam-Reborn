package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamInputBus;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

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
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTELargeSteamFurnace extends MTESteamMultiBlockBase<MTELargeSteamFurnace>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 2;
    private static final int VERTICAL_OFF_SET = 4;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTELargeSteamFurnace> STRUCTURE_DEFINITION = null;

    public int mSetTier = -1;
    protected int mCasingCount = 0;

    public MTELargeSteamFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeSteamFurnace(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeSteamFurnace(mName);
    }

    @Override
    public String getMachineType() {
        return "大型蒸汽高炉";
    }

    @Override
    public boolean isHighPressure() {
        return mSetTier >= 2;
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
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
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

    /**
     * 重写 getTexture，直接使用 getCasingTextureID() 返回贴图。
     * <p>
     * 不依赖基类 MTESteamMultiBlockBase 的 createTextureWithCasing → getCurrentCasing() 路径，
     * 因为该路径在某些情况下（如刚放置未形成多方块时）可能返回错误的 casing 贴图。
     * 参考 MTESteamHubArray / MTELargeGeothermalSteamBoiler 的实现模式，
     * 直接用 Textures.BlockIcons.getCasingTextureForId(int) 按 mSetTier 返回正确贴图。
     * <p>
     * 注意：mSetTier = -1（未初始化）时 getCasingTextureID() 返回青铜（等级1）贴图，
     * 确保刚放置时控制器显示青铜底材而非钢外壳。
     */
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                aActive ? TextureFactory.of(getActiveOverlay()) : TextureFactory.of(getInactiveOverlay()) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    /**
     * 更新所有 hatch 贴图，包括蒸汽总线列表。
     * <p>
     * 之前的实现遗漏了 mSteamInputs 和 mSteamOutputs（蒸汽输入/输出总线列表），
     * 导致等级2时蒸汽输入/输出总线仍显示等级1底材（镀铜砖）。
     * 现在补全所有 hatch 列表的贴图更新。
     */
    @Override
    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        // 蒸汽输入/输出总线（取消双注册后只在 mSteamInputs/mSteamOutputs 中）
        for (MTEHatch h : mSteamInputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputs) h.updateTexture(textureID);
        // 蒸汽流体输入仓
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        // 标准输入/输出总线（GTSRHatchElement.SteamInputBus/SteamOutputBus 接受的类型）
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
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
    public IStructureDefinition<MTELargeSteamFurnace> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeSteamFurnace>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "DBBBD", "BBBBB", "BBBBB", "BBBBB", "DBBBD" },
                            { "D   D", " CCC ", " CCC ", " CCC ", "D   D" },
                            { "D   D", " CCC ", " CCC ", " CCC ", "D   D" },
                            { "D   D", " CCC ", " CCC ", " CCC ", "D   D" },
                            { "DB~BD", "BBBBB", "BBBBB", "BBBBB", "DBBBD" } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTELargeSteamFurnace::onCasingAdded,
                            ofBlocksTiered(
                                MTELargeSteamFurnace::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTELargeSteamFurnace t, Integer tier) -> t.mSetTier = tier,
                                (MTELargeSteamFurnace t) -> t.mSetTier)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). Its mteBlacklist()
                        // excludes MTEHatchPressureSteamInput.class so NEI does not render it on casing positions.
                        buildHatchAdder(MTELargeSteamFurnace.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTELargeSteamFurnace.class).atLeast(SteamInputBus, SteamOutputBus)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTELargeSteamFurnace::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeSteamFurnace::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTELargeSteamFurnace t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTELargeSteamFurnace t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTELargeSteamFurnace::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeSteamFurnace::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTELargeSteamFurnace t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTELargeSteamFurnace t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingCount++;
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
        mSetTier = -1;
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }
        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSteamInputFluids.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        // 取消双注册后，蒸汽输入总线只在 mSteamInputs 中，蒸汽输出总线只在 mSteamOutputs 中
        if (mInputBusses.isEmpty() && mSteamInputs.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputBusses.isEmpty() && mSteamOutputs.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateHatchTexture();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.furnaceRecipes;
    }

    @Override
    public int getMaxParallelRecipes() {
        return mSetTier == 2 ? 48 : 24;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (availableVoltage < recipe.mEUt) {
                    return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            @Nonnull
            protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                double eutDiscount = mSetTier == 2 ? 0.4 : 0.6;
                double durationModifier = mSetTier == 2 ? (1.0 / 5.0) : (1.0 / 2.5);
                return OverclockCalculator.ofNoOverclock(recipe)
                    .setEUtDiscount(eutDiscount)
                    .setDurationModifier(durationModifier);
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    public int getTierRecipes() {
        return 1;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE;
    }

    // beta-2 兼容：MTESteamMultiBlockBase 将 getActiveGlowOverlay/getInactiveGlowOverlay 改为 abstract
    // 返回 Textures.BlockIcons.VOID（GT5U 官方"空纹理"常量，渲染器跳过 InvisibleIcon，无发光层）
    // 不能返回 null，否则 beta-2 的 createTextureWithCasing 会导致 GTTextureBuilder.build() 抛出
    // "iconContainer not specified!" 崩溃（创造物品栏渲染时触发）
    @Override
    protected IIconContainer getActiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
    }

    @Override
    protected IIconContainer getInactiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.speed"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.steam_eff"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + "24"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + ")"
                    + EnumChatFormatting.GOLD
                    + "/48"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.recipe_based"))
            .beginStructureBlock(3, 3, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.ctrl"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.output_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_or_pressure"),
                1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.shared.bronze_steel_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 19, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 3, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 6, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 1, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 6, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + "24"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + ")"
                    + EnumChatFormatting.GOLD
                    + "/48"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
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
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String steamType = hasSuperheatedSteamInHatch()
                    ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                    : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + steamType
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + EnumChatFormatting.YELLOW
                        + getMaxParallelRecipes()
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTELargeSteamFurnaceGui(this);
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String tierText = mSetTier >= 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        String steamType = hasSuperheatedSteamInHatch()
            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.steam_furnace.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                + EnumChatFormatting.GOLD
                + getMaxParallelRecipes() };
    }

    public boolean hasSuperheatedSteamInHatch() {
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
}
