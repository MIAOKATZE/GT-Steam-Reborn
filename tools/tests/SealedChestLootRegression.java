import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.WeightedRandomChestContent;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.SealedChestLoot;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntityUnsealedChest;

/** Small standalone regression; uses the actual planner and native Forge chest generator without starting a world. */
public final class SealedChestLootRegression {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        boolean plannerOnly = args.length > 0 && args[0].equals("--planner-only");
        Item stackable = new Item().setMaxStackSize(64), single = new Item().setMaxStackSize(1);
        if (!plannerOnly) {
            Item.itemRegistry.addObject(32000, "gtsr_test:stack", stackable);
            Item.itemRegistry.addObject(32001, "gtsr_test:single", single);
        }
        ItemStack a = new ItemStack(stackable, 60, 4), b = new ItemStack(stackable, 10, 4);
        NBTTagCompound proof = new NBTTagCompound(); proof.setString("proof", "original");
        a.setTagCompound(proof); b.setTagCompound((NBTTagCompound) proof.copy());
        ItemStack c = b.copy(); c.stackSize = 3; c.getTagCompound().setString("proof", "different");
        Method normalize = SealedChestLoot.class.getDeclaredMethod("normalize", List.class);
        normalize.setAccessible(true);
        List<ItemStack> packed = (List<ItemStack>) normalize.invoke(null, Arrays.asList(a, b, c, new ItemStack(single, 3)));
        check(packed.size() == 6, "merge/maxStack occupied slots");
        check(packed.get(0).stackSize == 64 && packed.get(1).stackSize == 6 && packed.get(2).stackSize == 3, "quantity conservation and typed NBT separation");
        packed.get(0).getTagCompound().setString("proof", "mutated");
        check(a.stackSize == 60 && b.stackSize == 10 && a.getTagCompound().getString("proof").equals("original"), "copy isolation");

        TileEntityUnsealedChest chest = new TileEntityUnsealedChest();
        check(chest.getSizeInventory() == 54 && chest.beginReward(3) && !chest.beginReward(3), "54 slots and idempotency");
        chest.setInventorySlotContents(53, new ItemStack(single, 5));
        check(chest.getStackInSlot(53).stackSize == 1, "inventory maxStack limit");
        SealedChestLoot.fill(chest, 5, new ItemStack(stackable));
        check(chest.getStackInSlot(12) == null, "generated reward returns before world RNG or witness writes");
        TileEntityUnsealedChest occupied = new TileEntityUnsealedChest();
        occupied.setInventorySlotContents(2, a.copy());
        SealedChestLoot.fill(occupied, 5, new ItemStack(stackable));
        check(occupied.getStackInSlot(2).stackSize == 60, "player inventory returns before world RNG");
        occupied.readFromNBT(new NBTTagCompound());
        SealedChestLoot.fill(occupied, 5, null);
        check(!occupied.canBeginReward(), "empty legacy NBT does not reroll");
        java.lang.reflect.Constructor<SealedChestLoot.Prepared> preparedConstructor = SealedChestLoot.Prepared.class
            .getDeclaredConstructor(int.class, ItemStack[].class);
        preparedConstructor.setAccessible(true);
        ItemStack[] preparedContents = new ItemStack[54];
        preparedContents[4] = b.copy(); preparedContents[12] = new ItemStack(single);
        SealedChestLoot.Prepared prepared = preparedConstructor.newInstance(2, preparedContents);
        TileEntityUnsealedChest commit = new TileEntityUnsealedChest();
        check(SealedChestLoot.apply(commit, prepared), "prepared plan commits without world or RNG");
        preparedContents[4].stackSize = 1;
        check(commit.getStackInSlot(4).stackSize == 10 && commit.getStackInSlot(12).stackSize == 1,
            "apply copies quantities and witness");
        check(!SealedChestLoot.apply(new TileEntityUnsealedChest(), prepared), "prepared plan is consumed exactly once");
        if (!plannerOnly) {
        NBTTagCompound saved = new NBTTagCompound(); chest.writeToNBT(saved);
        TileEntityUnsealedChest restored = new TileEntityUnsealedChest(); restored.readFromNBT(saved);
        check(restored.getStackInSlot(53).getItem() == single && !restored.beginReward(3), "54-slot persisted reward");
        NBTTagCompound legacy = new NBTTagCompound();
        NBTTagCompound slot = new NBTTagCompound(); slot.setByte("Slot", (byte) 26); a.writeToNBT(slot);
        NBTTagList items = new NBTTagList(); items.appendTag(slot); legacy.setTag("Items", items);
        restored.readFromNBT(legacy);
        check(restored.getStackInSlot(26).stackSize == 60 && restored.getStackInSlot(53) == null && !restored.beginReward(1), "legacy 27-slot mapping and no refill");
        restored.readFromNBT(new NBTTagCompound());
        check(!restored.beginReward(1), "legacy emptied chest remains awarded");
        }

        Class<?> captureClass = Class.forName(SealedChestLoot.class.getName() + "$Capture");
        java.lang.reflect.Constructor<?> constructor = captureClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object capture = constructor.newInstance(new TileEntityUnsealedChest());
        WeightedRandomChestContent entry = new WeightedRandomChestContent(new ItemStack(single, 1), 6, 6, 1);
        WeightedRandomChestContent.generateChestContents(new Random(1), new WeightedRandomChestContent[] { entry },
            (net.minecraft.inventory.IInventory) capture, 1);
        java.lang.reflect.Field field = captureClass.getDeclaredField("stacks"); field.setAccessible(true);
        check(((List<ItemStack>) field.get(capture)).size() == 6, "native emission collector does not overwrite random slots");
        System.out.println(plannerOnly ? "PASS: quantity/NBT/maxStack/copy, native emissions, 54-slot inventory and reward idempotency"
            : "PASS: quantity/NBT/maxStack, native emissions, 54-slot persistence, old 27-slot and emptied reward idempotency");
    }
}
