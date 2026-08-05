package com.miaokatze.gtsr.common.machine;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTECriticalSingularityCompressorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;

/** Tier 2 steam entanglement machine. */
public class MTECriticalSingularityCompressor extends MTESingularityMachineBase {

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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        super.checkMachine(aBaseMetaTileEntity, aStack, errors);
        updateAllHatchTextures();
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
    public CheckRecipeResult checkProcessing() {
        return processAggregationCycle();
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTECriticalSingularityCompressorGui(this);
    }
}
