package com.miaokatze.gtsr.common.machine.hatch.critical;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.miaokatze.gtsr.common.critical.CriticalEnergyPorts.LongItemSource;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.gui.modularui.hatch.MTEHatchInputBusMEGui;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;

/** Native GT ME input with independently configured, real long stocking views for critical controllers. */
@IMetaTileEntity.SkipGenerateDescription
public class MTEHatchCriticalInputBusME extends MTEHatchInputBusME {

    private final CriticalMEInputSettings longSettings = new CriticalMEInputSettings();

    public MTEHatchCriticalInputBusME(int id, String name, String regionalName) {
        super(id, false, name, regionalName);
    }

    public MTEHatchCriticalInputBusME(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, false, tier, description, textures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEHatchCriticalInputBusME(mName, mTier, mDescriptionArray, mTextures);
    }

    public long getLongSlotLimit(int slot) {
        return longSettings.get(slot);
    }

    public void setLongSlotLimit(int slot, long amount) {
        longSettings.set(slot, amount);
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** Each returned view remains bound to this slot's type and backing network, never to an int fake stack. */
    public LongItemSource getLongInputSource(int index) {
        ItemStack prototype = getSlotConfig(index);
        if (prototype == null || !isLongInputActive()) return null;
        final IMEMonitor<IAEItemStack> inventory;
        try {
            inventory = getProxy().getStorage()
                .getItemInventory();
        } catch (GridAccessException unavailable) {
            return null;
        }
        return new LongItemSource() {

            @Override
            public Object networkIdentity() {
                return inventory;
            }

            @Override
            public ItemStack prototype() {
                return prototype.copy();
            }

            private boolean current() throws GridAccessException {
                ItemStack current = getSlotConfig(index);
                return current != null
                    && (prototype.getItem() == current.getItem() && prototype.getItemDamage() == current.getItemDamage()
                        && ItemStack.areItemStackTagsEqual(prototype, current))
                    && isLongInputActive()
                    && getProxy().getStorage()
                        .getItemInventory() == inventory;
            }

            @Override
            public long available() {
                try {
                    if (!current()) return 0;
                    IAEItemStack request = AEItemStack.create(prototype);
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
                    IAEItemStack request = AEItemStack.create(prototype);
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
            slot.extracted.stackSize = Math.min(slot.extracted.stackSize, limit);
            slot.extractedAmount = slot.extracted.stackSize;
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
        ItemStack config = getSlotConfig(index);
        long limit = getLongSlotLimit(index);
        if (config == null || limit == 0) return 0;
        try {
            IAEItemStack request = AEItemStack.create(config);
            request.setStackSize(limit);
            IAEItemStack stored = getProxy().getStorage()
                .getItemInventory()
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
        return new MTEHatchInputBusMEGui(this, slots) {

            @Override
            protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
                ParentWidget<?> content = super.createContentSection(panel, syncManager);
                CriticalMEInputDisplay.items(
                    content,
                    syncManager,
                    MTEHatchCriticalInputBusME.this::getSlotConfig,
                    MTEHatchCriticalInputBusME.this::longStock);
                return longSettings.addControl(
                    content,
                    panel,
                    syncManager,
                    MTEHatchCriticalInputBusME.this::longStock,
                    MTEHatchCriticalInputBusME.this::setLongSlotLimit);
            }
        }.build(data, manager, settings);
    }

}
