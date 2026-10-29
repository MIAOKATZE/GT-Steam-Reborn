package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.critical.CriticalEnergyPorts.FluidAmount;

import gregtech.api.util.GTUtility;

/** Bounded registry identities and quantities. Names are resolved in the receiving client's language. */
final class CriticalMaterialSnapshot {

    private static final int LIMIT = 64, MAX_ID = 256, MAX_TAG = 1024, TAG_BUDGET = 8192;
    private final List<Entry> entries;
    private final int omitted;

    private CriticalMaterialSnapshot(List<Entry> entries, int omitted) {
        this.entries = entries;
        this.omitted = omitted;
    }

    static CriticalMaterialSnapshot capture(ItemStack[] items, FluidStack[] fluids) {
        List<FluidAmount> amounts = new ArrayList<>();
        for (FluidStack fluid : fluids) if (fluid != null && fluid.amount > 0)
            amounts.add(new FluidAmount(fluid, BigInteger.valueOf(fluid.amount)));
        return capture(items, amounts);
    }

    static CriticalMaterialSnapshot capture(ItemStack[] items, List<FluidAmount> fluids) {
        List<Entry> entries = new ArrayList<>();
        int omitted = 0;
        int tagBudget = TAG_BUDGET;
        for (ItemStack stack : items) {
            if (stack == null || stack.stackSize <= 0) continue;
            if (entries.size() == LIMIT) {
                omitted++;
                continue;
            }
            String id = Item.itemRegistry.getNameForObject(stack.getItem());
            if (id == null || id.length() > MAX_ID) {
                omitted++;
                continue;
            }
            String tag = boundedTag(stack.getTagCompound(), tagBudget);
            tagBudget -= tag.length();
            entries.add(
                new Entry(
                    false,
                    id,
                    stack.getItemDamage(),
                    BigInteger.valueOf(stack.stackSize),
                    tag,
                    stack.hasTagCompound() && tag.isEmpty()));
        }
        Map<GTUtility.FluidId, FluidAmount> fluidTotals = new LinkedHashMap<>();
        for (FluidAmount fluid : fluids) {
            GTUtility.FluidId key = GTUtility.FluidId.create(fluid.fluid);
            FluidAmount previous = fluidTotals.get(key);
            fluidTotals
                .put(key, previous == null ? fluid : new FluidAmount(fluid.fluid, previous.amount.add(fluid.amount)));
        }
        for (FluidAmount fluid : fluidTotals.values()) {
            if (fluid.amount.signum() <= 0) continue;
            FluidStack stack = fluid.fluid;
            if (entries.size() == LIMIT) {
                omitted++;
                continue;
            }
            String id = stack.getFluid()
                .getName();
            if (id.length() > MAX_ID) {
                omitted++;
                continue;
            }
            String tag = boundedTag(stack.tag, tagBudget);
            tagBudget -= tag.length();
            entries.add(new Entry(true, id, 0, fluid.amount, tag, stack.tag != null && tag.isEmpty()));
        }
        return new CriticalMaterialSnapshot(entries, omitted);
    }

    void write(PacketBuffer packet) throws IOException {
        packet.writeVarIntToBuffer(entries.size());
        packet.writeVarIntToBuffer(omitted);
        for (Entry row : entries) {
            packet.writeBoolean(row.fluid);
            packet.writeStringToBuffer(row.id);
            packet.writeInt(row.damage);
            packet.writeStringToBuffer(row.amount.toString());
            packet.writeStringToBuffer(row.tag);
            packet.writeBoolean(row.tagOmitted);
        }
    }

    static CriticalMaterialSnapshot read(PacketBuffer packet) throws IOException {
        return read(packet, true);
    }

