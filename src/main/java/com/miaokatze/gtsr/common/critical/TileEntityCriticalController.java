package com.miaokatze.gtsr.common.critical;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import gregtech.api.interfaces.tileentity.IEnergyConnected;

/** Legacy compatibility host; only the GT MTE drives the business engine. */
public class TileEntityCriticalController extends TileEntity implements ISidedInventory, IFluidHandler,
    IEnergyConnected, com.gtnewhorizon.structurelib.alignment.constructable.IConstructable {

    public static final int INPUT_SLOTS = CriticalMachineState.INPUT_SLOTS;
    public static final int TANK_CAPACITY = CriticalMachineState.TANK_CAPACITY;
    private final CriticalMachineState state = new CriticalMachineState();

    private void bind() {
        state.bindHost(this);
    }

    public CriticalMachineState getCriticalState() {
        bind();
        return state;
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        bind();
        state.writeToNBT(n);
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        bind();
        state.readFromNBT(n);
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || worldObj.getTotalWorldTime() % 20 != 0) return;
        int id = CriticalRuntime.getLoomMetaId();
        if (id <= 0 || gregtech.api.GregTechAPI.METATILEENTITIES[id] == null) return;
        NBTTagCompound legacy = new NBTTagCompound();
        writeToNBT(legacy);
        net.minecraft.block.Block oldBlock = worldObj.getBlock(xCoord, yCoord, zCoord);
        int oldMeta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        try {
            if (!worldObj.setBlock(xCoord, yCoord, zCoord, gregtech.api.GregTechAPI.sBlockMachines, 0, 3)) return;
            TileEntity replacement = worldObj.getTileEntity(xCoord, yCoord, zCoord);
            if (!(replacement instanceof gregtech.api.metatileentity.BaseMetaTileEntity gt))
                throw new IllegalStateException("GT host not created");
            gt.setInitialValuesAsNBT(null, (short) id);
            if (!(gt.getMetaTileEntity() instanceof com.miaokatze.gtsr.common.machine.MTECriticalMachine machine))
                throw new IllegalStateException("Critical MTE not available");
            machine.importLegacyState(legacy);
            String owner = legacy.getString("owner");
            if (!owner.isEmpty()) gt.setOwnerUuid(java.util.UUID.fromString(owner));
            gt.markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        } catch (RuntimeException failure) {
            worldObj.setBlock(xCoord, yCoord, zCoord, oldBlock, oldMeta, 3);
            validate();
            worldObj.setTileEntity(xCoord, yCoord, zCoord, this);
            readFromNBT(legacy);
            markDirty();
        }
    }

    @Override
    public void onChunkUnload() {
        bind();
        state.onChunkUnload();
        super.onChunkUnload();
    }

    @Override
    public void invalidate() {
        bind();
        state.invalidate();
        super.invalidate();
    }

    public CriticalTier getTier() {
        bind();
        return state.getTier();
    }

    public CriticalMachineKind getKind() {
        bind();
        return state.getKind();
    }

    public CriticalMachineKind getMachineKind() {
        bind();
        return state.getMachineKind();
    }

    public int getParallelPlugins() {
        bind();
        return state.getParallelPlugins();
    }

    public int getSpeedPlugins() {
        bind();
        return state.getSpeedPlugins();
    }

    public int getEfficiencyPlugins() {
        bind();
        return state.getEfficiencyPlugins();
    }

    public String getStatus() {
        bind();
        return state.getStatus();
    }

    public int getCursor() {
        bind();
        return state.getCursor();
    }

    public int getConstructionPlaced() {
        bind();
        return state.getConstructionPlaced();
    }

    public boolean isStructureComplete() {
        bind();
        return state.isStructureComplete();
    }

    public boolean isStructureValidated() {
        bind();
        return state.isStructureValidated();
    }

    public int getStage() {
        bind();
        return state.getStage();
    }

    public int getState() {
        bind();
        return state.getState();
    }

    public String getJobId() {
        bind();
        return state.getJobId();
    }

    public String getBatchId() {
        bind();
        return state.getBatchId();
    }

    public long getEnergy() {
        bind();
        return state.getEnergy();
    }

    public CriticalConfiguration getConfiguration() {
        bind();
        return state.getConfiguration();
    }

    public long getEnergyStored() {
        bind();
        return state.getEnergyStored();
    }

    public long getEnergyCapacity() {
        bind();
        return state.getEnergyCapacity();
    }

    public int getJobProgress() {
        bind();
        return state.getJobProgress();
    }

    public int getJobTotal() {
        bind();
        return state.getJobTotal();
    }

    public int getBatchProgress() {
        bind();
        return state.getBatchProgress();
    }

    public int getBatchDuration() {
        bind();
        return state.getBatchDuration();
    }

    public int getStatusCode() {
        bind();
        return state.getStatusCode();
    }

    public int getReasonCode() {
        bind();
        return state.getReasonCode();
    }

    public boolean isEnabled() {
        bind();
        return state.isEnabled();
    }

    public boolean isWorking() {
        bind();
        return state.isWorking();
    }

    public String getStructureId() {
        bind();
        return state.getStructureId();
    }

    public double getVisualCycleTicks(float partialTicks) {
        bind();
        return state.getVisualCycleTicks(partialTicks);
    }

    public int getCycleTicks() {
        bind();
        return state.getCycleTicks();
    }

    public int getCycleDuration() {
        bind();
        return state.getCycleDuration();
    }

    public int getOriginX() {
        bind();
        return state.getOriginX();
    }

    public int getOriginY() {
        bind();
        return state.getOriginY();
    }

    public int getOriginZ() {
        bind();
        return state.getOriginZ();
    }

    public boolean canUse(EntityPlayer p) {
        bind();
        return state.canUse(p);
    }

    public boolean configure(EntityPlayer p, CriticalTier tier, CriticalMachineKind kind, int a, int b, int c) {
        bind();
        return state.configure(p, tier, kind, a, b, c);
    }

    public boolean startConstruction(EntityPlayer p) {
        bind();
        return state.startConstruction(p);
    }

    public boolean startDismantle(EntityPlayer p) {
        bind();
        return state.startDismantle(p);
    }

    public boolean repairStructure(EntityPlayer p) {
        bind();
        return state.repairStructure(p);
    }

    public boolean setEnabled(EntityPlayer p, boolean value) {
        bind();
        return state.setEnabled(p, value);
    }

    public boolean canRemove() {
        bind();
        return state.canRemove();
    }

    public void invalidateStructure() {
        bind();
        state.invalidateStructure();
    }

    public long injectEnergyUnits(ForgeDirection side, long voltage, long amperage) {
        bind();
        return state.injectEnergyUnits(side, voltage, amperage);
    }

    public boolean inputEnergyFrom(ForgeDirection side) {
        bind();
        return state.inputEnergyFrom(side);
    }

    public boolean outputsEnergyTo(ForgeDirection side) {
        bind();
        return state.outputsEnergyTo(side);
    }

    public byte getColorization() {
        bind();
        return state.getColorization();
    }

    public byte setColorization(byte color) {
        bind();
        return state.setColorization(color);
    }

    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        bind();
        return state.fill(from, resource, doFill);
    }

    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        bind();
        return state.drain(from, resource, doDrain);
    }

    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        bind();
        return state.drain(from, maxDrain, doDrain);
    }

    public boolean canFill(ForgeDirection from, Fluid fluid) {
        bind();
        return state.canFill(from, fluid);
    }

    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        bind();
        return state.canDrain(from, fluid);
    }

    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        bind();
        return state.getTankInfo(from);
    }

    public int getSizeInventory() {
        bind();
        return state.getSizeInventory();
    }

    public ItemStack getStackInSlot(int slot) {
        bind();
        return state.getStackInSlot(slot);
    }

    public ItemStack decrStackSize(int slot, int amount) {
        bind();
        return state.decrStackSize(slot, amount);
    }

    public ItemStack getStackInSlotOnClosing(int slot) {
        bind();
        return state.getStackInSlotOnClosing(slot);
    }

    public void setInventorySlotContents(int slot, ItemStack stack) {
        bind();
        state.setInventorySlotContents(slot, stack);
    }

    public String getInventoryName() {
        bind();
        return state.getInventoryName();
    }

    public boolean hasCustomInventoryName() {
        bind();
        return state.hasCustomInventoryName();
    }

    public int getInventoryStackLimit() {
        bind();
        return state.getInventoryStackLimit();
    }

    public boolean isUseableByPlayer(EntityPlayer p) {
        bind();
        return state.isUseableByPlayer(p);
    }

    public void openInventory() {
        bind();
        state.openInventory();
    }

    public void closeInventory() {
        bind();
        state.closeInventory();
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        bind();
        return state.isItemValidForSlot(slot, stack);
    }

    public int[] getAccessibleSlotsFromSide(int side) {
        bind();
        return state.getAccessibleSlotsFromSide(side);
    }

    public boolean canInsertItem(int slot, ItemStack item, int side) {
        bind();
        return state.canInsertItem(slot, item, side);
    }

    public boolean canExtractItem(int slot, ItemStack item, int side) {
        bind();
        return state.canExtractItem(slot, item, side);
    }

    public Packet getDescriptionPacket() {
        bind();
        return state.getDescriptionPacket();
    }

    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        bind();
        state.onDataPacket(network, packet);
    }

    public AxisAlignedBB getRenderBoundingBox() {
        bind();
        return state.getRenderBoundingBox();
    }

    public double getMaxRenderDistanceSquared() {
        bind();
        return state.getMaxRenderDistanceSquared();
    }

    public void construct(ItemStack trigger, boolean hintsOnly) {
        bind();
        state.construct(trigger, hintsOnly);
    }

    public String[] getStructureDescription(ItemStack trigger) {
        bind();
        return state.getStructureDescription(trigger);
    }
}
