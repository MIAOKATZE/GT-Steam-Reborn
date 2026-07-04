package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.gtsr.api.IAutoInputHatch;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;

@Mixin(value = MTEHatchInputBus.class, remap = false)
public abstract class MTEHatchInputBusMixin extends MTEHatch implements IAutoInputHatch {

    public MTEHatchInputBusMixin(String aName, int aTier, int aInvSlotCount, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
    }

    @Shadow(remap = false)
    public boolean disableFilter;

    @Shadow(remap = false)
    public boolean disableSort;

    @Shadow(remap = false)
    public boolean disableLimited;

    @Unique
    private boolean gtsr$autoInput = false;

    /**
     * @reason 4-state orthogonal toggle: input filter × auto-input.
     *         Shift+click preserves sort/limit cycling.
     * @author GTSR
     */
    @Overwrite
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (!getBaseMetaTileEntity().getCoverAtSide(side)
            .isGUIClickable()) return;

        if (aPlayer.isSneaking()) {
            if (disableSort) {
                disableSort = false;
            } else {
                if (disableLimited) {
                    disableLimited = false;
                } else {
                    disableSort = true;
                    disableLimited = true;
                }
            }
            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.hatch.sort_mode")
                    + " "
                    + (disableSort ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")
                        : EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on"))
                    + EnumChatFormatting.RESET
                    + "  "
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.hatch.limit_mode")
                    + " "
                    + (disableLimited
                        ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")
                        : EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")));
        } else {
            if (disableFilter && !gtsr$autoInput) {
                disableFilter = false;
            } else if (!disableFilter && !gtsr$autoInput) {
                disableFilter = true;
                gtsr$autoInput = true;
            } else if (disableFilter && gtsr$autoInput) {
                disableFilter = false;
            } else {
                disableFilter = true;
                gtsr$autoInput = false;
            }

            GTUtility.sendChatToPlayer(
                aPlayer,
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.hatch.input_filter")
                    + " "
                    + (disableFilter
                        ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")
                        : EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on"))
                    + EnumChatFormatting.RESET
                    + "  "
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.hatch.auto_input")
                    + " "
                    + (gtsr$autoInput
                        ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                        : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
        }
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$saveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setBoolean("gtsr$autoInput", gtsr$autoInput);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$loadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        gtsr$autoInput = aNBT.getBoolean("gtsr$autoInput");
    }

    /**
     * 直接注入 MTEHatchInputBus.onPostTick (TAIL)。
     * MTEHatchInputBus 覆写了 onPostTick 且不调用 super，因此注入到父类
     * CommonMetaTileEntity.onPostTick 的统一调度 (CommonMetaTileEntityMixin) 对本类无效，
     * 必须在此直接注入。MTEHatchSteamBusInput 继承本类方法，同样由此修复。
     */
    @Inject(method = "onPostTick", at = @At("TAIL"), remap = false)
    private void gtsr$onPostTickAutoInput(IGregTechTileEntity aBaseMetaTileEntity, long aTimer, CallbackInfo ci) {
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (gtsr$autoInput) {
            gtsr$doAutoInput(aBaseMetaTileEntity, aTimer);
        }
    }

    @Override
    public boolean gtsr$isAutoInput() {
        return gtsr$autoInput;
    }

    @Override
    public void gtsr$setAutoInput(boolean value) {
        gtsr$autoInput = value;
    }

    @Override
    public void gtsr$doAutoInput(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isAllowedToWork()) return;
        if (aTick % 100 != 0) return; // 5秒一次（100 tick），保留

        MTEHatchInputBus self = (MTEHatchInputBus) (Object) this;

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return;

        TileEntity tTileEntity = world.getTileEntity(
            aBaseMetaTileEntity.getXCoord() + front.offsetX,
            aBaseMetaTileEntity.getYCoord() + front.offsetY,
            aBaseMetaTileEntity.getZCoord() + front.offsetZ);
        if (!(tTileEntity instanceof IInventory)) return;

        IInventory sourceInv = (IInventory) tTileEntity;

        // 修复Bug1：按"格"计数，每5秒只处理1格（参考GT5U CoverConveyor.stacksPerTransfer=1）
        final int MAX_STACKS_PER_TRANSFER = 1;
        int stacksProcessed = 0;
        boolean changed = false; // 标记是否实际发生转移，控制updateSlots调用

        for (int i = 0; i < sourceInv.getSizeInventory() && stacksProcessed < MAX_STACKS_PER_TRANSFER; i++) {
            ItemStack stack = sourceInv.getStackInSlot(i);
            if (stack == null) continue;

            ItemStack moved = stack.copy();
            stacksProcessed++; // 这一格就算处理过（参考CoverConveyor语义）

            // 单趟遍历目标槽位（参考MTEHatchOutputBus.storePartial第182-204行）
            for (int j = 0; j < self.getSizeInventory() && moved.stackSize > 0; j++) {
                if (!self.allowPutStack(aBaseMetaTileEntity, j, front, moved)) continue;

                ItemStack existing = self.getStackInSlot(j);

                // 修复Bug2：异种跳过（参考MTEHatchOutputBus.storePartial第188行 areStacksEqual检查）
                if (existing != null && !GTUtility.areStacksEqual(existing, moved)) continue;

                // 修复Bug3：用existing的maxStackSize（existing!=null时与moved同种，二者相等；用existing更安全）
                int maxStack = Math.min(
                    self.getInventoryStackLimit(),
                    existing != null ? existing.getMaxStackSize() : moved.getMaxStackSize());
                int space = existing == null ? maxStack : maxStack - existing.stackSize;
                if (space <= 0) continue;

                int toMove = Math.min(moved.stackSize, space);
                if (existing == null) {
                    ItemStack newStack = moved.copy();
                    newStack.stackSize = toMove;
                    self.setInventorySlotContents(j, newStack);
                } else {
                    existing.stackSize += toMove; // 此时existing必与moved同种，安全
                }

                sourceInv.decrStackSize(i, toMove);
                moved.stackSize -= toMove;
                changed = true;
            }
        }

        // 修复Bug4：仅在实际发生转移时调用updateSlots
        if (changed) {
            self.updateSlots();
        }
    }
}
