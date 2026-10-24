package com.miaokatze.gtsr.common.critical;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Real ledger and NBT with an explicit frozen manifest; no world, ME simulation or FML startup. */
public final class CriticalConstructionEconomyContract {
    private static int checks;
    private static final Item SHELL = new Item(), EQUIPMENT = new Item(), CONTROLLER = new Item();
    private static final Fluid SOLDER = new Fluid("gtsr_economy_solder"), COOLANT = new Fluid("gtsr_economy_coolant");

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
        checks++;
    }

    private static ItemStack item(Item item, int count) {
        return new ItemStack(item, count, 0);
    }

    private static CriticalGeometry.Voxel voxel(int x, int stage, boolean controller) throws Exception {
        Constructor<CriticalGeometry.Voxel> constructor = CriticalGeometry.Voxel.class.getDeclaredConstructor(
            int.class, int.class, int.class, Block.class, int.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(controller ? 56 : x, 4, controller ? 35 : 40, null, stage,
            "v2_alloy_frame", "test", "v2_base_t3");
    }

    private static CriticalConstructionEconomy manifest(CriticalGeometry.Voxel carrier, int shells) {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("layout", "frozen-r31");
        n.setTag("controller", CriticalResourceNbt.writeItems(new ItemStack[] { item(CONTROLLER, 1) }));
        n.setTag("demand", CriticalResourceNbt.writeItems(new ItemStack[] {
            item(CONTROLLER, 1), item(SHELL, shells), item(EQUIPMENT, 13) }));
        NBTTagList materials = new NBTTagList();
        NBTTagCompound material = new NBTTagCompound();
        material.setString("alias", "v2_alloy_frame");
        material.setTag("item", CriticalResourceNbt.writeItems(new ItemStack[] { item(SHELL, 1) }));
        materials.appendTag(material);
        n.setTag("materials", materials);
        NBTTagList carriers = new NBTTagList();
        NBTTagCompound equipment = new NBTTagCompound();
        equipment.setLong("position", carrier.positionKey());
        equipment.setTag("bill", CriticalResourceNbt.writeItems(new ItemStack[] { item(EQUIPMENT, 13) }));
        carriers.appendTag(equipment);
        n.setTag("carriers", carriers);
        return CriticalConstructionEconomy.read(n);
    }

    private static NBTTagCompound save(CriticalConstructionLedger ledger) {
        NBTTagCompound n = new NBTTagCompound(); ledger.write(n); ledger.writeSpent(n); return n;
    }

    private static CriticalConstructionEconomy fluidManifest(CriticalGeometry.Voxel carrier) {
        NBTTagCompound n = new NBTTagCompound();
        manifest(carrier, 12).write(n);
        n.setInteger("version", 3);
        n.getTagList("materials", 10).getCompoundTagAt(0).setTag("fluids",
            CriticalResourceNbt.writeFluids(new FluidStack[] { new FluidStack(SOLDER, 36) }));
        n.getTagList("carriers", 10).getCompoundTagAt(0).setTag("fluids",
            CriticalResourceNbt.writeFluids(new FluidStack[] { new FluidStack(SOLDER, 2304), new FluidStack(COOLANT, 4000) }));
        return CriticalConstructionEconomy.read(n);
    }

    private static long fluidCount(FluidStack[] fluids, Fluid fluid) {
        long count = 0; for (FluidStack stack : fluids) if (stack.getFluid() == fluid) count += stack.amount; return count;
    }

    private static CriticalConstructionEconomy currentManifest(CriticalGeometry.Voxel carrier) {
        NBTTagCompound n = new NBTTagCompound();
        fluidManifest(carrier).write(n);
        n.setInteger("version", 4);
        NBTTagList bases = new NBTTagList();
        FluidStack[][] fluids = {
            { new FluidStack(SOLDER, 7), new FluidStack(COOLANT, 11) },
            { new FluidStack(SOLDER, 13) }, { new FluidStack(COOLANT, 17) } };
        for (int stage = 1; stage <= 3; stage++) {
            NBTTagCompound row = new NBTTagCompound();
            row.setInteger("stage", stage);
            row.setTag("fluids", CriticalResourceNbt.writeFluids(fluids[stage - 1]));
            bases.appendTag(row);
        }
        n.setTag("baseFluids", bases);
        return CriticalConstructionEconomy.read(n);
    }

    private static void currentFluidStages(CriticalGeometry.Voxel carrier, CriticalGeometry.Voxel main) throws Exception {
        CriticalConstructionEconomy current = currentManifest(carrier);
        List<CriticalGeometry.Voxel> positions = Arrays.asList(
            voxel(21, 1, false), voxel(22, 2, false), voxel(23, 3, false), voxel(24, 4, false), carrier, main);
        check(CriticalConstructionEconomy.VERSION == 4 && current.version() == 4,
            "Only newly frozen jobs use economy version 4");
        check(fluidCount(current.fluidCosts(positions, 0, 1), SOLDER) == 7
            && fluidCount(current.fluidCosts(positions, 1, 2), SOLDER) == 13
            && fluidCount(current.fluidCosts(positions, 2, 3), COOLANT) == 17
            && fluidCount(current.fluidCosts(positions, 3, 4), SOLDER) == 36,
            "The same shell identity uses its frozen base layer recipe or body recipe at the actual work stage");
        FluidStack[] total = current.fluidCosts(positions, 0, positions.size());
        check(fluidCount(total, SOLDER) == 2396 && fluidCount(total, COOLANT) == 4028,
            "Current full-job reference includes every base, body and carrier recipe exactly once");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(current);
        NBTTagCompound frozen = save(ledger).getCompoundTag("constructionPhysicalJob");
        for (CriticalGeometry.Voxel position : positions) ledger = build(ledger, Arrays.asList(position), true);
        NBTTagCompound saved = save(ledger);
        FluidStack[] paid = CriticalResourceNbt.readFluids(saved.getTagList("constructionPaidFluids", 10), -1);
        check(saved.getInteger("constructionEconomyVersion") == 4 && !ledger.hasIntegrityError()
            && saved.getCompoundTag("constructionPhysicalJob").equals(frozen),
            "Version 4 retains its original base, body and equipment prices across split payments and reloads");
        check(fluidCount(paid, SOLDER) == 2396 && fluidCount(paid, COOLANT) == 4028,
            "Current consumed fluid journal exactly equals the same frozen reference bill");
        ItemStack[] recovery = ledger.recoveryItems(1);
        check(count(recovery, CONTROLLER) == 1 && count(recovery, SHELL) == 0 && count(recovery, EQUIPMENT) == 1,
            "Adding consumed fluids creates no extra recoverable assets or refund rounding");
        NBTTagCompound broken = save(ledger);
        broken.getCompoundTag("constructionPhysicalJob").removeTag("baseFluids");
        CriticalConstructionLedger isolated = new CriticalConstructionLedger(); isolated.read(broken);
        check(isolated.hasIntegrityError(), "Missing version 4 base prices quarantine rather than waive payment");
    }

    private static void fluidStages(CriticalGeometry.Voxel carrier, List<CriticalGeometry.Voxel> first,
        List<CriticalGeometry.Voxel> last) {
        CriticalConstructionEconomy old = manifest(carrier, 12), current = fluidManifest(carrier);
        check(old.version() == 2 && old.stage(carrier) == 4 && old.fluidCosts(last, 0, last.size()).length == 0,
            "Frozen version 2 retains original stage and never acquires new fluid costs");
        check(current.stage(last.get(0)) == 4 && current.stage(carrier) == 5 && current.stage(first.get(1)) == 1,
            "Structural body precedes equipment carrier; original base work order is unchanged");
        FluidStack[] bill = current.fluidCosts(last, 0, last.size());
        check(fluidCount(bill, SOLDER) == 2412 && fluidCount(bill, COOLANT) == 4000,
            "Body panels and equipment contribute their exact distinct frozen fluid recipes");
        bill[0].amount = 1;
        check(fluidCount(current.fluidCosts(last, 0, last.size()), SOLDER) == 2412,
            "NEI and GUI fluid views cannot mutate the frozen job price");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(current);
        ledger = build(ledger, first, true);
        ledger = build(ledger, last.subList(0, 2), true);
        ledger = build(ledger, last.subList(2, 3), true);
        FluidStack[] paid = CriticalResourceNbt.readFluids(save(ledger).getTagList("constructionPaidFluids", 10), -1);
        check(fluidCount(paid, SOLDER) == 2736 && fluidCount(paid, COOLANT) == 4000,
            "Every split-stage fluid is charged once across payment, placement retry and NBT reload");
        check(save(ledger).getInteger("constructionEconomyVersion") == 3 && !ledger.hasIntegrityError(),
            "Version 3 stage snapshots and cumulative consumed fluids survive reload");
        ledger.markPhysicalDamaged(carrier);
        ledger = build(ledger, Arrays.asList(carrier), true);
        paid = CriticalResourceNbt.readFluids(save(ledger).getTagList("constructionPaidFluids", 10), -1);
        check(fluidCount(paid, SOLDER) == 5076 && fluidCount(paid, COOLANT) == 8000,
            "Repair consumes the replacement carrier's fluids exactly once");
        NBTTagCompound broken = save(ledger);
        broken.getCompoundTag("constructionPhysicalJob").getTagList("materials", 10).getCompoundTagAt(0).removeTag("fluids");
        CriticalConstructionLedger isolated = new CriticalConstructionLedger(); isolated.read(broken);
        check(isolated.hasIntegrityError(), "Missing frozen fluid identities quarantine the job instead of waiving its price");
    }

    private static CriticalConstructionLedger reload(CriticalConstructionLedger ledger) {
        CriticalConstructionLedger copy = new CriticalConstructionLedger();
        NBTTagCompound n = save(ledger); copy.read(n); copy.readSpent(n); return copy;
    }

    private static long count(ItemStack[] items, Item item) {
        long count = 0; for (ItemStack stack : items) if (stack.getItem() == item) count += stack.stackSize; return count;
    }

    private static void conservation(CriticalConstructionLedger ledger) {
        NBTTagList rows = save(ledger).getTagList("constructionPhysicalBalances", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            check(row.getLong("paid") == row.getLong("escrow") + row.getLong("installed")
                + row.getLong("destroyed") + row.getLong("refunded"), "Each real identity conserves every collected unit");
        }
    }

    private static long balance(CriticalConstructionLedger ledger, Item item, String field) {
        NBTTagList rows = save(ledger).getTagList("constructionPhysicalBalances", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            if (CriticalResourceNbt.readItems(row.getTagList("item", 10), 1)[0].getItem() == item)
                return row.getLong(field);
        }
        return 0;
    }

    private static CriticalConstructionLedger build(CriticalConstructionLedger ledger, List<CriticalGeometry.Voxel> stage,
        boolean install) {
        ledger.snapshotPhysicalStage(stage);
        int rounds = CriticalConstructionLedger.roundCount(ledger.stageItems()), cursor = 0;
        for (int r = 0; r < rounds; r++) {
            CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, stage);
            ledger.recordRoundPayment(round.items(), round.fluids());
            ledger.advanceElapsed(round.next);
            ledger = reload(ledger);
            CriticalConstructionLedger.Round retry = ledger.planPhysicalRound(400, stage);
            check(retry.items().length == 0 && retry.fluids().length == 0, "Reloaded paid round requests no second item or fluid debit");
            if (install) for (; cursor < round.voxelEnd; cursor++) {
                ledger.markPhysicalInstalled(stage.get(cursor));
                ledger = reload(ledger); // Includes partially placed multi-voxel rounds, not just paid boundaries.
                ledger.markPhysicalInstalled(stage.get(cursor));
            }
            conservation(ledger);
            ledger.completeRound();
        }
        ledger.resetStage(); return ledger;
    }

    @SuppressWarnings("unchecked")
    private static void productionLayouts() throws Exception {
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        java.lang.reflect.Field materialsField = CriticalMaterials.class.getDeclaredField("MATERIALS");
        materialsField.setAccessible(true);
        java.util.Map<String, Block> blocks = (java.util.Map<String, Block>) materialsField.get(null);
        java.lang.reflect.Field materialId = CriticalMaterials.MaterialBlock.class.getDeclaredField("materialId");
        materialId.setAccessible(true);
        for (String id : CriticalMaterialDefinitions.catalog().definitions().keySet()) {
            CriticalMaterials.MaterialBlock block = (CriticalMaterials.MaterialBlock) unsafe.allocateInstance(CriticalMaterials.MaterialBlock.class);
            materialId.set(block, id); blocks.put(id, block);
        }
        CriticalMachineKind[] kinds = { CriticalMachineKind.SOLAR, CriticalMachineKind.TURBINE,
            CriticalMachineKind.BATTERY, CriticalMachineKind.SUN };
        int[] expectedShells = { 67996, 118532, 102627, 59203 }, expectedCarriers = { 9, 8, 4, 4 };
        int[] expectedSurplus = { 100, 140, 93, 141 };
        int[][] expectedStages = { { 6730, 8950, 9198, 43118 }, { 6730, 8950, 9198, 93654 },
            { 6730, 8950, 9198, 77749 }, { 6730, 8950, 9198, 34325 } };
        for (int k = 0; k < kinds.length; k++) {
            CriticalConfiguration config = new CriticalConfiguration(CriticalTier.T3, kinds[k], 8, 0, 0);
            CriticalLayoutDefinition layout = CriticalLayoutDefinition.select(2, 31, false, CriticalTier.T3, kinds[k]);
            List<CriticalGeometry.Voxel> paid = new ArrayList<>();
            int[] stages = new int[4]; int mains = 0;
            java.util.Map<String, Integer> quantities = new java.util.LinkedHashMap<>();
            for (CriticalGeometry.Voxel voxel : CriticalCompiledLayout.get(layout, config).voxels()) {
                // A hatch-capable casing remains paid unless the world actually contains a real GT hatch there.
                if (CriticalLayoutDefinition.isLoom(voxel)) continue;
                paid.add(voxel);
                if (CriticalStructures.isController(voxel)) { mains++; continue; }
                com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog.family(voxel.material);
                stages[voxel.stage - 1]++;
                quantities.merge(CriticalMaterialDefinitions.canonicalId(voxel.material), 1, Integer::sum);
            }
            check(mains == 1 && paid.size() - 1 == expectedShells[k], "Real compiled r31 " + kinds[k]
                + " shell total and separate main actual=" + (paid.size() - mains) + "/" + mains + " stages=" + Arrays.toString(stages));
            check(Arrays.equals(stages, expectedStages[k]), "Real compiled r31 " + kinds[k] + " all four stage identities resolve");
            java.util.Map<String, Long> carriers = CriticalConstructionEconomy.carrierPositions(kinds[k], paid);
            check(carriers.size() == expectedCarriers[k] && new java.util.HashSet<>(carriers.values()).size() == carriers.size(),
                "Real compiled r31 " + kinds[k] + " every macro unit owns one distinct stable equipment carrier");
            int surplus = 0;
            for (int quantity : quantities.values()) surplus += (quantity + 15) / 16 * 16 - quantity;
            check(surplus == expectedSurplus[k], "Full-job per-identity manufacturing rounds once and leaves the original-table surplus");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) for (Class<?> type : new Class<?>[] { CriticalConstructionEconomy.class,
            CriticalConstructionLedger.class, com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog.class }) {
            java.nio.file.Path origin = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            check(origin.toRealPath().equals(java.nio.file.Path.of(args[0]).toRealPath()), "Candidate jar binds " + type.getName());
            System.out.println("SOURCE " + type.getName() + "=" + origin);
        }
        check(com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog.identities().size() == 24
            && com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog.Family.values().length == 8,
            "Exactly 24 independent identities and eight manufacturing templates");
        check(com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog.identities().keySet()
            .equals(CriticalMaterialCatalog.current().currentPhysical()), "Manufacturing covers the real current physical catalog");
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 31010, "gtsr:economy_shell", SHELL);
        register.invoke(Item.itemRegistry, 31011, "gtsr:economy_equipment", EQUIPMENT);
        register.invoke(Item.itemRegistry, 31012, "gtsr:economy_controller", CONTROLLER);
        java.lang.reflect.Method registerBlock = Block.blockRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        registerBlock.setAccessible(true);
        registerBlock.invoke(Block.blockRegistry, 9, "minecraft:water", new Block(Material.water) {});
        registerBlock.invoke(Block.blockRegistry, 11, "minecraft:lava", new Block(Material.lava) {});
        java.lang.reflect.Field unsafeAccess = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeAccess.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeAccess.get(null);
        java.lang.reflect.Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
        loader.setAccessible(true);
        loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        FluidRegistry.registerFluid(SOLDER); FluidRegistry.registerFluid(COOLANT);
        CriticalGeometry.Voxel carrier = voxel(12, 4, false), main = voxel(0, 1, true);
        List<CriticalGeometry.Voxel> first = new ArrayList<>(); first.add(main);
        for (int i = 0; i < 9; i++) first.add(voxel(i, 1, false));
        List<CriticalGeometry.Voxel> last = Arrays.asList(voxel(10, 4, false), voxel(11, 4, false), carrier);
        fluidStages(carrier, first, last);
        currentFluidStages(carrier, main);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger();
        ledger.freezeJob(manifest(carrier, 12));
        NBTTagCompound frozen = save(ledger).getCompoundTag("constructionPhysicalJob");
        ledger = build(ledger, first, true);
        check(ledger.isPhysicalJob() && ledger.layoutKey().equals("frozen-r31"), "Stage transitions retain frozen full-job version");
        check(count(ledger.recoveryItems(1), SHELL) == 0, "Nine installed shells preserve the fractional salvage carry");
        ledger = build(ledger, last, true);
        check(save(ledger).getCompoundTag("constructionPhysicalJob").equals(frozen),
            "Later stages preserve the entire original price basis and carrier coordinates byte-for-byte");
        ItemStack[] refund = ledger.recoveryItems(1);
        check(count(refund, SHELL) == 1 && count(refund, EQUIPMENT) == 1 && count(refund, CONTROLLER) == 1,
            "Across stages and one-unit rounds, 12 shells recover 1; carrier equipment 13 recovers 1; main returns once");
        check(count(ledger.recoveryItems(999), SHELL) == 1, "New recovery is deterministic and does not depend on RNG seed");
        ledger.markPhysicalDamaged(carrier);
        check(count(ledger.recoveryItems(1), EQUIPMENT) == 0, "Destroyed carrier cannot return its old installed equipment");
        // A damaged adjacent panel charges one casing and no macro equipment.
        check(count(ledger.physicalJob().bill(last.get(0)), EQUIPMENT) == 0, "Panel detail never creates equipment charges");
        ledger = build(ledger, Arrays.asList(carrier), true);
        check(count(ledger.recoveryItems(1), EQUIPMENT) == 1, "Repair installs exactly one real replacement carrier bill");
        ledger.markPhysicalDamaged(main);
        check(count(ledger.recoveryItems(1), CONTROLLER) == 0, "A missing old controller is never exchanged for a new one");
        ledger = build(ledger, Arrays.asList(main), false);
        check(count(ledger.recoveryItems(1), CONTROLLER) == 1, "Uninstalled replacement controller escrow is fully returned");
        conservation(ledger);
        ledger.clearSpentItems(); conservation(ledger);
        check(ledger.recoveryItems(1).length == 0, "Preparing persistent pending refund consumes entitlement exactly once");
        ledger.resetJob(); check(!ledger.isPhysicalJob(), "Only settled dismantle releases the job price basis");
        List<CriticalGeometry.Voxel> bulk = new ArrayList<>();
        for (int i = 0; i < 501; i++) bulk.add(voxel(i + 100, 4, false));
        bulk.add(101, main); // Real controller marker is not in the first round.
        CriticalGeometry.Voxel bulkCarrier = bulk.get(bulk.size() - 1);
        ledger = new CriticalConstructionLedger(); ledger.freezeJob(manifest(bulkCarrier, 501));
        NBTTagCompound beforePreview = save(ledger);
        ItemStack[] preview = ledger.physicalNextItems(bulk);
        check(count(preview, SHELL) <= 3 && count(preview, CONTROLLER) == 1 && save(ledger).equals(beforePreview),
            "Before any stage snapshot, preview shows one bounded exact round without changing payment truth");
        ledger.snapshotPhysicalStage(bulk);
        CriticalConstructionLedger.Round firstPayment = ledger.planPhysicalRound(400, bulk);
        check(count(firstPayment.items(), CONTROLLER) == 1,
            "First payment reserves exactly one controller even when its installation marker is in a later round");
        ledger.resetStage(); ledger = build(ledger, bulk, true);
        check(balance(ledger, SHELL, "paid") == 501 && balance(ledger, SHELL, "installed") == 501
            && balance(ledger, SHELL, "escrow") == 0 && balance(ledger, CONTROLLER, "paid") == 1
            && balance(ledger, EQUIPMENT, "paid") == 13,
            "Exact identities match every multi-voxel round after partial placement reload; no batch rounding or second main debit");
        check(count(ledger.recoveryItems(1), SHELL) == 50,
            "501 separately installed units recover exactly 50 across all tiny rounds");
        CriticalConstructionLedger old = new CriticalConstructionLedger();
        old.recordLegacyItem(item(SHELL, 27));
        old = reload(old);
        check(!old.isPhysicalJob() && count(old.recoveryItems(12345), SHELL) == 2,
            "Old jobs remain old economy and retain seeded legacy recovery");
        NBTTagCompound unsupported = save(ledger);
        unsupported.setInteger("constructionEconomyVersion", 99);
        CriticalConstructionLedger isolated = new CriticalConstructionLedger(); isolated.read(unsupported);
        check(isolated.hasIntegrityError() && isolated.hasSpentItems(), "Unknown economy schema remains quarantined instead of downgrading to old prices");
        check(save(isolated).getCompoundTag("constructionEconomyQuarantine").equals(unsupported),
            "Unknown economy retains the complete original NBT as verification evidence");
        NBTTagCompound broken = save(ledger);
        broken.getTagList("constructionPhysicalBalances", 10).getCompoundTagAt(0).setLong("paid", -1);
        isolated = new CriticalConstructionLedger(); isolated.read(broken);
        check(isolated.hasIntegrityError() && save(isolated).getCompoundTag("constructionEconomyQuarantine").equals(broken),
            "Invalid balance is frozen and written losslessly without crashing the chunk load");
        isolated = reload(isolated);
        check(isolated.hasIntegrityError() && save(isolated).getCompoundTag("constructionEconomyQuarantine").equals(broken),
            "Repeated quarantine reload preserves one original copy without recursively growing NBT");
        productionLayouts();
        System.out.println("PASS CriticalConstructionEconomyContract checks=" + checks);
    }
}
