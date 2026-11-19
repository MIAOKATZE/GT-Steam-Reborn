// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.debug;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.orecenter.MTEMineralIntegrationCenter;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTUtility;

/** Controller adapters own their schema, validation and application semantics. */
public final class MachineDebugRegistry {

    public static final class Option {

        private final String key;
        private final String[] values;

        public Option(String key, String[] values) {
            this.key = key;
            this.values = values;
        }

        public String key() {
            return key;
        }

        public String[] values() {
            return values;
        }
    }

    public interface Adapter {

        String family();

        boolean supports(IMetaTileEntity machine);

        Option[] options(IMetaTileEntity machine);

        default boolean canUse(IMetaTileEntity machine, EntityPlayer player) {
            return true;
        }

        void apply(IMetaTileEntity machine, EntityPlayer player, int[] values, boolean reverse);
    }

    private static final List<Adapter> ADAPTERS = new ArrayList<>();

    static {
        register(new SingularityAdapter());
        register(new ConstructionAdapter());
    }

    private MachineDebugRegistry() {}

    public static void register(Adapter adapter) {
        ADAPTERS.add(adapter);
    }

    public static Adapter findActive(ItemStack wand, IMetaTileEntity machine) {
        Adapter adapter = findFamily(activeFamily(wand));
        return adapter != null && adapter.supports(machine) ? adapter : null;
    }

    public static String activeFamily(ItemStack wand) {
        return wand.hasTagCompound() && wand.getTagCompound()
            .getBoolean("gtsrWandMachineMode") ? "construction" : "singularity";
    }

    public static void selectFamily(ItemStack wand, Adapter adapter) {
        if (!wand.hasTagCompound()) wand.setTagCompound(new NBTTagCompound());
        wand.getTagCompound()
            .setBoolean("gtsrWandMachineMode", "construction".equals(adapter.family()));
    }

    public static Adapter findFamily(String family) {
        for (Adapter adapter : ADAPTERS) if (adapter.family()
            .equals(family)) return adapter;
        return null;
    }

    public static String[] families() {
        return ADAPTERS.stream()
            .map(Adapter::family)
            .toArray(String[]::new);
    }

    public static int[] readProfile(ItemStack wand, Adapter adapter, IMetaTileEntity machine) {
        Option[] options = adapter.options(machine);
        int[] values = new int[options.length];
        NBTTagCompound profile = wand.hasTagCompound() ? wand.getTagCompound()
            .getCompoundTag("gtsrDebugProfiles")
            .getCompoundTag(adapter.family()) : new NBTTagCompound();
        for (int i = 0; i < values.length; i++) values[i] = profile.getInteger(options[i].key());
        if (adapter instanceof SingularityAdapter && profile.hasNoTags()) {
            values[0] = SingularityTuningWand.getSelectedEntry(wand);
            values[1] = SingularityTuningWand.getSelectedDeltaIndex(wand);
        }
        if (adapter instanceof ConstructionAdapter && profile.hasKey("requirements") && !profile.hasKey("items")) {
            int legacy = profile.getInteger("requirements");
            values[0] = legacy == 1 || legacy == 3 ? 1 : 0;
            values[1] = legacy == 2 || legacy == 3 ? 1 : 0;
            values[2] = profile.getInteger("energy");
        }
        for (int i = 0; i < values.length; i++)
            if (values[i] < 0 || values[i] >= options[i].values().length) values[i] = 0;
        return values;
    }

    public static boolean writeProfile(ItemStack wand, Adapter adapter, IMetaTileEntity machine, int[] values) {
        Option[] options = adapter.options(machine);
        if (values.length != options.length) return false;
        for (int i = 0; i < values.length; i++)
            if (values[i] < 0 || values[i] >= options[i].values().length) return false;
        if (!wand.hasTagCompound()) wand.setTagCompound(new NBTTagCompound());
        NBTTagCompound profiles = wand.getTagCompound()
            .getCompoundTag("gtsrDebugProfiles");
        NBTTagCompound profile = new NBTTagCompound();
        for (int i = 0; i < values.length; i++) profile.setInteger(options[i].key(), values[i]);
        profiles.setTag(adapter.family(), profile);
        wand.getTagCompound()
            .setTag("gtsrDebugProfiles", profiles);
        if (adapter instanceof SingularityAdapter) {
            SingularityTuningWand.setSelectedEntry(wand, values[0]);
            SingularityTuningWand.setSelectedDeltaIndex(wand, values[1]);
        }
        return true;
    }

