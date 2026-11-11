// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

/** Persistence decoding only: no world, host, payment, recovery or state-transition authority. */
final class CriticalStateCodec {

    private CriticalStateCodec() {}

    static NBTTagCompound copy(NBTTagCompound data) {
        return (NBTTagCompound) data.copy();
    }

    static NBTTagCompound compound(NBTTagCompound root, String key) {
        if (root.hasKey(key) && !root.hasKey(key, 10))
            throw new CriticalResourceNbt.InvalidJournalException("Runtime is not a compound: " + key);
        return copy(root.getCompoundTag(key));
    }

    /** Decode the full resource boundary before installing any arrays into the live owner. */
    static Resources resources(NBTTagCompound root) {
        Resources result = new Resources();
        for (String key : new String[] { "inventory", "escrow", "pendingItems", "repairEscrow",
            "uncertainFundingItems" })
            result.items.put(
                key,
                CriticalResourceNbt
                    .readItems(CriticalResourceNbt.journal(root, key), key.equals("inventory") ? 36 : -1));
        for (String key : new String[] { "tanks", "fluidEscrow", "pendingFluids", "uncertainFundingFluids" })
            result.fluids.put(
                key,
                CriticalResourceNbt.readFluids(CriticalResourceNbt.journal(root, key), key.equals("tanks") ? 16 : -1));
        for (String key : new String[] { "constructionStageCosts", "constructionStageFluidCosts",
            "constructionPaidFluids", "spent" }) CriticalResourceNbt.journal(root, key);
        validateEnergy(compound(root, "criticalEnergyRuntime"), 0);
        compound(root, "criticalStellarRuntime");
        compound(root, "criticalBatchRecipe");
        compound(root, "config");
        return result;
    }

    private static final java.util.Set<String> EXACT = new java.util.HashSet<>(
        java.util.Arrays
            .asList("eu", "exactAmount", "targetLitres", "confirmedLitres", "euPerL", "remainingHeat", "paidLitres"));

    private static void validateExactAmounts(NBTTagCompound data) {
        for (String key : EXACT) if (data.hasKey(key)) {
            if (!data.hasKey(key, 8))
                throw new CriticalResourceNbt.InvalidJournalException("Exact amount is not text: " + key);
            try {
                if (new BigInteger(data.getString(key)).signum() < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException bad) {
                throw new CriticalResourceNbt.InvalidJournalException("Invalid exact amount: " + key);
            }
        }
    }

    /** Validate only owned ledger fields; arbitrary item/fluid payload tags remain opaque. */
    private static void validateEnergy(NBTTagCompound data, int ignoredDepth) {
        validateExactAmounts(data);
        if (data.hasKey("chemicalDebitIntent")) validateExactAmounts(compound(data, "chemicalDebitIntent"));
        for (String key : new String[] { "outputs", "inputs", "chemicalCompoundPayment", "chemicalBatchCredits",
            "chemicalCredits" }) {
            NBTTagList list = CriticalResourceNbt.journal(data, key);
            if (list.tagCount() > CriticalResourceNbt.MAX_JOURNAL_SLOTS)
                throw new CriticalResourceNbt.InvalidJournalException("Runtime list budget exceeded: " + key);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                validateExactAmounts(entry);
                if ((key.equals("outputs") || key.equals("inputs"))
                    && (FluidStack.loadFluidStackFromNBT(entry) == null || !entry.hasKey("exactAmount", 8)))
                    throw new CriticalResourceNbt.InvalidJournalException("Unknown runtime fluid: " + key);
            }
        }
        NBTTagList items = CriticalResourceNbt.journal(data, "items");
        if (items.tagCount() > CriticalResourceNbt.MAX_JOURNAL_SLOTS)
            throw new CriticalResourceNbt.InvalidJournalException("Runtime item budget exceeded");
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            if (ItemStack.loadItemStackFromNBT(entry) == null || !entry.hasKey("exactCount", 3)
                || entry.getInteger("exactCount") <= 0)
                throw new CriticalResourceNbt.InvalidJournalException("Unknown runtime item");
        }
    }

    static final class Resources {

        private final Map<String, ItemStack[]> items = new LinkedHashMap<>();
        private final Map<String, FluidStack[]> fluids = new LinkedHashMap<>();

        ItemStack[] items(String key) {
            return items.get(key);
        }

        FluidStack[] fluids(String key) {
            return fluids.get(key);
        }
    }
}
