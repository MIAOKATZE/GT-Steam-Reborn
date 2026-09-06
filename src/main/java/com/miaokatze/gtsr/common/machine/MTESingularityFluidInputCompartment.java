package com.miaokatze.gtsr.common.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.register.TextureManager;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 奇点输入仓（接收仓）：模式锁定 mIsOutputMode=true（枢纽→仓，从枢纽接受流体），
 * 绑定蓄水枢纽阵列（类型串 singularity_fluid_in，0 奇点消耗），
 * 容量 256,000 L、枢纽交互速率 256,000 L/s（固定常量，无速率档）、任意流体。
 * S1 起改继承近亲 MTEWaterHubInputHatch（原 extends MTEFilteredCacheNode 量子缸链），
 * 绑定数据与终端链路经 MTESingularityCompartmentBase（接口+组合）保留。
 */
public class MTESingularityFluidInputCompartment extends MTEWaterHubInputHatch
    implements MTESingularityCompartmentBase {

    /** S1 数值口径（D1+D3）：容量 256,000 L。 */
    private static final int CAPACITY = 256_000;

    /** 枢纽交互速率 256,000 L/s（固定常量，仓无速率档）。 */
    private static final int HUB_TRANSFER_RATE = 256_000;

    private final MTESingularityCompartmentBase.HubCompartmentState mHubState = new MTESingularityCompartmentBase.HubCompartmentState();

    public MTESingularityFluidInputCompartment(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESingularityFluidInputCompartment(String aName, int aTier, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityFluidInputCompartment(mName, mTier, mDescriptionArray, mTextures);
    }

    // ===== MTESingularityCompartmentBase 语义实现 =====

    @Override
    public MTESingularityCompartmentBase.HubCompartmentState getHubState() {
        return mHubState;
    }

    /** 接收仓：锁定 mIsOutputMode=true（transferWithBoundNodes 的 isOutputMode=true 分支=枢纽抽取灌入本仓）。 */
    @Override
    public boolean getLockedOutputMode() {
        return true;
    }

    @Override
    public int getBaseHubTransferRate() {
        return HUB_TRANSFER_RATE;
    }

    @Override
    public IIconContainer getFrameIconContainer() {
        return TextureManager.HUB_FRAME_RECEIVE;
    }

    @Override
    public Fluid getDefaultWindowFluid() {
        return FluidRegistry.WATER;
    }

    @Override
    public String getFluidRangeTooltipKey() {
        return "gtsr.tooltip.singularity_compartment.fluid_any";
    }

    @Override
    public String getBindTargetTooltipKey() {
        return "gtsr.tooltip.singularity_compartment.bind_target_fluid";
    }

    @Override
    public int getBindingSingularityCost() {
        return 0;
    }

    @Override
    public boolean isFluidAllowed(Fluid fluid) {
        return fluid != null;
    }

    @Override
    public FluidStack getStoredFluidStackLocal() {
        return mFluid;
    }

    // ===== 容量（基量×容量档，屏蔽近亲 mController 视图）=====

    @Override
    public int getCapacity() {
        // S4 容量档：基量×百分比；下方自管 fill 的 space<=0 防御保证降档拒新入、超额温和保留
        return (int) ((long) CAPACITY * getCapacityLimitPercent() / 100);
    }

    @Override
    public long getCapacityLong() {
        return (long) CAPACITY * getCapacityLimitPercent() / 100;
    }

    @Override
    public boolean canTankBeFilled() {
        // 阻断外部管道/容器灌入语义（先例 MTEWaterHubInputHatch 族阻断开关）；枢纽 UNKNOWN 路径自管不受限
        return false;
    }

    // ===== fill 完全自管：屏蔽近亲 MTEWaterHubInputHatch.fill 向 mController 的转发抢流 =====

    /**
     * 自管填充（枢纽 fill(ForgeDirection.UNKNOWN) 与内部路径共用）：不转发 mController、
     * 不查 canTankBeFilled（该标记只承担外部管道/容器阻断语义），过滤走 isFluidInputAllowed，
     * 容量实时读 getCapacity()（S4 容量档覆写通路）。
     */
    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null || aFluid.amount <= 0) return 0;
        if (!isFluidInputAllowed(aFluid)) return 0;
        FluidStack existing = mFluid;
        if (existing == null || existing.amount <= 0) {
            int toFill = Math.min(aFluid.amount, getCapacity());
            if (doFill) {
                mFluid = aFluid.copy();
                if (mFluid.amount > getCapacity()) {
                    mFluid.amount = getCapacity();
                }
                getBaseMetaTileEntity().markDirty();
            }
            return toFill;
        }
        if (!existing.isFluidEqual(aFluid)) return 0;
        int space = getCapacity() - existing.amount;
        if (space <= 0) return 0;
        int toFill = Math.min(aFluid.amount, space);
        if (doFill) {
            existing.amount += toFill;
            getBaseMetaTileEntity().markDirty();
        }
        return toFill;
    }

    // ===== 仅枢纽交互：方向参数版 fill/drain 只放行 UNKNOWN =====

    @Override
    public int fill(ForgeDirection side, FluidStack aFluid, boolean doFill) {
        return side == ForgeDirection.UNKNOWN ? fill(aFluid, doFill) : 0;
    }

    @Override
    public FluidStack drain(ForgeDirection side, int maxDrain, boolean doDrain) {
        return side == ForgeDirection.UNKNOWN ? drain(maxDrain, doDrain) : null;
    }

    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluidStack, boolean doDrain) {
        if (side != ForgeDirection.UNKNOWN || fluidStack == null) return null;
        FluidStack stored = getStoredFluidStackLocal();
        return stored != null && stored.isFluidEqual(fluidStack) ? drain(fluidStack.amount, doDrain) : null;
    }

    // ===== 无 GUI =====

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        // 持终端右击=速率循环（本分支）；Shift+右击容量=HubTerminal.onItemUse 潜行路径
        if (MTESingularityCompartmentBase.handleHubTerminalRateClick(aBaseMetaTileEntity, this, aPlayer)) return true;
        return true;
    }

    // ===== NBT 三处 =====

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        saveCompartmentNBT(aNBT);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        loadCompartmentNBT(aNBT);
    }

    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        // 手写三键，不调 super（近亲 hatch 族 setItemNBT 默认为空；罐内流体不保留）
        writeCompartmentItemNBT(aNBT);
    }

    // ===== 终端登记与渲染同步 =====

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        // 近亲链仅 MTEHatchInput.detectInventoryChange，无流体推送；保留基础 tick + 枢纽登记/渲染同步
        super.onPostTick(aBaseMetaTileEntity, aTick);
        onCompartmentHubTick(aBaseMetaTileEntity, aTick);
    }

    // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起 description packet 改走
    // writeToStream/readFromStream 流路径且基类 NBT 覆写点已删除（beta-3 编译目标下 @Override
    // 不再成立），本组 NBT 方法仅 beta-1/2 经运行时虚分派生效——去 @Override 去 super
    // （super 链 MTEHatch 只写/读 texturePage/textureIndex 两键，GTSR 无设置入口恒 0=0，
    // 两键随 super 移除而丢失=行为中性）。
    public NBTTagCompound getDescriptionData() {
        // 原 super 返回 null 后自 new 的防御保留为直接 new
        return writeCompartmentDescriptionData(new NBTTagCompound());
    }

    public void onDescriptionPacket(NBTTagCompound data) {
        readCompartmentDescriptionData(data);
    }

    // [GT-compat] beta-3 stream 同步：首行 super 承接 MTEHatch beta-3 覆写的
    // texturePage/textureIndex 两 int 承重，再委托接口助手补 bound+fluid 两字段
    // （字段序与 getDescriptionData 增量一致）；beta-1/2 基类无此覆写点，仅 beta-3 生效。
    @Override
    public void writeToStream(ByteBuf buf) {
        super.writeToStream(buf);
        writeCompartmentToStream(buf);
    }

    // [GT-compat] beta-3 stream 同步：读序与 writeToStream 严格对称
    // （super 两 int → bound bool → fluid UTF8 名）。
    @Override
    public void readFromStream(ByteBuf buf) {
        super.readFromStream(buf);
        readCompartmentFromStream(buf);
    }

    // ===== 正面流体窗 + 语义固定框架 =====

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection sideDirection,
        ForgeDirection facingDirection, int colorIndex, boolean active, boolean redstoneLevel) {
        ITexture[] kinTextures = super.getTexture(
            aBaseMetaTileEntity,
            sideDirection,
            facingDirection,
            colorIndex,
            active,
            redstoneLevel);
        return buildCompartmentTextures(kinTextures, sideDirection, facingDirection, colorIndex);
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        addCompartmentTooltip(tooltip);
    }
}
