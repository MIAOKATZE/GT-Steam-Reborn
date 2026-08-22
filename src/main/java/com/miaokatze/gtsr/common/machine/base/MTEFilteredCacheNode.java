package com.miaokatze.gtsr.common.machine.base;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
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
import net.minecraftforge.fluids.IFluidHandler;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTEFilteredCacheNodeGui;
import com.miaokatze.gtsr.common.util.GTSRFluidWindowTexture;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.HubTeleportUtil;
import com.miaokatze.gtsr.register.TextureManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

/**
 * 量子缸族缓存节点基类。S1 起实现 {@link IHubCacheNode} 共同接口：四个奇点仓已脱离本继承链
 * （改继承各自仓室近亲并实现该接口），两枢纽与 HubTerminal 的节点链路统一面向 IHubCacheNode。
 */
public abstract class MTEFilteredCacheNode extends MTEDigitalTankBase implements IHubCacheNode {

    public MTEFilteredCacheNode(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEFilteredCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public com.cleanroommc.modularui.screen.ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager syncManager,
        com.cleanroommc.modularui.screen.UISettings uiSettings) {
        return new MTEFilteredCacheNodeGui(this).build(data, syncManager, uiSettings);
    }

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;
    protected int mTransferRatePercent = 100;
    private long mNextRegistrationTick;
    // 是否已绑定到枢纽（独立于 mHubDim，避免主世界 dim=0 被误判为未绑定）
    protected boolean mBound = false;

    // 节点自定义名：按原版物品 display.Name NBT 结构对称存储（saveNBTData/setItemNBT/loadNBTData 三处），
    // 与 MTERemoteWorkerNode 同名机制一致；空串表示未自定义（UI 回退默认类型名）
    protected String mCustomName = "";

    // 顶面流体窗+枢纽框架层的客户端渲染状态副本（description packet 同步，见下方同步段）：
    // 服务端真值 mBound/mIsOutputMode/mHubType/罐内流体不经普通同步到达客户端，getTexture 只读本组字段；
    // 默认值=未绑定外观（未收到包前可接受）。mIsOutputMode=true 表示从枢纽接受（接收模式，枢纽→节点）
    protected boolean mClientBound = false;
    protected boolean mClientOutputMode = true;
    protected String mClientHubType = "";
    protected String mClientFluidName = "";

    /** 渲染状态同步去重 key（bound|out|fluid 拼接），服务端 onPostTick 维护，变化才 issueTileUpdate。 */
    private String mLastSyncKey = null;

    /** 传输速率档位单源于 IHubCacheNode，缓存节点与奇点仓共用。 */
    private static final int[] TRANSFER_RATE_CYCLE = IHubCacheNode.TRANSFER_RATE_CYCLE;

    /** S4 容量上限档：100 → 80 → 60 → 40 → 20 → 10 → 5 → 回 100（值域单源见 IHubCacheNode）。 */
    protected int mCapacityLimitPercent = 100;

    protected abstract boolean isFluidAllowed(Fluid fluid);

    protected abstract int getBaseHubTransferRate();

    /**
     * 节点 tooltip「流体类型」行的本地化 key（SR-OPT-02：原六变体同构 tooltip 6→1 上提后的变体钩子，
     * 每个变体只给类型文案 key，公共行模板见 {@link #addAdditionalTooltipInformation}）。
     */
    protected abstract String getFluidTypeTooltipLangKey();

    /**
     * 变体专属 tooltip 行（SR-OPT-02 变体钩子）：插在公共「流体类型/输出速率/容量」三行之后、
     * 四行通用绑定提示之前。默认空（基础蒸汽节点无额外行）；强化/超压/通用流体节点覆写补
     * 奇点消耗、绑定要求、绑定目标等行。
     */
    protected void addVariantTooltipLines(List<String> tooltip) {}

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getCurrentEquippedItem();
            // 枢纽终端右击：循环传输速率（Shift 右击容量档由 HubTerminal.onItemUse 服务端权威处理）
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

    // ===== S4 容量上限档（NBT 键 mCapacityLimitPercent，与 mTransferRatePercent 对称）=====

    @Override
    public boolean supportsCapacityTier() {
        return true;
    }

    @Override
    public int getCapacityLimitPercent() {
        return mCapacityLimitPercent;
    }

