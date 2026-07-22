package com.miaokatze.gtsr.common.machine.base;

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

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
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
    // 是否已绑定到枢纽（独立于 mHubDim，避免主世界 dim=0 被误判为未绑定）
    protected boolean mBound = false;

    // 节点自定义名：按原版物品 display.Name NBT 结构对称存储（saveNBTData/setItemNBT/loadNBTData 三处），
    // 与 MTERemoteWorkerNode 同名机制一致；空串表示未自定义（UI 回退默认类型名）
    protected String mCustomName = "";

    private static final int[] TRANSFER_RATE_CYCLE = { 100, 80, 60, 40, 20, 10, 5, 1, 0 };

    protected abstract boolean isFluidAllowed(Fluid fluid);

    protected abstract int getBaseHubTransferRate();

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getCurrentEquippedItem();
            // 枢纽终端右击：循环传输速率（潜行右击切换输入/输出模式由 HubTerminal.onItemUseFirst 拦截处理）
            if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
                // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
                if (!mBound) {
                    GTUtility
                        .sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
                    return true;
                }
                // 循环逻辑抽为公共方法，供枢纽状态 UI 的「速率循环」按钮远程复用
                cycleTransferRatePercent();
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

    /**
     * 在 TRANSFER_RATE_CYCLE 中循环到下一档速率百分比，返回新百分比。
     * 供芯片右击与枢纽状态 UI 的「速率循环」按钮共用（行为与芯片右击一致）。
     */
    public int cycleTransferRatePercent() {
        int currentIdx = -1;
        for (int i = 0; i < TRANSFER_RATE_CYCLE.length; i++) {
            if (TRANSFER_RATE_CYCLE[i] == mTransferRatePercent) {
                currentIdx = i;
                break;
            }
        }
        int nextIdx = (currentIdx + 1) % TRANSFER_RATE_CYCLE.length;
        mTransferRatePercent = TRANSFER_RATE_CYCLE[nextIdx];
        return mTransferRatePercent;
    }

    /**
     * 设置枢纽交互方向模式（输出=节点→枢纽 / 输入=枢纽→节点）。
     * 已与父类 MTEDigitalTankBase 的自动输出开关 mOutputFluid 解耦：
     * 方向模式只管节点与枢纽之间的传输方向，自动输出（向正面相邻容器推送流体）
     * 由 isAutoOutput/setAutoOutput 独立控制，两者互不影响。
     * 调用方（枢纽侧）还需同步更新自身绑定记录（IHubArray.updateCacheNodeMode）。
     */
    public void setOutputMode(boolean output) {
        mIsOutputMode = output;
    }

    public boolean isOutputMode() {
        return mIsOutputMode;
    }

    /**
     * 枢纽终端潜行右击切换输入/输出模式：翻转 mIsOutputMode，
     * 同步绑定枢纽的注册记录（IHubArray.updateCacheNodeMode），并向玩家发送聊天反馈。
     * 必须在服务端调用（调用方已保证）；枢纽世界未加载或方块不存在时静默跳过同步。
     */
    public void toggleOutputModeFromTerminal(EntityPlayer player) {
        mIsOutputMode = !mIsOutputMode;
        // 解析绑定的枢纽并同步方向模式（解析写法与 registerWithHub 一致）
        World world = DimensionManager.getWorld(mHubDim);
        if (world != null && world.blockExists(mHubX, mHubY, mHubZ)) {
            TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
            if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof IHubArray hub) {
                hub.updateCacheNodeMode(
                    getBaseMetaTileEntity().getXCoord(),
                    getBaseMetaTileEntity().getYCoord(),
                    getBaseMetaTileEntity().getZCoord(),
                    getBaseMetaTileEntity().getWorld().provider.dimensionId,
                    mIsOutputMode);
            }
        }
        GTUtility.sendChatToPlayer(
            player,
            StatCollector.translateToLocal(
                mIsOutputMode ? "gtsr.cache_node.mode_output_now" : "gtsr.cache_node.mode_input_now"));
    }

    /** 是否已绑定到枢纽（供 HubTerminal 等跨包调用方判断，mBound 为 protected 字段）。 */
    public boolean isBoundToHub() {
        return mBound;
    }

    /** 自动输出开关：true 时节点向正面相邻容器自动推送流体（父类 mOutputFluid，已持久化）。 */
    public boolean isAutoOutput() {
        return isOutputFluid();
    }

    /** 设置自动输出开关（与方向模式 mIsOutputMode 解耦，互不影响）。 */
    public void setAutoOutput(boolean auto) {
        setOutputFluid(auto);
    }

    /** 当前存储流体的注册名（FluidRegistry 名）；无流体时返回空串。UI 侧按注册名本地化显示。 */
    public String getStoredFluidName() {
        return mFluid != null ? mFluid.getFluid()
            .getName() : "";
    }

    /** 当前存储量（long，强化/超压节点容量超出 int 范围）。 */
    public long getStoredFluidAmount() {
        return mFluid != null ? mFluid.amount : 0L;
    }

    /** 节点容量（long，强化/超压节点容量超出 int 范围）。 */
    public long getFluidCapacityLong() {
        return (long) getRealCapacity();
    }

    public String getCustomName() {
        return mCustomName == null ? "" : mCustomName;
    }

    public void setCustomName(String name) {
        this.mCustomName = name == null ? "" : name;
    }

    /**
     * GUI 窗口标题：有自定义名时优先显示自定义名，否则回退父类默认本地化名。
     * 与 MTERemoteWorkerNode.getLocalName 同理：MUI1 主窗口双端各自构建，
     * 客户端名字依赖 description packet 同步（见本类 getDescriptionData/onDescriptionPacket）。
     */
    @Override
    public String getLocalName() {
        return getCustomName().isEmpty() ? super.getLocalName() : getCustomName();
    }

    public long getEffectiveHubTransferRate() {
        return (long) getBaseHubTransferRate() * mTransferRatePercent / 100;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mIsOutputMode", mIsOutputMode);
        aNBT.setInteger("mTransferRatePercent", mTransferRatePercent);
        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
        if (mBound) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            // 与 loadNBTData 的反转读取语义对称：output 字段取反存储
            // loadNBTData 中 mIsOutputMode = !hubTag.getBoolean("output")
            // 故 save 时应 hubTag.setBoolean("output", !mIsOutputMode)
            hubTag.setBoolean("output", !mIsOutputMode);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
        // 自定义名：按原版物品 display.Name 结构写入（aNBT → display(compound) → Name(string)），三处对称
        if (!getCustomName().isEmpty()) {
            NBTTagCompound displayTag = new NBTTagCompound();
            displayTag.setString("Name", getCustomName());
            aNBT.setTag("display", displayTag);
        }
    }

    /**
     * 机器被破坏时，由 BaseMetaTileEntity.getDrops() 调用，用于把绑定数据写入掉落物的 NBT。
     * 默认实现（CommonMetaTileEntity.setItemNBT）为空，必须覆写才能让破坏后的物品保留 gtsr.hubPos 等绑定信息。
     * 必须先调用 super.setItemNBT 让 MTEDigitalTankBase 写入 mFluid/mLockFluid 等罐子数据。
     * output 字段语义与 saveNBTData 一致（反转存储），与 loadNBTData 的反转读取对称。
     */
    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        super.setItemNBT(aNBT);
        aNBT.setInteger("mTransferRatePercent", mTransferRatePercent);
        if (mBound) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            // 反转语义：与 saveNBTData 一致，与 loadNBTData 的反转读取对称
            hubTag.setBoolean("output", !mIsOutputMode);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
        // 保留奇点消耗标记，避免玩家通过破坏→重新放置来重复利用蒸汽纠缠奇点
        aNBT.setBoolean("gtsr.singularity_consumed", true);
        // 自定义名写入掉落物（原版 display.Name 结构）：物品栏直接显示自定义名，且铁砧改名走同一标签
        if (!getCustomName().isEmpty()) {
            NBTTagCompound displayTag = new NBTTagCompound();
            displayTag.setString("Name", getCustomName());
            aNBT.setTag("display", displayTag);
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
            // 物品NBT中 output=false 表示输出模式(节点→枢纽), output=true 表示输入模式(枢纽→节点)
            // mIsOutputMode=true 表示输出模式(节点→枢纽), mIsOutputMode=false 表示输入模式(枢纽→节点)
            // 语义一致：output字段的值取反即为mIsOutputMode的值
            if (hubTag.hasKey("output")) {
                mIsOutputMode = !hubTag.getBoolean("output");
            }
            // 已从 NBT 读取到绑定信息，标记为已绑定
            mBound = true;
        } else {
            mHubX = 0;
            mHubY = 0;
            mHubZ = 0;
            mHubDim = 0;
            mHubType = "";
            mIsOutputMode = true;
            mRegistered = false;
            // 无绑定信息，标记为未绑定
            mBound = false;
        }
        // 读取自定义名（null 防御：旧节点无 display 标签时回退空串）
        if (aNBT.hasKey("display")) {
            NBTTagCompound displayTag = aNBT.getCompoundTag("display");
            mCustomName = displayTag.hasKey("Name") ? displayTag.getString("Name") : "";
        } else {
            mCustomName = "";
        }
    }

    // ===== 自定义名客户端同步（description packet）=====
    // GT5U 机制：CommonBaseMetaTileEntity.getDescriptionPacket 调 IMetaTileEntity.getDescriptionData()，
    // 非 null 返回值作为 "mte" 标签随 S35PacketUpdateTileEntity 发出；客户端 onDataPacket 回调
    // onDescriptionPacket()。初始区块同步与 issueTileUpdate() 触发的重发都走此链路。
    // 注意必须始终返回非 null：返回 null 时客户端收不到回调，「清除自定义名」将无法同步到客户端。
    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        if (data == null) data = new NBTTagCompound();
        if (!getCustomName().isEmpty()) {
            data.setString("gtsr.customName", getCustomName());
        }
        return data;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        // 无 key 表示服务端已清除自定义名，回退空串（GUI 标题恢复默认本地化名）
        mCustomName = data.hasKey("gtsr.customName") ? data.getString("gtsr.customName") : "";
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
            boolean isOutput = hubTag.hasKey("output") && !hubTag.getBoolean("output");
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

        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
        if (!mRegistered && mBound) {
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

}
