package com.miaokatze.gtsr.common.critical;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Vanilla window packets carry inventory and compact telemetry; actions are validated on the server. */
public final class ContainerCriticalController extends Container {

    public static final int WIDTH = 348;
    public static final int HEIGHT = 326;
    private static final int MACHINE_SLOTS = 36;
    private final TileEntityCriticalController controller;
    private final int[] telemetry = new int[23];
    private int[] previous;

    public ContainerCriticalController(EntityPlayer player, TileEntityCriticalController controller) {
        this.controller = controller;
        for (int i = 0; i < MACHINE_SLOTS; i++) {
            int x = 10 + i % 9 * 18 + (i >= 18 ? 168 : 0);
            int y = 138 + (i % 18 / 9) * 18;
            addSlotToContainer(new Slot(controller, i, x, y) {

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return controller.isItemValidForSlot(getSlotIndex(), stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 94 + col * 18, 242 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, col, 94 + col * 18, 300));
        }
        sample();
    }

    public CriticalConfiguration configuration() {
        return new CriticalConfiguration(
            CriticalTier.byId(telemetry[0]),
            CriticalMachineKind.byId(telemetry[1]),
            telemetry[2],
            telemetry[3],
            telemetry[4]);
    }

    public int statusCode() {
        return telemetry[5];
    }

    public boolean enabled() {
        return telemetry[6] != 0;
    }

    public long energy() {
        return combineLong(7);
    }

    public long capacity() {
        return combineLong(11);
    }

    public int jobProgress() {
        return telemetry[15] | telemetry[16] << 16;
    }

    public int batchProgress() {
        return telemetry[17];
    }

    public int batchDuration() {
        return telemetry[18];
    }

    public int reasonCode() {
        return telemetry[19];
    }

    public int stage() {
        return telemetry[20];
    }

    public int jobTotal() {
        return telemetry[21] | telemetry[22] << 16;
    }

    private long combineLong(int start) {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long) telemetry[start + i] << (i * 16);
        return value;
    }

    private void storeLong(int start, long value) {
        for (int i = 0; i < 4; i++) telemetry[start + i] = (int) (value >>> (i * 16)) & 65535;
    }

    private void sample() {
        CriticalConfiguration config = controller.getConfiguration();
        telemetry[0] = config.tier.ordinal();
        telemetry[1] = config.kind.ordinal();
        telemetry[2] = config.parallel;
        telemetry[3] = config.speed;
        telemetry[4] = config.economy;
        telemetry[5] = controller.getStatusCode();
        telemetry[6] = controller.isEnabled() ? 1 : 0;
        storeLong(7, controller.getEnergyStored());
        storeLong(11, controller.getEnergyCapacity());
        telemetry[15] = controller.getJobProgress() & 65535;
        telemetry[16] = controller.getJobProgress() >>> 16;
        telemetry[17] = Math.min(65535, controller.getBatchProgress());
        telemetry[18] = Math.min(65535, controller.getBatchDuration());
        telemetry[19] = controller.getReasonCode();
        telemetry[20] = controller.getStage();
        telemetry[21] = controller.getJobTotal() & 65535;
        telemetry[22] = controller.getJobTotal() >>> 16;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        sample();
        for (Object observer : crafters) {
            ICrafting client = (ICrafting) observer;
            for (int i = 0; i < telemetry.length; i++) {
                if (previous == null || previous[i] != telemetry[i])
                    client.sendProgressBarUpdate(this, i, telemetry[i]);
            }
        }
        previous = telemetry.clone();
    }

    @Override
    public void addCraftingToCrafters(ICrafting client) {
        super.addCraftingToCrafters(client);
        sample();
        for (int i = 0; i < telemetry.length; i++) client.sendProgressBarUpdate(this, i, telemetry[i]);
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id >= 0 && id < telemetry.length) telemetry[id] = value & 65535;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return controller.isUseableByPlayer(player);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (controller.getWorldObj().isRemote || !canInteractWith(player)) return false;
        CriticalConfiguration config = controller.getConfiguration();
        if (action >= 1 && action <= 8) {
            int tier = config.tier.ordinal(), kind = config.kind.ordinal();
            int a = config.parallel, b = config.speed, c = config.economy;
            switch (action) {
                case 1:
                    tier = (tier + 1) % 3;
                    break;
                case 2:
                    kind = (kind + 1) % 9;
                    break;
                case 3:
                    a++;
                    break;
                case 4:
                    a--;
                    break;
                case 5:
                    b++;
                    break;
                case 6:
                    b--;
                    break;
                case 7:
                    c++;
                    break;
                case 8:
                    c--;
                    break;
                default:
                    return false;
            }
            if (a < 0 || b < 0 || c < 0 || a + b + c > 8) return false;
            controller.configure(player, CriticalTier.byId(tier), CriticalMachineKind.byId(kind), a, b, c);
        } else if (action == 9) controller.startConstruction(player);
        else if (action == 10) controller.startDismantle(player);
        else if (action == 11) controller.setEnabled(player, !controller.isEnabled());
        else if (action == 12) controller.repairStructure(player);
        else return false;
        detectAndSendChanges();
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (!canInteractWith(player) || index < 0 || index >= inventorySlots.size()) return null;
        Slot slot = (Slot) inventorySlots.get(index);
        if (!slot.getHasStack()) return null;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!mergeItemStack(stack, MACHINE_SLOTS, inventorySlots.size(), true)) return null;
        } else if (!mergeItemStack(stack, 0, 18, false)) return null;
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        if (stack.stackSize == original.stackSize) return null;
        slot.onPickupFromSlot(player, stack);
        return original;
    }
}
