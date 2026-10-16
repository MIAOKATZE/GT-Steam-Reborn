"""Historical draft generator, retained as a debugging reference only.

Its fixtures predate the domain-owned plans and are not a current acceptance suite.
For replay, use merge_plans.py with the validated fragments in plans/ instead.
"""
from pathlib import Path
import argparse
import json

G = "com.miaokatze.gtsr."
M = G + "common.machine."
I = G + "common.gui.mui2.inventory."
LORE = G + "common.dimension.prosperity.lore."
ALTAR = G + "common.dimension.prosperity.altar."
steps = []


def ref(name): return {"ref": name}
def cls(name): return ref("@" + name)
def field(target, name): return {"target": target, "field": name}
def call(target, name, *args): return {"target": target, "call": name, "args": list(args)}
def add(op, **kwargs): steps.append({"op": op, **kwargs})
def server(op, **kwargs): add(op, side="server", **kwargs)
def invoke(target, method, *args, side="server", save=None):
    entry = {"target": target, "method": method, "args": list(args)}
    if save: entry["save"] = save
    add("invoke", side=side, **entry)
def value(expression, save, side="server"): add("evaluate", side=side, value=expression, save=save)
def assertion(name, actual, expected, side="server", **extra): add("assert", name=name, actual=actual, expected=expected, side=side, **extra)
def wait(ticks=25): add("wait", ticks=ticks)
def close():
    add("key", code=1); wait(2); add("key", code=1); wait(20)
def screen(name, panel, slots=None):
    kwargs = {"name": name, "panel": panel}
    if slots is not None: kwargs["slots"] = slots
    add("expectScreen", **kwargs); wait(15); add("screenshot", name=name)
def snapshot(name, container="container"): server("inventorySnapshot", name=name, target=ref(container))
def conserved(name, before, after): server("inventoryConserved", name=name, before=before, after=after)
def group(name): add("group", name=name)
def lab():
    invoke(call(ref("server"), "getCommandManager|func_71187_D"), "executeCommand|func_71556_a", ref("serverPlayer"), "gtsrtest enter")
    wait(50)
    assertion("fixture.actual-TestLab-server-dimension", field(ref("serverPlayer"), "dimension|field_71093_bK"), 1048577)
    assertion("fixture.actual-TestLab-client-dimension", field(ref("player"), "dimension|field_71093_bK"), 1048577, side="client")
    server("setField", target=field(ref("serverPlayer"), "capabilities|field_71075_bZ"), field="isFlying|field_75100_b", value=True)
    invoke(ref("serverPlayer"), "sendPlayerAbilities|func_71016_p")
def put_player(slot, stack): invoke(field(ref("serverPlayer"), "inventory|field_71071_by"), "setInventorySlotContents|func_70299_a", slot, stack)
def held(stack):
    put_player(0, stack)
    server("setField", target=field(ref("serverPlayer"), "inventory|field_71071_by"), field="currentItem|field_70461_c", value=0)
    wait(25)
def gt(name, kind, x):
    server("placeGt", name=name, **{"class": M + kind}, x=x, y=70, z=16); wait(25)
def right_click(name):
    invoke(ref(name), "onRightclick", ref(name + "Host"), ref("serverPlayer"), {"enum": "net.minecraftforge.common.util.ForgeDirection", "name": "UP"}, {"float": .5}, {"float": .5}, {"float": .5})
def item(name, identifier=None, count=1, item_ref=None):
    data = {"save": name, "count": count, "mark": "v1.21.38-owned-" + name}
    if item_ref: data["item"] = item_ref
    else: data["id"] = identifier
    server("stack", **data)
def reload_machine(name):
    server("snapshotNbt", target=ref(name + "Host"), save=name + "Saved")
    invoke(ref(name + "Host"), "readFromNBT|func_145839_a", ref(name + "Saved"))
    value(call(ref(name + "Host"), "getMetaTileEntity"), name)
    wait(20)
def save_container(name): value(ref("container"), name)
def click(widget): add("click", widget=widget)


