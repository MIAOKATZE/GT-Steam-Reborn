package com.miaokatze.gtsr.common.machine;

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

import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.IHubArray;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsr.common.util.GTSRFluidWindowTexture;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.HubBindingUtil;
import com.miaokatze.gtsr.common.util.HubTeleportUtil;
import com.miaokatze.gtsr.register.TextureManager;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;

/**
 * 奇点仓共同契约（S1 重写）：方向模式锁定的枢纽缓存仓（四个实现 = 蒸汽收/发 + 流体收/发）。
 * <p>
 * 四仓各自改继承对应仓室近亲（蒸汽仓 extends MTEHatchPressureSteamInput、蒸汽输出仓 extends
 * MTEPressureSteamOutputHatch、流体输入仓 extends MTEWaterHubInputHatch、流体输出仓 extends
 * MTEWaterHubOutputHatch——四近亲不同根，无法统一 extends），本类型因此从抽象基类重写为
 * 「接口 + 默认实现 + 组合状态载体」，类名保留（枢纽 resolveCacheNodeType 的 instanceof 依赖）。
 * <p>
 * 承载并迁移自原 MTEFilteredCacheNode 链的仅限数据与终端链路：绑定字段组与 gtsr.hubPos NBT 三处读写
 * （output 反转语义不变）、终端登记 registerWithHub（20t 重试 + chunk 加载）、持终端右键解绑交互、
 * isOutputModeLocked()=true、正面流体窗渲染。缓存节点机制（GUI/速率档/顶面节点渲染/罐式管道交互/
 * 自动外排/mTransferRatePercent）一律不迁移。
 * <p>
 * 模式锁定语义（与枢纽 transferWithBoundNodes 的 isOutputMode 分支严格一致）：
 * 接收仓 mIsOutputMode=true → 枢纽抽取自身灌入仓（hub→仓）；发送仓 false → 枢纽从仓抽取存入自身
 * （仓→hub）。物品 NBT output 反转语义（mIsOutputMode=!output）：接收仓 output=false、发送仓 output=true。
 */
public interface MTESingularityCompartmentBase extends IHubCacheNode {

    // ===== 组合状态载体（四仓各持一份，默认实现经 getHubState() 读写）=====

    /**
     * 绑定/登记/客户端渲染状态的组合载体。
     * 服务端真值（hubPos 组/bound/registered）与客户端渲染副本（clientBound/clientFluidName，
     * description packet 同步）都在这里；getTexture 只读客户端副本。
     */
    class HubCompartmentState {

        public int hubX = 0;

        public int hubY = 0;

        public int hubZ = 0;

        public int hubDim = 0;

        public String hubType = "";

        public boolean bound = false;

        public boolean registered = false;

        /** 下次登记重试的最早 tick（登记失败后 20t 退避）。 */
        public long nextRegistrationTick = 0;

        /** 渲染状态同步去重 key（bound|fluid 拼接），服务端 tick 维护，变化才 issueTileUpdate。 */
        public String lastSyncKey = null;

        /** S4 容量上限档百分比（默认 100=基量；仅接收仓生效，发送仓不参与容量档）。 */
        public int capacityLimitPercent = 100;

        /** 枢纽传输速率档百分比（默认 100；四仓均支持九档）。 */
        public int transferRatePercent = 100;

        // 客户端渲染副本（description packet 同步）：默认值=未绑定外观（收到包前可接受）
        public boolean clientBound = false;

        public String clientFluidName = "";
    }

    // ===== 子类语义常量（四仓各自实现）=====

    /** 组合状态载体。 */
    HubCompartmentState getHubState();

    /**
     * 锁定的 mIsOutputMode 值（子类语义恒定）：
     * true=接收仓（枢纽→仓），false=发送仓（仓→枢纽），与 transferWithBoundNodes 分支方向一致。
     */
    boolean getLockedOutputMode();

