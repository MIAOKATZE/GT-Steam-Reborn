import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.loader.BlockLoader;
import com.miaokatze.gtsr.loader.ItemLoader;
import com.miaokatze.gtsr.loader.MachineLoader;
import com.miaokatze.gtsr.register.CreativeTabManager;
import com.miaokatze.gtsr.register.GTSRItemOrderCatalog;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;

/** GTSR catalogue contract; shared Core owns the actual NEI comparator regression. */
public final class NEIItemOrderingRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("NEIItemOrderingRuntime", args[0]);
    }
}

class NEIItemOrderingRuntime {
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(gregtech.GTMod.proxy, gregtech.common.GTProxy.class, "mCTMBlockCache",
            new gregtech.api.util.GTBlockMap<Boolean>());
        ItemLoader.initItems();
        MachineLoader.initMachines();
        BlockLoader.initBlocks();
        com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks.registerBlocks();
        com.miaokatze.gtsr.common.critical.CriticalRenderAnchors.register();
        CreativeTabManager.initCreativeTab();

        List<ItemStack> raw = GTSRItemOrderCatalog.rawItems();
        List<ItemStack> ordered = GTSRItemOrderCatalog.orderedItems();
        sameOrder(raw, ordered);
        // GT material generation is intentionally outside this off-game registration fixture.
        if (!GTSRItemOrderCatalog.generatedDimensionItems().isEmpty())
            throw new AssertionError("Uninitialized generated forms must not create catalogue placeholders");
        List<ItemStack> creative = new ArrayList<>();
        CreativeTabManager.CREATIVE_TAB.displayAllReleventItems(creative);
        sameOrder(ordered, creative);
        List<ItemStack> registered = CreativeTabManager.getRegisteredItems();
        for (ItemStack stack : registered) {
            if (index(ordered, stack) < 0) throw new AssertionError("Dropped registered entry " + name(stack));
        }
        if (ordered.size() != registered.size() + 10) throw new AssertionError("Missing weapons or duplicates");
        boolean dimension = false;
        int dimensionCount = 0;
        for (ItemStack stack : ordered) {
            if (GTSRItemOrderCatalog.isDimensionItem(stack)) { dimension = true; dimensionCount++; }
            else if (dimension) throw new AssertionError("Non-dimension entry after dimension " + name(stack));
            if (name(stack).equals("gtsr:critical_render_anchor")) throw new AssertionError("Internal anchor visible");
        }
        if (dimensionCount < 100) throw new AssertionError("Dimension classification incomplete: " + dimensionCount);
        for (GTSRItemList frozen : new GTSRItemList[] { GTSRItemList.CriticalProcessing,
            GTSRItemList.CriticalEntangler, GTSRItemList.CriticalDimension, GTSRItemList.CriticalAssembly,
            GTSRItemList.CriticalAccelerator }) {
            if (index(ordered, frozen.get(1)) >= 0) throw new AssertionError("Frozen controller visible " + frozen);
        }
        assertBefore(ordered, GTSRItemList.VeinPyrolyzerChipT1, GTSRItemList.VeinPyrolyzerChipT2,
            GTSRItemList.VeinPyrolyzerChipT3);
        assertBefore(ordered, GTSRItemList.SteamCacheNode, GTSRItemList.ReinforcedSteamCacheNode,
            GTSRItemList.OverpressureSteamCacheNode);
        assertBefore(ordered, GTSRItemList.AmmoniaCatalystNickel, GTSRItemList.AmmoniaCatalystPlatinum,
            GTSRItemList.AmmoniaCatalystUranium, GTSRItemList.AmmoniaCatalystOsmium,
            GTSRItemList.AmmoniaCatalystFeCo, GTSRItemList.AmmoniaCatalystRuthenium,
            GTSRItemList.AmmoniaCatalystQuantum);
        Item shared = GTSRItemList.SteamCacheNode.getItem();
        if (ItemOrderCatalog.view().entry(new ItemStack(shared, 1, 1)) != null)
            throw new AssertionError("Claimed unrelated shared GT machine meta");
        for (int meta = 0; meta < 4; meta++) {
            ItemStack variant = new ItemStack(com.miaokatze.gtsr.common.blocks.BlocksGTSR.runawaySingularity, 1, meta);
            if (index(ordered, variant) < 0) throw new AssertionError("Missing singularity meta " + meta);
        }
        net.minecraft.block.Block[] metaBlocks = {
            com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperitySurface,
            com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris,
            com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing };
        int[] counts = { 6, 4, 3 };
        for (int family = 0; family < metaBlocks.length; family++) {
            int previous = -1;
            for (int meta = 0; meta < counts[family]; meta++) {
                ItemStack variant = new ItemStack(metaBlocks[family], 1, meta);
                int current = index(ordered, variant);
                if (current <= previous || ItemOrderCatalog.view().entry(variant) == null)
                    throw new AssertionError("Missing or unordered dimension metadata " + name(variant) + ":" + meta);
                previous = current;
            }
        }
        for (net.minecraft.block.Block internal : new net.minecraft.block.Block[] {
            com.miaokatze.gtsr.common.critical.CriticalRenderAnchors.BLOCK,
            com.miaokatze.gtsr.common.dimension.prosperity.altar.SpacetimeAltarBlocks.core,
            com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.sealedChest,
            com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry.unsealedChest,
            com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinMechanisms.block }) {
            ItemStack stack = new ItemStack(internal);
            if (index(ordered, stack) >= 0 || ItemOrderCatalog.view().entry(stack) != null)
                throw new AssertionError("Claimed internal world block " + name(stack));
        }
        // Core only reorders supplied entries, and raw/ordered accessors must not expose owned stacks.
        List<ItemStack> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled, new Random(20261022));
        ItemOrderCatalog.sort("gtsr", shuffled);
        sameOrder(ordered, shuffled);
        List<ItemStack> subset = new ArrayList<>(List.of(ordered.get(10), ordered.get(0)));
        ItemOrderCatalog.sort("gtsr", subset);
        if (subset.size() != 2 || index(subset, ordered.get(0)) != 0) throw new AssertionError("sort added entries");
        ordered.get(0).stackSize = 42;
        registered.get(0).stackSize = 42;
        if (GTSRItemOrderCatalog.orderedItems().get(0).stackSize != 1
            || CreativeTabManager.getRegisteredItems().get(0).stackSize != 1)
            throw new AssertionError("Snapshot leaked mutable stacks");
        Item late = new Item().setUnlocalizedName("late_catalog_probe");
        cpw.mods.fml.common.registry.GameRegistry.registerItem(late, "LateCatalogProbe");
        CreativeTabManager.addItemToTab(new ItemStack(late));
        if (index(GTSRItemOrderCatalog.orderedItems(), new ItemStack(late)) < 0)
            throw new AssertionError("Late registration lost to early snapshot");
        System.out.println("RESULT catalogue PASS version=" + version + " registered=" + registered.size()
            + " ordered=" + raw.size() + " dimension=" + dimensionCount + " weapons=10");
    }
    private static void assertBefore(List<ItemStack> items, GTSRItemList... entries) {
        int last = -1;
        for (GTSRItemList entry : entries) {
            int current = index(items, entry.get(1));
            if (current <= last) throw new AssertionError("Wrong family order: " + entry);
            last = current;
        }
    }
    private static String name(ItemStack stack) { return Item.itemRegistry.getNameForObject(stack.getItem()); }
    private static int index(List<ItemStack> items, ItemStack key) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.getItem() == key.getItem() && stack.getItemDamage() == key.getItemDamage()) return i;
        }
        return -1;
    }
    private static void sameOrder(List<ItemStack> expected, List<ItemStack> actual) {
        if (expected.size() != actual.size()) throw new AssertionError("Different catalogue sizes");
        for (int i = 0; i < expected.size(); i++) {
            if (index(actual, expected.get(i)) != i) throw new AssertionError("Different catalogue rank " + i);
        }
    }
}
