import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.util.AutoInputItemTransfer;

import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

/** Native MC stacks and GTNHLib transfer/iterator; controlled source and target inventories. */
public final class AutoInputTransferRegression {
    private static int checks;
    private static final ForgeDirection FRONT = ForgeDirection.EAST;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Source implements ISidedInventory {
        final ItemStack[] stacks = new ItemStack[3];
        int[] accessible = {0, 1, 2};
        int side = -1, dirty;
        boolean extract = true;
        public int getSizeInventory() {return stacks.length;}
        public ItemStack getStackInSlot(int slot) {return stacks[slot];}
        public ItemStack decrStackSize(int slot, int count) {throw new AssertionError("Native iterator owns extraction");}
        public ItemStack getStackInSlotOnClosing(int slot) {return null;}
        public void setInventorySlotContents(int slot, ItemStack value) {stacks[slot] = value;}
        public String getInventoryName() {return "test";}
        public boolean hasCustomInventoryName() {return false;}
        public int getInventoryStackLimit() {return 64;}
        public void markDirty() {dirty++;}
        public boolean isUseableByPlayer(EntityPlayer player) {return true;}
        public void openInventory() {}
        public void closeInventory() {}
        public boolean isItemValidForSlot(int slot, ItemStack stack) {return true;}
        public int[] getAccessibleSlotsFromSide(int side) {this.side=side;return accessible;}
        public boolean canInsertItem(int slot, ItemStack stack, int side) {return false;}
        public boolean canExtractItem(int slot, ItemStack stack, int side) {this.side=side;return extract && slot != 2;}
    }

    private static ItemStack iron(int count) {return new ItemStack(net.minecraft.init.Items.iron_ingot,count);}

    public static void main(String[] args) {
        net.minecraft.init.Bootstrap.func_151354_b();
        Source source = new Source();
        MTEHatchInputBus target = new MTEHatchInputBus();
        source.stacks[0] = iron(12);
        check(AutoInputItemTransfer.pull(source,target,null,FRONT),"normal transfer");
        check(source.stacks[0] == null && target.stacks[0].stackSize == 12,"conserved normal transfer");
        check(source.side == ForgeDirection.WEST.ordinal(),"source uses the adjacent face");
        check(source.dirty > 0,"native extraction marks source dirty");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(12);source.extract=false;
        check(!AutoInputItemTransfer.pull(source,target,null,FRONT),"blocked extraction");
        check(source.stacks[0].stackSize == 12 && target.stacks[0] == null,"blocked extraction preserves inventories");
        source = new Source();target = new MTEHatchInputBus();source.stacks[2]=iron(1);
        check(!AutoInputItemTransfer.pull(source,target,null,FRONT),"circuit-like protected slot");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(12);source.accessible=new int[]{1};
        check(!AutoInputItemTransfer.pull(source,target,null,FRONT),"inaccessible slot");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(12);target.stacks[0]=iron(60);target.blockSecond=true;
        check(AutoInputItemTransfer.pull(source,target,null,FRONT),"partial insertion");
        check(source.stacks[0].stackSize==8 && target.stacks[0].stackSize==64,"native rollback returns rejected remainder");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(5);source.stacks[1]=iron(7);
        target.stacks[0]=new ItemStack(net.minecraft.init.Items.gold_ingot,64);target.stacks[1]=iron(64);
        check(!AutoInputItemTransfer.pull(source,target,null,FRONT),"full target rejects");
        check(source.stacks[0].stackSize==5 && source.stacks[1].stackSize==7,"no loss or second source attempt");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(5);target.stacks[1]=iron(10);
        check(AutoInputItemTransfer.pull(source,target,null,FRONT) && target.stacks[0].stackSize==5
            && target.stacks[1].stackSize==10,"preserve ascending target order before merging a later slot");
        source = new Source();target = new MTEHatchInputBus();source.stacks[0]=iron(80);
        check(AutoInputItemTransfer.pull(source,target,null,FRONT) && source.stacks[0]==null
            && target.stacks[0].stackSize==64 && target.stacks[1].stackSize==16,"preserve oversized single-slot budget");
        System.out.println("PASS auto-input native transfer checks="+checks);
    }
}
