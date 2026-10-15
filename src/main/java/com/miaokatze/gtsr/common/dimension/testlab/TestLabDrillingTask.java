package com.miaokatze.gtsr.common.dimension.testlab;

import java.util.Locale;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.mojang.authlib.GameProfile;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Dedicated real remote-mining scene: bound item, seeded ore/deposit, pipe inventory and observed hub output. */
final class TestLabDrillingTask implements TestLabTask {

    private final MetaTileEntityID target;
    private final boolean fluid;
    private IGregTechTileEntity tile, node, steamInput, itemOutput, fluidOutput;
    private MTESingularityDrillingHub hub;
    private TestLabStructure structure;
    private TestLabUnderground deposit;
    private int phase, originX, originY, originZ;
    private long initialDeposit;
    private boolean oreSeeded;

    TestLabDrillingTask(MetaTileEntityID target) {
        this.target = target;
        fluid = target == MetaTileEntityID.SINGULARITY_DRILLING_NODE;
    }

    @Override
    public String id() {
        return target.name()
            .toLowerCase(Locale.ROOT);
    }

    @Override
    public String displayName() {
        return target.name() + " real bound mining scene";
    }

    @Override
    public int timeoutTicks() {
        return 2400;
    }

    @Override
    public void start(WorldServer world, int x, int y, int z) throws Exception {
        originX = x;
        originY = y;
        originZ = z;
        tile = TestLabFixtures.machine(world, x + 80, y + 12, z + 48, MetaTileEntityID.SINGULARITY_DRILLING_HUB.ID);
        hub = (MTESingularityDrillingHub) tile.getMetaTileEntity();
        structure = new TestLabStructure(world, hub, new ItemStack(net.minecraft.init.Items.stick));
    }