    static boolean handleHubTerminalRateClick(IGregTechTileEntity gte, IHubCacheNode node, EntityPlayer aPlayer) {
        if (!gte.isServerSide()) return false;
        ItemStack held = aPlayer.getCurrentEquippedItem();
        if (held == null || !GTSRItemList.HubTerminal.isStackEqual(held, false, true)) return false;
        if (!node.isBoundToHub()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
            return true;
        }
        int percent = node.cycleTransferRatePercent();
        String msg = StatCollector.translateToLocal("gtsr.cache_node.transfer_rate") + " "
            + percent
            + "% ("
            + String.format("%,d", node.getEffectiveHubTransferRate())
            + " "
            + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s")
            + ")";
        GTUtility.sendChatToPlayer(aPlayer, msg);
        return true;
    }

    /** 基础枢纽交互速率 L/s（蒸汽两仓 8,000,000、流体两仓 256,000，速率档在 effective getter 生效）。 */
    int getBaseHubTransferRate();

    /** 正面/顶面语义固定框架图标：接收仓 HUB_FRAME_RECEIVE，发送仓 HUB_FRAME_SEND。 */
    IIconContainer getFrameIconContainer();

    /** 罐空时流体窗的枢纽类型默认流体：蒸汽枢纽系→蒸汽，流体枢纽系→水。 */
    Fluid getDefaultWindowFluid();

    /** tooltip 流体范围词条键（蒸汽全家族 / 任意流体）。 */
    String getFluidRangeTooltipKey();

    /** tooltip 绑定目标词条键（蒸汽枢纽阵列 / 蓄水枢纽阵列）。 */
    String getBindTargetTooltipKey();

    /** 绑定枢纽的奇点消耗（蒸汽两仓=1，流体两仓=0）。 */
    int getBindingSingularityCost();

    /** 流体范围判定（蒸汽全家族 / 任意流体）。 */
    boolean isFluidAllowed(Fluid fluid);

    /** 本地罐内流体（近亲链的 mFluid；不含任何 mController 转发语义）。 */
    FluidStack getStoredFluidStackLocal();

    /** 容量读数（四仓各自覆写为终值：蒸汽两仓 8,000,000、流体两仓 256,000；fill 实时读取）。 */
    int getCapacity();

    /** 蒸汽全家族判定（复用 MTESteamHubOutputHatch 既有静态口径，含致密/超临界变体）。 */
    static boolean isSteamFamily(FluidStack aFluid) {
        return MTESteamHubOutputHatch.isAnySteamFluid(aFluid);
    }

    static boolean isSteamFamily(Fluid fluid) {
        if (fluid == null) return false;
        return MTESteamHubOutputHatch.isAnySteamFluidType(fluid) || GTModHandler.isAnySteam(new FluidStack(fluid, 1))
            || GTModHandler.isSuperHeatedSteam(new FluidStack(fluid, 1));
    }

    // ===== IHubCacheNode 默认实现（仓侧语义）=====

    /** 方向模式恒为锁定值（loadNBTData 强制归位的等价实现：不落地可变字段，外部 NBT 篡改无效）。 */
    @Override
    default boolean isOutputMode() {
        return getLockedOutputMode();
    }

    /** 任何路径不得改写（枢纽侧按 isOutputModeLocked 拒改 GUI 模式按钮与右键翻转分支）。 */
    @Override
    default void setOutputMode(boolean output) {}

    /** 奇点仓方向模式恒定锁定。 */
    @Override
    default boolean isOutputModeLocked() {
        return true;
    }

    /** 终端潜行右击：锁定仓只发提示不切换。 */
    @Override
    default void toggleOutputModeFromTerminal(EntityPlayer player) {
        GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.cache_node.mode_locked"));
    }

    @Override
    default boolean isBoundToHub() {
        return getHubState().bound;
    }

    /** 奇点仓无自定义名机制（缓存节点 GUI 机制已随 S1 删除）。 */
    @Override
    default String getCustomName() {
        return "";
    }

    @Override
    default void setCustomName(String name) {}

    @Override
    default String getStoredFluidName() {
        FluidStack fluid = getStoredFluidStackLocal();
        return fluid != null && fluid.getFluid() != null ? fluid.getFluid()
            .getName() : "";
    }

