package com.miaokatze.gtsr.common.commands;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.blocks.TileSpacetimeSingularity;
import com.miaokatze.gtsr.common.dimension.prosperity.air.ProsperityAirAudit;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.IndustrialRecipeLedger;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialRecipes;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;
import com.miaokatze.gtsr.config.Config;
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
        return "/gtsrspacetimeaudit [portal|production]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        try {
            ProsperityIndustrialMaterials.verifyRegistration();
            ProsperityIndustrialMaterials.auditRegistration();
            ProsperityIndustrialRecipes.verifyRegistration();
            SpacetimeMachineRecipes.verifyRegistration();
            int stages = IndustrialRecipeLedger.get().recipes.size();
            int distilleryAlternatives = 0;
            for (IndustrialRecipeLedger.Stage stage : IndustrialRecipeLedger.get().recipes) {
                if ("distillationTowerRecipes".equals(stage.machineMap) && stage.outputs.size() == 1
                    && "fluid".equals(stage.outputs.get(0).kind)) distilleryAlternatives++;
            }
            require(
                ProsperityIndustrialRecipes.getRegisteredRecipeCount() == stages + distilleryAlternatives,
                "Industrial recipe count");
            for (String id : new String[] { "gtsr:fiction_witness_pedestal", "gtsr:fiction_starter" }) {
                Block block = RemasterBlocks.get(id);
                require(
                    block != null && block.getBlockHardness(null, 0, 0, 0) == -1F
                        && block.getMobilityFlag() == 2
                        && !block.canDropFromExplosion(null),
                    "Protected fixture block: " + id);
            }
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
                    "[GTSR-SPACETIME-AUDIT] PASS materials=64 dust=15 gas=10 fluid=39 recipes="
                        + ProsperityIndustrialRecipes.getRegisteredRecipeCount()
                        + " stages="
                        + stages
                        + " distillery="
                        + distilleryAlternatives
                        + " colors=true unbreakable=true crafts=2 controller=true ic2=true trips=5"));
            if (args.length > 0 && "portal".equals(args[0])) verifyPortal(sender);
            if (args.length > 0 && "production".equals(args[0])) {
                WorldServer world = DimensionManager.getWorld(Config.prosperityDimId);
                if (world == null) {
                    DimensionManager.initDimension(Config.prosperityDimId);
                    world = DimensionManager.getWorld(Config.prosperityDimId);
                }
                require(world != null, "Prosperity server world");
                ProsperityAirAudit.verify(sender, world);
            }
        } catch (RuntimeException failure) {
            sender.addChatMessage(new ChatComponentText("[GTSR-SPACETIME-AUDIT] FAIL " + failure.getMessage()));
            throw failure;
        }
    }

    private static void verifyPortal(ICommandSender sender) {
        require(BlocksGTSR.spacetimeSingularity != null, "Spacetime block registration");
        double[] point = SpacetimeTravel.findProsperityLanding();
        WorldServer world = DimensionManager.getWorld(Config.prosperityDimId);
        require(SpacetimeTravel.verifyProsperityLanding(world, point), "Origin surface landing");
        require(
            BlocksGTSR.spacetimeSingularity.createTileEntity(world, 0) instanceof TileSpacetimeSingularity,
            "Independent spacetime tile factory");
        TileSpacetimeSingularity core = new TileSpacetimeSingularity();
        core.setWorldObj(world);
        require(core.getType() == TileRunawaySingularity.SingularityType.STABLE, "Stable spacetime identity");
        require(core.getActiveFactor() == 0, "Unowned core cannot activate");
        NBTTagCompound nbt = new NBTTagCompound();
        core.writeToNBT(nbt);
        nbt.getCompoundTag("gtsrSingularity")
            .setInteger("attribute", TileRunawaySingularity.ATTRIBUTE_NATURE);
        nbt.getCompoundTag("gtsrSingularity")
            .setString("type", "NATURAL");
        nbt.setBoolean("calibrationActive", true);
        nbt.setBoolean("calibrationVisible", true);
        TileSpacetimeSingularity restored = new TileSpacetimeSingularity();
        restored.setWorldObj(world);
        restored.readFromNBT(nbt);
        require(
            restored.getType() == TileRunawaySingularity.SingularityType.STABLE && restored.getActiveFactor() == 0,
            "Server NBT cannot restore a live portal lease");
        require(
            restored.getAttributeId() == TileRunawaySingularity.ATTRIBUTE_NULL_PLUS && restored.getDuration() == -1
                && restored.getDamage() == 0
                && !restored.isNatural(),
            "Spacetime NBT cannot inherit natural mining or damage");
        sender.addChatMessage(
            new ChatComponentText(
                "[GTSR-SPACETIME-PORTAL] PASS dimension=" + Config.prosperityDimId
                    + " origin="
                    + point[0]
                    + ","
                    + point[1]
                    + ","
                    + point[2]
                    + " stable_block=true cold_nbt=true"));
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