    /**
     * 在 CAPACITY_LIMIT_CYCLE 中循环到下一档容量百分比，返回新百分比（仿 cycleTransferRatePercent
     * 越界自愈：当前值不在值域时归位首档 100）。
     * 供空手 Shift+右击（HubTerminal 事件拦截）与枢纽状态 UI 的「容量循环」按钮共用。
     */
    @Override
    public int cycleCapacityLimitPercent() {
        int[] cycle = IHubCacheNode.CAPACITY_LIMIT_CYCLE;
        int currentIdx = -1;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == mCapacityLimitPercent) {
                currentIdx = i;
                break;
            }
        }
        int nextIdx = (currentIdx + 1) % cycle.length;
        mCapacityLimitPercent = cycle[nextIdx];
        return mCapacityLimitPercent;
    }

    /**
     * 容量基量（档位乘法前）：默认 MTEDigitalTankBase 的 tier 算式（commonSizeCompute(mTier)），
     * 六个节点子类各自覆写返回硬编码 CAPACITY 常量。乘法统一在本类 {@link #getRealCapacity()}。
     */
    public int getBaseRealCapacity() {
        return super.getRealCapacity();
    }

    /**
     * 生效容量 = 基量 × 容量档百分比 / 100（long 中间量防溢出；超 int 上限钳 Integer.MAX_VALUE）。
     * 父类 getCapacity/getInfo/getTankInfo 与 fill 空间计算实时读本方法，降档即时生效。
     */
    @Override
    public int getRealCapacity() {
        long capped = (long) getBaseRealCapacity() * mCapacityLimitPercent / 100;
        return capped > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capped;
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
     * 方向模式是否锁定（语义恒定节点覆写为 true，如奇点仓四件套）。
     * 锁定后 setOutputMode/toggleOutputModeFromTerminal 拒改、loadNBTData 强制归位，
     * 枢纽侧据此拒改 GUI 模式按钮与右键翻转分支。默认 false（普通节点可自由切换）。
     */
    public boolean isOutputModeLocked() {
        return false;
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
                mIsOutputMode ? "gtsr.cache_node.mode_input_now" : "gtsr.cache_node.mode_output_now"));
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

    /** 节点容量（long，强化/超压节点容量超出 int 范围）：基量×容量档的精确乘后值（不经 int 钳位）。 */
    public long getFluidCapacityLong() {
        return (long) getBaseRealCapacity() * mCapacityLimitPercent / 100;
    }

    /**
     * 温和保留防御（S4）：降档后罐内存量可能超过当前容量上限（负 space），父类
     * MTEDigitalTankBase.fill 的 Math.min 会把负 space 入账（销毁超额）——此处在负/零 space 时
     * 拒绝新入，超额部分保留罐内不销毁。所有 fill 路径（含方向参数版）最终汇入本两参版。
     */
    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        FluidStack fillable = getFillableStack();
        if (fillable != null && (long) getRealCapacity() - fillable.amount <= 0) return 0;
        return super.fill(aFluid, doFill);
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

    // ===== 顶面流体窗 + 枢纽框架层（客户端渲染）=====
    // 框架层 ITexture（HUB_FRAME_* 为 customAlpha pass1 图标容器）静态缓存一份，getTexture 复用勿每调用 new；
    // registerIcons（仅客户端）时构造，服务端类加载不触碰渲染类
    private static ITexture FRAME_RECEIVE_LAYER;
    private static ITexture FRAME_SEND_LAYER;
    private static ITexture FRAME_UNBOUND_LAYER;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        if (FRAME_UNBOUND_LAYER == null) {
            FRAME_RECEIVE_LAYER = TextureFactory.of(TextureManager.HUB_FRAME_RECEIVE);
            FRAME_SEND_LAYER = TextureFactory.of(TextureManager.HUB_FRAME_SEND);
            FRAME_UNBOUND_LAYER = TextureFactory.of(TextureManager.HUB_FRAME_UNBOUND);
        }
        super.registerIcons(aBlockIconRegister);
    }

    /**
     * 顶面流体窗内容（纯客户端副本路径，S2 起与 MTESingularityCompartmentBase#getClientWindowFluid 同构）：
     * 罐内流体优先，罐空走 {@link #getDefaultWindowFluid()} 兜底。与奇点仓成功链的根因差异：
     * 旧版空罐兜底只有 hubType contains 字符串解析一层，解析不出即返回 null（窗层自跳过）——
     * 绑定后空罐的窗口流体没有任何非空保证（hubType 实为节点自身类型串，缺 type 键的旧档/异常路径
     * 得空串，静默无窗）。未绑定也使用家族默认流体。
     */
    protected Fluid getClientWindowFluid() {
        Fluid fluid = mClientFluidName.isEmpty() ? null : FluidRegistry.getFluid(mClientFluidName);
        return fluid != null ? fluid : getDefaultWindowFluid();
    }

    /**
     * 罐空兜底（三段式，S2 对齐奇点仓 getDefaultWindowFluid 成功先例）：
     * ① 类型串解析：含 steam→蒸汽、含 water→水（六节点现值类型串全命中）；
     * ② 家族默认：子类静态常量（{@link #getFamilyDefaultWindowFluid}），类型串缺失/未知时兜住。
     * 保证绑定或未绑定时空罐均显示家族默认流体窗。
     */
    protected Fluid getDefaultWindowFluid() {
        if (mClientHubType != null) {
            if (mClientHubType.contains("steam")) return FluidRegistry.getFluid("steam");
            if (mClientHubType.contains("water")) return FluidRegistry.WATER;
        }
        return getFamilyDefaultWindowFluid();
    }

    /**
     * 节点家族默认窗流体（罐空且类型串解析不出的终层兜底）。
     * 家族无法从 isFluidAllowed 推导（通用流体节点已放宽为恒真），必须子类静态给出：
     * 蒸汽三节点→蒸汽、通用流体三节点→水（与蓄水枢纽阵列系奇点仓的默认流体口径一致）。
     */
    protected abstract Fluid getFamilyDefaultWindowFluid();

    /**
     * 顶面三层纹理：未绑定→[基材, 家族默认窗, 未绑框架]；绑定→
     * [基材, 流体窗, 接收/发送框架]（接收模式=从枢纽接受→RECEIVE，输出模式→SEND）。
     * 绑定分支的窗流体经三段兜底（罐内→类型串→家族默认）恒非空（steam 注册缺失等极端情况才自跳过）。
     */
    protected ITexture[] getTopFaceTextures(ITexture baseTexture) {
        if (!mClientBound) {
            return new ITexture[] { baseTexture, GTSRFluidWindowTexture.getOrCreate(getClientWindowFluid()),
                FRAME_UNBOUND_LAYER };
        }
        return new ITexture[] { baseTexture, GTSRFluidWindowTexture.getOrCreate(getClientWindowFluid()),
            mClientOutputMode ? FRAME_RECEIVE_LAYER : FRAME_SEND_LAYER };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mIsOutputMode", mIsOutputMode);
        aNBT.setInteger("mTransferRatePercent", mTransferRatePercent);
        aNBT.setInteger("mCapacityLimitPercent", mCapacityLimitPercent);
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
        aNBT.setInteger("mCapacityLimitPercent", mCapacityLimitPercent);
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
        // The hub may load after this node. Always rebuild the registration handshake on the server.
        mRegistered = false;
        mNextRegistrationTick = 0;
        mIsOutputMode = aNBT.hasKey("mIsOutputMode") ? aNBT.getBoolean("mIsOutputMode") : true;
        // 速率档读回；旧档无键或非法值（不在档位表内）回退默认 100
        mTransferRatePercent = 100;
        if (aNBT.hasKey("mTransferRatePercent")) {
            int rate = aNBT.getInteger("mTransferRatePercent");
            for (int r : TRANSFER_RATE_CYCLE) {
                if (r == rate) {
                    mTransferRatePercent = rate;
                    break;
                }
            }
        }
        // 旧档无容量档键或非法值时回退默认 100，避免容量计算越界。
        mCapacityLimitPercent = 100;
        if (aNBT.hasKey("mCapacityLimitPercent")) {
            int capacity = aNBT.getInteger("mCapacityLimitPercent");
            for (int value : IHubCacheNode.CAPACITY_LIMIT_CYCLE) {
                if (value == capacity) {
                    mCapacityLimitPercent = capacity;
                    break;
                }
            }
        }
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            mHubX = hubTag.getInteger("x");
            mHubY = hubTag.getInteger("y");
            mHubZ = hubTag.getInteger("z");
            mHubDim = hubTag.getInteger("dim");
            mHubType = hubTag.getString("type");
            // 物品NBT中 output=false 表示从枢纽输出（枢纽→节点），output=true 表示向枢纽输入（节点→枢纽）
            // mIsOutputMode=true 表示接收模式（枢纽→节点），mIsOutputMode=false 表示发送模式（节点→枢纽）
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
        // 渲染状态（顶面流体窗+框架层）：绑定/方向模式/枢纽类型/罐内流体名（空串=罐空）
        data.setBoolean("gtsr.bound", mBound);
        data.setBoolean("gtsr.out", mIsOutputMode);
        data.setString("gtsr.hubType", mHubType == null ? "" : mHubType);
        data.setString("gtsr.fluid", getStoredFluidName());
        return data;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        // 无 key 表示服务端已清除自定义名，回退空串（GUI 标题恢复默认本地化名）
        mCustomName = data.hasKey("gtsr.customName") ? data.getString("gtsr.customName") : "";
        // 渲染状态副本回读落地（getTexture 只读这组字段）
        mClientBound = data.getBoolean("gtsr.bound");
        mClientOutputMode = data.getBoolean("gtsr.out");
        mClientHubType = data.getString("gtsr.hubType");
        mClientFluidName = data.getString("gtsr.fluid");
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.issueTextureUpdate();
        }
    }

    @Override
    public final void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.cache_node.base_transfer_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", getBaseHubTransferRate())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.cache_node.chip_adjust"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_kept_on_drop"));
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
        // ===== 节点族公共段（SR-OPT-02：原六变体 addAdditionalTooltipInformation 同构段 6→1 上提）=====
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal(getFluidTypeTooltipLangKey()));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.output_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", getBaseHubTransferRate())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", getRealCapacity())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"));
        // 变体专属行（奇点消耗/绑定要求/绑定目标等），默认空
        addVariantTooltipLines(tooltip);
        // 通用绑定提示尾段（六变体一致）
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.cache_node_standalone"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.cache_node_hub_transfer"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_hint"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_all_hint"));
        tooltip.add(GTSRUtils.getAddedByLine());
    }

    @Override
    public final void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定；成功登记每 600t 周期复查
        if (mBound && aTick >= mNextRegistrationTick) {
            mRegistered = registerWithHub(aBaseMetaTileEntity);
            mNextRegistrationTick = aTick + (mRegistered ? 600 : 20);
        }

        // 渲染状态同步：绑定/方向模式/流体类型任一变化才发 description packet（覆盖绑定/解绑/模式切换/
        // 流体类型变化/清空全部路径）；节点每 20t 传输只变量不触发，正常稳态零发包
        String syncKey = mBound + "|" + mIsOutputMode + "|" + getStoredFluidName();
        if (!syncKey.equals(mLastSyncKey)) {
            mLastSyncKey = syncKey;
            aBaseMetaTileEntity.issueTileUpdate();
        }

        // ===== 自动排出公共模板（SR-OPT-02：原六变体 onPostTick 同构块 6→1 上提，行序与原实现一致）=====
        // 每 20t 向正面相邻容器排出 getBaseHubTransferRate() 的量（六变体的自动排出速率与枢纽基础
        // 传输速率本就同值，上提后单源防数值漂移）；mOutputFluid 为父类自动输出开关。
        if (mOutputFluid && getDrainableStack() != null && (aTick % 20 == 0)) {
            IFluidHandler tTank = aBaseMetaTileEntity.getITankContainerAtSide(aBaseMetaTileEntity.getFrontFacing());
            if (tTank != null) {
                FluidStack tDrained = drain(getBaseHubTransferRate(), false);
                if (tDrained != null) {
                    int tFilledAmount = tTank.fill(aBaseMetaTileEntity.getBackFacing(), tDrained, false);
                    if (tFilledAmount > 0)
                        tTank.fill(aBaseMetaTileEntity.getBackFacing(), drain(tFilledAmount, true), true);
                }
            }
        }
    }

    private boolean registerWithHub(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null) return false;
        if (!HubTeleportUtil.ensureChunkLoaded(world, mHubX, mHubZ)) return false;
        if (!world.blockExists(mHubX, mHubY, mHubZ)) return false;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return false;

        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return false;

        if (!hub.acceptsNodeType(mHubType)) return false;

        hub.registerCacheNode(
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord(),
            aBaseMetaTileEntity.getWorld().provider.dimensionId,
            mIsOutputMode);
        return true;
    }

    /**
     * 机器信息面板模板（SR-O2-10：原六变体 getInfoData 六份近似实现 6→1 上提 final 模板，
     * 方法体与变体实现逐字一致）。容量读数统一走 {@link #getRealCapacity()} 跟随容量档——
     * 水族旧口径（读 CAPACITY 常量）已由 SR-B2-01 修复，上提后单源，同类漂移不可再发生
     * （O2-09 由本项吸收闭环）。
     */
    @Override
    public final String[] getInfoData() {
        String nameKey = "gt.blockmachines." + mName + ".name";
        if (mFluid == null) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal(nameKey) + EnumChatFormatting.RESET,
                StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid"),
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid.empty")
                    + EnumChatFormatting.RESET,
                EnumChatFormatting.GREEN + "0 L"
                    + EnumChatFormatting.RESET
                    + " "
                    + EnumChatFormatting.YELLOW
                    + formatNumber(getRealCapacity())
                    + " L"
                    + EnumChatFormatting.RESET };
        }
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(nameKey) + EnumChatFormatting.RESET,
            StatCollector.translateToLocal("GT5U.infodata.digital_tank.stored_fluid"),
            EnumChatFormatting.GOLD + mFluid.getLocalizedName() + EnumChatFormatting.RESET,
            EnumChatFormatting.GREEN + formatNumber(mFluid.amount)
                + " L"
                + EnumChatFormatting.RESET
                + " "
                + EnumChatFormatting.YELLOW
                + formatNumber(getRealCapacity())
                + " L"
                + EnumChatFormatting.RESET };
    }

}