    public static void feedback(EntityPlayer player, String key) {
        player.addChatMessage(new ChatComponentTranslation(key));
    }

    private static final class SingularityAdapter implements Adapter {

        public String family() {
            return "singularity";
        }

        public boolean supports(IMetaTileEntity machine) {
            return machine instanceof MTESingularityMachineBase;
        }

        public Option[] options(IMetaTileEntity machine) {
            String[] entries = new String[] { "gtsr.wand.entry.overlimit", "gtsr.wand.entry.instability",
                "gtsr.wand.entry.tear" };
            String[] deltas = new String[SingularityTuningWand.DELTA_STEPS.length];
            for (int i = 0; i < deltas.length; i++) deltas[i] = SingularityTuningWand.deltaLabel(i);
            return new Option[] { new Option("entry", entries), new Option("delta", deltas) };
        }

        public void apply(IMetaTileEntity target, EntityPlayer player, int[] values, boolean reverse) {
            MTESingularityMachineBase machine = (MTESingularityMachineBase) target;
            if (values[0] == 2 && !(machine instanceof MTECriticalSingularityCompressor)) {
                feedback(player, "gtsr.wand.no_tear");
                return;
            }
            double delta = SingularityTuningWand.DELTA_STEPS[values[1]] * (reverse ? -1 : 1);
            double updated;
            switch (values[0]) {
                case 1:
                    updated = machine.mInstability = Math.max(0, machine.mInstability + delta);
                    break;
                case 2:
                    updated = machine.mTear = Math.max(0, machine.mTear + delta);
                    break;
                default:
                    updated = machine.mOverlimit = Math.max(0, machine.mOverlimit + delta);
                    break;
            }
            machine.getBaseMetaTileEntity()
                .issueTileUpdate();
            GTUtility.sendChatToPlayer(
                player,
                String.format(
                    "%s %+.0f → %.2f%%",
                    StatCollector.translateToLocal("gtsr.wand.entry." + SingularityTuningWand.entryNameKey(values[0])),
                    delta,
                    updated));
        }
    }

    private static final class ConstructionAdapter implements Adapter {

        public String family() {
            return "construction";
        }

        public boolean supports(IMetaTileEntity machine) {
            return machine instanceof MTEMineralIntegrationCenter
                || machine instanceof MTECriticalMachine loom && loom.isKindSelector();
        }

        public boolean canUse(IMetaTileEntity machine, EntityPlayer player) {
            return machine instanceof MTEMineralIntegrationCenter center ? center.canEditGraphs(player)
                : ((MTECriticalMachine) machine).getCriticalState()
                    .canUse(player);
        }

        private static Option toggle(String key, String enabled) {
            return new Option(key, new String[] { "gtsr.wand.debug.normal", "gtsr.wand.debug." + enabled });
        }

        public Option[] options(IMetaTileEntity machine) {
            Option items = machine instanceof MTEMineralIntegrationCenter
                ? new Option("items", new String[] { "gtsr.wand.debug.normal" })
                : toggle("items", "no_items");
            return new Option[] { items, toggle("fluids", "no_fluids"), toggle("energy", "ignore_energy"),
                toggle("quick_build", "quick_build_ready") };
        }

        public void apply(IMetaTileEntity machine, EntityPlayer player, int[] values, boolean reverse) {
            boolean items = !reverse && values[0] == 1;
            boolean fluids = !reverse && values[1] == 1;
            boolean energy = !reverse && values[2] == 1;
            boolean quick = !reverse && values[3] == 1;
            if (machine instanceof MTEMineralIntegrationCenter center) {
                String result = center.applyWandDebug(player, fluids, energy, quick);
                feedback(
                    player,
                    reverse && "gtsr.wand.debug.applied".equals(result) ? "gtsr.wand.debug.reset" : result);
                return;
            }
            var state = ((MTECriticalMachine) machine).getCriticalState();
            if (!state.applyConstructionDebug(player, (items ? 1 : 0) | (fluids ? 2 : 0), 0, energy)) {
                feedback(player, "gtsr.wand.debug.denied");
                return;
            }
            if (quick) state.requestDamageHighlight(player);
            feedback(
                player,
                reverse ? "gtsr.wand.debug.reset" : quick ? "gtsr.wand.debug.loom_checked" : "gtsr.wand.debug.applied");
        }
    }
}