def inventories():
    group("aggregator-production-61-slots")
    close(); lab()
    server("snapshotNbt", target=ref("serverPlayer"), method="writeToNBT|func_70014_b", save="originalPlayer")
    server("clearPlayer")
    server("findItem", **{"class": "gtneioreplugin.plugin.item.ItemDimensionDisplay"}, save="dimensionItem")
    item("dimensions", count=8, item_ref=ref("dimensionItem")); item("dirt", "minecraft:dirt", 16)
    put_player(9, ref("dimensions")); put_player(10, ref("dirt"))
    gt("aggregator", "MTECrustMatterAggregator", 16)
    reload_machine("aggregator")
    invoke(ref("aggregator"), "openConfigGui", ref("serverPlayer"))
    screen("aggregator-initial", "aggregator_config", 61)
    save_container("aggregatorContainer"); snapshot("aggregator-before")
    add("windowClick", slot=25, button=0, mode=1); wait()
    snapshot("aggregator-shift"); conserved("aggregator.shift-full-NBT-cursor-conserved", "aggregator-before", "aggregator-shift")
    server("inventoryLimits", name="aggregator.machine-limit1", snapshot="aggregator-shift", **{"from": 0, "to": 24, "maximum": 1})
    add("windowClick", slot=26, button=0, mode=1); wait()
    snapshot("aggregator-invalid"); conserved("aggregator.invalid-item-conserved", "aggregator-shift", "aggregator-invalid")
    assertion("aggregator.invalid-item-stays-player", field(call(call(ref("container"), "getSlot|func_75139_a", 26), "getStack|func_75211_c"), "stackSize|field_77994_a"), 16)
    click("aggregator_ore_mode"); wait(); click("aggregator_sort_name"); wait()
    snapshot("aggregator-actions"); conserved("aggregator.config-actions-preserve-real-items", "aggregator-before", "aggregator-actions")
    click("aggregator_slot_0"); wait(); snapshot("aggregator-cursor")
    close(); snapshot("aggregator-closed", "aggregatorContainer"); conserved("aggregator.close-held-item-conserved", "aggregator-cursor", "aggregator-closed")
    reload_machine("aggregator")
    invoke(ref("aggregator"), "openConfigGui", ref("serverPlayer")); screen("aggregator-reopened", "aggregator_config", 61)
    close()

    group("unsealed-chest-legacy27-to54-real90")
    lab(); server("clearPlayer")
    server("placeBlock", block=field(cls(G + "common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry"), "unsealedChest"), x=32, y=70, z=16, save="chest")
    invoke(ref("chest"), "beginReward", 1)
    item("oldChest0", "minecraft:diamond", 8); item("oldChest26", "minecraft:gold_ingot", 3)
    invoke(ref("chest"), "setInventorySlotContents|func_70299_a", 0, ref("oldChest0"))
    invoke(ref("chest"), "setInventorySlotContents|func_70299_a", 26, ref("oldChest26"))
    server("snapshotNbt", target=ref("chest"), save="legacy27Chest")
    invoke(ref("chest"), "readFromNBT|func_145839_a", ref("legacy27Chest"))
    assertion("chest.old-27-schema-loads54", call(ref("chest"), "getSizeInventory|func_70302_i_"), 54)
    assertion("chest.new-last-slot-empty", call(ref("chest"), "getStackInSlot|func_70301_a", 53), None)
    invoke(cls(I + "UnsealedChestFactory"), "open", ref("serverPlayer"), ref("chest"))
    screen("chest-legacy27-loaded", "unsealed_chest", 90); save_container("chestContainer")
    assertion("chest.viewer-open-once", call(ref("chest"), "getViewerCount"), 1)
    snapshot("chest-before")
    add("windowClick", slot=0, button=0, mode=1); wait()
    add("windowClick", slot=26, button=2, mode=2); wait()
    snapshot("chest-transfers"); conserved("chest.shift-hotbar-full-NBT-conserved", "chest-before", "chest-transfers")
    click("unsealed_slot_26"); wait(); snapshot("chest-cursor")
    close(); snapshot("chest-closed", "chestContainer"); conserved("chest.close-held-stack-conserved", "chest-cursor", "chest-closed")
    assertion("chest.viewer-final-zero", call(ref("chest"), "getViewerCount"), 0)
    invoke(cls(I + "UnsealedChestFactory"), "open", ref("serverPlayer"), ref("chest")); screen("chest-reopened", "unsealed_chest", 90)
    invoke(ref("serverPlayer"), "setPositionAndUpdate|func_70634_a", {"double": 64.5}, {"double": 72}, {"double": 16.5}); wait(35)
    assertion("chest.distance-invalid-closes-viewer", call(ref("chest"), "getViewerCount"), 0)
    close()

    for n, kind in enumerate(("MTESingularityMinerNode", "MTESingularityDrillingNode")):
        name = "miner" if n == 0 else "driller"
        group(name + "-production39")
        lab(); server("clearPlayer"); item(name + "Pipes", "minecraft:cobblestone", 32); put_player(9, ref(name + "Pipes"))
        gt(name, kind, 48 + n * 16); reload_machine(name); right_click(name)
        screen(name + "-initial", "remote_worker_node", 39); save_container(name + "Container"); snapshot(name + "Before")
        add("windowClick", slot=3, button=0, mode=1); wait()
        snapshot(name + "Shift"); conserved(name + ".shift-full-NBT-conserved", name + "Before", name + "Shift")
        click("worker_pipe_0"); wait(); snapshot(name + "Cursor")
        close(); snapshot(name + "Closed", name + "Container"); conserved(name + ".close-held-item-conserved", name + "Cursor", name + "Closed")
        reload_machine(name); right_click(name); screen(name + "-reopened", "remote_worker_node", 39); close()


