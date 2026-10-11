package com.miaokatze.gtsr.common.debug;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.items.SingularityTuningWand;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;

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

    public static Adapter find(IMetaTileEntity machine) {
        for (Adapter adapter : ADAPTERS) if (adapter.supports(machine)) return adapter;
        return null;
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
        GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal(key));
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
            return machine instanceof MTECriticalMachine;
        }

        public boolean canUse(IMetaTileEntity machine, EntityPlayer player) {
            return ((MTECriticalMachine) machine).getCriticalState()
                .canUse(player);
        }

        public Option[] options(IMetaTileEntity machine) {
            return new Option[] {
                new Option(
                    "requirements",
                    new String[] { "gtsr.wand.debug.normal", "gtsr.wand.debug.no_items", "gtsr.wand.debug.no_fluids",
                        "gtsr.wand.debug.no_materials" }),
                new Option("speed", new String[] { "gtsr.wand.debug.normal", "2x", "10x", "gtsr.wand.debug.instant" }),
                new Option(
                    "energy",
                    new String[] { "gtsr.wand.debug.energy_required", "gtsr.wand.debug.ignore_energy" }) };
        }

        public void apply(IMetaTileEntity machine, EntityPlayer player, int[] values, boolean reverse) {
            var state = ((MTECriticalMachine) machine).getCriticalState();
            boolean applied = reverse ? state.resetConstructionDebug(player)
                : state.applyConstructionDebug(player, values[0], values[1], values[2] == 1);
            feedback(
                player,
                applied ? reverse ? "gtsr.wand.debug.reset" : "gtsr.wand.debug.applied" : "gtsr.wand.debug.denied");
        }
    }
}
