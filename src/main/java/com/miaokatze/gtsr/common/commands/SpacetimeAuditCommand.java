package com.miaokatze.gtsr.common.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialRecipes;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;
import com.miaokatze.gtsr.loader.recipes.SpacetimeMachineRecipes;

import gregtech.api.GregTechAPI;
import ic2.api.item.ElectricItem;

/** Read-only registry and detached-stack diagnostics for administrators and dedicated-server verification. */
public final class SpacetimeAuditCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtsrspacetimeaudit";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtsrspacetimeaudit";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            ProsperityIndustrialMaterials.verifyRegistration();
            ProsperityIndustrialRecipes.verifyRegistration();
            SpacetimeMachineRecipes.verifyRegistration();
            require(ProsperityIndustrialRecipes.getRegisteredRecipeCount() == 70, "Industrial recipe count");
            require(
                GregTechAPI.METATILEENTITIES[MetaTileEntityID.SPACETIME_CALIBRATION.ID] instanceof MTESpacetimeCalibration,
                "Controller registry");
            MTESpacetimeCalibration prototype = (MTESpacetimeCalibration) GregTechAPI.METATILEENTITIES[MetaTileEntityID.SPACETIME_CALIBRATION.ID];
            require(prototype.getStructureDefinition() != null, "Runtime structure materials and shape");
            ItemStack beacon = GTSRItemList.SpacetimeAnchorBeacon.get(1)
                .copy();
            require(beacon.getItem() instanceof SpacetimeAnchorBeacon, "Beacon registry");
            SpacetimeAnchorBeacon item = (SpacetimeAnchorBeacon) beacon.getItem();
            require(item.getTier(beacon) == 6 && item.getTransferLimit(beacon) == 32768, "LuV charger contract");
            double charged = ElectricItem.manager.charge(beacon, 102400000, 6, true, false);
            require(
                charged == 102400000 && ElectricItem.manager.getCharge(beacon) == 102400000,
                "Real IC2 full charge");
            for (int i = 0; i < 5; i++) {
                double simulated = ElectricItem.manager.discharge(beacon, 20480000, 6, true, false, true);
                require(
                    simulated == 20480000 && ElectricItem.manager.getCharge(beacon) == 102400000 - i * 20480000,
                    "Reservation must not consume charge");
                require(
                    ElectricItem.manager.discharge(beacon, 20480000, 6, true, false, false) == 20480000,
                    "Successful charge settlement");
            }
            require(ElectricItem.manager.getCharge(beacon) == 0, "Five trips per full charge");
            require(
                ElectricItem.manager.discharge(beacon, 20480000, 6, true, false, true) == 0,
                "Empty beacon cannot reserve a trip");
            sender.addChatMessage(
                new ChatComponentText(
                    "[GTSR-SPACETIME-AUDIT] PASS materials=64 dust=15 gas=10 fluid=39 recipes=70 crafts=2 controller=true ic2=true trips=5"));
        } catch (RuntimeException failure) {
            sender.addChatMessage(new ChatComponentText("[GTSR-SPACETIME-AUDIT] FAIL " + failure.getMessage()));
            throw failure;
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
