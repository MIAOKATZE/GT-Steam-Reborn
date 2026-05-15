package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

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
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTELargeCokeOven extends MTEEnhancedMultiBlockBase<MTELargeCokeOven>
    implements IConstructable, ISurvivalConstructable {

    private static final double HEAT_UP_RATE = 0.001d;
    private static final double HEAT_DOWN_RATE = 0.002d;
    private static final int BASE_RECIPE_TIME_SECONDS = 1800;
    private static final int HEAT_SPEEDUP_PER_PERCENT = 10;
    private static final int MIN_RECIPE_TIME_SECONDS = 800;
    private static final int MAX_PARALLEL_T1 = 4;
    private static final int MAX_PARALLEL_T2 = 16;
    private static final int CREOSOTE_PER_RECIPE = 500;

    private double mHeat = 0.0d;
    private int mTier = 1;
    private int mActiveRecipes = 0;

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static IStructureDefinition<MTELargeCokeOven> STRUCTURE_DEFINITION = null;

    public MTELargeCokeOven(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeCokeOven(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeCokeOven(mName);
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
        return true;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.largeCokeOvenRecipes;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (mHeat < 0.01d) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Override
    public IStructureDefinition<MTELargeCokeOven> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeCokeOven>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(InputBus)
                            .casingIndex(10)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputBus)
                            .casingIndex(10)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputHatch)
                            .casingIndex(10)
                            .dot(1)
                            .shouldReject(
                                t -> t.mOutputHatches.stream()
                                    .anyMatch(
                                        h -> h instanceof com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch))
                            .buildAndChain(
                                onElementPass(
                                    MTELargeCokeOven::onCasingAdded,
                                    ofBlocksTiered(
                                        MTELargeCokeOven::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTELargeCokeOven t, Integer tier) -> t.mTier = tier,
                                        (MTELargeCokeOven t) -> t.mTier)))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {}

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.recipe.large_coke_oven"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.1"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.3"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.ctrl"))
            .beginStructureBlock(3, 3, 3, true)
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.casing_t1"))
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.casing_t2"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.large_coke_oven.output_hatch"), 1)
            .toolTipFinisher("GTSR");
        return tt;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        clearHatches();
        mTier = 1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0)) {
            return false;
        }

        if (mTier < 1) {
            return false;
        }

        return mInputBusses.size() >= 1 && mOutputBusses.size() >= 1 && mOutputHatches.size() >= 1;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            onPostTickServer(aBaseMetaTileEntity, aTick);
        }
    }

    private void onPostTickServer(IGregTechTileEntity baseMetaTileEntity, long tick) {
        if (!mMachine) {
            mActiveRecipes = 0;
            mMaxProgresstime = 0;
            mProgresstime = 0;
            mHeat = Math.max(0.0d, mHeat - HEAT_DOWN_RATE);
            baseMetaTileEntity.setActive(false);
            return;
        }

        processRecipeLogic();
        updateHeat();

        baseMetaTileEntity.setActive(mMaxProgresstime > 0);
    }

    private void processRecipeLogic() {
        if (mMaxProgresstime > 0) {
            mProgresstime++;
            if (mProgresstime >= mMaxProgresstime) {
                finishRecipe();
            }
            return;
        }

        tryStartRecipe();
    }

    private void tryStartRecipe() {
        if (!getBaseMetaTileEntity().isAllowedToWork()) return;

        int maxParallel = (mTier >= 2) ? MAX_PARALLEL_T2 : MAX_PARALLEL_T1;
        int coalAvailable = countCoalInInputBusses();

        if (coalAvailable <= 0) return;

        mActiveRecipes = Math.min(coalAvailable, maxParallel);

        if (!checkOutputSpace()) {
            mActiveRecipes = 0;
            return;
        }

        consumeCoalFromInputBusses(mActiveRecipes);

        int recipeTimeTicks = calculateRecipeTimeTicks();
        mMaxProgresstime = recipeTimeTicks;
        mProgresstime = 0;
    }

    private int countCoalInInputBusses() {
        int count = 0;
        for (MTEHatchInputBus bus : mInputBusses) {
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && isCoalItem(stack)) {
                    count += stack.stackSize;
                }
            }
        }
        return count;
    }

    private boolean isCoalItem(ItemStack stack) {
        if (stack == null) return false;
        int[] oreIDs = net.minecraftforge.oredict.OreDictionary.getOreIDs(stack);
        for (int id : oreIDs) {
            String name = net.minecraftforge.oredict.OreDictionary.getOreName(id);
            if ("gemCoal".equals(name) || "gemLignite".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void consumeCoalFromInputBusses(int amount) {
        int remaining = amount;
        for (MTEHatchInputBus bus : mInputBusses) {
            if (remaining <= 0) break;
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && isCoalItem(stack)) {
                    int toTake = Math.min(remaining, stack.stackSize);
                    stack.stackSize -= toTake;
                    remaining -= toTake;
                    if (stack.stackSize <= 0) {
                        bus.setInventorySlotContents(i, null);
                    }
                    if (remaining <= 0) break;
                }
            }
        }
    }

    private boolean checkOutputSpace() {
        ItemStack cokeItem = getCokeItem();
        if (cokeItem == null) return false;

        int totalCoke = mActiveRecipes;
        int cokeSpace = 0;
        for (MTEHatchOutputBus bus : mOutputBusses) {
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack == null) {
                    cokeSpace += cokeItem.getMaxStackSize();
                } else if (GTUtility.areStacksEqual(stack, cokeItem)) {
                    cokeSpace += cokeItem.getMaxStackSize() - stack.stackSize;
                }
            }
        }
        if (cokeSpace < totalCoke) return false;

        FluidStack creosoteStack = Materials.Creosote.getFluid(CREOSOTE_PER_RECIPE * mActiveRecipes);
        if (creosoteStack == null) return false;

        FluidStack copied = creosoteStack.copy();
        for (MTEHatchOutput hatch : mOutputHatches) {
            FluidStack existing = hatch.getDrainableStack();
            if (existing == null) {
                copied.amount -= hatch.getCapacity();
            } else if (GTUtility.areFluidsEqual(existing, creosoteStack)) {
                copied.amount -= hatch.getCapacity() - existing.amount;
            }
            if (copied.amount <= 0) return true;
        }
        return false;
    }

    private ItemStack getCokeItem() {
        ArrayList<ItemStack> ores = net.minecraftforge.oredict.OreDictionary.getOres("fuelCoke");
        if (!ores.isEmpty()) {
            ItemStack result = ores.get(0)
                .copy();
            result.stackSize = 1;
            return result;
        }
        return null;
    }

    private int calculateRecipeTimeTicks() {
        int reducedSeconds = (int) (mHeat * 100.0d * HEAT_SPEEDUP_PER_PERCENT);
        int actualSeconds = Math.max(MIN_RECIPE_TIME_SECONDS, BASE_RECIPE_TIME_SECONDS - reducedSeconds);
        return actualSeconds * 20;
    }

    private void finishRecipe() {
        ItemStack cokeItem = getCokeItem();
        FluidStack creosoteStack = Materials.Creosote.getFluid(CREOSOTE_PER_RECIPE * mActiveRecipes);

        if (cokeItem != null) {
            ItemStack output = cokeItem.copy();
            output.stackSize = mActiveRecipes;
            pushCokeToOutputBusses(output);
        }

        if (creosoteStack != null) {
            addOutput(creosoteStack);
        }

        mMaxProgresstime = 0;
        mProgresstime = 0;
        mActiveRecipes = 0;
    }

    private void pushCokeToOutputBusses(ItemStack stack) {
        if (stack == null) return;
        for (MTEHatchOutputBus bus : mOutputBusses) {
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack existing = bus.getStackInSlot(i);
                if (existing == null) {
                    bus.setInventorySlotContents(i, stack.copy());
                    return;
                }
                if (GTUtility.areStacksEqual(existing, stack)) {
                    int space = existing.getMaxStackSize() - existing.stackSize;
                    int toAdd = Math.min(space, stack.stackSize);
                    existing.stackSize += toAdd;
                    stack.stackSize -= toAdd;
                    if (stack.stackSize <= 0) return;
                }
            }
        }
    }

    private void updateHeat() {
        if (mMaxProgresstime > 0) {
            mHeat = Math.min(1.0d, mHeat + HEAT_UP_RATE);
        } else {
            mHeat = Math.max(0.0d, mHeat - HEAT_DOWN_RATE);
        }
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                        + String.format("%.2f%%", mHeat * 100.0d)))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val));
        if (mMaxProgresstime > 0) {
            int secondsRemaining = (mMaxProgresstime - mProgresstime) / 20;
            screenElements.widget(
                new TextWidget().setStringSupplier(
                    () -> StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time") + secondsRemaining + "s"))
                .widget(
                    new TextWidget().setStringSupplier(
                        () -> StatCollector.translateToLocal("gtsr.gui.coke_oven.parallel") + mActiveRecipes));
        }
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.info.coke_oven.name"));
        info.add(
            StatCollector.translateToLocal("gtsr.info.coke_oven.tier") + EnumChatFormatting.YELLOW
                + (mTier >= 2 ? StatCollector.translateToLocal("gtsr.info.coke_oven.tier2")
                    : StatCollector.translateToLocal("gtsr.info.coke_oven.tier1")));
        info.add(
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                + String.format("%.2f%%", mHeat * 100.0d));
        if (mMaxProgresstime > 0) {
            int secondsRemaining = (mMaxProgresstime - mProgresstime) / 20;
            info.add(StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time") + secondsRemaining + "s");
            info.add(StatCollector.translateToLocal("gtsr.gui.coke_oven.parallel") + mActiveRecipes);
        }
        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setInteger("mTier", mTier);
        aNBT.setInteger("mActiveRecipes", mActiveRecipes);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeat = aNBT.getDouble("mHeat");
        mTier = aNBT.getInteger("mTier");
        mActiveRecipes = aNBT.getInteger("mActiveRecipes");
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UPRIGHT;
    }

    protected int getCasingTextureID() {
        if (mTier >= 2) {
            return ((gregtech.common.blocks.BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((gregtech.common.blocks.BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
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
}
