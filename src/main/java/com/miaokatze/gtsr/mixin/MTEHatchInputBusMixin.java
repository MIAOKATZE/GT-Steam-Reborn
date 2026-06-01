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

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTUtility;

@Mixin(value = MTEHatchInputBus.class, remap = false)
public abstract class MTEHatchInputBusMixin extends MTEHatch {

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

    @Inject(method = "onPostTick", at = @At("TAIL"), remap = false)
    private void gtsr$onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (!aBaseMetaTileEntity.isServerSide() || !gtsr$autoInput) return;
        if (!aBaseMetaTileEntity.isAllowedToWork()) return;
        if ((aTick & 0x63) != 0) return;

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
        int transferred = 0;

        for (int i = 0; i < sourceInv.getSizeInventory() && transferred < 64; i++) {
            ItemStack stack = sourceInv.getStackInSlot(i);
            if (stack == null) continue;

            ItemStack moved = stack.copy();
            moved.stackSize = Math.min(stack.stackSize, 64 - transferred);

            for (int j = 0; j < self.getSizeInventory(); j++) {
                if (!self.allowPutStack(aBaseMetaTileEntity, j, front, moved)) continue;

                ItemStack existing = self.getStackInSlot(j);
                int maxStack = Math.min(moved.getMaxStackSize(), self.getInventoryStackLimit());
                int space = existing == null ? maxStack : maxStack - existing.stackSize;
                if (space <= 0) continue;

                int toMove = Math.min(moved.stackSize, space);
                if (existing == null) {
                    ItemStack newStack = moved.copy();
                    newStack.stackSize = toMove;
                    self.setInventorySlotContents(j, newStack);
                } else {
                    existing.stackSize += toMove;
                }

                sourceInv.decrStackSize(i, toMove);
                transferred += toMove;
                moved.stackSize -= toMove;

                if (moved.stackSize <= 0) break;
            }
        }

        if (transferred > 0) {
            self.updateSlots();
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
}