    static CriticalMaterialSnapshot read(PacketBuffer packet, boolean wideAmounts) throws IOException {
        int count = packet.readVarIntFromBuffer(), omitted = packet.readVarIntFromBuffer();
        if (count < 0 || count > LIMIT || omitted < 0) throw new IOException("Invalid material preview size");
        List<Entry> rows = new ArrayList<>(count);
        int tagBytes = 0;
        for (int i = 0; i < count; i++) {
            Entry row = new Entry(
                packet.readBoolean(),
                packet.readStringFromBuffer(MAX_ID),
                packet.readInt(),
                readAmount(packet, wideAmounts),
                packet.readStringFromBuffer(MAX_TAG),
                packet.readBoolean());
            tagBytes += row.tag.length();
            if (tagBytes > TAG_BUDGET) throw new IOException("Material preview NBT budget exceeded");
            if (row.amount.signum() < 0) throw new IOException("Invalid material preview quantity");
            rows.add(row);
        }
        return new CriticalMaterialSnapshot(rows, omitted);
    }

    private static BigInteger readAmount(PacketBuffer packet, boolean wideAmounts) throws IOException {
        if (!wideAmounts) return BigInteger.valueOf(packet.readInt());
        try {
            return new BigInteger(packet.readStringFromBuffer(64));
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid material preview quantity", invalid);
        }
    }

    String lines() {
        StringBuilder text = new StringBuilder();
        for (Entry row : entries) {
            if (text.length() > 0) text.append('\n');
            text.append(row.name())
                .append(" × ")
                .append(row.amount)
                .append(row.fluid ? " mB" : "");
        }
        if (omitted > 0) text.append('\n')
            .append(StatCollector.translateToLocalFormatted("gtsr.critical.ui.materials.omitted", omitted));
        return text.toString();
    }

    boolean same(CriticalMaterialSnapshot other) {
        return other != null && omitted == other.omitted && entries.equals(other.entries);
    }

    private static String boundedTag(NBTTagCompound tag, int remaining) {
        if (tag == null) return "";
        String value = tag.toString();
        if (value.length() > MAX_TAG || value.length() > remaining) return "";
        // Bound parser depth as well as text length; unusual tags remain explicitly unidentified.
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '{' || c == '[') {
                if (++depth > 16) return "";
            } else if (c == '}' || c == ']') depth--;
        }
        return value;
    }

    private static final class Entry {

        private final boolean fluid, tagOmitted;
        private final String id, tag;
        private final int damage;
        private final BigInteger amount;
        private boolean resolved, unknown;
        private ItemStack displayItem;
        private FluidStack displayFluid;

        private Entry(boolean fluid, String id, int damage, BigInteger amount, String tag, boolean tagOmitted) {
            this.fluid = fluid;
            this.id = id;
            this.damage = damage;
            this.amount = amount;
            this.tag = tag;
            this.tagOmitted = tagOmitted;
        }

        private void resolve() {
            if (resolved) return;
            resolved = true;
            NBTTagCompound nbt = null;
            unknown = tagOmitted;
            if (!tag.isEmpty()) try {
                nbt = (NBTTagCompound) JsonToNBT.func_150315_a(tag);
            } catch (Exception invalid) {
                unknown = true;
            }
            if (fluid) {
                net.minecraftforge.fluids.Fluid value = FluidRegistry.getFluid(id);
                displayFluid = value == null ? null : new FluidStack(value, 1);
                if (displayFluid != null) displayFluid.tag = nbt;
            } else {
                Item value = (Item) Item.itemRegistry.getObject(id);
                displayItem = value == null ? null : new ItemStack(value, 1, damage);
                if (displayItem != null) displayItem.setTagCompound(nbt);
            }
        }

        String name() {
            resolve();
            // Names remain dynamic for language/font reloads; registry and bounded NBT resolution is once per sample.
            String name = fluid ? displayFluid == null ? id : displayFluid.getLocalizedName()
                : displayItem == null ? id : displayItem.getDisplayName();
            return unknown
                ? name + " [" + StatCollector.translateToLocal("gtsr.critical.ui.materials.nbt_omitted") + "]"
                : name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Entry row && fluid == row.fluid
                && damage == row.damage
                && amount.equals(row.amount)
                && tagOmitted == row.tagOmitted
                && id.equals(row.id)
                && tag.equals(row.tag);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(fluid, id, damage, amount, tag, tagOmitted);
        }
    }
}