    @Override
    public TestLabReport.Result tick(WorldServer world, int elapsedTicks) throws Exception {
        if (phase == 0) {
            if (!structure.buildTick()) return null;
            phase++;
            return null;
        }
        if (phase == 1) {
            steamInput = structure.install(
                ItemList.Hatch_Input_HV.get(1)
                    .getItemDamage());
            phase++;
            return null;
        }
        if (phase == 2) {
            itemOutput = structure.install(
                ItemList.Hatch_Output_Bus_HV.get(1)
                    .getItemDamage());
            phase++;
            return null;
        }
        if (phase == 3) {
            fluidOutput = structure.install(
                ItemList.Hatch_Output_HV.get(1)
                    .getItemDamage());
            if (steamInput == null || itemOutput == null || fluidOutput == null)
                return result(TestLabReport.Status.FAIL, "structure=FAIL native-compatible hub ports not found");
            if (!hub.checkStructure(true, tile)) return result(
                TestLabReport.Status.FAIL,
                "structure=FAIL native checkStructure rejected " + structure.diagnoseFailure());
            TestLabSupply.add(world, steamInput, FluidRegistry.getFluidStack("ic2superheatedsteam", 1));
            seedAndBind(world);
            tile.enableWorking();
            node.enableWorking();
            phase++;
            return null;
        }
        boolean consumed = TestLabSupply.replenished(steamInput) > 0;
        if (fluid) {
            FluidStack output = ((MTEHatchOutput) fluidOutput.getMetaTileEntity()).getFluid();
            if (output != null && output.amount > 0 && deposit.amount() < initialDeposit && consumed) return result(
                TestLabReport.Status.PASS,
                "structure=PASS production=PASS real_binding=true real_deposit_consumed="
                    + (initialDeposit - deposit.amount())
                    + " observed="
                    + output.getFluid()
                        .getName()
                    + "="
                    + output.amount
                    + " steamConsumed=true");
        } else {
            IInventory inventory = (IInventory) itemOutput;
            for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
                ItemStack output = inventory.getStackInSlot(slot);
                if (output != null && output.stackSize > 0
                    && oreSeeded
                    && world.isAirBlock(node.getXCoord() + 1, node.getYCoord() - 1, node.getZCoord())
                    && consumed)
                    return result(
                        TestLabReport.Status.PASS,
                        "structure=PASS production=PASS real_binding=true seeded_ore_removed=true real_pipe_inventory=true observed="
                            + output
                            + " steamConsumed=true");
            }
        }
        return null;
    }

    private void seedAndBind(WorldServer world) throws Exception {
        MetaTileEntityID nodeId = fluid ? MetaTileEntityID.SINGULARITY_DRILLING_NODE
            : MetaTileEntityID.SINGULARITY_MINER_NODE;
        FakePlayer player = FakePlayerFactory.get(world, new GameProfile(TestLabFixtures.OWNER, "GTSR Test Lab"));
        player.setPosition(tile.getXCoord(), tile.getYCoord(), tile.getZCoord() - 2);
        player.inventory.currentItem = 0;
        player.inventory.mainInventory[0] = GregTechAPI.METATILEENTITIES[nodeId.ID].getStackForm(1);
        player.inventory.mainInventory[1] = GTSRItemList.SteamEntangledSingularity.get(1);
        hub.tryHandleNodeBindClick(tile, player, true);
        ItemStack bound = player.getHeldItem();
        if (bound == null || !bound.hasTagCompound()
            || !bound.getTagCompound()
                .hasKey("gtsr.hubPos"))
            throw new IllegalStateException("Real drilling-hub binding failed");
        node = TestLabFixtures.machine(world, originX + 112, originY + 12, originZ + 64, nodeId.ID);
        NBTTagCompound state = new NBTTagCompound();
        node.getMetaTileEntity()
            .saveNBTData(state);
        state.setTag(
            "gtsr.hubPos",
            bound.getTagCompound()
                .getCompoundTag("gtsr.hubPos")
                .copy());
        node.getMetaTileEntity()
            .loadNBTData(state);
        ItemStack pipes = GTModHandler.getIC2Item("miningPipe", 16);
        if (pipes == null) throw new IllegalStateException("Missing IC2 mining pipes");
        ((IInventory) node).setInventorySlotContents(0, pipes);
        player.inventory.mainInventory[0] = null;
        player.inventory.mainInventory[1] = null;
        for (int depth = 1; depth <= 16; depth++)
            TestLabFixtures.track(world, node.getXCoord(), node.getYCoord() - depth, node.getZCoord());
        if (fluid) {
            TestLabFixtures.track(world, node.getXCoord(), node.getYCoord() - 1, node.getZCoord());
            world.setBlock(node.getXCoord(), node.getYCoord() - 1, node.getZCoord(), Blocks.bedrock, 0, 2);
            deposit = new TestLabUnderground(world, node.getXCoord() >> 4, node.getZCoord() >> 4);
            initialDeposit = deposit.amount();
        } else {
            TestLabFixtures.track(world, node.getXCoord() + 1, node.getYCoord() - 1, node.getZCoord());
            world.setBlock(node.getXCoord() + 1, node.getYCoord() - 1, node.getZCoord(), Blocks.iron_ore, 0, 2);
            oreSeeded = true;
        }
    }

    @Override
    public void stop(WorldServer world) {
        if (steamInput != null) TestLabSupply.remove(steamInput);
        if (tile != null) tile.disableWorking();
        if (hub != null) hub.stopMachine(ShutDownReasonRegistry.NONE);
        if (node != null) {
            if (hub != null) hub
                .unregisterCacheNode(node.getXCoord(), node.getYCoord(), node.getZCoord(), world.provider.dimensionId);
            node.disableWorking();
            TestLabFixtures.unbindNode(node);
            ((MTERemoteWorkerNode) node.getMetaTileEntity()).releaseAllChunks();
        }
        if (deposit != null) deposit.restore();
    }

    @Override
    public String debug(WorldServer world) {
        return "phase=" + phase
            + " scene="
            + (fluid ? "seeded_lab_chunk_deposit" : "seeded_iron_ore")
            + " "
            + TestLabFixtures.snapshot(tile)
            + " node{"
            + TestLabFixtures.snapshot(node)
            + "} output{"
            + TestLabFixtures.snapshot(fluid ? fluidOutput : itemOutput)
            + "} supply="
            + TestLabSupply.describe(world);
    }

    private TestLabReport.Result result(TestLabReport.Status status, String detail) {
        return new TestLabReport.Result(
            status,
            detail + " controller="
                + tile.getXCoord()
                + ","
                + tile.getYCoord()
                + ","
                + tile.getZCoord()
                + " node="
                + (node == null ? "null" : node.getXCoord() + "," + node.getYCoord() + "," + node.getZCoord()));
    }
}
