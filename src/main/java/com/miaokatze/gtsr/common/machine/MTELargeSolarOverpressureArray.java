package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;

public class MTELargeSolarOverpressureArray extends MTEEnhancedMultiBlockBase<MTELargeSolarOverpressureArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 5;
    private static final int VERTICAL_OFF_SET = 3;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTELargeSolarOverpressureArray> STRUCTURE_DEFINITION = null;

    protected int mSetTier = -1;
    private float mHeat = 0.0f;
    private long mCalcificationTicks = 0;

    public MTELargeSolarOverpressureArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeSolarOverpressureArray(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeSolarOverpressureArray(mName);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    protected void updateHatchTextures() {
        int textureID = getCasingTextureID();
        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch) {
                ((gregtech.api.metatileentity.implementations.MTEHatch) hatch).updateTexture(textureID);
            }
        }
        for (IMetaTileEntity hatch : mInputHatches) {
            if (hatch instanceof gregtech.api.metatileentity.implementations.MTEHatch) {
                ((gregtech.api.metatileentity.implementations.MTEHatch) hatch).updateTexture(textureID);
            }
        }
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
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 3;
        return null;
    }

    @Nullable
    public static Integer getConductorTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockMetal6 && meta == 10) return 1;
        if (block == Blocks.gold_block && meta == 0) return 2;
        if (block == GregTechAPI.sBlockMetal5 && meta == 4) return 3;
        return null;
    }

    @Nullable
    public static Integer getGlassTier(Block block, int meta) {
        if (block == Blocks.glass || block == GregTechAPI.sBlockGlass1) {
            if (meta == 0) return 1;
            if (meta <= 3) return 2;
        }
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2 || mSetTier == 3) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    @Override
    public IStructureDefinition<MTELargeSolarOverpressureArray> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeSolarOverpressureArray>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "CCCCC~CCCCC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC",
                                "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CGGGGGGGGGC", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "C         C", "C         C", "C         C", "C         C", "C         C",
                                "C         C", "C         C", "C         C", "C         C", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC",
                                "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CDDDDDDDDDC", "CCCCCCCCCCC" },
                            { "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC",
                                "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC", "CCCCCCCCCCC" } }))
                .addElement(
                    'C',
                    buildHatchAdder(MTELargeSolarOverpressureArray.class).atLeast(OutputHatch, InputHatch)
                        .casingIndex(bronzeCasingIndex)
                        .dot(1)
                        .buildAndChain(
                            onElementPass(
                                t -> {},
                                ofBlocksTiered(
                                    MTELargeSolarOverpressureArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                    -1,
                                    (MTELargeSolarOverpressureArray t, Integer tier) -> t.mSetTier = tier,
                                    (MTELargeSolarOverpressureArray t) -> t.mSetTier))))
                .addElement(
                    'G',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getGlassTier,
                        ImmutableList.of(Pair.of(Blocks.glass, 0), Pair.of(GregTechAPI.sBlockGlass1, 0)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.mSetTier = tier,
                        (MTELargeSolarOverpressureArray t) -> t.mSetTier))
                .addElement(
                    'D',
                    ofBlocksTiered(
                        MTELargeSolarOverpressureArray::getConductorTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockMetal6, 10),
                            Pair.of(Blocks.gold_block, 0),
                            Pair.of(GregTechAPI.sBlockMetal5, 4)),
                        -1,
                        (MTELargeSolarOverpressureArray t, Integer tier) -> t.mSetTier = tier,
                        (MTELargeSolarOverpressureArray t) -> t.mSetTier))
                .addElement('~', ofBlock(GregTechAPI.sBlockCasings1, 10))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mSetTier = -1;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;
        if (mSetTier <= 0) return false;
        updateHatchTextures();
        return true;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Solar Overpressure Array")
            .addInfo("Produces Steam using Solar Energy")
            .addInfo("Output: " + getBaseOutput() + " L/s")
            .addInfo("Type: " + (isNickel() ? "Superheated Steam" : "Steam"))
            .beginStructureBlock(11, 4, 3, false)
            .addController("Front bottom center")
            .addCasingInfoRange("Casing", 60, -1, false)
            .addOtherStructurePart("Glass (any tier)", "Top layer")
            .addOtherStructurePart("Conductor Block", "Layer 2")
            .addOutputHatch("Any Casing position", 1)
            .addInputHatch("Any Casing position", 1)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (active) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
                    .extFacing()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (mSetTier <= 0) return;

        boolean canSeeSky = aBaseMetaTileEntity.getSkyAtSide(ForgeDirection.UP);
        boolean isDaytime = aBaseMetaTileEntity.getWorld()
            .isDaytime();
        boolean isRaining = aBaseMetaTileEntity.getWorld()
            .isRaining()
            || aBaseMetaTileEntity.getWorld()
                .isThundering();

        float heatUpRate = getHeatUpRate();
        float heatDownRate = getHeatDownRate();

        if (canSeeSky && isDaytime && !isRaining) {
            mHeat = Math.min(1.0f, mHeat + heatUpRate);
        } else {
            mHeat = Math.max(0.0f, mHeat - heatDownRate);
        }

        if (aTick % 20 == 0 && mHeat > 0.0f && canSeeSky && isDaytime) {
            int baseOutput = getBaseOutput();

            float solarBooster = calculateSolarBooster();
            baseOutput = (int) (baseOutput * solarBooster);

            float outputMultiplier = mHeat;

            if (isUsingNormalWater()) {
                mCalcificationTicks += 20;
                long calcStart = getCalcificationStart();
                long calcFull = getCalcificationFull();

                if (mCalcificationTicks > calcFull) {
                    outputMultiplier *= CALCIFICATION_MIN_OUTPUT;
                } else if (mCalcificationTicks > calcStart) {
                    float calcProgress = (float) (mCalcificationTicks - calcStart) / (calcFull - calcStart);
                    outputMultiplier *= (1.0f - calcProgress * (1.0f - CALCIFICATION_MIN_OUTPUT));
                }
            }

            int steamOutput = (int) (baseOutput * outputMultiplier);

            if (steamOutput > 0) {
                if (isNickel()) {
                    distributeSuperheatedSteamToOutputHatches(steamOutput);
                } else {
                    distributeSteamToOutputHatches(steamOutput);
                }
            }
        }
    }

    private float calculateSolarBooster() {
        float booster = 1.0f;

        ItemStack stack = mInventory[0];
        if (stack != null) {
            Item item = stack.getItem();

            if (isItemSolarBoiler(item)) {
                booster += 0.01f * Math.min(stack.stackSize, 64);
            } else if (isItemHighPressureSolarBoiler(item)) {
                booster += 0.02f * Math.min(stack.stackSize, 64);
            }
        }

        return Math.min(booster, 2.0f);
    }

    private boolean isItemSolarBoiler(Item item) {
        String name = item.getUnlocalizedName()
            .toLowerCase();
        return name.contains("solarboiler") || name.contains("steam.solar");
    }

    private boolean isItemHighPressureSolarBoiler(Item item) {
        String name = item.getUnlocalizedName()
            .toLowerCase();
        return name.contains("hpsolarboiler") || name.contains("hp.steam.solar");
    }

    private boolean isUsingNormalWater() {
        return false;
    }

    private void distributeSteamToOutputHatches(int totalSteam) {
        List<IMetaTileEntity> outputHatches = new ArrayList<>();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof MTESteamOutputHatch || hatch instanceof MTEPressureSteamOutputHatch) {
                outputHatches.add(hatch);
            }
        }

        if (outputHatches.isEmpty()) return;

        int perHatch = totalSteam / outputHatches.size();
        int remainder = totalSteam % outputHatches.size();

        for (int i = 0; i < outputHatches.size(); i++) {
            IMetaTileEntity hatch = outputHatches.get(i);
            int amount = perHatch + (i < remainder ? 1 : 0);

            if (amount > 0 && hatch instanceof MTESteamOutputHatch) {
                ((MTESteamOutputHatch) hatch).fill(ForgeDirection.UNKNOWN, Materials.Steam.getGas(amount), true);
            } else if (amount > 0 && hatch instanceof MTEPressureSteamOutputHatch) {
                ((MTEPressureSteamOutputHatch) hatch)
                    .fill(ForgeDirection.UNKNOWN, Materials.Steam.getGas(amount), true);
            }
        }
    }

    private void distributeSuperheatedSteamToOutputHatches(int totalSuperheatedSteam) {
        List<MTEPressureSteamOutputHatch> pressureHatches = new ArrayList<>();

        for (IMetaTileEntity hatch : mOutputHatches) {
            if (hatch instanceof MTEPressureSteamOutputHatch) {
                pressureHatches.add((MTEPressureSteamOutputHatch) hatch);
            }
        }

        if (pressureHatches.isEmpty()) {
            stopMachine();
            return;
        }

        int perHatch = totalSuperheatedSteam / pressureHatches.size();
        int remainder = totalSuperheatedSteam % pressureHatches.size();

        for (int i = 0; i < pressureHatches.size(); i++) {
            MTEPressureSteamOutputHatch hatch = pressureHatches.get(i);
            int amount = perHatch + (i < remainder ? 1 : 0);

            if (amount > 0) {
                FluidStack superheatedSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", amount);
                hatch.fill(ForgeDirection.UNKNOWN, superheatedSteam, true);
            }
        }
    }

    private int getBaseOutput() {
        switch (mSetTier) {
            case 3:
            case 2:
                return 96000;
            case 1:
            default:
                return 24000;
        }
    }

    private float getHeatUpRate() {
        switch (mSetTier) {
            case 1:
                return 0.00015f;
            case 2:
                return 0.00012f;
            case 3:
                return 0.00005f;
            default:
                return 0.00015f;
        }
    }

    private float getHeatDownRate() {
        switch (mSetTier) {
            case 1:
                return 0.0001f;
            case 2:
                return 0.00008f;
            case 3:
                return 0.00004f;
            default:
                return 0.0001f;
        }
    }

    private long getCalcificationStart() {
        switch (mSetTier) {
            case 1:
                return 12L * 3600 * 20;
            case 2:
                return 6L * 3600 * 20;
            case 3:
                return 4L * 3600 * 20;
            default:
                return 12L * 3600 * 20;
        }
    }

    private long getCalcificationFull() {
        switch (mSetTier) {
            case 1:
                return 24L * 3600 * 20;
            case 2:
                return 12L * 3600 * 20;
            case 3:
                return 8L * 3600 * 20;
            default:
                return 24L * 3600 * 20;
        }
    }

    private static final float CALCIFICATION_MIN_OUTPUT = 0.25f;

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(
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
        aNBT.setFloat("mHeat", mHeat);
        aNBT.setLong("mCalcificationTicks", mCalcificationTicks);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mHeat = aNBT.getFloat("mHeat");
        mCalcificationTicks = aNBT.getLong("mCalcificationTicks");
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
    public String[] getDescription() {
        List<String> description = new ArrayList<>();

        description.add(
            EnumChatFormatting.AQUA
                + StatCollector.translateToLocal("gtsr.tooltip.large_solar_overpressure_array.name"));
        description.add(EnumChatFormatting.GREEN + "Output: " + getBaseOutput() + " L/s");
        description
            .add(isNickel() ? EnumChatFormatting.RED + "Superheated Steam" : EnumChatFormatting.YELLOW + "Steam");

        return description.toArray(new String[0]);
    }

    @Override
    public String[] getInfoData() {
        String tierText = isNickel() ? "Nickel" : isSteel() ? "Steel" : isBronze() ? "Bronze" : "N/A";
        return new String[] { EnumChatFormatting.BLUE + "Large Solar Overpressure Array",
            EnumChatFormatting.GRAY + "Tier: " + EnumChatFormatting.GOLD + tierText,
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Heat: " + EnumChatFormatting.YELLOW + String.format("%.1f%%", mHeat * 100),
            EnumChatFormatting.GRAY + "Output: " + EnumChatFormatting.AQUA + getBaseOutput() + " L/s" };
    }
}