def terminals():
    terminal = call(field(cls(G + "common.api.enums.GTSRItemList"), "HubTerminal"), "get", 1)
    for n, kind in enumerate(("MTESteamHubArray", "MTEWaterHubArray", "MTESingularityDrillingHub")):
        name = ("steamHub", "waterHub", "drillingHub")[n]
        group(name + "-actual-terminal")
        lab(); held(terminal); gt(name, kind, 96 + n * 16); right_click(name)
        screen(name + "-initial", "hub_terminal")
        add("text", widget="hub_search", text="uiqa-empty"); wait(); click("hub_sort"); wait()
        add("record", name=name + "-widgets-after-filter-sort", value=call(ref("screen"), "getName"), side="client")
        close(); right_click(name); screen(name + "-reopened", "hub_terminal"); close()

    group("cluster-five-live-pages")
    lab(); held(terminal); gt("cluster", "MTESteamMineralLogisticsCluster", 160)
    invoke(cls(G + "common.gui.mui2.cluster.ClusterTerminalFactory"), "open", ref("serverPlayer"), ref("clusterHost"), 0)
    screen("cluster-topology", "cluster_terminal")
    for page in range(1, 5):
        click("cluster_tab_" + str(page)); wait(); add("screenshot", name="cluster-page-" + str(page))
    close()


