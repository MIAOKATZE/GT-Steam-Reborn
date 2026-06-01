package com.miaokatze.gtsr.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

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
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.util.GTUtility;

@Mixin(value = MTEHatchInput.class, remap = false)
public abstract class MTEHatchInputMixin extends MTEHatch implements IAutoInputHatch {

    public MTEHatchInputMixin(String aName, int aTier, int aInvSlotCount, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aInvSlotCount, aDescription, aTextures);
    }

    @Shadow(remap = false)
    public boolean disableFilter;

    @Unique
    private boolean gtsr$autoInput = false;

    /**
     * @reason 4-state orthogonal toggle: input filter × auto-input
     * @author GTSR
     */
    @Overwrite
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (!getBaseMetaTileEntity().getCoverAtSide(side)
            .isGUIClickable()) return;

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
                + (disableFilter ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")
                    : EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on"))
                + EnumChatFormatting.RESET
                + "  "
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.hatch.auto_input")
                + " "
                + (gtsr$autoInput ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$saveNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        aNBT.setBoolean("gtsr$autoInput", gtsr$autoInput);
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"), remap = false)
    private void gtsr$loadNBTData(NBTTagCompound aNBT, CallbackInfo ci) {
        gtsr$autoInput = aNBT.getBoolean("gtsr$autoInput");
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
        if (!aBaseMetaTileEntity.isAllowedToWork()) {
            System.out.println("[GTSR-DEBUG] doAutoInput: not allowed to work");
            return;
        }

        MTEHatchInput self = (MTEHatchInput) (Object) this;

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        IFluidHandler tTileEntity = aBaseMetaTileEntity.getITankContainerAtSide(front);
        if (tTileEntity == null) {
            System.out.println("[GTSR-DEBUG] doAutoInput: no IFluidHandler at front side=" + front);
            return;
        }

        FluidStack drained = tTileEntity.drain(front.getOpposite(), 100, false);
        if (drained == null) {
            System.out.println("[GTSR-DEBUG] doAutoInput: drain returned null from side=" + front.getOpposite());
            return;
        }

        System.out
            .println("[GTSR-DEBUG] doAutoInput: drained=" + drained.amount + "L of " + drained.getLocalizedName());

        int filled = self.fill(front, drained, true);
        System.out.println("[GTSR-DEBUG] doAutoInput: filled=" + filled + "L");
        if (filled > 0) {
            tTileEntity.drain(front.getOpposite(), filled, true);
        }
    }
}
