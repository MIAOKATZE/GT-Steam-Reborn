package com.miaokatze.gtsr.common.machine;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.register.TextureManager;

import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import io.netty.buffer.ByteBuf;

/**
 * 奇点通用蒸汽仓（接收仓）：模式锁定 mIsOutputMode=true（枢纽→仓，从枢纽接受蒸汽），
 * 绑定蒸汽枢纽阵列（类型串 singularity_steam，消耗 1 奇点），
 * 容量 8,000,000 L、枢纽交互速率 8,000,000 L/s（固定常量，无速率档）、蒸汽全家族。
 * S1 起改继承近亲 MTEHatchPressureSteamInput（原 extends MTEFilteredCacheNode 量子缸链），
 * 绑定数据与终端链路经 MTESingularityCompartmentBase（接口+组合）保留。
 */
public class MTESingularitySteamCompartment extends MTEHatchPressureSteamInput
    implements MTESingularityCompartmentBase {

    /** S1 数值口径（D1+D3）：容量 8,000,000 L。 */
    private static final int CAPACITY = 8_000_000;

    /** 枢纽交互速率 8,000,000 L/s（固定常量，仓无速率档）。 */
    private static final int HUB_TRANSFER_RATE = 8_000_000;

    private final MTESingularityCompartmentBase.HubCompartmentState mHubState = new MTESingularityCompartmentBase.HubCompartmentState();

    public MTESingularitySteamCompartment(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTESingularitySteamCompartment(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        // gt++ 基类默认返回基类实例，必须覆写返回本类
        return new MTESingularitySteamCompartment(mName, mTier, mDescriptionArray, mTextures);
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
        return FluidRegistry.getFluid("steam");
    }

    @Override
    public String getFluidRangeTooltipKey() {
        return "gtsr.tooltip.singularity_compartment.fluid_steam";
    }

    @Override
    public String getBindTargetTooltipKey() {
        return "gtsr.tooltip.singularity_compartment.bind_target_steam";
    }

    @Override
    public int getBindingSingularityCost() {
        return 1;
    }

    @Override
    public boolean isFluidAllowed(Fluid fluid) {
        return MTESingularityCompartmentBase.isSteamFamily(fluid);
    }

    @Override
    public FluidStack getStoredFluidStackLocal() {
        return mFluid;
    }

    // ===== 容量与流体过滤 =====

    @Override
    public int getCapacity() {
        // 近亲构造链硬编码 512,000，此处覆写终值×容量档（S4）；继承的 MTEHatchPressureSteamInput.fill
        // 实时读本方法且自带负 space 防御（space<=0 拒绝入账）——降档即时拒新入，超额温和保留不销毁
        return (int) ((long) CAPACITY * getCapacityLimitPercent() / 100);
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        // 全蒸汽家族（近亲窄口径只认 steam/ic2superheatedsteam）
        return aFluid != null && aFluid.getFluid() != null && isFluidAllowed(aFluid.getFluid());
    }

    @Override
    public boolean doesEmptyContainers() {
        // 近亲 gt++ 默认 true：仓无 GUI/无容器交互，阻断漏斗投入容器的自动倒灌路径
        return false;
    }

    // ===== 仅枢纽交互：方向参数版 fill/drain 只放行 UNKNOWN（阻管道注入，T6 判据⑦）=====

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

    // ===== NBT 三处（gtsr.hubPos / gtsr.modeLocked / gtsr.singularity_consumed）=====

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
        // 近亲链无自动推送，保留基础 tick + 枢纽登记/渲染同步
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