    @Override
    default long getStoredFluidAmount() {
        FluidStack fluid = getStoredFluidStackLocal();
        return fluid != null ? fluid.amount : 0L;
    }

    @Override
    default long getFluidCapacityLong() {
        return getCapacity();
    }

    /** 当前枢纽传输速率档百分比（默认 100，兼容旧存档）。 */
    @Override
    default int getTransferRatePercent() {
        return getHubState().transferRatePercent;
    }

    /** 在共享九档速率值域中循环，越界值自愈归位首档 100。 */
    @Override
    default int cycleTransferRatePercent() {
        int[] cycle = IHubCacheNode.TRANSFER_RATE_CYCLE;
        HubCompartmentState s = getHubState();
        int currentIdx = -1;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == s.transferRatePercent) {
                currentIdx = i;
                break;
            }
        }
        s.transferRatePercent = cycle[(currentIdx + 1) % cycle.length];
        return s.transferRatePercent;
    }

    /** 仓固定基准速率按档位生效，使用 long 中间量防溢出。 */
    @Override
    default long getEffectiveHubTransferRate() {
        return (long) getBaseHubTransferRate() * getTransferRatePercent() / 100;
    }

    // ===== 容量上限档（S4，仅接收仓；发送仓罐只出不进、容量上限无意义）=====

    /** 容量档仅接收仓支持（getLockedOutputMode=true）；发送仓 false（循环 no-op、GUI 按钮禁用）。 */
    @Override
    default boolean supportsCapacityTier() {
        return getLockedOutputMode();
    }

    @Override
    default int getCapacityLimitPercent() {
        return getHubState().capacityLimitPercent;
    }

    /**
     * 在 CAPACITY_LIMIT_CYCLE 中循环到下一档容量百分比（越界自愈归 100，与缓存节点同款循环）。
     * 接收仓覆写的 getCapacity()/getCapacityLong() 实时读档位，降档即时拒新入；
     * 超额部分温和保留（近亲 fill 的负 space 防御拒绝入账，不销毁）。
     */
    @Override
    default int cycleCapacityLimitPercent() {
        if (!supportsCapacityTier()) return getCapacityLimitPercent();
        int[] cycle = IHubCacheNode.CAPACITY_LIMIT_CYCLE;
        HubCompartmentState s = getHubState();
        int currentIdx = -1;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == s.capacityLimitPercent) {
                currentIdx = i;
                break;
            }
        }
        s.capacityLimitPercent = cycle[(currentIdx + 1) % cycle.length];
        return s.capacityLimitPercent;
    }

    /** 自动外排机制已随 S1 删除。 */
    @Override
    default boolean isAutoOutput() {
        return false;
    }

    @Override
    default void setAutoOutput(boolean auto) {}

    // ===== NBT 读写链三处（gtsr 键兼容，与原 MTEFilteredCacheNode 链口径一致）=====

    /**
     * saveNBTData 增量：gtsr.modeLocked + gtsr.hubPos（子键 x/y/z/dim/type，output 反转语义：
     * 写入 !mIsOutputMode，读回 mIsOutputMode=!output——存档兼容）。
     */
    default void saveCompartmentNBT(NBTTagCompound aNBT) {
        HubCompartmentState s = getHubState();
        // 锁定状态持久化（子类语义恒定，写标记只为防御与外部工具可读）
        aNBT.setBoolean("gtsr.modeLocked", true);
        // S4 容量档（仅接收仓持久化；键名与缓存节点 mCapacityLimitPercent 对称）
        if (supportsCapacityTier()) aNBT.setInteger("mCapacityLimitPercent", s.capacityLimitPercent);
        aNBT.setInteger("mTransferRatePercent", s.transferRatePercent);
        if (s.bound) {
            // 反转语义：与 setItemNBT 一致，与读取侧的反转解读对称
            aNBT.setTag(
                "gtsr.hubPos",
                HubBindingUtil.createHubPosTag(s.hubX, s.hubY, s.hubZ, s.hubDim, s.hubType, !isOutputMode()));
        }
    }

    /**
     * loadNBTData 增量：读回 gtsr.hubPos 并重建登记握手（hub 可能在仓之后加载，服务端总是重试）。
     * output 键读取后不落地可变字段——mIsOutputMode 恒为锁定值（isOutputMode()），
     * 防御外部工具改 NBT（hubPos.output）解锁。
     */
    default void loadCompartmentNBT(NBTTagCompound aNBT) {
        HubCompartmentState s = getHubState();
        s.registered = false;
        s.nextRegistrationTick = 0;
        // S4 容量档读回（旧档/发送仓无键时保持默认 100，存档兼容）
        s.capacityLimitPercent = 100;
        if (supportsCapacityTier() && aNBT.hasKey("mCapacityLimitPercent")) {
            int capacity = aNBT.getInteger("mCapacityLimitPercent");
            for (int value : IHubCacheNode.CAPACITY_LIMIT_CYCLE) {
                if (value == capacity) {
                    s.capacityLimitPercent = capacity;
                    break;
                }
            }
        }
        // 速率档读回；旧档无键或非法值（不在档位表内）回退默认 100。
        s.transferRatePercent = 100;
        if (aNBT.hasKey("mTransferRatePercent")) {
            int rate = aNBT.getInteger("mTransferRatePercent");
            for (int r : IHubCacheNode.TRANSFER_RATE_CYCLE) {
                if (r == rate) {
                    s.transferRatePercent = rate;
                    break;
                }
            }
        }
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            s.hubX = hubTag.getInteger("x");
            s.hubY = hubTag.getInteger("y");
            s.hubZ = hubTag.getInteger("z");
            s.hubDim = hubTag.getInteger("dim");
            s.hubType = hubTag.getString("type");
            s.bound = true;
        } else {
            s.hubX = 0;
            s.hubY = 0;
            s.hubZ = 0;
            s.hubDim = 0;
            s.hubType = "";
            s.bound = false;
        }
    }

    /**
     * setItemNBT 全量（掉落物保留绑定）：手写三键 gtsr.hubPos / gtsr.modeLocked /
     * gtsr.singularity_consumed，不调 super 链——近亲 hatch 族 setItemNBT 默认为空，罐内流体不保留。
     */
    default void writeCompartmentItemNBT(NBTTagCompound aNBT) {
        HubCompartmentState s = getHubState();
        if (s.bound) {
            // 反转语义：与 saveNBTData 一致
            aNBT.setTag(
                "gtsr.hubPos",
                HubBindingUtil.createHubPosTag(s.hubX, s.hubY, s.hubZ, s.hubDim, s.hubType, !isOutputMode()));
        }
        aNBT.setBoolean("gtsr.modeLocked", true);
        // S4 容量档随掉落物保留（仅接收仓）
        if (supportsCapacityTier()) aNBT.setInteger("mCapacityLimitPercent", getHubState().capacityLimitPercent);
        aNBT.setInteger("mTransferRatePercent", getHubState().transferRatePercent);
        // 保留奇点消耗标记，避免玩家通过破坏→重新放置来重复利用蒸汽纠缠奇点
        aNBT.setBoolean("gtsr.singularity_consumed", true);
    }

    // ===== 客户端渲染同步（description packet）=====

    /** getDescriptionData 增量：正面流体窗渲染状态（绑定 + 罐内流体名，空串=罐空）。 */
    default NBTTagCompound writeCompartmentDescriptionData(NBTTagCompound data) {
        HubCompartmentState s = getHubState();
        data.setBoolean("gtsr.bound", s.bound);
        data.setString("gtsr.fluid", getStoredFluidName());
        return data;
    }

    /** onDescriptionPacket 增量：客户端渲染副本落地。 */
    default void readCompartmentDescriptionData(NBTTagCompound data) {
        HubCompartmentState s = getHubState();
        s.clientBound = data.getBoolean("gtsr.bound");
        s.clientFluidName = data.getString("gtsr.fluid");
        IGregTechTileEntity base = ((IMetaTileEntity) this).getBaseMetaTileEntity();
        if (base != null) {
            base.issueTextureUpdate();
        }
    }

    // ===== 客户端渲染同步（beta-3 stream：writeToStream/readFromStream 增量）=====

    /**
     * [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起 description packet 改走
     * writeToStream/readFromStream 流路径，本助手承担四仓 stream 增量（正面流体窗渲染状态）。
     * 字段序与 {@link #writeCompartmentDescriptionData} 两键一致（bound、fluid），与旧 NBT
     * 路径同样无条件写全（fluid 空串也写长度前缀，无省略哨兵），与读侧严格对称。
     */
    default void writeCompartmentToStream(ByteBuf buf) {
        HubCompartmentState s = getHubState();
        buf.writeBoolean(s.bound);
        ByteBufUtils.writeUTF8String(buf, getStoredFluidName());
    }

    /**
     * [GT-compat] beta 兼容层（beta1/beta2/beta3）：readFromStream 增量，客户端渲染副本落地。
     * 与 {@link #readCompartmentDescriptionData} 对称读（bound bool → fluid UTF8 名），但
     * 不调 issueTextureUpdate——beta-3 流路径的贴图刷新时机由基类统一负责，助手只落状态。
     */
    default void readCompartmentFromStream(ByteBuf buf) {
        HubCompartmentState s = getHubState();
        s.clientBound = buf.readBoolean();
        s.clientFluidName = ByteBufUtils.readUTF8String(buf);
    }

    // ===== 终端登记（源 MTEFilteredCacheNode#registerWithHub 整段迁移）=====

    /**
     * 服务端每 tick 的枢纽登记与渲染同步（四仓 onPostTick 调用，内部自判服务端）。
     * 登记失败 20t 重试、成功 600t 周期复查（hub 重建后绑定自动恢复）；绑定/流体类型变化才发 description packet（稳态零发包）。
     */
    default void onCompartmentHubTick(IGregTechTileEntity baseTE, long tick) {
        if (!baseTE.isServerSide()) return;
        HubCompartmentState s = getHubState();
        if (s.bound && tick >= s.nextRegistrationTick) {
            s.registered = registerCompartmentWithHub(baseTE);
            s.nextRegistrationTick = tick + (s.registered ? 600 : 20);
        }
        String syncKey = s.bound + "|" + getStoredFluidName();
        if (!syncKey.equals(s.lastSyncKey)) {
            s.lastSyncKey = syncKey;
            baseTE.issueTileUpdate();
        }
    }

    /** 解析绑定的枢纽并登记（含 chunk 加载与类型校验；解析写法与原链一致）。 */
    default boolean registerCompartmentWithHub(IGregTechTileEntity baseTE) {
        HubCompartmentState s = getHubState();
        World world = DimensionManager.getWorld(s.hubDim);
        if (world == null) return false;
        if (!HubTeleportUtil.ensureChunkLoaded(world, s.hubX, s.hubZ)) return false;
        if (!world.blockExists(s.hubX, s.hubY, s.hubZ)) return false;

        TileEntity te = world.getTileEntity(s.hubX, s.hubY, s.hubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return false;
        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return false;
        if (!hub.acceptsNodeType(s.hubType)) return false;

        hub.registerCacheNode(
            baseTE.getXCoord(),
            baseTE.getYCoord(),
            baseTE.getZCoord(),
            baseTE.getWorld().provider.dimensionId,
            isOutputMode());
        return true;
    }

    // ===== 渲染：正面三层 [近亲基材, 流体窗, 语义固定框架]；顶面 [近亲基材, 同款框架] =====

    /** 语义固定框架层（静态缓存，getTexture 仅客户端渲染路径调用，registerIcons 不必介入）。 */
    default ITexture getFixedFrameLayer() {
        // [GT-compat] 兜底降级说明：TextureManager 静态初始化失败时 HUB_FRAME_RECEIVE/SEND 同为 VOID 常量对象，
        // 本三元恒走 receive 分支——两分支同为透明纹理，渲染不可见，方向区分丢失属无感降级（详见 plan/beta-compat-audit.md B 节）。
        return getFrameIconContainer() == TextureManager.HUB_FRAME_RECEIVE
            ? TextureManager.getOrCreateTexture("gtsr.hub_frame_receive_layer", TextureManager.HUB_FRAME_RECEIVE)
            : TextureManager.getOrCreateTexture("gtsr.hub_frame_send_layer", TextureManager.HUB_FRAME_SEND);
    }

    /**
     * 流体窗内容：罐内流体优先，罐空回退枢纽类型默认（奇点流体仓的枢纽类型串
     * singularity_fluid_in/out 不含 "water"，需子类默认流体兜底）；未绑定也回退到枢纽类型默认流体，保证窗口始终有稳定默认材质。
     */
    default Fluid getClientWindowFluid() {
        HubCompartmentState s = getHubState();
        Fluid fluid = s.clientFluidName.isEmpty() ? null : FluidRegistry.getFluid(s.clientFluidName);
        return fluid != null ? fluid : getDefaultWindowFluid();
    }

    /**
     * 仓面纹理组装（四仓 getTexture 覆写传入近亲基类材质后调用）：
     * 顶面 [近亲基材, 框架]（无流体窗，俯视即可区分收/发仓）；正面三层
     * [近亲基材, 流体窗（罐内流体/枢纽类型默认，未绑定也使用默认流体）, 语义固定框架]；其余面近亲基类材质原样。
     * 底材结构成型时跟随结构，未成型回退 LV。
     * v1.10.89：底材经兼容层反射读取（beta-2 MTEHatch#getCasingTexture 为 beta-2-only 符号，
     * beta-1 无 ICasingTextureProvider 接口，恒回退 LV 机壳=v1.10.83 前行为）。
     */
    default ITexture[] buildCompartmentTextures(ITexture[] kinTextures, ForgeDirection side, ForgeDirection facing,
        int colorIndex) {
        ITexture baseTexture = GTVersionCompat.getCasingTextureOrNull(this);
        if (baseTexture == null) baseTexture = Textures.BlockIcons.MACHINE_CASINGS[1][colorIndex + 1];
        if (side == ForgeDirection.UP) {
            return new ITexture[] { baseTexture };
        }
        if (side == facing) {
            return new ITexture[] { baseTexture, GTSRFluidWindowTexture.getOrCreate(getClientWindowFluid()),
                getFixedFrameLayer() };
        }
        if (kinTextures == null || kinTextures.length == 0) return new ITexture[] { baseTexture };
        ITexture[] textures = kinTextures.clone();
        textures[0] = baseTexture;
        return textures;
    }

    // ===== tooltip（原 MTESingularityCompartmentBase#addAdditionalTooltipInformation 迁移）=====

    /** 四仓 addAdditionalTooltipInformation 覆写调用（在近亲行之后追加仓语义行）。 */
    default void addCompartmentTooltip(List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.singularity_compartment.mode")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal(
                    getLockedOutputMode() ? "gtsr.tooltip.singularity_compartment.direction_receive"
                        : "gtsr.tooltip.singularity_compartment.direction_send"));
        tooltip.add(
            EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("gtsr.tooltip.singularity_compartment.mode_locked"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.fluid_type")
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal(getFluidRangeTooltipKey()));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.output_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", getBaseHubTransferRate())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.capacity")
                + EnumChatFormatting.GOLD
                + String.format("%,d", getFluidCapacityLong())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l"));
        if (getBindingSingularityCost() > 0) {
            tooltip.add(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_compartment.bind_cost_one"));
        } else {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_compartment.bind_cost_free"));
        }
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal(getBindTargetTooltipKey()));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_hint"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.bind_all_hint"));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.cache_node_hub_transfer"));
        tooltip.add(GTSRUtils.getAddedByLine());
    }
}
