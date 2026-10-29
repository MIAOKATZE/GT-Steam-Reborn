package com.miaokatze.gtsr.common.machine.hatch.critical;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.IterationCounter;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/** One authoritative native cache, with a fixed total capacity and checked long accounting. */
public final class CriticalMEOutputProvider<T extends IAEStack<T>> extends MTEHatchOutputMEBase<T> {

    // AE1080 ceilDiv adds the divisor before subtracting one. Keep both that addition and double power math safe.
    static final long FLUSH_CHUNK = (1L << 52) - 1;
    private final Environment<T> environment;

    public CriticalMEOutputProvider(Environment<T> environment) {
        // Preserve GT's global DEFAULT_CAPACITY for ordinary native outputs.
        super(environment, environment.getChannel() == StorageChannel.FLUIDS ? 128_000 : 1_600);
        this.environment = environment;
        baseCapacity = Long.MAX_VALUE;
    }

    @Override
    public long getCacheCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    public void addToCache(T stack) {
        long amount = stack.getStackSize();
        if (amount < 0 || amount > getPhysicalSpace()) {
            throw new IllegalStateException("Critical ME output cache capacity exceeded");
        }
        if (amount > 0) cache.insert(stack.copy(), amount);
    }

    @Override
    public boolean storePartial(T input, boolean simulate) {
        if (input.getStackSize() <= 0 || !canStore(input)) return input.getStackSize() == 0;
        long accepted = Math.min(input.getStackSize(), getPhysicalSpace());
        if (!simulate && accepted > 0) {
            addToCache(
                input.copy()
                    .setStackSize(accepted));
            updateLastInputTick();
            environment.dispatchMarkDirty();
        }
        input.setStackSize(input.getStackSize() - accepted);
        return input.getStackSize() == 0;
    }

    @Override
    public void flushCachedStack() {
        // GT invokes this synchronously on the server thread. Only the attempted head is passed to native
        // poweredInsert;
        // the disjoint, unattempted tail is restored even if insertion throws. No second persistent cache is
        // maintained.
        long before = getCachedAmount();
        List<T> tails = new ArrayList<>();
        cache.updateAll((stack, amount) -> {
            long head = Math.min(amount, FLUSH_CHUNK);
            if (amount > head) tails.add(
                stack.copy()
                    .setStackSize(amount - head));
            return head;
        });
        try {
            super.flushCachedStack();
        } finally {
            for (T tail : tails) cache.insert(tail, tail.getStackSize());
            if (getCachedAmount() != before) {
                environment.dispatchMarkDirty();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void cellToCacheTransfer() {
        if (getCacheMode() || environment.getCellStack() == null) return;
        IMEInventoryHandler<T> cell = AEApi.instance()
            .registries()
            .cell()
            .getCellInventory(environment.getCellStack(), environment.getISaveProvider(), environment.getChannel());
        if (cell == null) return;
        IItemList<T> stacks = (IItemList<T>) cell.getStackType()
            .createPrimitiveList();
        for (T stack : cell.getAvailableItems(stacks, IterationCounter.fetchNewId())) {
            long amount = Math.min(stack.getStackSize(), getPhysicalSpace());
            if (amount == 0) break;
            T extracted = cell.extractItems(
                stack.copy()
                    .setStackSize(amount),
                Actionable.MODULATE,
                environment.getActionSource());
            if (extracted != null) addToCache(extracted);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        long total = 0;
        NBTTagList entries = tag.getTagList("cache", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            T stack = environment.loadStackFromNBT(entries.getCompoundTagAt(i));
            if (stack == null) continue;
            long amount = stack.getStackSize();
            if (amount < 0 || amount > Long.MAX_VALUE - total) {
                throw new IllegalStateException("Invalid critical ME output cache NBT");
            }
            total += amount;
        }
        // GT's loader appends to its cache. A second load replaces the previous persisted contents instead.
        cache.updateAll((stack, amount) -> 0);
        super.loadNBTData(tag);
        baseCapacity = Long.MAX_VALUE;
    }

    public Batch<T> beginBatch() {
        return new Batch<>(this, environment);
    }

    public static final class Batch<T extends IAEStack<T>> {

        private final CriticalMEOutputProvider<T> provider;
        private final Environment<T> environment;
        private final List<T> pending = new ArrayList<>();
        private long reserved;
        private boolean active = true;

        private Batch(CriticalMEOutputProvider<T> provider, Environment<T> environment) {
            this.provider = provider;
            this.environment = environment;
        }

        public long remaining() {
            return Math.max(0, provider.getPhysicalSpace() - reserved);
        }

        public long insert(T prototype, long amount) {
            requireActive();
            if (amount <= 0 || !provider.canStore(prototype)) return 0;
            long accepted = Math.min(amount, remaining());
            if (accepted > 0) {
                pending.add(
                    prototype.copy()
                        .setStackSize(accepted));
                reserved += accepted;
            }
            return accepted;
        }

        public void commit() {
            requireActive();
            active = false;
            if (reserved > provider.getPhysicalSpace()) {
                throw new IllegalStateException("Critical ME output capacity changed during batch");
            }
            for (T stack : pending) if (!provider.canStore(stack)) {
                throw new IllegalStateException("Critical ME output filter changed during batch");
            }
            for (T stack : pending) provider.addToCache(stack);
            if (reserved > 0) {
                provider.updateLastInputTick();
                environment.dispatchMarkDirty();
            }
        }

        private void requireActive() {
            if (!active) throw new IllegalStateException("Critical ME output batch already committed");
        }
    }
}
