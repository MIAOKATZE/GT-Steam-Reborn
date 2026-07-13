package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamInputBus;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.VoidMinerUtilityShim;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEVoidCrustSteamBorer extends MTESteamMultiBlockBase<MTEVoidCrustSteamBorer>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 10;
    private static final int DEPTH_OFF_SET = 1;

    protected static final int VOID_STEAM_L_EUT = 400;
    public static final int VOID_WORK_TIME_TICKS = 100;
    public static final int VOID_STEAM_PER_SECOND = VOID_STEAM_L_EUT * 20;

    private static final String ITEM_DIM_DISPLAY_CLASS = "gtneioreplugin.plugin.item.ItemDimensionDisplay";

    private static final Map<String, Integer> ABBR_TO_DIM_ID = new HashMap<>();
    static {
        ABBR_TO_DIM_ID.put("Ow", 0);
        ABBR_TO_DIM_ID.put("Ne", -1);
        ABBR_TO_DIM_ID.put("ED", 1);
        ABBR_TO_DIM_ID.put("EA", 1);
        ABBR_TO_DIM_ID.put("TF", 7);
    }

    private static Boolean pluginLoaded = null;
    private static Class<?> itemDimDisplayClass = null;
    private static java.lang.reflect.Method getDimensionMethod = null;

    private static IStructureDefinition<MTEVoidCrustSteamBorer> STRUCTURE_DEFINITION = null;

    private int mCountCasing = 0;
    private VoidMinerUtility.DropMap dropMap = null;
    private VoidMinerUtility.DropMap extraDropMap = null;
    public String lastDimAbbr = "None";
    public String mLastOreName = "";
    public boolean dropMapValid = false;

    public MTEVoidCrustSteamBorer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEVoidCrustSteamBorer(String aName) {
        super(aName);
    }

    private static synchronized boolean isPluginLoaded() {
        if (pluginLoaded == null) {
            if (!Loader.isModLoaded("gtneioreplugin")) {
                pluginLoaded = false;
            } else {
                try {
                    itemDimDisplayClass = Class
                        .forName(ITEM_DIM_DISPLAY_CLASS, true, MTEVoidCrustSteamBorer.class.getClassLoader());
                    getDimensionMethod = itemDimDisplayClass.getMethod("getDimension", ItemStack.class);
                    pluginLoaded = true;
                } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException e) {
                    pluginLoaded = false;
                }
            }
        }
        return pluginLoaded;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEVoidCrustSteamBorer(mName);
    }

    @Override
    public String getMachineType() {
        return "虚空地壳钻探器";
    }

    @Override
    protected boolean isHighPressure() {
        return true;
    }

    private int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
    }

    @Override
    protected void updateHatchTexture() {
        super.updateHatchTexture();
        int textureID = getCasingTextureID();
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {}

    @Override
    public byte getUpdateData() {
        return 0;
    }

    private boolean hasPressureSteamHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            if (hatch instanceof MTEHatchPressureSteamInput) return true;
        }
        return false;
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
    public IStructureDefinition<MTEVoidCrustSteamBorer> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEVoidCrustSteamBorer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   F F   ", "    C    ", "   F F   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   F F   ", "    C    ", "   F F   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   F F   ", "    C    ", "   F F   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   F F   ", "    C    ", "   F F   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "  F   F  ", "   BBB   ", "   BCB   ", "   BBB   ", "  F   F  ",
                                "         ", "         " },
                            { "         ", "         ", "  F B F  ", "   EBE   ", "  BBCBB  ", "   EBE   ", "  F B F  ",
                                "         ", "         " },
                            { "         ", "  BD~DB  ", " BFBDBFB ", " DBBDBBD ", " BDDCDDB ", " DBBDBBD ", " BFBDBFB ",
                                "  BDBDB  ", "         " },
                            { "  B B B  ", " BFFFFFB ", "B F   F B", " F     F ", "BF  C  FB", " F     F ", "BFF   FFB",
                                " BFFFFFB ", "  B B B  " } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(MTEVoidCrustSteamBorer::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). The hatch element's
                        // mteBlacklist() excludes MTEHatchPressureSteamInput.class, preventing NEI from rendering
                        // the pressure steam hatch at every casing position.
                        buildHatchAdder(MTEVoidCrustSteamBorer.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEVoidCrustSteamBorer.class).atLeast(SteamInputBus, SteamOutputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(MTEVoidCrustSteamBorer::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 13)))
                .addElement(
                    'D',
                    onElementPass(MTEVoidCrustSteamBorer::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 3)))
                .addElement(
                    'E',
                    onElementPass(MTEVoidCrustSteamBorer::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings3, 14)))
                .addElement(
                    'F',
                    onElementPass(
                        MTEVoidCrustSteamBorer::onCasingAdded,
                        ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)))
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCountCasing = 0;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        // 取消双注册后，蒸汽输出总线只在 mSteamOutputs 中，需要合并计数
        if (this.mSteamInputFluids.size() != 1 || (this.mOutputBusses.size() + this.mSteamOutputs.size()) != 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!isPluginLoaded()) return;
        String dimAbbr = readDimensionOverride();
        if (!"None".equals(dimAbbr)) {
            lastDimAbbr = dimAbbr;
            recalculateDropMap(dimAbbr);
        }
    }

    private String readDimensionOverride() {
        if (!isPluginLoaded()) return "None";
        try {
            ItemStack slotStack = mInventory[1];
            if (slotStack != null) {
                Item slotItem = slotStack.getItem();
                if (slotItem != null) {
                    if (itemDimDisplayClass != null && itemDimDisplayClass.isInstance(slotItem)) {
                        Object result = getDimensionMethod.invoke(null, slotStack);
                        if (result instanceof String) return (String) result;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "None";
    }

    private void recalculateDropMap(String dimAbbr) {
        dropMap = null;
        extraDropMap = null;
        dropMapValid = false;

        if ("None".equals(dimAbbr)) return;

        Integer dimId = ABBR_TO_DIM_ID.get(dimAbbr);

        if (dimId != null) {
            recalculateDropMapById(dimId);
        }
    }

    private void recalculateDropMapById(int dimId) {
        String dimName = VoidMinerUtilityShim.dimIdToName(dimId);
        if (dimName == null) {
            dropMap = new VoidMinerUtility.DropMap();
            extraDropMap = new VoidMinerUtility.DropMap();
            dropMapValid = false;
            return;
        }
        dropMap = VoidMinerUtilityShim.getDropMap(dimName);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMap(dimName);
        dropMap.isDistributionCached(extraDropMap);
        dropMapValid = (dropMap != null && dropMap.getTotalWeight() > 0);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!isPluginLoaded()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        String dimAbbr = readDimensionOverride();
        if (!dimAbbr.equals(lastDimAbbr)) {
            lastDimAbbr = dimAbbr;
            recalculateDropMap(dimAbbr);
        }

        if ("None".equals(dimAbbr)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (!dropMapValid) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (getTotalSteamStored() > 0) {
            lEUt = -VOID_STEAM_L_EUT;
            mMaxProgresstime = VOID_WORK_TIME_TICKS;
            mEfficiencyIncrease = 10000;
            mOutputItems = emptyItemStackArray;
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    protected void outputAfterRecipe() {
        if (dropMap != null && dropMap.getTotalWeight() > 0) {
            GTUtility.ItemId oreId = dropMap.nextOre();
            if (oreId != null) {
                ItemStack oreStack = oreId.getItemStack();
                if (oreStack != null) {
                    addOutputPartial(oreStack);
                    mLastOreName = oreStack.getDisplayName();
                }
            }
        }
        updateSlots();
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
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
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.void_borer.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.void_borer.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.void_borer.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " "
                    + NumberFormatUtil.formatNumber(VOID_STEAM_PER_SECOND)
                    + " L/s")
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.superheated_quadruples"))
            .beginStructureBlock(5, 6, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.void_borer.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.void_borer.output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.shared.bronze_steel_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 11, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 10, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.void_borer.hint_bronze")
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
        aNBT.setString("lastDimAbbr", lastDimAbbr);
        aNBT.setString("mLastOreName", mLastOreName);
        aNBT.setBoolean("dropMapValid", dropMapValid);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        lastDimAbbr = aNBT.getString("lastDimAbbr");
        mLastOreName = aNBT.getString("mLastOreName");
        dropMapValid = aNBT.getBoolean("dropMapValid");
        if (!"None".equals(lastDimAbbr) && isPluginLoaded()) {
            recalculateDropMap(lastDimAbbr);
        }
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.tier.steel")))
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusText;
                if ("None".equals(lastDimAbbr)) {
                    statusText = EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.void_borer.no_dimension");
                } else if (!dropMapValid) {
                    statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.void_borer.no_ores");
                } else if (mMaxProgresstime <= 0) {
                    statusText = EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.void_borer.no_steam");
                } else {
                    statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText;
            }))
            .widget(new FakeSyncWidget.StringSyncer(() -> lastDimAbbr, val -> lastDimAbbr = val))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> dropMapValid, val -> dropMapValid = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.dimension")
                        + EnumChatFormatting.GREEN
                        + lastDimAbbr))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.mining")
                        + (mLastOreName != null && !mLastOreName.isEmpty() ? EnumChatFormatting.GREEN + mLastOreName
                            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.none"))))
            .widget(new FakeSyncWidget.StringSyncer(() -> mLastOreName, val -> mLastOreName = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.void_borer.steam_cost")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(VOID_STEAM_PER_SECOND)
                        + " L/s"))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                        + EnumChatFormatting.YELLOW
                        + (VOID_WORK_TIME_TICKS / 20)
                        + "s"
                        + EnumChatFormatting.RESET));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTEVoidCrustSteamBorerGui(this);
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.void_borer.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String tierText = StatCollector.translateToLocal("gtsr.gui.tier.steel");
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
        return new String[] { EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.void_borer.type"),
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

    private String getStatusText() {
        if (!mMachine) return EnumChatFormatting.RED + "Incomplete" + EnumChatFormatting.RESET;
        if (!isPluginLoaded()) return EnumChatFormatting.RED + "Plugin Missing" + EnumChatFormatting.RESET;
        if ("None".equals(lastDimAbbr)) return EnumChatFormatting.RED + "No Dimension" + EnumChatFormatting.RESET;
        if (!dropMapValid) return EnumChatFormatting.RED + "No Ores" + EnumChatFormatting.RESET;
        if (!getBaseMetaTileEntity().isAllowedToWork())
            return EnumChatFormatting.YELLOW + "Disabled" + EnumChatFormatting.RESET;
        if (getTotalSteamStored() <= 0) return EnumChatFormatting.YELLOW + "No Steam" + EnumChatFormatting.RESET;
        return EnumChatFormatting.GREEN + "Running" + EnumChatFormatting.RESET;
    }
}
