package com.miaokatze.gtsr.common.gui.mui2.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;

/** A real MUI container with one idempotent viewer lease per open interval. */
public final class UnsealedChestModularContainer extends GuardedModularContainer {

    public static final int CHEST_SLOTS = 54;
    public static final int TOTAL_SLOTS = 90;
    private final TileEntityUnsealedChest chest;
    private final AtomicBoolean leaseRequested = new AtomicBoolean();
    private volatile boolean disposed;

    public UnsealedChestModularContainer(TileEntityUnsealedChest chest) {
        this.chest = chest;
    }

    public boolean isViewing(TileEntityUnsealedChest inventory) {
        return !disposed && leaseRequested.get()
            && inventory == chest
            && isInitialized()
            && !isClient()
            && getPlayer().openContainer == this
            && getSyncManager().isOpen()
            && canInteractWith(getPlayer());
    }

    @Override
    public void onModularContainerOpened() {
        super.onModularContainerOpened();
        if (!isClient() && !disposed && leaseRequested.compareAndSet(false, true)) {
            Mui2ServerActions.enqueue(() -> { if (isViewing(chest)) chest.openModularInventory(this); });
        }
    }

    private void releaseLease() {
        if (!isClient() && leaseRequested.compareAndSet(true, false)) {
            // Release the captured token even if another container/TE has already replaced this one.
            Mui2ServerActions.enqueue(() -> chest.closeModularInventory(this));
        }
    }

    @Override
    public void onModularContainerClosed() {
        releaseLease();
        super.onModularContainerClosed();
    }

    @Override
    public void onModularContainerDisposed() {
        releaseLease();
        disposed = true;
        super.onModularContainerDisposed();
    }

    @Override
    protected ItemStack transferItem(ModularSlot source, ItemStack offered) {
        int index = inventorySlots.indexOf(source);
        List<ModularSlot> targets = new ArrayList<>();
        if (index >= 0 && index < CHEST_SLOTS) {
            for (int n = TOTAL_SLOTS - 1; n >= CHEST_SLOTS; n--) targets.add(getModularSlot(n));
        } else if (index >= CHEST_SLOTS) {
            for (int n = 0; n < CHEST_SLOTS; n++) targets.add(getModularSlot(n));
        }
        return InventorySlots.moveInto(offered, targets);
    }
}
