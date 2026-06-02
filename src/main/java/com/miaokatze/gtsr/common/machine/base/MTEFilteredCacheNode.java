package com.miaokatze.gtsr.common.machine.base;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.CycleButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FluidSlotWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.gui.modularui.widget.FluidLockWidget;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

public abstract class MTEFilteredCacheNode extends MTEDigitalTankBase {

    public MTEFilteredCacheNode(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEFilteredCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;
    protected int mTransferRatePercent = 100;

    private static final int[] TRANSFER_RATE_CYCLE = { 100, 80, 60, 40, 20, 10, 5, 1, 0 };

    protected abstract boolean isFluidAllowed(Fluid fluid);

    protected abstract int getBaseHubTransferRate();

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getCurrentEquippedItem();
            if (held != null && (GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)
                || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(held, true, true))) {
                if (mHubDim == 0) {
                    GTUtility
                        .sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
                    return true;
                }
                int currentIdx = -1;
                for (int i = 0; i < TRANSFER_RATE_CYCLE.length; i++) {
                    if (TRANSFER_RATE_CYCLE[i] == mTransferRatePercent) {
                        currentIdx = i;
                        break;
                    }
                }
                int nextIdx = (currentIdx + 1) % TRANSFER_RATE_CYCLE.length;
                mTransferRatePercent = TRANSFER_RATE_CYCLE[nextIdx];
                long actualRate = (long) getBaseHubTransferRate() * mTransferRatePercent / 100;
                String msg = StatCollector.translateToLocal("gtsr.cache_node.transfer_rate") + " "
                    + mTransferRatePercent
                    + "% ("
                    + String.format("%,d", actualRate)
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s")
                    + ")";
                GTUtility.sendChatToPlayer(aPlayer, msg);
                return true;
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    public int getTransferRatePercent() {
        return mTransferRatePercent;
    }

    public long getEffectiveHubTransferRate() {
        return (long) getBaseHubTransferRate() * mTransferRatePercent / 100;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mIsOutputMode", mIsOutputMode);
        aNBT.setInteger("mTransferRatePercent", mTransferRatePercent);
        if (mHubDim != 0) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mIsOutputMode = aNBT.hasKey("mIsOutputMode") ? aNBT.getBoolean("mIsOutputMode") : true;
        mTransferRatePercent = aNBT.hasKey("mTransferRatePercent") ? aNBT.getInteger("mTransferRatePercent") : 100;
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            mHubX = hubTag.getInteger("x");
            mHubY = hubTag.getInteger("y");
            mHubZ = hubTag.getInteger("z");
            mHubDim = hubTag.getInteger("dim");
            mHubType = hubTag.getString("type");
        } else {
            mHubX = 0;
            mHubY = 0;
            mHubZ = 0;
            mHubDim = 0;
            mHubType = "";
            mIsOutputMode = true;
            mRegistered = false;
        }
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.cache_node.base_transfer_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", getBaseHubTransferRate())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.cache_node.chip_adjust"));
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = stack.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int hubX = hubTag.getInteger("x");
            int hubY = hubTag.getInteger("y");
            int hubZ = hubTag.getInteger("z");
            String hubType = hubTag.getString("type");
            boolean isOutput = hubTag.hasKey("output") && hubTag.getBoolean("output");
            String mode = isOutput ? translateToLocal("gtsr.binding.debug_output")
                : translateToLocal("gtsr.binding.debug_input");
            tooltip.add(
                translateToLocal("gtsr.binding.bound_to") + " "
                    + hubType
                    + " @ "
                    + hubX
                    + ", "
                    + hubY
                    + ", "
                    + hubZ
                    + " ["
                    + mode
                    + "]");
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && mHubDim != 0) {
            mRegistered = true;
            registerWithHub(aBaseMetaTileEntity);
        }
    }

    private void registerWithHub(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return;

        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return;

        if (!hub.acceptsNodeType(mHubType)) return;

        hub.registerCacheNode(
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord(),
            aBaseMetaTileEntity.getWorld().provider.dimensionId,
            mIsOutputMode);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        fluidTank.setAllowOverflow(allowOverflow());
        fluidTank.setPreventDraining(mLockFluid);
        final boolean isServer = GTUtility.isServer();
        FluidSlotWidget fluidSlotWidget = new FluidSlotWidget(fluidTank)
            .setFilter(fluid -> fluid != null && isFluidAllowed(fluid));
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(71, 45))
            .widget(
                new SlotWidget(inventoryHandler, getInputSlot())
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(79, 16))
            .widget(
                new SlotWidget(inventoryHandler, getOutputSlot()).setAccess(true, false)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_OUT)
                    .setPos(79, 43))
            .widget(
                fluidSlotWidget.setOnClickContainer(widget -> onEmptyingContainerWhenEmpty())
                    .setBackground(GTUITextures.TRANSPARENT)
                    .setPos(58, 41))
            .widget(
                new TextWidget(translateToLocal("GT5U.machines.digitaltank.fluid.amount"))
                    .setDefaultColor(COLOR_TEXT_WHITE.get())
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> numberFormat.format(mFluid != null ? mFluid.amount : 0))
                    .setDefaultColor(COLOR_TEXT_WHITE.get())
                    .setPos(10, 30))
            .widget(
                new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                    .setPos(98, 16)
                    .setSize(71, 45))
            .widget(new FluidLockWidget(this).setPos(149, 41))
            .widget(
                new TextWidget(translateToLocal("GT5U.machines.digitaltank.lockfluid.label"))
                    .setDefaultColor(COLOR_TEXT_WHITE.get())
                    .setPos(101, 20))
            .widget(TextWidget.dynamicString(() -> {
                FluidStack fluidStack = FluidRegistry.getFluidStack(lockedFluidName, 1);
                return fluidStack != null ? fluidStack.getLocalizedName()
                    : translateToLocal("GT5U.machines.digitaltank.lockfluid.empty");
            })
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setTextAlignment(Alignment.CenterLeft)
                .setMaxWidth(65)
                .setPos(101, 30))
            .widget(new CycleButtonWidget().setToggle(() -> mOutputFluid, val -> {
                mOutputFluid = val;
                if (isServer) {
                    if (!mOutputFluid) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("262", "Fluid Auto Output Disabled"));
                    } else {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("263", "Fluid Auto Output Enabled"));
                    }
                }
            })
                .setVariableBackground(GTUITextures.BUTTON_STANDARD_TOGGLE)
                .setStaticTexture(GTUITextures.OVERLAY_BUTTON_AUTOOUTPUT_FLUID)
                .setGTTooltip(() -> mTooltipCache.getData("GT5U.machines.digitaltank.autooutput.tooltip"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(7, 63)
                .setSize(18, 18))
            .widget(new CycleButtonWidget().setToggle(() -> mLockFluid, val -> {
                lockFluid(val);
                fluidTank.setPreventDraining(mLockFluid);

                String inBrackets;
                if (mLockFluid) {
                    if (mFluid == null) {
                        setLockedFluidName(null);
                        inBrackets = GTUtility
                            .trans("264", "currently none, will be locked to the next that is put in");
                    } else {
                        setLockedFluidName(
                            getDrainableStack().getFluid()
                                .getName());
                        inBrackets = getDrainableStack().getLocalizedName();
                    }
                    if (isServer) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            String.format("%s (%s)", GTUtility.trans("265", "1 specific Fluid"), inBrackets));
                    }
                } else {
                    fluidTank.drain(0, true);
                    if (isServer) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("266", "Lock Fluid Mode Disabled"));
                    }
                }
                fluidSlotWidget.notifyTooltipChange();
            })
                .setVariableBackground(GTUITextures.BUTTON_STANDARD_TOGGLE)
                .setStaticTexture(GTUITextures.OVERLAY_BUTTON_LOCK)
                .setGTTooltip(() -> mTooltipCache.getData("GT5U.machines.digitaltank.lockfluid.tooltip"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(25, 63)
                .setSize(18, 18))
            .widget(new CycleButtonWidget().setToggle(() -> mAllowInputFromOutputSide, val -> {
                mAllowInputFromOutputSide = val;
                if (isServer) {
                    if (!mAllowInputFromOutputSide) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            translateToLocal("gt.interact.desc.input_from_output_off"));
                    } else {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            translateToLocal("gt.interact.desc.input_from_output_on"));
                    }
                }
            })
                .setVariableBackground(GTUITextures.BUTTON_STANDARD_TOGGLE)
                .setStaticTexture(GTUITextures.OVERLAY_BUTTON_INPUT_FROM_OUTPUT_SIDE)
                .setGTTooltip(() -> mTooltipCache.getData("GT5U.machines.digitaltank.inputfromoutput.tooltip"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(43, 63)
                .setSize(18, 18))
            .widget(new CycleButtonWidget().setToggle(() -> mVoidFluidPart, val -> {
                mVoidFluidPart = val;
                fluidTank.setAllowOverflow(allowOverflow());
                if (isServer) {
                    if (!mVoidFluidPart) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("267", "Overflow Voiding Mode Disabled"));
                    } else {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("268", "Overflow Voiding Mode Enabled"));
                    }
                }
            })
                .setVariableBackground(GTUITextures.BUTTON_STANDARD_TOGGLE)
                .setStaticTexture(GTUITextures.OVERLAY_BUTTON_TANK_VOID_EXCESS)
                .setGTTooltip(() -> mTooltipCache.getData("GT5U.machines.digitaltank.voidoverflow.tooltip"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(98, 63)
                .setSize(18, 18))
            .widget(new CycleButtonWidget().setToggle(() -> mVoidFluidFull, val -> {
                mVoidFluidFull = val;
                fluidTank.setAllowOverflow(allowOverflow());
                if (isServer) {
                    if (!mVoidFluidFull) {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("269", "Void Full Mode Disabled"));
                    } else {
                        GTUtility.sendChatToPlayer(
                            buildContext.getPlayer(),
                            GTUtility.trans("270", "Void Full Mode Enabled"));
                    }
                }
            })
                .setVariableBackground(GTUITextures.BUTTON_STANDARD_TOGGLE)
                .setStaticTexture(GTUITextures.OVERLAY_BUTTON_TANK_VOID_ALL)
                .setGTTooltip(() -> mTooltipCache.getData("GT5U.machines.digitaltank.voidfull.tooltip"))
                .setTooltipShowUpDelay(TOOLTIP_DELAY)
                .setPos(116, 63)
                .setSize(18, 18));
    }
}
