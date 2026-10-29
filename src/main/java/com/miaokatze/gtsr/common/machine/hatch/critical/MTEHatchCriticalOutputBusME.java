package com.miaokatze.gtsr.common.machine.hatch.critical;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.mixin.CriticalMEOutputProviderAccess;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchCriticalOutputBusME extends MTEHatchOutputBusME {

    public MTEHatchCriticalOutputBusME(int id, String name, String regionalName) {
        super(id, name, regionalName);
        installProvider();
    }

    public MTEHatchCriticalOutputBusME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
        installProvider();
    }

    private void installProvider() {
        ((CriticalMEOutputProviderAccess) (Object) this).gtsr$setCriticalProvider(new CriticalMEOutputProvider<>(this));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchCriticalOutputBusME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gtsr.critical.me.output_bus.desc");
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture base = Textures.BlockIcons.MACHINE_CASINGS[9][color + 1]; // UHV mechanical casing
        return side == facing ? (active ? getTexturesActive(base) : getTexturesInactive(base))
            : new ITexture[] { base };
    }

    @Override
    @SuppressWarnings("unchecked")
    public CriticalMEOutputProvider<IAEItemStack> getProvider() {
        return (CriticalMEOutputProvider<IAEItemStack>) super.getProvider();
    }

    public long getRemainingCapacity() {
        return getProvider().getPhysicalSpace();
    }

    public Batch beginLongOutput() {
        return new Batch();
    }

    @Override
    public IOutputBusTransaction createTransaction() {
        return beginLongOutput();
    }

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize <= 0) return stack != null && stack.stackSize == 0;
        IAEItemStack input = AEItemStack.create(stack);
        getProvider().storePartial(input, simulate);
        stack.stackSize = (int) input.getStackSize();
        return stack.stackSize == 0;
    }

    @Override
    public NBTTagCompound saveStackToNBT(IAEItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        ItemStack identity = stack.getItemStack();
        identity.stackSize = 1;
        tag.setTag("itemStack", GTUtility.saveItem(identity));
        tag.setLong("size", stack.getStackSize());
        return tag;
    }

    @Override
    public void loadNBTData(NBTTagCompound original) {
        NBTTagCompound tag = (NBTTagCompound) original.copy();
        NBTTagList cache = tag.getTagList("cache", 10);
        NBTTagList legacy = tag.getTagList("cachedItems", 10);
        for (int i = 0; i < legacy.tagCount(); i++) {
            NBTTagCompound entry = legacy.getCompoundTagAt(i);
            if (entry.hasKey("itemStack")) cache.appendTag(entry.copy());
            else {
                ItemStack item = GTUtility.loadItem(entry);
                if (item != null) cache.appendTag(saveStackToNBT(AEItemStack.create(item)));
            }
        }
        if (tag.hasKey("cachedStack", 10)) {
            ItemStack item = GTUtility.loadItem(tag.getCompoundTag("cachedStack"));
            if (item != null) cache.appendTag(saveStackToNBT(AEItemStack.create(item)));
        }
        tag.removeTag("cachedStack");
        tag.removeTag("cachedItems");
        tag.setTag("cache", cache);
        super.loadNBTData(tag);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.removeTag("cachedItems");
        tag.removeTag("cachedStack");
    }

    public final class Batch implements IOutputBusTransaction {

        private final CriticalMEOutputProvider.Batch<IAEItemStack> batch = getProvider().beginBatch();

        public long insert(ItemStack prototype, long amount) {
            if (prototype == null) return 0;
            return batch.insert(AEItemStack.create(prototype), amount);
        }

        @Override
        public gregtech.api.interfaces.IOutputBus getBus() {
            return MTEHatchCriticalOutputBusME.this;
        }

        @Override
        public boolean hasAvailableSpace() {
            return batch.remaining() > 0;
        }

        @Override
        public boolean storePartial(GTUtility.ItemId id, ItemStack stack, long totalPerParallel, long perParallel) {
            long accepted = insert(stack, stack.stackSize);
            stack.stackSize -= (int) accepted;
            return accepted > 0;
        }

        @Override
        public void complete(GTUtility.ItemId id) {}

        @Override
        public void commit() {
            batch.commit();
        }
    }
}
