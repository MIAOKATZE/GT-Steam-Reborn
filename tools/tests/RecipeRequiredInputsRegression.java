package com.miaokatze.gtsr.loader.recipes;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Real ItemStack construction: a missing registry Item does not produce a null stack. */
public final class RecipeRequiredInputsRegression {
    public static void main(String[] args) {
        int failures = 0;
        Item item = new Item();
        failures += check("null input array rejected", nullArrayRejected());
        failures += check("missing input rejected", RecipeLoaderUtils.hasNull(new ItemStack(item), null));
        failures += check("missing registry item wrapper rejected", RecipeLoaderUtils.hasNull(new ItemStack((Item) null, 32, 2)));
        failures += check("valid item accepted", !RecipeLoaderUtils.hasNull(new ItemStack(item, 16, 0)));
        failures += check("zero-size selector accepted", !RecipeLoaderUtils.hasNull(new ItemStack(item, 0, 24)));
        failures += check("valid empty item input accepted", !RecipeLoaderUtils.hasNull(new ItemStack[0]));
        if (failures != 0) throw new AssertionError(failures + " required-input regressions");
    }
    private static boolean nullArrayRejected() {
        try { return RecipeLoaderUtils.hasNull((ItemStack[]) null); }
        catch (NullPointerException failure) { return false; }
    }
    private static int check(String name, boolean valid) {
        System.out.println((valid ? "PASS " : "FAIL ") + name);
        return valid ? 0 : 1;
    }
}
