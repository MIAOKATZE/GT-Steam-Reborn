package com.miaokatze.gtsr.client.tooltip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ISecondaryDescribable;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.ItemMachines;

/**
 * A presentation layer over the real, localized GT descriptions. Only a complete matching description block
 * belonging to an exact registered GTSR item/meta pair is replaced. Additional NBT, installed covers, GT energy
 * information, advanced item IDs and other mods' lines are outside that block and remain intact.
 */
public final class GTSRMachineTooltips {

    static final String PREFIX = "gtsr.tooltip.machine.";

    private GTSRMachineTooltips() {}

    public static boolean rewrite(ItemStack stack, List<String> lines, boolean details) {
        if (stack == null || !(stack.getItem() instanceof ItemMachines)) return false;
        GTSRItemList entry = ownedEntry(stack);
        if (entry == null) return false;
        int meta = stack.getItemDamage();
        if (meta <= 0 || meta >= GregTechAPI.METATILEENTITIES.length) return false;
        IMetaTileEntity machine = GregTechAPI.METATILEENTITIES[meta];
        if (machine == null || !machine.getClass()
            .getName()
            .startsWith("com.miaokatze.gtsr.")) return false;

        ISecondaryDescribable secondary = machine instanceof ISecondaryDescribable s ? s : null;
        String[] primary = secondary == null ? machine.getDescription() : secondary.getPrimaryDescription();
        String[] structure = secondary == null ? new String[0] : secondary.getSecondaryDescription();
        String[] current = secondary != null && secondary.isDisplaySecondaryDescription() ? structure : primary;
        List<String> displayed = displayedDescription(machine, meta, current, secondary);
        int start = indexOfBlock(lines, displayed, 1);
        if (start < 0) return false; // Unknown translation/event transformations are preserved, never guessed away.

        List<String> replacement;
        if (details) {
            replacement = new ArrayList<>();
            replacement.add(EnumChatFormatting.AQUA + tr("details"));
            appendDetails(replacement, primary);
            if (structure.length > 0) {
                replacement.add("");
                appendDetails(replacement, structure);
            }
            replacement.add(EnumChatFormatting.DARK_GRAY + tr("release_shift"));
        } else {
            List<String> additional = new ArrayList<>();
            machine.addAdditionalTooltipInformation(stack, additional);
            List<String> primaryLines = new ArrayList<>();
            List<String> structureLines = new ArrayList<>();
            appendSource(primaryLines, primary);
            appendSource(structureLines, structure);
            replacement = GTSRMachineTooltipProfiles.summary(entry, primaryLines, structureLines, additional);
            replacement.add(EnumChatFormatting.YELLOW + tr("hold_shift"));
        }
        lines.subList(start, start + displayed.size())
            .clear();
        lines.addAll(start, replacement);
        if (!details) compactNodeDocumentation(entry, machine, stack, lines, start + replacement.size());
        return true;
    }

    /** Item identity is required as GT shares a single ItemMachines item across all mods. */
    private static GTSRItemList ownedEntry(ItemStack stack) {
        for (GTSRItemList entry : GTSRItemList.values()) {
            if (entry.hasBeenSet() && entry.getItem() == stack.getItem() && entry.getMeta() == stack.getItemDamage()) {
                return entry;
            }
        }
        return null;
    }

    /** Mirrors ItemMachines' localization selection; no GTSR machine currently uses %%% parameter substitution. */
    private static List<String> displayedDescription(IMetaTileEntity machine, int meta, String[] current,
        ISecondaryDescribable secondary) {
        if (machine.getClass()
            .getAnnotation(IMetaTileEntity.SkipGenerateDescription.class) != null) {
            return current == null ? Collections.emptyList() : Arrays.asList(current);
        }
        String suffix = secondary != null && secondary.isDisplaySecondaryDescription() ? "_secondary" : "";
        String key = "gt.blockmachines." + machine.getMetaName() + ".tooltip" + suffix;
        if (StatCollector.canTranslate(key + "." + meta)) key += "." + meta;
        if (StatCollector.canTranslate(key)) {
            String translated = StatCollector.translateToLocal(key);
            // Leave a future parameterized GT description untouched until its contract is implemented explicitly.
            if (translated.contains("%s")) return Collections.emptyList();
            return Arrays.asList(GTSplit.split(translated));
        }
        List<String> fallback = new ArrayList<>();
        appendSource(fallback, current);
        return fallback;
    }

    static int indexOfBlock(List<String> lines, List<String> block, int from) {
        if (block.isEmpty()) return -1;
        for (int i = from; i + block.size() <= lines.size(); i++) {
            boolean matches = true;
            for (int j = 0; j < block.size(); j++) {
                if (!plain(lines.get(i + j)).equals(plain(block.get(j)))) {
                    matches = false;
                    break;
                }
            }
            if (matches) return i;
        }
        return -1;
    }

    private static void appendSource(List<String> target, String[] source) {
        if (source == null) return;
        for (String line : source) if (GTUtility.isStringValid(line)) target.add(line);
    }

    private static void appendDetails(List<String> target, String[] source) {
        if (source == null) return;
        for (String line : source) {
            if (line == null || isOldShiftHint(line) || isBrand(line)) continue;
            target.add(line);
        }
    }

    static boolean isBrand(String line) {
        String plain = plain(line);
        return plain.equals(plain(GTSRUtils.getAddedByLine()))
            || plain.equals(StatCollector.translateToLocal("GT5U.MBTT.Mod") + ": GTSR");
    }

    static boolean isOldShiftHint(String line) {
        return plain(line).equals(
            StatCollector.translateToLocal("GT5U.MBTT.Hold") + " [LSHIFT] "
                + StatCollector.translateToLocal("GT5U.MBTT.Display"));
    }

    /**
     * Node tables live in addAdditionalTooltipInformation, outside the GT description. Match that entire
     * separately generated block before shortening only explicitly named documentation rows. Any other row,
     * notably bound coordinates, direction, contents and configured NBT, survives byte-for-byte.
     */
    private static void compactNodeDocumentation(GTSRItemList entry, IMetaTileEntity machine, ItemStack stack,
        List<String> lines, int from) {
        List<String> documentation = GTSRMachineTooltipProfiles.nodeDocumentationKeys(entry);
        if (documentation.isEmpty()) return;
        List<String> additional = new ArrayList<>();
        machine.addAdditionalTooltipInformation(stack, additional);
        int start = indexOfBlock(lines, additional, from);
        if (start < 0) return;
        List<String> compact = new ArrayList<>();
        for (String line : additional) {
            boolean known = GTSRMachineTooltipProfiles.isNodeDocumentationLine(entry, line, documentation);
            if (!known) compact.add(line);
        }
        lines.subList(start, start + additional.size())
            .clear();
        lines.addAll(start, compact);
    }

    /** Select a known authored field, including values composed by the machine's actual tooltip method. */
    static boolean matchesKey(String line, String key) {
        boolean embedded = key.startsWith("~");
        if (embedded) key = key.substring(1);
        if (!StatCollector.canTranslate(key)) return false;
        String text = plain(StatCollector.translateToLocal(key)).replace("%%", "%");
        int format = text.indexOf('%');
        if (format >= 0) text = text.substring(0, format);
        text = text.trim();
        return !text.isEmpty() && (embedded ? plain(line).contains(text)
            : plain(line).trim()
                .startsWith(text));
    }

    static String plain(String line) {
        return line == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(line);
    }

    static String tr(String key) {
        return StatCollector.translateToLocal(PREFIX + key);
    }
}
