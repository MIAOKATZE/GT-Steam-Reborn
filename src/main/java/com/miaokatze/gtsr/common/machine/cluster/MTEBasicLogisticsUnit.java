package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.gui.cluster.ClusterTerminalUiFactory;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 物流模块：集群的单点链执行器（本切片只落骨架——链持有与双流体 tank 分发；链执行在后续批次接入）。
 * <p>
 * 每个物流模块恰好持有 1 条有序链（{@link LogisticsChain}，默认空链，永非 null）。
 * <p>
 * 流体模型：MTEBasicTank 只有单一 mFluid 主 tank，本类将其弃用（getFillableStack/getDrainableStack 钉
 * null、容器倒换关闭），自持两个独立 {@link FluidTank}（容量 {@link ClusterParams#LOGISTICS_TANK_CAPACITY_L}）：
 * <ul>
 * <li>{@link #getWaterTank()}——只接受水（洗矿 link 批流体）；</li>
 * <li>{@link #getChemBathTank()}——通用 tank，接受非水流体（化洗 link 按实际配方匹配的化浴液）。</li>
 * </ul>
 * 分发点按 MTEBasicTank 源码实读：所有填充路径（管道→BaseMetaTileEntity.fill(side,…)→fill_default→
 * fill(FluidStack,boolean)）收口于 {@link #fill(FluidStack, boolean)}，在此按水/非水路由；
 * 放出侧覆写 {@link #drain(int, boolean)}（化洗优先）与类型敏感 drain(ForgeDirection,FluidStack,int,boolean)
 * （按请求流体匹配对应 tank）；{@link #getTankInfo(ForgeDirection)} 返回双 tank 信息供管道/ME 交互。
 * <p>
 * tank 与链的 NBT 持久化在本类自落（{@link #saveNBTData}）：链存 "clusterChain" int 数组，
 * 双 tank 存 "clusterWaterTank"/"clusterChemTank"（基类 MTEBasicTank 只持久化 mFluid，自持 FluidTank
 * 不入基类 NBT）；链有效性细化仍留 M4 批接入。
 * 手工容器交互沿承基类语义（关闭）：onPreTick 的桶装路径会直写 setFillableStack 绕过双 tank 分发，
 * 必须保持关闭；纹理亦直接继承基类青铜机器三面。
 * 类型名 key：gtsr.gui.cluster.unit_type.logistics。
 */
public class MTEBasicLogisticsUnit extends MTEClusterUnitBase<MTEBasicLogisticsUnit> {

    /** 本模块的有序链（永非 null；setChain(null) 亦只置空链）。 */
    private LogisticsChain chain = new LogisticsChain();

    /** 水 tank：仅接受水（洗矿 link 每批 1000L 从此扣）。 */
    private final FluidTank waterTank = new FluidTank(ClusterParams.LOGISTICS_TANK_CAPACITY_L);

    /** 化洗 tank：接受非水流体（化洗 link 按实际配方匹配的化浴液）。 */
    private final FluidTank chemBathTank = new FluidTank(ClusterParams.LOGISTICS_TANK_CAPACITY_L);

    public MTEBasicLogisticsUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEBasicLogisticsUnit(String aName) {
        super(aName);
    }

    @Override
    protected String[][] getUnitShape() {
        return new String[][] { { "AAA", "AAA", "A~A", "AAA" }, { "CAC", "C C", "CAC", "ABA" },
            { "AAA", "AAA", "AAA", "ABA" }, };
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void addUnitStructureElements(StructureDefinition.Builder builder) {
        addPipeElement(builder);
        builder.addElement(
            'C',
            com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock(GregTechAPI.sBlockFrames, 300));
    }

    @Override
    protected int getStructureOffsetA() {
        return 1;
    }

    @Override
    protected int getStructureOffsetB() {
        return 2;
    }

    @Override
    protected int getStructureOffsetC() {
        return 0;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBasicLogisticsUnit(mName);
    }

    /** 朝向透传（供 'L' 槽结构校验取正向）：等价 getBaseMetaTileEntity().getFrontFacing()。 */
    public ForgeDirection getFrontFacing() {
        return getBaseMetaTileEntity().getFrontFacing();
    }

    /** 判水：与 FluidRegistry.WATER 同体或名字相等（防他 mod 替换注册实例）。 */
    private static boolean isWater(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        Fluid fluid = aFluid.getFluid();
        return fluid == FluidRegistry.WATER || "water".equals(fluid.getName());
    }

    public FluidTank getWaterTank() {
        return waterTank;
    }

    public FluidTank getChemBathTank() {
        return chemBathTank;
    }

    public LogisticsChain getChain() {
        return chain;
    }

    /** 整链替换（编辑器/预设载入用）；null 安全——置为空链，保证字段永非 null。 */
    public void setChain(LogisticsChain aChain) {
        this.chain = (aChain != null) ? aChain : new LogisticsChain();
    }

    @Override
    public String getUnitTypeNameKey() {
        return "gtsr.gui.cluster.unit_type.logistics";
    }

    /** 单元 tooltip：链执行说明 + 双流体 tank + 右击入口提示（键见 gtsr.tooltip.cluster.unit.logistics.*）。 */

    /**
     * 右击分流（空手/持任意物品含枢纽终端皆同）：服务端打开集群终端并固定落到链路编辑页
     * （{@link ClusterTerminalUiFactory#PAGE_CHAIN_EDIT}），双端均返回 true 消费事件。
     * 空手右击不再走基类普通机器 GUI——物流模块自身无独立 GUI，集群终端即其操作界面；
     * 总控侧空手右击仍走标准 GUI，不冲突。
     * 注：潜行右击收不到本事件——GT BaseMetaTileEntity 在潜行时拦截右击（用于贴墙放方块），
     * 与聚合器/枢纽同款限制（见 MTECrustMatterAggregator#onRightclick 注释）。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ClusterTerminalUiFactory.open(aPlayer, aBaseMetaTileEntity, ClusterTerminalUiFactory.PAGE_CHAIN_EDIT);
        }
        return true;
    }

    /**
     * 状态细化（自上而下）：
     * <ol>
     * <li>未入集群（cluster null）→ STANDBY；</li>
     * <li>总控停机（!{@code cluster.isMachineEnabled()}）→ STANDBY；</li>
     * <li>链空 → STANDBY；</li>
     * <li>链不可执行（!{@link LogisticsChain#isExecutable}，含缺工作模块/磁选热离未通电/无效形状）→
     * NO_POWER_OR_INVALID；</li>
     * <li>链含洗矿（ORE_WASH）/化洗（CHEM_BATH）且对应批流体不足（!{@link #hasBatchFluids}）→
     * FLUID_MISSING（批用量：洗矿耗水 1000L、化洗耗化浴液 1000L，简易洗矿与其余 link 无批流体）；</li>
     * <li>其余（就绪或批冷却中，{@link #getChainCooldownTicks()}＞0）→ WORKING——就绪/运行同为绿。</li>
     * </ol>
     */
    @Override
    public ClusterUnitStatus getUnitStatus() {
        if (!isUnitStructureFormed()) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        MTESteamMineralLogisticsCluster cluster = getCluster();
        if (cluster == null) return ClusterUnitStatus.STANDBY;
        if (!cluster.isMachineEnabled()) return ClusterUnitStatus.STANDBY;
        if (chain.isEmpty()) return ClusterUnitStatus.STANDBY;
        if (!chain.isExecutable(cluster.getTopology())) return ClusterUnitStatus.NO_POWER_OR_INVALID;
        boolean needWater = chain.countOf(ChainLink.ORE_WASH) > 0;
        boolean needChemBath = chain.countOf(ChainLink.CHEM_BATH) > 0;
        if ((needWater || needChemBath) && !hasBatchFluids(needWater, needChemBath)) {
            return ClusterUnitStatus.FLUID_MISSING;
        }
        return ClusterUnitStatus.WORKING;
    }

    /** 填充统一分发点：水→waterTank，非水→chemBathTank；成功写入时标记脏块。 */
    @Override
    public int fill(FluidStack aFluid, boolean doFill) {
        if (aFluid == null || aFluid.getFluid() == null || aFluid.amount <= 0 || !canTankBeFilled()) return 0;
        FluidTank target = isWater(aFluid) ? waterTank : chemBathTank;
        int filled = target.fill(aFluid, doFill);
        if (filled > 0 && doFill) markDirty();
        return filled;
    }

    /** 无类型放出：化洗 tank 优先，空则放水 tank（覆盖基类 mFluid 语义）。 */
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0 || !canTankBeEmptied()) return null;
        FluidStack drained = chemBathTank.getFluid() != null ? chemBathTank.drain(maxDrain, doDrain) : null;
        if (drained == null) drained = waterTank.drain(maxDrain, doDrain);
        if (drained != null && doDrain) markDirty();
        return drained;
    }

    /** 类型敏感放出（ME/CD 管道按流体请求）：匹配哪个 tank 就从哪个放，都不匹配返回 null。 */
    @Override
    public FluidStack drain(ForgeDirection side, FluidStack fluidStack, int amount, boolean doDrain) {
        if (fluidStack == null || amount <= 0) return null;
        FluidStack water = waterTank.getFluid();
        if (water != null && water.isFluidEqual(fluidStack)) {
            FluidStack drained = waterTank.drain(amount, doDrain);
            if (drained != null && doDrain) markDirty();
            return drained;
        }
        FluidStack chemBath = chemBathTank.getFluid();
        if (chemBath != null && chemBath.isFluidEqual(fluidStack)) {
            FluidStack drained = chemBathTank.drain(amount, doDrain);
            if (drained != null && doDrain) markDirty();
            return drained;
        }
        return null;
    }

    /** 主 tank（mFluid）弃用：一切读写经双 tank 分发，杜绝基类存储旁路。 */
    @Override
    public FluidStack getFillableStack() {
        return null;
    }

    @Override
    public FluidStack getDrainableStack() {
        return null;
    }

    /** 兼容读数：化洗优先的非空内容视图（类型敏感 drain 兜底与 GUI 直读用）。 */
    @Override
    public FluidStack getFluid() {
        if (chemBathTank.getFluid() != null) return chemBathTank.getFluid();
        return waterTank.getFluid();
    }

    /** 兼容读数：双 tank 总存量。 */
    @Override
    public int getFluidAmount() {
        return waterTank.getFluidAmount() + chemBathTank.getFluidAmount();
    }

    /** 单 tank 名义容量（管道/ME 显示用；实际总容量的双 tank 见 getTankInfo）。 */
    @Override
    public int getCapacity() {
        return ClusterParams.LOGISTICS_TANK_CAPACITY_L;
    }

    /** 双 tank 信息暴露：管道/ME 按侧可见两个独立 tank。 */
    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        return new FluidTankInfo[] { waterTank.getInfo(), chemBathTank.getInfo() };
    }

    /**
     * 批流体判定：链含洗矿（needWater）/化洗（needChemBath）时，对应 tank 存量须达到每批用量
     * （{@link ClusterParams#WASH_WATER_PER_BATCH_L} / {@link ClusterParams#CHEM_BATH_FLUID_PER_BATCH_L}，
     * 均 1000L）；两者都不需要时恒 true。
     */
    public boolean hasBatchFluids(boolean needWater, boolean needChemBath) {
        if (!isModuleEnabled()) return false;
        if (needWater && waterTank.getFluidAmount() < ClusterParams.WASH_WATER_PER_BATCH_L) return false;
        return !needChemBath || chemBathTank.getFluidAmount() >= ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L;
    }

    /**
     * 链配置与双 tank 持久化：链存 "clusterChain" int 数组（ChainLink.ordinal 列表，空链存空数组）；
     * 双 tank 存 "clusterWaterTank"/"clusterChemTank"（{@link FluidTank#writeToNBT}）。super 保留基类
     * mFluid 语义（本类恒 null，等价写空 tag，保持 NBT 对称）。
     */
    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        List<ChainLink> links = chain.getLinks();
        int[] ordinals = new int[links.size()];
        for (int i = 0; i < links.size(); i++) {
            ordinals[i] = links.get(i)
                .ordinal();
        }
        aNBT.setIntArray("clusterChain", ordinals);
        aNBT.setTag("clusterWaterTank", waterTank.writeToNBT(new NBTTagCompound()));
        aNBT.setTag("clusterChemTank", chemBathTank.writeToNBT(new NBTTagCompound()));
    }

    /**
     * 回读对称：按 ordinal 反解链并整链重建（new LogisticsChain() + setLinks，越界 ordinal 静默丢弃），
     * 双 tank 经 {@link FluidTank#readFromNBT} 恢复；tag 缺失时 getIntArray 回空数组、getCompoundTag
     * 回空 tag，天然回退空链/空 tank，不崩。
     */
    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        LogisticsChain rebuilt = new LogisticsChain();
        List<ChainLink> parsed = new ArrayList<>();
        ChainLink[] values = ChainLink.values();
        for (int ordinal : aNBT.getIntArray("clusterChain")) {
            if (ordinal >= 0 && ordinal < values.length) parsed.add(values[ordinal]);
        }
        rebuilt.setLinks(parsed);
        setChain(rebuilt);
        waterTank.readFromNBT(aNBT.getCompoundTag("clusterWaterTank"));
        chemBathTank.readFromNBT(aNBT.getCompoundTag("clusterChemTank"));
    }

    /**
     * 批处理冷却剩余 tick：每批执行后由 ClusterChainExecutor 置为 max(20, 本批耗时秒×20)，
     * 总控每 20t 统一 -20，≤0 且全部门控通过才可再执行一批。不持久化——重载/重摆后从零
     * 开始（冷却只是节拍器，非玩家资产），不入 {@link #saveNBTData}。
     */
    private long chainCooldownTicks;

    /** @return 批处理冷却剩余 tick（0 = 可立即执行下一批）。 */
    public long getChainCooldownTicks() {
        return chainCooldownTicks;
    }

    /** 设置批处理冷却（ClusterChainExecutor 批执行后写入；不持久化，重载后从零开始）。 */
    public void setChainCooldownTicks(long ticks) {
        this.chainCooldownTicks = ticks;
    }
}
