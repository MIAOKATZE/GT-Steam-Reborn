package com.miaokatze.gtsr.common.machine.hatch.critical;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.miaokatze.gtsr.common.critical.CriticalEnergyPorts.LongFluidSource;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEFluidStack;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.gui.modularui.hatch.MTEHatchInputMEGui;
import gregtech.common.tileentities.machines.MTEHatchInputME;

/** Native GT ME input with independently configured, real long stocking views for critical controllers. */
@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchCriticalInputME extends MTEHatchInputME {

    private final CriticalMEInputSettings longSettings = new CriticalMEInputSettings();

    public MTEHatchCriticalInputME(int id, String name, String regionalName) {
        super(id, false, name, regionalName);
    }

    public MTEHatchCriticalInputME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, false, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchCriticalInputME(mName, mTier, mDescriptionArray, mTextures);
    }

    public long getLongSlotLimit(int slot) {
        return longSettings.get(slot);
    }

    public void setLongSlotLimit(int slot, long amount) {
        longSettings.set(slot, amount);
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** Each returned view remains bound to this slot's type and backing network, never to an int fake stack. */
    public LongFluidSource getLongInputSource(int index) {
        FluidStack prototype = getSlotConfig(index);
        if (prototype == null || !isLongInputActive()) return null;
        final IMEMonitor<IAEFluidStack> inventory;
        try {
            inventory = getProxy().getStorage()
                .getFluidInventory();
        } catch (GridAccessException unavailable) {
            return null;
        }
        return new LongFluidSource() {

            @Override
            public Object networkIdentity() {
                return inventory;
            }

            @Override
            public FluidStack prototype() {
                return prototype.copy();
            }

            private boolean current() throws GridAccessException {
                FluidStack current = getSlotConfig(index);
                return current != null && (prototype.isFluidEqual(current))
                    && isLongInputActive()
                    && getProxy().getStorage()
                        .getFluidInventory() == inventory;
            }

            @Override
            public long available() {
                try {
                    if (!current()) return 0;
                    IAEFluidStack request = AEFluidStack.create(prototype);
                    request.setStackSize(getLongSlotLimit(index));
                    return CriticalMETransfer
                        .available(getProxy().getEnergy(), inventory, request, longRequestSource());
                } catch (GridAccessException unavailable) {
                    return 0;
                }
            }

            @Override
            public long extract(long wanted, boolean commit) {
                if (wanted <= 0 || wanted > getLongSlotLimit(index)) return 0;
                try {
                    if (!current()) return 0;
                    IAEFluidStack request = AEFluidStack.create(prototype);
                    request.setStackSize(wanted);
                    return CriticalMETransfer
                        .extract(getProxy().getEnergy(), inventory, request, longRequestSource(), commit);
                } catch (GridAccessException unavailable) {
                    return 0;
                }
            }
        };
    }

    private boolean isLongInputActive() {
        return isValid() && getBaseMetaTileEntity() != null
            && getBaseMetaTileEntity().isAllowedToWork()
            && isAllowedToWork()
            && getProxy().isActive();
    }

    private BaseActionSource longRequestSource() {
        if (requestSource == null) requestSource = new MachineSource((IActionHost) getBaseMetaTileEntity());
        return requestSource;
    }

    @Override
    public void updateInformationSlot(int index) throws GridAccessException {
        super.updateInformationSlot(index);
        Slot slot = slots[index];
        if (slot != null && slot.extracted != null) {
            int limit = (int) Math.min(Integer.MAX_VALUE, getLongSlotLimit(index));
            slot.extracted.amount = Math.min(slot.extracted.amount, limit);
            slot.extractedAmount = slot.extracted.amount;
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        longSettings.save(tag);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        longSettings.load(tag);
    }

    @Override
    public NBTTagCompound getCopiedData(net.minecraft.entity.player.EntityPlayer player) {
        NBTTagCompound tag = super.getCopiedData(player);
        longSettings.save(tag);
        return tag;
    }

    @Override
    public boolean pasteCopiedData(net.minecraft.entity.player.EntityPlayer player, NBTTagCompound tag) {
        if (!super.pasteCopiedData(player, tag)) return false;
        longSettings.load(tag);
        getBaseMetaTileEntity().markDirty();
        return true;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture base = Textures.BlockIcons.MACHINE_CASINGS[9][color + 1];
        return side == facing ? (active ? getTexturesActive(base) : getTexturesInactive(base))
            : new ITexture[] { base };
    }

    /** Native ME information-slot semantics: visible marked stock, independent of extraction energy. */
    private long longStock(int index) {
        if (!getBaseMetaTileEntity().isServerSide() || !isLongInputActive()) return 0;
        FluidStack config = getSlotConfig(index);
        long limit = getLongSlotLimit(index);
        if (config == null || limit == 0) return 0;
        try {
            IAEFluidStack request = AEFluidStack.create(config);
            request.setStackSize(limit);
            IAEFluidStack stored = getProxy().getStorage()
                .getFluidInventory()
                .extractItems(request, Actionable.SIMULATE, longRequestSource());
            return stored == null ? 0 : Math.min(limit, stored.getStackSize());
        } catch (GridAccessException unavailable) {
            return 0;
        }
    }

    @Override
    public String[] getDescription() {
        String[] original = super.getDescription();
        String[] extra = gregtech.api.util.GTSplit.splitLocalized("gtsr.critical.me.input.desc");
        String[] combined = java.util.Arrays.copyOf(original, original.length + extra.length);
        System.arraycopy(extra, 0, combined, original.length, extra.length);
        return combined;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        return new MTEHatchInputMEGui(this, slots) {

            @Override
            protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
                ParentWidget<?> content = super.createContentSection(panel, syncManager);
                CriticalMEInputDisplay.fluids(
                    content,
                    syncManager,
                    MTEHatchCriticalInputME.this::getSlotConfig,
                    MTEHatchCriticalInputME.this::longStock);
                return longSettings.addControl(
                    content,
                    panel,
                    syncManager,
                    MTEHatchCriticalInputME.this::longStock,
                    MTEHatchCriticalInputME.this::setLongSlotLimit);
            }
        }.build(data, manager, settings);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        FluidTankInfo[] tanks = super.getTankInfo(side);
        for (FluidTankInfo tank : tanks) {
            if (tank.fluid == null) continue;
            long limit = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                FluidStack config = getSlotConfig(i);
                if (config != null && config.isFluidEqual(tank.fluid)) limit = Math.max(limit, getLongSlotLimit(i));
            }
            tank.fluid.amount = (int) Math.min(tank.fluid.amount, limit);
        }
        return tanks;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluid, int amount, boolean commit) {
        long limit = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            FluidStack config = getSlotConfig(i);
            if (config != null && config.isFluidEqual(fluid)) limit = Math.max(limit, getLongSlotLimit(i));
        }
        return super.drain(side, fluid, (int) Math.min(amount, limit), commit);
    }

}
