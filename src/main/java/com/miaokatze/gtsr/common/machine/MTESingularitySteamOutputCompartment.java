package com.miaokatze.gtsr.common.machine;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.register.TextureManager;

import gregtech.GTMod;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;

/**
 * 奇点通用蒸汽输出仓（发送仓）：模式锁定 mIsOutputMode=false（仓→枢纽，输出到枢纽），
 * 绑定蒸汽枢纽阵列（类型串 singularity_steam_out，消耗 1 奇点），
 * 容量 8,000,000 L、枢纽交互速率 8,000,000 L/s（固定常量，无速率档）、蒸汽全家族。
 * S1 起改继承近亲 MTEPressureSteamOutputHatch（原 extends MTEFilteredCacheNode 量子缸链），
 * 绑定数据与终端链路经 MTESingularityCompartmentBase（接口+组合）保留。
 */
public class MTESingularitySteamOutputCompartment extends MTEPressureSteamOutputHatch
    implements MTESingularityCompartmentBase {

    /** S1 数值口径（D1+D3）：容量 8,000,000 L。 */
    private static final int CAPACITY = 8_000_000;

    /** 枢纽交互速率 8,000,000 L/s（固定常量，仓无速率档）。 */
    private static final int HUB_TRANSFER_RATE = 8_000_000;

    private final MTESingularityCompartmentBase.HubCompartmentState mHubState = new MTESingularityCompartmentBase.HubCompartmentState();

    public MTESingularitySteamOutputCompartment(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        resetKinCasingTextureToUnset();
    }

    public MTESingularitySteamOutputCompartment(String aName, int aTier, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        resetKinCasingTextureToUnset();
    }

    /**
     * 复位近亲构造链反射写入的机壳贴图：MTEPressureSteamOutputHatch 两构造器均经反射将
     * MTEHatch texturePage/textureIndex 写为坚实钢机壳（sBlockCasings2 meta0），令本仓底材偏离
     * 其余三奇点仓。MTEHatch 自身构造器将两字段初始化为 0（即"从未调用贴图写入"的未设态），
     * beta-2 MTEHatch#getCasingTexture 判 texturePage&gt;0||textureIndex&gt;0 否则返回 null →
     * GTVersionCompat.getCasingTextureOrNull 透传 null → buildCompartmentTextures 回退
     * MACHINE_CASINGS[1][colorIndex+1]（LV 机壳），与三仓一致。反射习语沿用近亲
     * setPressureDefaultTextureIndex（字段私有且无版本安全 setter，仅构造期一次性复位）。
     */
    private void resetKinCasingTextureToUnset() {
        try {
            Field texturePageField = MTEHatch.class.getDeclaredField("texturePage");
            texturePageField.setAccessible(true);
            texturePageField.setInt(this, 0);

            Field textureIndexField = MTEHatch.class.getDeclaredField("textureIndex");
            textureIndexField.setAccessible(true);
            textureIndexField.setInt(this, 0);
        } catch (Exception ignored) {}
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularitySteamOutputCompartment(mName, mTier, mDescriptionArray, mTextures);
    }

    // ===== MTESingularityCompartmentBase 语义实现 =====

    @Override
    public MTESingularityCompartmentBase.HubCompartmentState getHubState() {
        return mHubState;
    }

    /** 发送仓：锁定 mIsOutputMode=false（transferWithBoundNodes 的 else 分支=枢纽从本仓抽取存入枢纽）。 */
    @Override
    public boolean getLockedOutputMode() {
        return false;
    }

    @Override
    public int getBaseHubTransferRate() {
        return HUB_TRANSFER_RATE;
    }

    @Override
    public IIconContainer getFrameIconContainer() {
        return TextureManager.HUB_FRAME_SEND;
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

    // ===== 容量与外部输入阻断（先例 MTEPressureSteamOutputHatch "No External Input Allowed"）=====

    @Override
    public int getCapacity() {
        // 近亲为 1,024,000，此处覆写终值
        return CAPACITY;
    }

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean acceptsFluid(FluidStack aFluid) {
        return false;
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

    // ===== 终端登记与渲染同步（不调 super 推送段：输出仓仅枢纽交互）=====

    /**
     * 不调用 super.onPostTick——近亲链三段自动推送（MTEHatchOutput 全量推正面罐、
     * MTESteamOutputHatch 6,400/t、MTEPressureSteamOutputHatch 51,200/t 定速推）全部跳过；
     * 其上仅剩 CommonMetaTileEntity 层的客户端隐藏管道纹理更新检测，此处等价保留。
     */
    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isClientSide() && GTMod.clientProxy()
            .changeDetected() == 4) {
            aBaseMetaTileEntity.issueTextureUpdate();
        }
        onCompartmentHubTick(aBaseMetaTileEntity, aTick);
    }

    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        if (data == null) data = new NBTTagCompound();
        return writeCompartmentDescriptionData(data);
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        readCompartmentDescriptionData(data);
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
