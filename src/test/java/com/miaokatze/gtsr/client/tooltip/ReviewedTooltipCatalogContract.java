// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.client.tooltip;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipBlocks;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart;
import com.miaokatze.miaokatzecore.api.tooltip.component.TooltipInput;

/** Exercises the shipped catalog, the production curve builder, Core block constructors and conservative ownership. */
public final class ReviewedTooltipCatalogContract {

    public static void main(String[] args) throws Exception {
        Set<String> ids = new HashSet<>(), owners = new HashSet<>(), state = new HashSet<>();
        int machines = 0, routes = 0, curves = 0, matrices = 0;
        try (var reader = new InputStreamReader(
            ReviewedTooltipCatalogContract.class.getResourceAsStream("/assets/gtsr/tooltip/zh_CN.json"),
            StandardCharsets.UTF_8)) {
            for (JsonElement raw : new JsonParser().parse(reader)
                .getAsJsonObject()
                .getAsJsonArray("items")) {
                JsonObject item = raw.getAsJsonObject(), owner = item.getAsJsonObject("owner");
                String id = item.get("id")
                    .getAsString();
                check(ids.add(id) && owners.add(owner.toString()), "Duplicate binding: " + id);
                if (owner.has("machine")) {
                    check(
                        GTSRItemList.valueOf(
                            owner.get("machine")
                                .getAsString())
                            .name()
                            .equals(id.substring(8)),
                        id);
                    machines++;
                } else {
                    check(
                        owner.get("item")
                            .getAsString()
                            .startsWith("gtsr:"),
                        id);
                    check(owner.has("meta") || id.startsWith("ammo.") || id.equals("item.SpacetimeAnchorBeacon"), id);
                }
                JsonObject tiers = item.getAsJsonObject("tiers");
                boolean route = tiers.getAsJsonArray("common")
                    .get(0)
                    .toString()
                    .contains("[item:gtsr:HubTerminal@0|");
                for (var tier : tiers.entrySet()) {
                    NativeTooltipPage page = NativeTooltipPage.forItem(null);
                    boolean terminal = false;
                    int slot = 0;
                    for (JsonElement e : tier.getValue()
                        .getAsJsonArray()) {
                        JsonObject b = e.getAsJsonObject();
                        String type = b.get("type")
                            .getAsString();
                        if (b.has("text")) {
                            String s = b.get("text")
                                .getAsString();
                            if (s.contains("[item:gtsr:HubTerminal@0|") && slot == 0) terminal = true;
                            String plain = TooltipInput.of(s)
                                .plain();
                            check(
                                !plain.contains("[item:") && !plain.contains("[fluid:") && !plain.contains("[right:"),
                                id);
                        }
                        if ("curve".equals(type)) {
                            TooltipChart c = GTSRReviewedCurve.chart(b);
                            if (id.equals("machine.TcdsController")) {
                                check(c.value(0, c.probeIndex) == 500 && c.x(c.probeIndex) == 1000, id);
                                for (int j = 0; j < c.points(); j++) {
                                    double x = c.x(j);
                                    double expected = x < 500 ? x : x <= 1000 ? 500 : 500 * Math.pow(x / 1000, .5693);
                                    check(Math.abs(c.value(0, j) - expected) < .0000001, id + " TCDS knee");
                                }
                            } else if (id.equals("machine.SteamSingularityEntangler") && c.legends.size() == 3) {
                                check(
                                    c.x(c.probeIndex) == 200000
                                        && c.readouts.equals(Arrays.asList("0.125", "0.25", "0.5")),
                                    id);
                            }
                            page.block(c);
                            curves++;
                        } else if (Arrays.asList("profiles", "anchor", "binding", "charge", "damage", "ammo")
                            .contains(type)) {
                                state.add(type);
                            } else {
                                check(NativeTooltipBlocks.append(page, b), id + " unknown block " + type);
                                if ("heat".equals(type)) matrices++;
                            }
                        slot++;
                    }
                    check(terminal == route, id + " terminal route missing or moved in " + tier.getKey());
                }
                if (route) routes++;
            }
        }
        check(
            ids.size() == 179 && machines == 81 && routes == 29 && curves == 8 && matrices == 2 && state.size() == 6,
            "Coverage: " + ids.size() + "/" + machines + "/" + routes + "/" + curves + "/" + matrices + "/" + state);
        List<String> vanilla = new ArrayList<>(Arrays.asList("name", "cover", "GTSR info", "fluid amount", "foreign"));
        check(GTSRReviewedPages.removeOwned(vanilla, Arrays.asList("GTSR info")), "owned block");
        check(
            vanilla.equals(Arrays.asList("name", "cover", "fluid amount", "foreign")),
            "unowned prefix/suffix stripped");
        vanilla.add("foreign");
        check(!GTSRReviewedPages.removeOwned(vanilla, Arrays.asList("foreign")), "ambiguous ownership");
        var stack = new net.minecraft.item.ItemStack(new net.minecraft.item.Item());
        check(
            GTSRReviewedState.binding(stack, null) == null && !stack.hasTagCompound(),
            "unbound cache must not create tags");
        var tag = new net.minecraft.nbt.NBTTagCompound();
        var hub = new net.minecraft.nbt.NBTTagCompound();
        hub.setInteger("x", -74);
        hub.setInteger("y", 19);
        hub.setInteger("z", 231);
        hub.setString("type", "fixture-hub");
        tag.setTag("gtsr.hubPos", hub);
        stack.setTagCompound(tag);
        for (boolean serializedOutput : new boolean[] { false, true }) {
            hub.setBoolean("output", serializedOutput);
            var before = tag.copy();
            String label = GTSRReviewedState.binding(stack, null);
            check(
                label.contains("fixture-hub @ -74, 19, 231") && label.contains(
                    net.minecraft.util.StatCollector
                        .translateToLocal(serializedOutput ? "gtsr.binding.debug_input" : "gtsr.binding.debug_output")),
                "reversed NBT direction");
            check(before.equals(tag), "hover mutated a binding");
        }
        System.out.println(
            "PASS 179 reviewed registrations, all tiers, 29 terminal routes (+ native boiler), 8 curves, 2 heatmaps and 6 stack-state block types");
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
