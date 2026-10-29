package com.miaokatze.gtsr.common.machine.hatch.critical;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.mixin.CriticalMEOutputProviderAccess;

import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchCriticalOutputME extends MTEHatchOutputME {

    public MTEHatchCriticalOutputME(int id, String name, String regionalName) {
        super(id, name, regionalName);
        installProvider();
    }

    public MTEHatchCriticalOutputME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
        installProvider();
    }

    private void installProvider() {
        ((CriticalMEOutputProviderAccess) (Object) this).gtsr$setCriticalProvider(new CriticalMEOutputProvider<>(this));
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchCriticalOutputME(mName, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gtsr.critical.me.output_hatch.desc");
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
    public CriticalMEOutputProvider<IAEFluidStack> getProvider() {
        return (CriticalMEOutputProvider<IAEFluidStack>) super.getProvider();
    }

    public long getRemainingCapacity() {
        return getProvider().getPhysicalSpace();
    }

    public Batch beginLongOutput() {
        return new Batch();
    }

    @Override
    public IOutputHatchTransaction createTransaction() {
        return beginLongOutput();
    }

    @Override
    public int fill(FluidStack fluid, boolean doFill) {
        if (fluid == null || fluid.amount <= 0) return 0;
        IAEFluidStack input = AEFluidStack.create(fluid);
        getProvider().storePartial(input, !doFill);
        return fluid.amount - (int) input.getStackSize();
    }

    @Override
    public NBTTagCompound saveStackToNBT(IAEFluidStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        FluidStack identity = stack.getFluidStack();
        identity.amount = 1;
        tag.setTag("fluidStack", identity.writeToNBT(new NBTTagCompound()));
        tag.setLong("size", stack.getStackSize());
        return tag;
    }

    @Override
    public void loadNBTData(NBTTagCompound original) {
        NBTTagCompound tag = (NBTTagCompound) original.copy();
        NBTTagList cache = tag.getTagList("cache", 10);
        NBTTagList legacy = tag.getTagList("cachedFluids", 10);
        for (int i = 0; i < legacy.tagCount(); i++) {
            NBTTagCompound entry = legacy.getCompoundTagAt(i);
            cache.appendTag(entry.copy());
        }
        tag.removeTag("cachedFluids");
        tag.setTag("cache", cache);
        super.loadNBTData(tag);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        tag.removeTag("cachedFluids");
    }

    public final class Batch implements IOutputHatchTransaction {

        private final CriticalMEOutputProvider.Batch<IAEFluidStack> batch = getProvider().beginBatch();

        public long insert(FluidStack prototype, long amount) {
            if (prototype == null) return 0;
            return batch.insert(AEFluidStack.create(prototype), amount);
        }

        @Override
        public gregtech.api.interfaces.IOutputHatch getHatch() {
            return MTEHatchCriticalOutputME.this;
        }

        @Override
        public boolean hasAvailableSpace() {
            return batch.remaining() > 0;
        }

        @Override
        public boolean storePartial(GTUtility.FluidId id, FluidStack stack, long totalPerParallel, long perParallel) {
            long accepted = insert(stack, stack.amount);
            stack.amount -= (int) accepted;
            return accepted > 0;
        }

        @Override
        public void complete(GTUtility.FluidId id) {}

        @Override
        public void commit() {
            batch.commit();
        }
    }
}
