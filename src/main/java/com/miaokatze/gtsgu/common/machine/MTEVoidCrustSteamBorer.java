package com.miaokatze.gtsgu.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsgu.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsgu.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsgu.common.machine.base.VoidMinerUtilityShim;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import cpw.mods.fml.common.Loader;
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
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings2;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTEVoidCrustSteamBorer extends MTESteamMultiBase<MTEVoidCrustSteamBorer>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 7;
    private static final int DEPTH_OFF_SET = 1;

    protected static final int VOID_STEAM_L_EUT = 400;
    protected static final int VOID_WORK_TIME_TICKS = 100;
    protected static final int VOID_STEAM_PER_SECOND = VOID_STEAM_L_EUT * 20;

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
    private String lastDimAbbr = "None";
    private String mLastOreName = "";
    private boolean dropMapValid = false;

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
        return "Void Crust Steam Borer";
    }

    private int getCasingTextureID() {
        return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
    }

    private void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
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
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "       ", "   B   ", "       ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", "       ", "   B   ", "  BCB  ", "   B   ", "       ", "       " },
                            { "       ", " B   B ", "  DAD  ", "  ACA  ", "  DAD  ", " B   B ", "       " },
                            { "  D D  ", " BA~AB ", " A   A ", " B C B ", " A   A ", " BABAB ", "       " },
                            { "  E E  ", " BBBBB ", "EB   BE", " B C B ", "EB   BE", " BBBBB ", "  E E  " } }))
                .addElement('A', ofBlock(GregTechAPI.sBlockCasings2, 0))
                .addElement('B', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings2, 0))
                .addElement('D', ofBlock(GregTechAPI.sBlockCasings2, 0))
                .addElement(
                    'E',
                    ofChain(
                        buildHatchAdder(MTEVoidCrustSteamBorer.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTEVoidCrustSteamBorer.class).atLeast(OutputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEVoidCrustSteamBorer::onCasingAdded,
                                    ofBlock(GregTechAPI.sBlockCasings2, 0)))))
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

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mSteamInputFluids.size() != 1 || this.mOutputBusses.size() != 1) return false;

        updateHatchTexture();
        return true;
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
        dropMap = VoidMinerUtilityShim.getDropMapById(dimId);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMapById(dimId);
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
                    addOutputPartial(oreStack, false);
                    mLastOreName = oreStack.getDisplayName();
                }
            }
        }
        updateSlots();
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
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.info"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.steel"))
            .addInfo(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.steam_cost")
                    + EnumChatFormatting.WHITE
                    + VOID_STEAM_PER_SECOND
                    + " L/s"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.pressure_hatch"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.dimension"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.requires_plugin"))
            .beginStructureBlock(7, 9, 7, false)
            .addOutputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.any_casing"),
                1)
            .addStructureInfo(
                StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.steam_or_pressure_hatch"))
            .addStructureInfo(
                EnumChatFormatting.GOLD + "42"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.steel_frame"))
            .addStructureInfo(
                EnumChatFormatting.GOLD + "20"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsgu.tooltip.void_crust_steam_borer.steel_casing"))
            .toolTipFinisher();
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

    @Override
    public String[] getInfoData() {
        String dimInfo;
        if (!isPluginLoaded()) {
            dimInfo = EnumChatFormatting.RED + "GT NEI Ore Plugin not found" + EnumChatFormatting.RESET;
        } else if ("None".equals(lastDimAbbr)) {
            dimInfo = EnumChatFormatting.RED + "No dimension selected" + EnumChatFormatting.RESET;
        } else {
            Integer dimId = ABBR_TO_DIM_ID.getOrDefault(lastDimAbbr, -999);
            dimInfo = EnumChatFormatting.AQUA + lastDimAbbr
                + EnumChatFormatting.RESET
                + " (ID:"
                + dimId
                + ")"
                + (dropMapValid ? EnumChatFormatting.GREEN + " OK" : EnumChatFormatting.RED + " No Ores");
        }

        String oreInfo = mLastOreName != null && !mLastOreName.isEmpty()
            ? EnumChatFormatting.GREEN + mLastOreName + EnumChatFormatting.RESET
            : EnumChatFormatting.GRAY + "None" + EnumChatFormatting.RESET;

        boolean boosted = hasSuperheatedSteamInHatch();
        int workTime = boosted ? VOID_WORK_TIME_TICKS / 4 : VOID_WORK_TIME_TICKS;

        String hatchInfo = hasPressureSteamHatch()
            ? EnumChatFormatting.GOLD + "Pressure Steam Hatch"
                + EnumChatFormatting.RESET
                + (boosted ? " " + EnumChatFormatting.GREEN + "(4x)" : "")
            : EnumChatFormatting.WHITE + "Steam Hatch" + EnumChatFormatting.RESET;

        return new String[] { EnumChatFormatting.BLUE + "Void Crust Steam Borer",
            EnumChatFormatting.GRAY + "Tier: " + EnumChatFormatting.GOLD + "Steel",
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Target Dimension: " + dimInfo, EnumChatFormatting.GRAY + "Mining: " + oreInfo,
            EnumChatFormatting.GRAY + "Hatch: " + hatchInfo,
            EnumChatFormatting.GRAY + "Steam Consumption: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(VOID_STEAM_PER_SECOND)
                + " L/s",
            EnumChatFormatting.GRAY + "Work Time: " + EnumChatFormatting.YELLOW + (workTime / 20) + "s" };
    }
}