def tools_and_lore():
    group("wand-air-real-profile-save-load")
    lab(); server("clearPlayer")
    wand = call(field(cls(G + "common.api.enums.GTSRItemList"), "SingularityTuningWand"), "get", 1)
    value(wand, "wand"); held(ref("wand")); add("wandAir"); screen("wand-families", "wand_families")
    click("wand.family.construction"); screen("wand-profile", "wand_profile")
    click("wand.choice.requirements"); wait(30)
    value(call(cls(G + "common.debug.MachineDebugRegistry"), "findFamily", "construction"), "constructionAdapter")
    value(call(cls(G + "common.debug.MachineDebugRegistry"), "readProfile", call(ref("serverPlayer"), "getHeldItem|func_70694_bm"), ref("constructionAdapter"), None), "savedProfile")
    add("record", name="wand-server-profile-after-action", value={"index": 0, "target": ref("savedProfile")}, side="server")
    close()
    server("snapshotNbt", target=call(ref("serverPlayer"), "getHeldItem|func_70694_bm"), method="writeToNBT|func_77955_b", save="wandSavedNBT")
    value(call(cls("net.minecraft.item.ItemStack"), "loadItemStackFromNBT|func_77949_a", ref("wandSavedNBT")), "wandReloaded")
    held(ref("wandReloaded")); add("wandAir"); screen("wand-families-reloaded", "wand_families")
    click("wand.family.construction"); screen("wand-profile-reloaded", "wand_profile"); close()

    group("journal-real-S2C-and-long-local-content")
    lab(); item("journal", item_ref=field(cls(LORE + "LoreRegistry"), "journal")); held(ref("journal"))
    invoke(call(ref("journal"), "getItem|func_77973_b"), "onItemRightClick|func_77659_a", ref("journal"), ref("world"), ref("serverPlayer"))
    screen("journal-authorized-starter", "prosperity_journal"); close()
    value({"new": G + "client.gui.mui2.lore.ProsperityJournalScreen$Model", "args": [True, 2147483647, 2147483647, 2147483647]}, "journalVisualModel", side="client")
    value({"new": G + "client.gui.mui2.lore.ProsperityJournalScreen", "args": [ref("journalVisualModel")]}, "journalVisualScreen", side="client")
    invoke(cls("com.cleanroommc.modularui.factory.ClientGUI"), "open", ref("journalVisualScreen"), side="client")
    screen("journal-local-unlocked-visual-fixture", "prosperity_journal")
    invoke(ref("journalVisualModel"), "selectTab", {"enum": LORE + "JournalTab", "name": "CHAPTERS"}, side="client")
    add("record", name="journal-complete-ordered-paragraph-keys", value=call(ref("journalVisualModel"), "paragraphKeys"), side="client")
    add("scroll", widget="journal.paragraphs", amount=10000); wait(); add("screenshot", name="journal-long-content-bottom"); close()

    group("navigator-original-dimension-gate")
    invoke(cls("net.minecraftforge.common.DimensionManager"), "initDimension", 78)
    value(call(cls("net.minecraftforge.common.DimensionManager"), "getWorld", 78), "prosperityWorld")
    invoke(cls(G + "common.dimension.testlab.TestLabCommand"), "transfer", ref("serverPlayer"), ref("prosperityWorld"), {"double": .5}, {"double": 100}, {"double": .5}, {"float": 0}, {"float": 0}); wait(60)
    value(call(field(cls(LORE + "LoreRegistry"), "RELICS"), "get", "future_city_address_witness"), "compassItem")
    item("compass", item_ref=ref("compassItem")); held(ref("compass"))
    invoke(call(ref("compass"), "getItem|func_77973_b"), "onItemRightClick|func_77659_a", ref("compass"), ref("world"), ref("serverPlayer"))
    screen("navigator-authorized", "prosperity_navigator")
    add("text", widget="navigator.search", text="foundry"); wait(); add("screenshot", name="navigator-filtered"); close()


def low_canvas_and_cleanup():
    group("actual-427x240-canvas-bounds")
    lab()
    add("resize", width=854, height=480, scale=2); wait(30)
    held(call(field(cls(G + "common.api.enums.GTSRItemList"), "HubTerminal"), "get", 1))
    invoke(ref("serverPlayer"), "setPositionAndUpdate|func_70634_a", {"double": 96.5}, {"double": 71}, {"double": 18.5})
    right_click("steamHub"); screen("steamHub-small-scale2", "hub_terminal"); add("bounds", soft=True); close()
    invoke(ref("serverPlayer"), "setPositionAndUpdate|func_70634_a", {"double": 160.5}, {"double": 71}, {"double": 18.5})
    invoke(cls(G + "common.gui.mui2.cluster.ClusterTerminalFactory"), "open", ref("serverPlayer"), ref("clusterHost"), 0)
    screen("cluster-small-scale2", "cluster_terminal"); add("bounds", soft=True); close()
    # A second TCDS is explicitly placed in TestLab, separate from the prior overworld pilot.
    gt("labTcds", "tcds.MTEThermoChemicalDenseSteamGenerator", 192); right_click("labTcds")
    screen("TCDS-small-scale2", "tcds_terminal"); add("bounds", soft=True); close()
    add("resize", width=1280, height=720, scale=2); wait()
    add("reloadResources"); wait(30)
    right_click("labTcds"); screen("TCDS-after-resource-reload", "tcds_terminal"); add("screenshot", name="TCDS-tooltip-after-reload", hover="tcds_formula_help"); close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--groups", default="inventory,terminals,tools,small")
    args = parser.parse_args()
    selected = set(args.groups.split(","))
    if "inventory" in selected: inventories()
    if "terminals" in selected: terminals()
    if "tools" in selected: tools_and_lore()
    if "small" in selected: low_canvas_and_cleanup()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(steps, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Generated", len(steps), "owned production QA steps:", args.output)


if __name__ == "__main__":
    main()
