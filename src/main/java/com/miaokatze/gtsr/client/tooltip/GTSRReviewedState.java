// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.tooltip;

import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.debug.MachineDebugRegistry;
import com.miaokatze.gtsr.common.items.SpacetimeAnchorBeacon;
import com.miaokatze.gtsr.common.machine.MTESingularityCompartmentBase;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.weapons.PortableWeapons;
import com.miaokatze.gtsr.common.weapons.WeaponController;
import com.miaokatze.gtsr.common.weapons.WeaponEnchantments;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import ic2.api.item.ElectricItem;

/** Values come from the hovered stack, never from the offline preview's sample coordinates or default profile. */
final class GTSRReviewedState {

    private GTSRReviewedState() {}

    static void append(NativeTooltipPage page, JsonObject block, ItemStack stack, IMetaTileEntity meta) {
        switch (block.get("type")
            .getAsString()) {
            case "binding" -> {
                String line = binding(stack, meta);
                if (line != null) page.line(line);
            }
            case "ammo" -> page
                .line(tr("ammo", WeaponController.remaining(stack), PortableWeapons.ammoKind(stack).capacity));
            case "damage" -> {
                var kind = PortableWeapons.kind(stack);
                page.line(
                    String.format(
                        Locale.ROOT,
                        block.get("text")
                            .getAsString(),
                        reading(WeaponEnchantments.damage(kind, stack)),
                        reading(WeaponEnchantments.penetration(kind, stack))));
            }
            case "charge" -> page.line(
                tr("charge", String.format(Locale.ROOT, "%,d", (long) ElectricItem.manager.getCharge(stack.copy()))));
            case "anchor" -> {
                if (SpacetimeAnchorBeacon.isBound(stack)) {
                    NBTTagCompound a = stack.getTagCompound()
                        .getCompoundTag("gtsrAnchor");
                    page.line(
                        tr(
                            "anchor",
                            a.getInteger("dimension"),
                            reading(a.getDouble("x")),
                            reading(a.getDouble("y")),
                            reading(a.getDouble("z")),
                            reading(a.getFloat("yaw")),
                            reading(a.getFloat("pitch"))));
                }
            }
            case "profiles" -> {
                for (String family : MachineDebugRegistry.families()) {
                    var adapter = MachineDebugRegistry.findFamily(family);
                    var options = adapter.options(null);
                    int[] values = MachineDebugRegistry.readProfile(stack, adapter, null);
                    StringBuilder line = new StringBuilder("§7")
                        .append(StatCollector.translateToLocal("gtsr.wand.debug.family." + family))
                        .append(": §e");
                    for (int i = 0; i < values.length; i++) {
                        if (i != 0) line.append(" / ");
                        line.append(StatCollector.translateToLocal(options[i].values()[values[i]]));
                    }
                    page.line(line.toString());
                }
            }
            default -> throw new IllegalArgumentException("Unknown dynamic block " + block);
        }
    }

    static String binding(ItemStack stack, IMetaTileEntity meta) {
        boolean worker = meta instanceof MTERemoteWorkerNode;
        if (!stack.hasTagCompound() || !stack.getTagCompound()
            .hasKey("gtsr.hubPos", 10))
            return worker ? "§e" + StatCollector.translateToLocal("gtsr.tooltip.shared.node_unbound") : null;
        NBTTagCompound hub = stack.getTagCompound()
            .getCompoundTag("gtsr.hubPos");
        String position = "§a" + StatCollector.translateToLocal("gtsr.binding.bound_to")
            + " §f"
            + hub.getString("type")
            + " @ "
            + hub.getInteger("x")
            + ", "
            + hub.getInteger("y")
            + ", "
            + hub.getInteger("z");
        if (worker) return position;
        boolean receive = meta instanceof MTESingularityCompartmentBase bay ? bay.getLockedOutputMode()
            : hub.hasKey("output") && !hub.getBoolean("output");
        return position + " §b["
            + StatCollector.translateToLocal(receive ? "gtsr.binding.debug_output" : "gtsr.binding.debug_input")
            + "]";
    }

    private static String reading(double number) {
        return java.math.BigDecimal.valueOf(number)
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gtsr.tooltip.reviewed." + key, args);
    }
}
