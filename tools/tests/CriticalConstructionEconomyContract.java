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
    private static final Item SHELL = new Item(), EQUIPMENT = new Item(), CONTROLLER = new Item(), SINGULARITY = new Item();
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
        check(CriticalConstructionEconomy.VERSION == 9 && current.version() == 4,
            "Only newly frozen jobs use economy version 9; saved version 4 keeps its price basis");
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
        int rounds = ledger.stageRoundCount(ledger.stageItems(), ledger.stageFluids(), ledger.physicalJob().stage(stage.get(0))), cursor = 0;
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
        seedSchedule(blocks);
        CriticalMachineKind[] kinds = { CriticalMachineKind.SOLAR, CriticalMachineKind.TURBINE,
            CriticalMachineKind.BATTERY, CriticalMachineKind.SUN };
        int[] expectedShells = { 67996, 118532, 102627, 59203 }, expectedCarriers = { 9, 8, 4, 4 };
        int[] expectedSurplus = { 100, 140, 93, 141 };
        int[] extraShells = { 1526, 2920, 2149, 1097 };
        int[][] expectedSteps = { { 1024, 128, 64, 32, 32, 1 }, { 2048, 128, 512, 32, 32, 1 },
            { 1024, 256, 256, 32, 32, 1 }, { 512, 128, 256, 32, 32, 1 } };
        int[] expectedSingularities = { 803, 898, 741, 1440 };
        int[][] perStepSingularities = { { 1, 0, 4, 0, 9, 3 }, { 0, 2, 1, 12, 0, 2 },
            { 1, 0, 1, 0, 7, 5 }, { 0, 2, 3, 5, 8, 0 } };
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
            CriticalConstructionEconomy frozen = CriticalConstructionEconomy.freeze(config, paid, layout.cacheKey());
            check(frozen.version() == 9 && count(frozen.demand(), CONTROLLER) == 1, "New full job freezes v9 and one complete controller " + kinds[k]);
            check(count(frozen.demand(), SINGULARITY) == expectedSingularities[k], "New construction adds the complete machine-specific singularity price " + kinds[k]);
            long shellsPaid = 0;
            for (ItemStack item : frozen.demand()) if (item.getItem() != CONTROLLER && item.getItem() != EQUIPMENT && item.getItem() != SINGULARITY) shellsPaid += item.stackSize;
            check(shellsPaid == expectedShells[k] + extraShells[k], "Rounded family parts include every local real shell allocation " + kinds[k]);
            List<ItemStack> displayed = new ArrayList<>(); List<FluidStack> displayedFluids = new ArrayList<>();
            int[] rowPositions = new int[6];
            java.util.Set<Integer> singularitySlots = new java.util.HashSet<>();
            for (CriticalGeometry.Voxel voxel : paid) rowPositions[frozen.stage(voxel) - 4]++;
            for (int group = 1; group <= 2; group++) for (int row = 0; row < 3; row++) {
                CriticalConstructionEconomy.DisplayRow profile = frozen.displayRowProfile(group, row, paid);
                check(profile.steps == Math.min(256, expectedSteps[k][(group - 1) * 3 + row])
                    && profile.steps <= CriticalConstructionEconomy.MAX_WORK_STEPS,
                    "Real repeated work count and one merged sensing/wiring step " + kinds[k] + "/" + group + "/" + row);
                System.out.println("STEPS " + kinds[k] + "/" + group + "/" + row + "=" + profile.steps);
                int itemCount = 0;
                ItemStack[] ordered = profile.perStepItems();
                for (int slot = 0; slot < ordered.length; slot++)
                    if (ordered[slot].getItem() == SINGULARITY) singularitySlots.add(slot);
                for (ItemStack item : ordered) {
                    itemCount += item.stackSize;
                    check(item.stackSize > 0, "Every merged work-step identity retains its full positive quantity");
                    System.out.println("ITEM " + kinds[k] + "/" + group + "/" + row + " "
                        + frozen.identity(item) + "=" + item.stackSize);
                }
                check(itemCount > 0, "Every actual work step has its complete item debit");
                check(count(profile.perStepItems(), SINGULARITY) == perStepSingularities[k][(group - 1) * 3 + row],
                    "Small repeated singularity doses use spare identity slots throughout body and installation work");
                for (FluidStack medium : profile.perStepFluids())
                    System.out.println("MEDIUM " + kinds[k] + "/" + group + "/" + row + " " + medium.getFluid().getName() + "=" + medium.amount);
                boolean advanced = false;
                for (FluidStack medium : profile.perStepFluids()) {
                    for (gregtech.api.enums.Materials plasma : new gregtech.api.enums.Materials[] {
                        gregtech.api.enums.Materials.Silver, gregtech.api.enums.Materials.Bismuth, gregtech.api.enums.Materials.Radon })
                        advanced |= medium.isFluidEqual(plasma.getPlasma(1));
                    advanced |= medium.isFluidEqual(gregtech.api.enums.Materials.Grade7PurifiedWater.getFluid(1))
                        || medium.isFluidEqual(gregtech.api.enums.Materials.Grade8PurifiedWater.getFluid(1));
                }
                check(advanced, "Every UHV commissioning row retains a genuinely advanced process medium");
                ItemStack[] bill = frozen.itemsForDisplayRow(group, row, paid);
                FluidStack[] fluids = frozen.fluidsForDisplayRow(group, row, paid);
                ItemStack[] repeated = profile.perStepItems();
                for (ItemStack item : repeated) item.stackSize *= profile.steps;
                check(sameBill(repeated, bill), "Repeated NEI item slots equal exact full physical row");
                for (FluidStack fluid : profile.perStepFluids())
                    check((long) fluid.amount * profile.steps == fluidCount(fluids, fluid.getFluid()),
                        "Repeated NEI fluid slots equal exact full physical row");
                check(bill.length <= 6 && fluids.length <= 2 && rowPositions[(group - 1) * 3 + row] > 0,
                    "Every visible row is a real bounded payment/work stage " + kinds[k] + "/" + group + "/" + row);
                boolean localRepair = true;
                for (CriticalGeometry.Voxel voxel : paid) if (frozen.stage(voxel) == (group == 1 ? 4 : 7) + row) {
                    for (FluidStack fluid : frozen.fluidCosts(Arrays.asList(voxel), 0, 1))
                        localRepair &= fluid.amount <= (fluidCount(fluids, fluid.getFluid())
                            + rowPositions[(group - 1) * 3 + row] - 1) / rowPositions[(group - 1) * 3 + row];
                }
                check(localRepair, "Single-position repair never recharges the whole macro-row fluid bill " + kinds[k] + "/" + group + "/" + row);
                for (ItemStack item : bill) CriticalConstructionEconomy.merge(displayed, item);
                for (FluidStack fluid : fluids) {
                    boolean merged = false;
                    for (FluidStack prior : displayedFluids) if (prior.isFluidEqual(fluid)) { prior.amount += fluid.amount; merged = true; break; }
                    if (!merged) displayedFluids.add(fluid.copy());
                }
            }
            check(singularitySlots.size() >= 3, "Machine-specific process anchors occupy at least three distinct slots " + kinds[k]);
            check(sameBill(displayed.toArray(new ItemStack[0]), frozen.demand()), "Six visible rows exactly equal full-job item debits " + kinds[k]);
            boolean fluidsEqual = true;
            for (FluidStack fluid : frozen.fluidCosts(paid, 0, paid.size()))
                fluidsEqual &= fluidCount(displayedFluids.toArray(new FluidStack[0]), fluid.getFluid()) == fluid.amount;
            check(fluidsEqual, "Six visible rows exactly equal full-job fluid debits " + kinds[k]);
            if (kinds[k] == CriticalMachineKind.SUN) {
                ItemStack[] mechanical = frozen.itemsForDisplayRow(2, 0, paid);
                ItemStack[] working = frozen.itemsForDisplayRow(2, 1, paid);
                ItemStack[] wiring = frozen.itemsForDisplayRow(2, 2, paid);
                check(exactCount(mechanical, gregtech.api.util.GTOreDictUnificator.get(
                    gregtech.api.enums.OrePrefixes.plate, gregtech.api.enums.Materials.CosmicNeutronium, 1)) == 384,
                    "SUN v6 confinement support pays 384 exact plate units beyond the GT ore lookup cap");
                check(exactCount(working, gregtech.api.enums.ItemList.Field_Generator_UHV.get(1)) == 384
                    && exactCount(working, gregtech.api.enums.ItemList.Emitter_UHV.get(1)) == 192
                    && exactCount(working, gregtech.api.enums.ItemList.Gravistar.get(1)) == 384,
                    "SUN active work row pays field generators, emitters and Gravistars together");
                check(exactCount(wiring, gregtech.api.enums.ItemList.Sensor_UHV.get(1)) == 64
                    && exactCount(wiring, gregtech.api.util.GTOreDictUnificator.get(
                        gregtech.api.enums.OrePrefixes.circuit, gregtech.api.enums.Materials.UHV, 1)) == 128
                    && exactCount(wiring, gregtech.api.util.GTOreDictUnificator.get(
                        gregtech.api.enums.OrePrefixes.wireFine, gregtech.api.enums.Materials.Neutronium, 1)) == 256
                    && count(wiring, CONTROLLER) == 1,
                    "SUN sensing/wiring row pays all sensors, circuits, fine wires and the controller");
            }
            NBTTagCompound schedule = new NBTTagCompound(); frozen.write(schedule);
            CriticalConstructionEconomy restored = CriticalConstructionEconomy.read(schedule);
            boolean rowsEqual = true;
            for (CriticalGeometry.Voxel voxel : paid) rowsEqual &= restored.stage(voxel) == frozen.stage(voxel);
            check(rowsEqual && sameBill(restored.demand(), frozen.demand()), "Frozen price and every position's stage survive NBT " + kinds[k]);
            singularityDistribution(frozen, paid, kinds[k]);
            mergedPayments(frozen, paid);
            frozenEightPayments(frozen, paid);
            CriticalConstructionEconomy legacy = legacySevenPayments(frozen, paid, expectedSteps[k]);
            concentratedEightPayments(legacy, paid, kinds[k]);
            singularityRecovery(frozen, paid, expectedSingularities[k]);
            if (k == 0) {
                check(frozen.stage(voxel(800_000, 4, false)) == 0,
                    "Positions excluded from the frozen price basis remain user-built rather than acquiring a future bill");
                compactPayments(legacyPriceBasis(frozen), paid);
                repeatedPayments(legacyPriceBasis(frozen), paid);
                roundedPayments(frozen, paid);
            }
            schedule.removeTag("workSchedule");
            boolean rejected = false; try { CriticalConstructionEconomy.read(schedule); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Missing v5 schedule cannot waive payment " + kinds[k]);
        }
    }

    private static void singularityDistribution(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid,
        CriticalMachineKind kind) {
        int[] positions = new int[6], owners = new int[6], totals = new int[6], maximum = new int[6];
        for (CriticalGeometry.Voxel voxel : paid) {
            int row = frozen.stage(voxel) - 4, local = Math.toIntExact(count(frozen.bill(voxel), SINGULARITY));
            positions[row]++; totals[row] += local; maximum[row] = Math.max(maximum[row], local);
            if (local > 0) owners[row]++;
        }
        for (int row = 0; row < 6; row++) {
            check(owners[row] == Math.min(positions[row], totals[row])
                && maximum[row] <= (totals[row] + positions[row] - 1) / positions[row],
                "Each row spreads singularities evenly over real local repair bills");
            System.out.println("DISTRIBUTION " + kind + "/" + (row / 3 + 1) + "/" + row % 3
                + " owners=" + owners[row] + " max=" + maximum[row] + " total=" + totals[row]);
        }
    }

    private static void mergedPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid) {
        List<CriticalGeometry.Voxel> first = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid) if (frozen.stage(voxel) == 4) first.add(voxel);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(frozen); ledger.snapshotPhysicalStage(first);
        CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, first);
        ItemStack[] expected = frozen.displayRowProfile(1, 0, paid).perStepItems();
        check(sameBill(round.items(), expected), "Merged real payment equals the displayed per-step quantities beyond 64 items");
        ledger.recordRoundPayment(round.items(), round.fluids()); ledger.advanceElapsed(round.next); ledger = reload(ledger);
        check(!ledger.hasIntegrityError() && ledger.stageRoundCount(ledger.stageItems(), ledger.stageFluids(), 4) == 256
            && ledger.planPhysicalRound(400, first).items().length == 0 && sameBill(ledger.recoveryItems(1), expected),
            "Merged schema8 checkpoint retains every paid unit as escrow across reload without duplicate payment");
        NBTTagCompound oversized = save(ledger); oversized.setInteger("constructionStepCount", 512);
        CriticalConstructionLedger isolated = new CriticalConstructionLedger(); isolated.read(oversized);
        check(isolated.hasIntegrityError(), "Schema8 rejects an out-of-range 512-step checkpoint while schema7 still supports its frozen larger counts");
        ledger.clearSpentItems(); ledger = reload(ledger); conservation(ledger);
        check(ledger.recoveryItems(1).length == 0, "Merged large escrow is refunded at most once");
    }

    /** Expanded legacy work counts and paid checkpoints remain untouched by the new schema's 256-step limit. */
    private static CriticalConstructionEconomy legacySevenPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid, int[] counts) {
        NBTTagCompound n = new NBTTagCompound(); frozen.write(n); n.setInteger("version", 7);
        n.setTag("demand", withoutSingularities(n.getTagList("demand", 10)));
        NBTTagList schedule = n.getTagList("workSchedule", 10), profiles = n.getTagList("stepProfiles", 10);
        for (int i = 0; i < schedule.tagCount(); i++) {
            NBTTagCompound row = schedule.getCompoundTagAt(i);
            row.setTag("extraItems", withoutSingularities(row.getTagList("extraItems", 10)));
        }
        for (int i = 0; i < profiles.tagCount(); i++) {
            NBTTagCompound row = profiles.getCompoundTagAt(i); int index = row.getInteger("stage") - 4;
            int divisor = counts[index] / row.getInteger("steps");
            ItemStack[] items = CriticalResourceNbt.readItems(withoutSingularities(row.getTagList("items", 10)), -1);
            FluidStack[] fluids = CriticalResourceNbt.readFluids(row.getTagList("fluids", 10), -1);
            for (ItemStack item : items) item.stackSize /= divisor;
            for (FluidStack fluid : fluids) fluid.amount /= divisor;
            row.setInteger("steps", counts[index]); row.setTag("items", CriticalResourceNbt.writeItems(items));
            row.setTag("fluids", CriticalResourceNbt.writeFluids(fluids));
        }
        CriticalConstructionEconomy legacy = CriticalConstructionEconomy.read(n);
        NBTTagCompound copy = new NBTTagCompound(); legacy.write(copy);
        check(copy.equals(n) && count(legacy.demand(), SINGULARITY) == 0, "Saved schema7 retains all original prices, work counts and no new singularity cost");
        List<CriticalGeometry.Voxel> first = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid) if (legacy.stage(voxel) == 4) first.add(voxel);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(legacy); ledger.snapshotPhysicalStage(first);
        check(ledger.stageRoundCount(ledger.stageItems(), ledger.stageFluids(), 4) == counts[0], "Legacy stage keeps its original 512/1024/2048-step count");
        CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, first);
        ledger.recordRoundPayment(round.items(), round.fluids()); ledger.advanceElapsed(round.next);
        NBTTagCompound saved = save(ledger); ledger = reload(ledger);
        check(!ledger.hasIntegrityError() && save(ledger).equals(saved) && ledger.planPhysicalRound(400, first).items().length == 0,
            "Paid legacy schema7 high-step checkpoint reloads without migration or duplicate collection");
        return legacy;
    }

    /** Previously frozen distributed prices and paid per-step snapshots are read without repricing. */
    private static void frozenEightPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid) {
        NBTTagCompound n = new NBTTagCompound(); frozen.write(n); n.setInteger("version", 8);
        CriticalConstructionEconomy prior = CriticalConstructionEconomy.read(n);
        NBTTagCompound copy = new NBTTagCompound(); prior.write(copy);
        check(copy.equals(n), "Frozen schema8 complete manifest remains unchanged under schema9 reader");
        List<CriticalGeometry.Voxel> positions = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid) if (prior.stage(voxel) == 4) positions.add(voxel);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(prior); ledger.snapshotPhysicalStage(positions);
        CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, positions);
        ledger.recordRoundPayment(round.items(), round.fluids()); ledger.advanceElapsed(round.next);
        NBTTagCompound saved = save(ledger); ledger = reload(ledger);
        check(!ledger.hasIntegrityError() && save(ledger).equals(saved)
            && ledger.planPhysicalRound(400, positions).items().length == 0
            && save(ledger).getCompoundTag("constructionPhysicalJob").equals(n),
            "Schema8 paid item/fluid checkpoint and manifest remain unchanged with no second debit");
    }

    /** Prior schema8 prototypes concentrated the complete price on one carrier; paid jobs keep that frozen basis. */
    private static void concentratedEightPayments(CriticalConstructionEconomy legacy, List<CriticalGeometry.Voxel> paid,
        CriticalMachineKind kind) {
        int count = switch (kind) {
            case SOLAR -> 1500;
            case TURBINE -> 750;
            case BATTERY -> 500;
            case SUN -> 2000;
            default -> throw new AssertionError(kind);
        };
        int targetStage = kind == CriticalMachineKind.SUN ? 7 : 9;
        CriticalGeometry.Voxel owner = null;
        List<CriticalGeometry.Voxel> positions = new ArrayList<>();
        for (CriticalGeometry.Voxel voxel : paid) if (legacy.stage(voxel) == targetStage) {
            positions.add(voxel);
            if (owner == null || CriticalStructures.isController(voxel)) owner = voxel;
        }
        ItemStack singularity = item(SINGULARITY, count);
        NBTTagCompound n = new NBTTagCompound(); legacy.write(n); n.setInteger("version", 8);
        List<ItemStack> demand = new ArrayList<>(Arrays.asList(legacy.demand()));
        CriticalConstructionEconomy.merge(demand, singularity);
        n.setTag("demand", CriticalResourceNbt.writeItems(demand.toArray(new ItemStack[0])));
        NBTTagList schedule = n.getTagList("workSchedule", 10), profiles = n.getTagList("stepProfiles", 10);
        for (int i = 0; i < schedule.tagCount(); i++) {
            NBTTagCompound row = schedule.getCompoundTagAt(i);
            if (row.getLong("position") != owner.positionKey()) continue;
            List<ItemStack> extras = new ArrayList<>(Arrays.asList(CriticalResourceNbt.readItems(row.getTagList("extraItems", 10), -1)));
            CriticalConstructionEconomy.merge(extras, singularity);
            row.setTag("extraItems", CriticalResourceNbt.writeItems(extras.toArray(new ItemStack[0])));
        }
        for (int i = 0; i < profiles.tagCount(); i++) {
            NBTTagCompound row = profiles.getCompoundTagAt(i);
            int stage = row.getInteger("stage"), oldSteps = row.getInteger("steps");
            int steps = kind == CriticalMachineKind.SUN && stage == 7 ? 16 : Math.min(256, oldSteps);
            List<ItemStack> items = new ArrayList<>(Arrays.asList(CriticalResourceNbt.readItems(row.getTagList("items", 10), -1)));
            FluidStack[] fluids = CriticalResourceNbt.readFluids(row.getTagList("fluids", 10), -1);
            for (ItemStack item : items) item.stackSize *= oldSteps / steps;
            for (FluidStack fluid : fluids) fluid.amount *= oldSteps / steps;
            if (stage == targetStage) CriticalConstructionEconomy.merge(items, item(SINGULARITY, count / steps));
            row.setInteger("steps", steps); row.setTag("items", CriticalResourceNbt.writeItems(items.toArray(new ItemStack[0])));
            row.setTag("fluids", CriticalResourceNbt.writeFluids(fluids));
        }
        CriticalConstructionEconomy prior = CriticalConstructionEconomy.read(n);
        NBTTagCompound copy = new NBTTagCompound(); prior.write(copy);
        check(copy.equals(n) && count(prior.demand(), SINGULARITY) == count,
            "Saved schema8 concentrated prices remain byte-for-byte unchanged by new distributed freezes");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(prior); ledger.snapshotPhysicalStage(positions);
        CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, positions);
        ledger.recordRoundPayment(round.items(), round.fluids()); ledger.advanceElapsed(round.next);
        NBTTagCompound saved = save(ledger); ledger = reload(ledger);
        check(!ledger.hasIntegrityError() && save(ledger).equals(saved) && ledger.planPhysicalRound(400, positions).items().length == 0
            && save(ledger).getCompoundTag("constructionPhysicalJob").equals(n),
            "Paid old-schema8 checkpoints retain concentrated singularities and never collect new distributed prices");
    }

    private static NBTTagList withoutSingularities(NBTTagList tags) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack item : CriticalResourceNbt.readItems(tags, -1)) if (item.getItem() != SINGULARITY) kept.add(item);
        return CriticalResourceNbt.writeItems(kept.toArray(new ItemStack[0]));
    }

    private static void singularityRecovery(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid, int machineCount) {
        List<CriticalGeometry.Voxel> local = new ArrayList<>();
        CriticalGeometry.Voxel panel = null;
        int count = 0, stage = 9;
        for (CriticalGeometry.Voxel voxel : paid)
            if (count(frozen.bill(voxel), SINGULARITY) > 0) stage = Math.min(stage, frozen.stage(voxel));
        for (CriticalGeometry.Voxel voxel : paid) {
            int share = Math.toIntExact(count(frozen.bill(voxel), SINGULARITY));
            if (share == 0) panel = voxel;
            else if (count < 10 && frozen.stage(voxel) == stage) {
                local.add(voxel); count += share;
            }
        }
        CriticalGeometry.Voxel main = local.get(0);
        int damaged = Math.toIntExact(count(frozen.bill(main), SINGULARITY));
        check(count >= 10 && count < machineCount && damaged <= 8 && count(frozen.bill(panel), SINGULARITY) == 0,
            "Local repairs replace a small distributed share rather than the whole machine singularity price");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(frozen);
        ledger.snapshotPhysicalStage(local);
        CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, local);
        int steps = ledger.stageRoundCount(ledger.stageItems(), ledger.stageFluids(), frozen.stage(main));
        check(count(round.items(), SINGULARITY) * steps == count,
            "Repeated local commissioning debits preserve every distributed singularity");
        ledger = build(ledger, local, false);
        check(!ledger.hasIntegrityError() && count(ledger.recoveryItems(1), SINGULARITY) == count,
            "Uninstalled singularity escrow survives NBT and refunds completely");
        for (CriticalGeometry.Voxel voxel : local) ledger.markPhysicalInstalled(voxel);
        ledger.resetStage(); ledger = reload(ledger);
        check(count(ledger.recoveryItems(1), SINGULARITY) == count / 10, "Installed singularities follow deterministic ten-percent salvage");
        ledger.markPhysicalDamaged(main);
        check(count(ledger.recoveryItems(1), SINGULARITY) == (count - damaged) / 10,
            "Destroyed local commissioning allocation cannot be salvaged again");
        ledger = build(ledger, Arrays.asList(main), true);
        check(balance(ledger, SINGULARITY, "paid") == count + damaged && count(ledger.recoveryItems(1), SINGULARITY) == count / 10,
            "Carrier repair pays its local singularities once and restores one salvage allocation");
        ledger.clearSpentItems(); ledger = reload(ledger); conservation(ledger);
        check(count(ledger.recoveryItems(1), SINGULARITY) == 0, "Settled singularity salvage cannot be collected repeatedly after reload");
    }

    /** Derive an explicit legacy fixture; live v7 padding is never silently imported into saved v5/v6 assets. */
    private static CriticalConstructionEconomy legacyPriceBasis(CriticalConstructionEconomy frozen) {
        NBTTagCompound n = new NBTTagCompound(); frozen.write(n);
        ItemStack[] demand = frozen.demand();
        NBTTagList schedule = n.getTagList("workSchedule", 10);
        java.util.Map<Integer, NBTTagList> media = new java.util.HashMap<>();
        for (int i = 0; i < schedule.tagCount(); i++) {
            NBTTagCompound row = schedule.getCompoundTagAt(i);
            int stage = row.getInteger("stage");
            if (!media.containsKey(stage)) media.put(stage, row.getTagList("fluids", 10));
            row.setTag("fluids", media.get(stage).copy());
            for (ItemStack extra : CriticalResourceNbt.readItems(row.getTagList("extraItems", 10), -1))
                for (ItemStack total : demand) if (CriticalConstructionEconomy.same(extra, total)) total.stackSize -= extra.stackSize;
            row.removeTag("extraItems");
        }
        List<ItemStack> positive = new ArrayList<>();
        for (ItemStack item : demand) if (item.stackSize > 0) positive.add(item);
        n.setTag("demand", CriticalResourceNbt.writeItems(positive.toArray(new ItemStack[0])));
        n.setInteger("version", 5); n.removeTag("stepProfiles");
        return CriticalConstructionEconomy.read(n);
    }

    /** Six-position v7 fixture exercises hundreds of actual payments without traversing a full world. */
    private static void roundedPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid) {
        CriticalConstructionEconomy basis = legacyPriceBasis(frozen);
        List<CriticalGeometry.Voxel> selected = new ArrayList<>();
        for (int stage = 4; stage <= 9; stage++) {
            CriticalGeometry.Voxel chosen = null;
            for (CriticalGeometry.Voxel voxel : paid) if (basis.stage(voxel) == stage
                && (chosen == null || CriticalStructures.isController(voxel))) chosen = voxel;
            selected.add(chosen);
        }
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (CriticalGeometry.Voxel voxel : selected) keys.add(voxel.positionKey());
        NBTTagCompound n = new NBTTagCompound(); basis.write(n); n.setInteger("version", 7);
        for (String key : Arrays.asList("workSchedule", "carriers")) {
            NBTTagList keep = new NBTTagList(), all = n.getTagList(key, 10);
            for (int i = 0; i < all.tagCount(); i++) if (keys.contains(all.getCompoundTagAt(i).getLong("position")))
                keep.appendTag(all.getCompoundTagAt(i));
            n.setTag(key, keep);
        }
        NBTTagList profiles = new NBTTagList(); List<ItemStack> demand = new ArrayList<>();
        int[] steps = {128, 128, 64, 32, 32, 1};
        for (int i = 0; i < selected.size(); i++) {
            CriticalGeometry.Voxel voxel = selected.get(i);
            ItemStack[] bill = basis.bill(voxel); List<ItemStack> extras = new ArrayList<>();
            for (ItemStack item : bill) {
                int original = item.stackSize;
                item.stackSize = (original + steps[i] - 1) / steps[i] * steps[i];
                CriticalConstructionEconomy.merge(demand, item);
                if (original < item.stackSize) {
                    ItemStack extra = item.copy(); extra.stackSize -= original; extras.add(extra);
                }
                item.stackSize /= steps[i];
            }
            FluidStack[] fluids = basis.fluidCosts(Arrays.asList(voxel), 0, 1);
            NBTTagCompound row = new NBTTagCompound(); row.setInteger("stage", i + 4); row.setInteger("steps", steps[i]);
            row.setTag("items", CriticalResourceNbt.writeItems(bill)); row.setTag("fluids", CriticalResourceNbt.writeFluids(fluids));
            profiles.appendTag(row);
            for (FluidStack fluid : fluids) fluid.amount *= steps[i];
            NBTTagList schedule = n.getTagList("workSchedule", 10);
            for (int j = 0; j < schedule.tagCount(); j++) {
                NBTTagCompound local = schedule.getCompoundTagAt(j);
                if (local.getLong("position") != voxel.positionKey()) continue;
                local.setTag("extraItems", CriticalResourceNbt.writeItems(extras.toArray(new ItemStack[0])));
                local.setTag("fluids", CriticalResourceNbt.writeFluids(fluids));
            }
        }
        n.setTag("demand", CriticalResourceNbt.writeItems(demand.toArray(new ItemStack[0]))); n.setTag("stepProfiles", profiles);
        CriticalConstructionEconomy compact = CriticalConstructionEconomy.read(n);
        NBTTagCompound saved = new NBTTagCompound(); compact.write(saved);
        check(saved.equals(n), "v7 local extra allocations and all frozen profiles round-trip exactly");
        CriticalConstructionLedger unplaced = new CriticalConstructionLedger(); unplaced.freezeJob(compact);
        List<CriticalGeometry.Voxel> first = Arrays.asList(selected.get(0)); unplaced.snapshotPhysicalStage(first);
        CriticalConstructionLedger.Round reserved = unplaced.planPhysicalRound(400, first);
        unplaced.recordRoundPayment(reserved.items(), reserved.fluids()); unplaced = reload(unplaced);
        check(exactCount(unplaced.recoveryItems(1), compact.bill(selected.get(0))[0]) == 1,
            "A paid but uninstalled rounded shell remains real escrow and returns in full");
        unplaced.clearSpentItems(); conservation(unplaced);
        check(unplaced.recoveryItems(1).length == 0, "Uninstalled rounded-shell escrow settles exactly once");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(compact);
        for (int i = 0; i < selected.size(); i++) {
            List<CriticalGeometry.Voxel> local = Arrays.asList(selected.get(i)); ledger.snapshotPhysicalStage(local);
            int rowSteps = steps[i];
            check(ledger.stageDuration() == rowSteps * 400, "v7 duration counts every real repeated work step");
            for (int step = 0; step < rowSteps; step++) {
                CriticalConstructionLedger.Round round = ledger.planPhysicalRound(400, local);
                check(sameBill(round.items(), compact.displayRowProfile(i < 3 ? 1 : 2, i % 3, selected).perStepItems()),
                    "v7 actual debit equals its displayed fixed work unit");
                ledger.recordRoundPayment(round.items(), round.fluids()); ledger.advanceElapsed(round.next);
                ledger = reload(ledger);
                check(!ledger.hasIntegrityError() && ledger.planPhysicalRound(400, local).items().length == 0,
                    "v7 funded rounds survive reload without duplicate item payment");
                if (round.voxelEnd > 0) ledger.markPhysicalInstalled(selected.get(i));
                ledger.completeRound(); conservation(ledger);
            }
            ledger.resetStage();
        }
        ItemStack[] firstBill = compact.bill(selected.get(0));
        check(firstBill[0].stackSize == 128 && balance(ledger, firstBill[0].getItem(), "escrow") == 0,
            "All 127 extra local shells are actually installed rather than left in escrow");
        long installedBefore = balance(ledger, firstBill[0].getItem(), "installed");
        ledger.markPhysicalDamaged(selected.get(0));
        check(balance(ledger, firstBill[0].getItem(), "installed") == installedBefore - 128,
            "Damage destroys the same complete local rounded shell bill");
        ledger = build(ledger, Arrays.asList(selected.get(0)), true);
        check(balance(ledger, firstBill[0].getItem(), "installed") == installedBefore,
            "Repair collects and installs the exact same local rounded shell bill");
        conservation(ledger);
        ItemStack[] refund = ledger.recoveryItems(1);
        check(exactCount(refund, firstBill[0]) == balance(ledger, firstBill[0].getItem(), "installed") / 10,
            "Extra real shells use the same deterministic installed salvage fraction");
        ledger.clearSpentItems(); conservation(ledger);
        check(ledger.recoveryItems(1).length == 0, "Rounded allocation refunds settle once without duplicate entitlement");
        NBTTagCompound broken = new NBTTagCompound(); compact.write(broken);
        broken.getTagList("workSchedule", 10).getCompoundTagAt(0).removeTag("extraItems");
        boolean rejected = false; try { CriticalConstructionEconomy.read(broken); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "A missing real local shell allocation cannot waive payment or physical-position validation");
    }

    private static void compactPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid) {
        List<CriticalGeometry.Voxel> selected = new ArrayList<>();
        for (int stage = 4; stage <= 9; stage++) {
            CriticalGeometry.Voxel first = null;
            for (CriticalGeometry.Voxel voxel : paid) if (frozen.stage(voxel) == stage) {
                if (first == null || CriticalStructures.isController(voxel)) first = voxel;
            }
            selected.add(first);
        }
        java.util.Set<Long> positions = new java.util.HashSet<>();
        for (CriticalGeometry.Voxel voxel : selected) positions.add(voxel.positionKey());
        NBTTagCompound n = new NBTTagCompound(); frozen.write(n);
        n.setInteger("version", 5); n.removeTag("stepProfiles");
        for (String key : Arrays.asList("workSchedule", "carriers")) {
            NBTTagList keep = new NBTTagList(), rows = n.getTagList(key, 10);
            for (int i = 0; i < rows.tagCount(); i++) if (positions.contains(rows.getCompoundTagAt(i).getLong("position")))
                keep.appendTag(rows.getCompoundTagAt(i));
            n.setTag(key, keep);
        }
        n.setTag("demand", CriticalResourceNbt.writeItems(frozen.costs(selected, 0, selected.size())));
        CriticalConstructionEconomy compact = CriticalConstructionEconomy.read(n);
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(compact);
        check(count(ledger.physicalNextItems(Arrays.asList(selected.get(0))), CONTROLLER) == 0,
            "Version 5 never hides the equipment controller in the first body material payment");
        for (CriticalGeometry.Voxel voxel : selected) ledger = build(ledger, Arrays.asList(voxel), true);
        FluidStack[] consumed = CriticalResourceNbt.readFluids(save(ledger).getTagList("constructionPaidFluids", 10), -1);
        boolean exactFluids = true;
        for (FluidStack fluid : compact.fluidCosts(selected, 0, selected.size()))
            exactFluids &= fluidCount(consumed, fluid.getFluid()) == fluid.amount;
        check(!ledger.hasIntegrityError() && exactFluids,
            "Six new stages consume their exact frozen fluids after every payment retry and NBT reload");
        NBTTagList balances = save(ledger).getTagList("constructionPhysicalBalances", 10);
        List<ItemStack> collected = new ArrayList<>();
        for (int i = 0; i < balances.tagCount(); i++) {
            NBTTagCompound row = balances.getCompoundTagAt(i);
            ItemStack item = CriticalResourceNbt.readItems(row.getTagList("item", 10), 1)[0];
            item.stackSize = (int) row.getLong("paid"); CriticalConstructionEconomy.merge(collected, item);
        }
        check(sameBill(collected.toArray(new ItemStack[0]), compact.demand()),
            "New-stage actual confirmed item payments exactly equal its frozen bill after every reload");
        conservation(ledger);
    }

    private static void repeatedPayments(CriticalConstructionEconomy frozen, List<CriticalGeometry.Voxel> paid) {
        List<CriticalGeometry.Voxel> selected = new ArrayList<>();
        String material = null;
        for (CriticalGeometry.Voxel voxel : paid) if (frozen.stage(voxel) == 4) {
            if (material == null) material = voxel.material;
            if (voxel.material.equals(material) && selected.size() < 12) selected.add(voxel);
        }
        for (int stage = 5; stage <= 9; stage++) {
            CriticalGeometry.Voxel chosen = null;
            for (CriticalGeometry.Voxel voxel : paid) if (frozen.stage(voxel) == stage
                && (chosen == null || CriticalStructures.isController(voxel))) chosen = voxel;
            selected.add(chosen);
        }
        java.util.Set<Long> positions = new java.util.HashSet<>();
        for (CriticalGeometry.Voxel voxel : selected) positions.add(voxel.positionKey());
        NBTTagCompound n = new NBTTagCompound(); frozen.write(n);
        n.setInteger("version", 6);
        for (String key : Arrays.asList("workSchedule", "carriers")) {
            NBTTagList kept = new NBTTagList(), rows = n.getTagList(key, 10);
            for (int i = 0; i < rows.tagCount(); i++) if (positions.contains(rows.getCompoundTagAt(i).getLong("position")))
                kept.appendTag(rows.getCompoundTagAt(i));
            n.setTag(key, kept);
        }
        n.setTag("demand", CriticalResourceNbt.writeItems(frozen.costs(selected, 0, selected.size())));
        NBTTagList profiles = new NBTTagList();
        for (int stage = 4; stage <= 9; stage++) {
            List<CriticalGeometry.Voxel> local = new ArrayList<>();
            for (CriticalGeometry.Voxel voxel : selected) if (frozen.stage(voxel) == stage) local.add(voxel);
            ItemStack[] items = frozen.costs(local, 0, local.size());
            FluidStack[] fluids = frozen.fluidCosts(local, 0, local.size());
            int steps = stage == 9 ? 1 : CriticalConstructionEconomy.exactStepCount(items, fluids);
            for (ItemStack item : items) item.stackSize /= steps;
            for (FluidStack fluid : fluids) fluid.amount /= steps;
            NBTTagCompound row = new NBTTagCompound(); row.setInteger("stage", stage); row.setInteger("steps", steps);
            row.setTag("items", CriticalResourceNbt.writeItems(items)); row.setTag("fluids", CriticalResourceNbt.writeFluids(fluids));
            profiles.appendTag(row);
        }
        n.setTag("stepProfiles", profiles);
        CriticalConstructionEconomy compact = CriticalConstructionEconomy.read(n);
        NBTTagCompound restored = new NBTTagCompound(); compact.write(restored);
        check(restored.equals(n), "All v6 frozen work profiles and local recipes round-trip exactly");
        check(compact.displayRowProfile(1, 0, selected).steps == 12, "Twelve identical local shells form twelve real work steps");
        check(CriticalConstructionEconomy.exactStepCount(new ItemStack[] { item(SHELL, 360) }, new FluidStack[0]) == 180,
            "Large GCD selects the largest divisor below 200 rather than min(GCD,200)");
        check(CriticalConstructionEconomy.exactStepCount(new ItemStack[0], new FluidStack[0]) == 1,
            "An empty work bill has one step rather than 200 artificial steps");
        check(CriticalConstructionEconomy.exactStepCount(new ItemStack[] { item(SHELL, 211) }, new FluidStack[0]) == 1,
            "Prime demand keeps a truthful single step without padded material cost");
        List<CriticalGeometry.Voxel> debugStage = selected.subList(0, 12);
        CriticalConstructionLedger debug = new CriticalConstructionLedger(); debug.freezeJob(compact);
        debug.snapshotPhysicalStage(debugStage);
        CriticalConstructionLedger.Round debugStart = debug.planPhysicalRound(123, debugStage);
        debug.recordRoundPayment(new ItemStack[0], new FluidStack[0]); debug.advanceElapsed(debugStart.next);
        NBTTagCompound debugPaid = save(debug);
        check(debugPaid.getInteger("constructionRoundVoxelEnd") == debugStart.voxelEnd,
            "Debug ignore-material work persists the funded physical boundary independently of escrow");
        debug = reload(debug);
        CriticalConstructionLedger.Round debugFinish = debug.planPhysicalRound(400, debugStage);
        check(!debug.hasIntegrityError() && debugFinish.voxelEnd == debugStart.voxelEnd && debugFinish.advance == 277
            && debugFinish.items().length == 0 && debugFinish.fluids().length == 0,
            "Debug ignore-material work reloads its exact paid boundary and remaining time without charging");
        debugPaid.setInteger("cursor", debugStart.voxelEnd + 1);
        CriticalConstructionLedger invalidBoundary = new CriticalConstructionLedger(); invalidBoundary.read(debugPaid);
        check(invalidBoundary.hasIntegrityError(), "A paid boundary below the saved placement cursor is quarantined");
        CriticalConstructionLedger ledger = new CriticalConstructionLedger(); ledger.freezeJob(compact);
        for (int stage = 4; stage <= 9; stage++) {
            List<CriticalGeometry.Voxel> local = new ArrayList<>();
            for (CriticalGeometry.Voxel voxel : selected) if (compact.stage(voxel) == stage) local.add(voxel);
            ledger.snapshotPhysicalStage(local);
            int steps = ledger.stageRoundCount(ledger.stageItems(), ledger.stageFluids(), stage), cursor = 0;
            check(ledger.stageDuration() == steps * 400, "v6 duration is 400 ticks per real work step");
            for (int step = 0; step < steps; step++) {
                CriticalConstructionLedger.Round begin = ledger.planPhysicalRound(123, local);
                check(begin.end == (step + 1) * 400 && begin.advance == 123, "Every step starts with a full 400-tick budget");
                check(sameBill(begin.items(), compact.displayRowProfile(stage < 7 ? 1 : 2,
                    stage < 7 ? stage - 4 : stage - 7, selected).perStepItems()), "Actual item debit equals the frozen NEI work unit");
                ledger.recordRoundPayment(begin.items(), begin.fluids()); ledger.advanceElapsed(begin.next); ledger = reload(ledger);
                check(!ledger.hasIntegrityError() && ledger.funded() && ledger.elapsed() == step * 400 + 123,
                    "Paid partial work time survives NBT reload beyond the old 400-tick limit");
                CriticalConstructionLedger.Round finish = ledger.planPhysicalRound(400, local);
                check(finish.advance == 277 && finish.items().length == 0 && finish.fluids().length == 0,
                    "Paid work retries consume only remaining time and never debit twice");
                ledger.advanceElapsed(finish.next);
                for (; cursor < finish.voxelEnd; cursor++) {
                    ledger.markPhysicalInstalled(local.get(cursor)); ledger = reload(ledger);
                    check(!ledger.hasIntegrityError(), "Every placement including final-step partial placement reloads safely");
                    ledger.markPhysicalInstalled(local.get(cursor));
                }
                conservation(ledger); ledger.completeRound();
            }
            check(cursor == local.size(), "Last real work step installs every selected local position");
            ledger.resetStage();
        }
        FluidStack[] before = CriticalResourceNbt.readFluids(save(ledger).getTagList("constructionPaidFluids", 10), -1);
        for (FluidStack fluid : compact.fluidCosts(selected, 0, selected.size()))
            check(fluidCount(before, fluid.getFluid()) == fluid.amount, "Confirmed v6 fluid debits equal frozen local total");
        CriticalGeometry.Voxel damaged = selected.get(0); ledger.markPhysicalDamaged(damaged);
        ledger = build(ledger, Arrays.asList(damaged), true);
        FluidStack[] after = CriticalResourceNbt.readFluids(save(ledger).getTagList("constructionPaidFluids", 10), -1);
        for (FluidStack fluid : compact.fluidCosts(Arrays.asList(damaged), 0, 1))
            check(fluidCount(after, fluid.getFluid()) - fluidCount(before, fluid.getFluid()) == fluid.amount,
                "v6 single-position repair charges only its frozen local fluid bill");
        conservation(ledger);
        NBTTagCompound broken = save(ledger); broken.getCompoundTag("constructionPhysicalJob").removeTag("stepProfiles");
        CriticalConstructionLedger invalid = new CriticalConstructionLedger(); invalid.read(broken);
        check(invalid.hasIntegrityError(), "Missing v6 profiles quarantine instead of reconstructing a new bill");
        NBTTagCompound legacy = save(ledger); legacy.setInteger("constructionEconomyVersion", 5);
        legacy.getCompoundTag("constructionPhysicalJob").setInteger("version", 5);
        legacy.getCompoundTag("constructionPhysicalJob").removeTag("stepProfiles"); legacy.removeTag("constructionStepCount");
        CriticalConstructionLedger old = new CriticalConstructionLedger(); old.read(legacy);
        old.snapshotPhysicalStage(selected.subList(0, 12));
        check(old.economyVersion() == 5 && !old.hasIntegrityError() && old.stageDuration() == 400
            && old.planPhysicalRound(400, selected.subList(0, 12)).end < 400,
            "Saved v5 job retains its original 400-tick whole-stage scheduler");
    }

    private static boolean sameBill(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) return false;
        for (ItemStack item : left) {
            boolean match = false;
            for (ItemStack other : right) if (CriticalConstructionEconomy.same(item, other) && item.stackSize == other.stackSize) match = true;
            if (!match) return false;
        }
        return true;
    }

    private static long exactCount(ItemStack[] rows, ItemStack wanted) {
        long count = 0;
        for (ItemStack item : rows) if (CriticalConstructionEconomy.same(item, wanted)) count += item.stackSize;
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void seedSchedule(java.util.Map<String, Block> blocks) throws Exception {
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        Class.forName("net.minecraft.init.Items");
        java.lang.reflect.Field feather = net.minecraft.init.Items.class.getDeclaredField("feather");
        unsafe.putObject(unsafe.staticFieldBase(feather), unsafe.staticFieldOffset(feather), EQUIPMENT);
        int registry = 1000, component = 1;
        java.lang.reflect.Method addBlock = Block.blockRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        java.lang.reflect.Method addItem = Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        addBlock.setAccessible(true); addItem.setAccessible(true);
        for (java.util.Map.Entry<String, Block> entry : blocks.entrySet()) {
            addBlock.invoke(Block.blockRegistry, registry, "gtsr:" + entry.getKey(), entry.getValue());
            addItem.invoke(Item.itemRegistry, registry++, "gtsr:" + entry.getKey(), new net.minecraft.item.ItemBlock(entry.getValue()));
        }
        for (String name : Arrays.asList("Electric_Motor_UHV", "Electric_Pump_UHV", "Electric_Piston_UHV",
            "Field_Generator_UHV", "Emitter_UHV", "Sensor_UHV", "Gravistar"))
            gregtech.api.enums.ItemList.valueOf(name).set(new ItemStack(EQUIPMENT, 1, component++));
        for (String name : Arrays.asList("CriticalSolar", "CriticalTurbine", "CriticalBattery", "CriticalSun"))
            com.miaokatze.gtsr.common.api.enums.GTSRItemList.valueOf(name).set(new ItemStack(CONTROLLER, 1, component++));
        com.miaokatze.gtsr.common.api.enums.GTSRItemList.CriticalSteamEntangledSingularity.set(new ItemStack(SINGULARITY, 1, 0));
        for (gregtech.api.enums.OrePrefixes prefix : new gregtech.api.enums.OrePrefixes[] {
            gregtech.api.enums.OrePrefixes.circuit, gregtech.api.enums.OrePrefixes.pipeMedium, gregtech.api.enums.OrePrefixes.gearGt,
            gregtech.api.enums.OrePrefixes.stickLong, gregtech.api.enums.OrePrefixes.rotor, gregtech.api.enums.OrePrefixes.plate,
            gregtech.api.enums.OrePrefixes.wireFine })
            for (gregtech.api.enums.Materials material : new gregtech.api.enums.Materials[] {
                gregtech.api.enums.Materials.UHV, gregtech.api.enums.Materials.Neutronium, gregtech.api.enums.Materials.NaquadahAlloy,
                gregtech.api.enums.Materials.Europium, gregtech.api.enums.Materials.Platinum, gregtech.api.enums.Materials.Iridium,
                gregtech.api.enums.Materials.Osmium, gregtech.api.enums.Materials.CosmicNeutronium })
                gregtech.api.util.GTOreDictUnificator.getName2StackMap().put(prefix.get(material).toString(), new ItemStack(EQUIPMENT, 1, component++));
        java.lang.reflect.Field ic2 = gregtech.api.util.GTModHandler.class.getDeclaredField("sIC2ItemMap"); ic2.setAccessible(true);
        ((java.util.Map<String, ItemStack>) ic2.get(null)).put("carbonPlate", new ItemStack(EQUIPMENT, 1, component++));
        for (gregtech.api.enums.Materials material : new gregtech.api.enums.Materials[] {
            gregtech.api.enums.Materials.Silver, gregtech.api.enums.Materials.Bismuth, gregtech.api.enums.Materials.Radon }) {
            Fluid fluid = new Fluid("schedule_plasma_" + material.mName); FluidRegistry.registerFluid(fluid); material.mPlasma = fluid;
        }
        Fluid water = new Fluid("schedule_water7"); FluidRegistry.registerFluid(water);
        gregtech.api.enums.Materials.Grade7PurifiedWater.mFluid = water;
        Fluid water8 = new Fluid("schedule_water8"); FluidRegistry.registerFluid(water8);
        gregtech.api.enums.Materials.Grade8PurifiedWater.mFluid = water8;
        for (gregtech.api.enums.Materials material : new gregtech.api.enums.Materials[] {
            gregtech.api.enums.Materials.SodiumPotassium, gregtech.api.enums.Materials.Lubricant,
            gregtech.api.enums.Materials.SulfuricAcid }) {
            Fluid fluid = new Fluid("schedule_fluid_" + material.mName); FluidRegistry.registerFluid(fluid); material.mFluid = fluid;
        }
        for (gregtech.api.enums.Materials material : new gregtech.api.enums.Materials[] {
            gregtech.api.enums.Materials.Helium, gregtech.api.enums.Materials.Argon, gregtech.api.enums.Materials.Nitrogen }) {
            Fluid fluid = new Fluid("schedule_gas_" + material.mName); FluidRegistry.registerFluid(fluid); material.mGas = fluid;
        }
        for (gregtech.api.enums.Materials material : new gregtech.api.enums.Materials[] {
            gregtech.api.enums.Materials.Platinum, gregtech.api.enums.Materials.SolderingAlloy, gregtech.api.enums.Materials.Naquadria }) {
            Fluid fluid = new Fluid("schedule_molten_" + material.mName); FluidRegistry.registerFluid(fluid); material.mStandardMoltenFluid = fluid;
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
        register.invoke(Item.itemRegistry, 31013, "gtsr:economy_singularity", SINGULARITY);
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
