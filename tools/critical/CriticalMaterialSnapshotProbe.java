package com.miaokatze.gtsr.common.gui;

import java.lang.reflect.Field;
import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Actual registry, NBT parser and PacketBuffer; no Minecraft client or world is started. */
public final class CriticalMaterialSnapshotProbe {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    public static void main(String[] args) throws Exception {
        Item item = new Item() {
            @Override
            public String getItemStackDisplayName(ItemStack stack) {
                return StatCollector.translateToLocal("probe.material." +
                    (stack.hasTagCompound() ? stack.getTagCompound().getInteger("circuit") : -1));
            }
        };
        Item.itemRegistry.addObject(31995, "probe:critical_material_snapshot", item);
        Field localized = StatCollector.class.getDeclaredField("localizedName");
        localized.setAccessible(true);
        Object translations = localized.get(null);
        Field list = translations.getClass().getDeclaredField("languageList");
        list.setAccessible(true);
        Map<String, String> language = (Map<String, String>) list.get(translations);
        language.put("probe.material.7", "Marked circuit seven");
        language.put("probe.material.8", "Marked circuit eight");
        language.put("gtsr.critical.ui.materials.nbt_omitted", "NBT omitted");
        language.put("gtsr.critical.ui.materials.omitted", "%s rows omitted");
        ItemStack first = new ItemStack(item, 100000, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("circuit", 7);
        first.setTagCompound(tag);
        ItemStack second = first.copy();
        second.getTagCompound().setInteger("circuit", 8);
        CriticalMaterialSnapshot captured = CriticalMaterialSnapshot.capture(new ItemStack[] { first, second }, new FluidStack[0]);
        String text = captured.lines();
        check(text.contains("circuit seven") && text.contains("circuit eight") && text.contains("100000"), "NBT identity and large quantities retained");
        check(first.getTagCompound().getInteger("circuit") == 7 && first.stackSize == 100000, "capture leaves original item unchanged");
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            captured.write(packet);
            CriticalMaterialSnapshot decoded = CriticalMaterialSnapshot.read(packet);
            check(captured.same(decoded) && decoded.lines().equals(text) && packet.readableBytes() == 0, "native PacketBuffer round trip");
            language.put("probe.material.7", "中文电路七");
            check(decoded.lines().contains("中文电路七"), "same descriptor follows receiving language without recapture");
        } finally { packet.release(); }
        ItemStack huge = first.copy();
        huge.getTagCompound().setString("long", new String(new char[2048]).replace('\0', 'x'));
        check(CriticalMaterialSnapshot.capture(new ItemStack[] { huge }, new FluidStack[0]).lines().contains("NBT omitted"), "oversized NBT explicitly marked unavailable");
        ItemStack deep = first.copy();
        NBTTagCompound current = deep.getTagCompound();
        for (int i = 0; i < 20; i++) {
            NBTTagCompound child = new NBTTagCompound();
            current.setTag("nested", child);
            current = child;
        }
        check(CriticalMaterialSnapshot.capture(new ItemStack[] { deep }, new FluidStack[0]).lines().contains("NBT omitted"), "deep NBT explicitly marked unavailable");
        ItemStack[] budget = new ItemStack[12];
        for (int i = 0; i < budget.length; i++) {
            budget[i] = first.copy();
            budget[i].getTagCompound().setString("padding", new String(new char[900]).replace('\0', 'x'));
        }
        CriticalMaterialSnapshot limited = CriticalMaterialSnapshot.capture(budget, new FluidStack[0]);
        check(limited.lines().contains("NBT omitted"), "aggregate NBT budget degrades explicitly");
        PacketBuffer bounded = new PacketBuffer(Unpooled.buffer());
        try {
            limited.write(bounded);
            check(bounded.readableBytes() < 10000 && limited.same(CriticalMaterialSnapshot.read(bounded)), "aggregate payload bound survives native round trip");
        } finally { bounded.release(); }
        net.minecraft.init.Bootstrap.func_151354_b();
        Fluid fluid = new Fluid("critical_material_probe") {
            @Override public String getLocalizedName(FluidStack stack) { return StatCollector.translateToLocal("probe.fluid"); }
        };
        FluidRegistry.registerFluid(fluid);
        language.put("probe.fluid", "Probe liquid");
        CriticalMaterialSnapshot fluidSample = CriticalMaterialSnapshot.capture(new ItemStack[0], new FluidStack[] { new FluidStack(fluid, 2147483647) });
        check(fluidSample.lines().contains("Probe liquid × 2147483647 mB"), "fluid registry name and large amount retained");
        ItemStack[] many = new ItemStack[65];
        java.util.Arrays.fill(many, first);
        check(CriticalMaterialSnapshot.capture(many, new FluidStack[0]).lines().contains("1 rows omitted"), "row cap reports omitted identities");
        PacketBuffer invalid = new PacketBuffer(Unpooled.buffer());
        try {
            invalid.writeVarIntToBuffer(65);
            invalid.writeVarIntToBuffer(0);
            boolean rejected = false;
            try { CriticalMaterialSnapshot.read(invalid); } catch (java.io.IOException expected) { rejected = true; }
            check(rejected, "oversized incoming row count rejected");
        } finally { invalid.release(); }
        System.out.println("MATERIAL_SNAPSHOT_PASS checks=" + checks);
    }
}
