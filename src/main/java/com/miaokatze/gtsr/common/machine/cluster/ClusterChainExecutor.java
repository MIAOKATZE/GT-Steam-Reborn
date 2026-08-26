package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.util.GTSROutputBusCompat;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * IOF 式链批处理执行器：把一个物流单元（{@link MTEBasicLogisticsUnit}）的有序链
 * （{@link LogisticsChain}）瞬时推进一批，范式移植自 GT5U {@code MTEIntegratedOreFactory}
 * （下称 IOF，行号对准 5.09.54.20 源码）——取料 ：306-319、processStep :413-430、各步查询
 * :432-539、getOutputStack :558-581、doCompress :583-599。
 *
 * <p>
 * I/O 边界（§3.3.3 + SR-Cluster-r6 S2）：一切取料/产出/批流体均经物流单元自身的访问器——
 * {@code getLogisticsInputBusses()}/{@code getLogisticsOutputBusses()}/{@code getLogisticsInputHatches()}
 * （E3b 同批契约）；执行器不访问主控总线（主控输入仓仅保留给蒸汽/润滑经济结算）。批流体零自持缓存：
 * 洗矿水/化浴液按 {@link GTSRHatchFluidAccess} 统一访问层（hasEnoughAcross/depleteFluidAcross 口径）
 * 直接对物流单元输入仓结算，与其他加工模块的流体使用同一管线（探测一律模拟、实扣 3 参 UNKNOWN drain）。
 *
 * <p>
 * 热量门控（§3.6.4 取料前低温门控，SR-Cluster-r5 决策 2）：冷却检查通过后、取料登记前检查宿主
 * {@link ClusterBatchHost#heatFraction()}；&lt;1.0 时直接返回 0——不取料、不 runChain、不输出、
 * 不扣水/化浴液、不记吞吐、零副作用（低温不再吞批/停机，仅暂停开批；热量回满后自动恢复，
 * 无 owner 通知与软锤复位需求）。
 *
 * <p>
 * 跳步时间节约（SR-Cluster-r5 决策 12）：runChain 跟踪实际命中配方的链步集合
 * {@code processedLinks}，本批配方时间按 {@code itemTimeSec(processedLinks, ...)} 计算
 * （仅计实际加工的链步；全透传批 = 纯物流段时间）。吞吐/蒸汽 C 聚合口径不变。
 *
 * <p>
 * 真实配方逐物品扣液（§3.6.5 + r6 S2，IOF :413-539 范式）：不再按「链含洗矿/化洗每批扣固定 1000L」，
 * 而是链加工单遍执行中对每个将实际处理的物品按命中配方累计流体需求（ORE_WASH 按命中配方
 * 蒸馏水 200mB 或普通水 1000mB，输入仓合计探得蒸馏水时优先蒸馏路径；CHEM_BATH 按配方汞 1000mB
 * 或过硫酸钠 100mB，以输入仓实际非水流体匹配；SIMPLE_WASH 每命中物品普通水 100mB）。任一不足
 * →整批零副作用（此时尚未扣料/扣流体/产出）；实扣推迟到输出可接收、事务提交之后；失败回滚
 * 路径零扣。配方查找失败/形态不接受/SIMPLE_WASH 图缺失 → 原样透传（§3.6.5-5）。
 *
 * <p>
 * 零副作用事务：门控（含批流体预检）未过即返 0 且无任何状态变化；扣料虽按 IOF 口径改写输入
 * 总线 live 引用的 stackSize，但产出的放置走「逐组探测→实放→台账」，放置失败时已扣数量原样
 * 加回、已实放产物按 (bus,itemId,amount) 台账回滚，整批表现为未发生。链加工全程只操作取料副本。
 *
 * <p>
 * 增益（§3.6.3 + r6-S6）：{@link BoosterState} 的主产物增益（同类最高仅一生效）作用于主产物 chance、
 * 副产物增益（可加算）作用于副产物 chance，最终 chance 钳制 [0,1]；100% 主产物不因增益重复产出。
 * 粉碎链步（仅 CRUSH，不含 HAMMER 锻造）的副产物条目在增幅后最终概率上再乘粉碎乘率（tier0 ×0.1 / tier≥1 ×0.5）。
 *
 * <p>
 * 配方时间模型（SR-Cluster-r6 S3，批冷却语义重定义）：每单元 {@code chainCooldownTicks} 在成功批
 * 提交后写为本批<b>配方时间</b>（tick）＝{@code ceil(itemTimeSec(processedLinks) × 20)}（ExecutionPlan
 * 时间口径，含物流段时间；空 processedLinks 即纯物流时间），总控每 20t 统一 -20 且对仍 &gt;0 的单元
 * 跳过本秒；该值同时驱动物流单元虚拟空配方进度与工作态窗口（见 MTEBasicLogisticsUnit.onBatchProcessed）。
 *
 * <p>
 * 吞吐（§3.6.6-4）：真实成功批经 {@link ClusterBatchHost#addRealBatchThroughput(int)} 累计
 * （同秒多链求和，窗口换算归宿主），不写「最后一条链理论吞吐」。
 *
 * <p>
 * 线程模型：仅服务器主线程调用（与 IOF / 集群其余部分一致）。
 */
public final class ClusterChainExecutor {

    /** 概率副产物的正态近似随机源（IOF :145 同款 XSTR；静态单例，仅主线程使用）。 */
    private static final XSTR RANDOM = new XSTR();

    private ClusterChainExecutor() {}

    /**
     * 推进一个物流单元的链批处理（每秒由总控 runChains 调用一次；配方时间未到直接返回）。
     *
     * <p>
     * 事务流程：门控（主控+单元启用+物理电源/链可执行/tier/配方时间）→ 低温门控（热量不足取料前
     * 零副作用返 0）→ 取料登记（不扣料）→ 链加工（副本单遍执行 + 逐物品真实配方流体需求累计 +
     * processedLinks 跟踪）→ 批流体预检（不足整批零副作用）→ 扣料 → 产出探测-实放（失败回滚
     * 输入，零副作用）→ 提交（实扣配方流体 + 配方时间/记账/吞吐/处理窗口开窗）。
     *
     * @param cluster   集群总控（拓扑、tier 与累计记账入口）
     * @param unit      物流单元（链、I/O 总线、输入仓流体结算面、配方时间字段持有者）
     * @param batchHost 批宿主（热量分率/断供锁存/真实吞吐累计契约，批 2 E5 由主控实现）
     * @return 本批实际处理矿数（0=未执行）
     */
    public static int executeBatch(MTESteamMineralLogisticsCluster cluster, MTEBasicLogisticsUnit unit,
        ClusterBatchHost batchHost) {
        if (cluster == null || unit == null || batchHost == null) return 0;
        LogisticsChain chain = unit.getChain();
        if (chain == null || chain.isEmpty()) return 0;

        // 1) 门控：主控开机 + 单元自身允许工作（成型/通电）+ 物理电源开（软锤关停即
        // isPowerAllowed()=false 的单元在满热下不得执行批处理，口径同
        // MTEBasicLogisticsUnit.isChainExecutableNow）+ 链可执行 + tier 有效
        int tier = cluster.getStructureTierIndex();
        ClusterTopology topology = cluster.getTopology();
        if (!cluster.isMachineEnabled() || !unit.isModuleEnabled()
            || !unit.isPowerAllowed()
            || !chain.isExecutable(topology)
            || tier < 0) return 0;

        // 2) 配方时间未到（r6 S3：批冷却即本批配方时间，由调用方按 20t 递减，本方法不递减）
        if (unit.getChainCooldownTicks() > 0) return 0;

        // 3) 低温门控（§3.6.4 取料前，决策 2）：热量不满 → 直接返 0 零副作用——不取料/不加工/
        // 不输出/不扣批流体/不记吞吐/不停机（低温不再吞批，热量回满后自动恢复开批）
        if (batchHost.heatFraction() < 1.0) return 0;

        // 4) 并行与输入：从物流单元自己的输入总线收集全部非 OTHER 形态的物品（决策 3：
        // ORE 与粉碎矿/污浊粉等全部中间态均收），只登记台账不扣料
        BoosterState booster = BoosterState.aggregate(topology.getBoosterUnits());
        int parallel = ExecutionPlan.effectiveParallel(tier, booster);
        List<InputTake> takes = new ArrayList<>();
        int batch = collectOreBatch(unit, parallel, takes);
        if (batch <= 0) return 0;

        // 5) 链加工（副本单遍执行，逐物品累计真实配方流体需求 + processedLinks 跟踪；此点零副作用）
        List<ItemStack> mid = new ArrayList<>(batch);
        for (InputTake take : takes) {
            for (int i = 0; i < take.amount; i++) {
                mid.add(GTUtility.copyAmountUnsafe(1, take.live));
            }
        }
        BatchFluidLedger fluids = new BatchFluidLedger();
        EnumSet<ChainLink> processedLinks = EnumSet.noneOf(ChainLink.class);
        List<ItemStack> outputs = runChain(chain, mid, unit, fluids, booster, processedLinks, tier);

        // 6) 批流体预检（§3.6.5-3）：本批将处理物品的累计需求任一不足 → 整批零副作用
        if (!fluids.isSatisfiable(unit)) return 0;

        // 7) 扣料（IOF :306-319 口径：live 引用 stackSize -= take；0-size 槽由总线自身 tick 收口）
        applyTakes(takes);

        // 8) 产出：写入物流单元输出总线——整组放得下才实放（探测-实放+台账，GTSROutputBusCompat
        // 兼容 ME 总线）；任一组无处可放 → 回滚已实放产物并加回输入，整批零副作用（含零扣流体）
        if (!tryEmitOutputs(unit, outputs)) {
            restoreInputs(takes);
            return 0;
        }

        // 9) 提交：输出可接收后才实扣配方流体（§3.6.5-4，r6 S2：直接对物流单元输入仓跨仓结算）；
        // 此点之后不再回滚
        fluids.consume(unit);
        unit.markDirty();

        // 10) 配方时间与记账（r6 S3）：本批配方时间（tick）= ceil(itemTimeSec(processedLinks)×20)
        // —— ExecutionPlan 时间口径含物流段，仅计实际命中配方的链步（决策 12 跳步时间节约），
        // 空集合即纯物流时间；写入后由总控每 20t 递减、并经 onBatchProcessed 驱动物流单元虚拟
        // 空配方进度与工作态窗口
        List<ChainLink> processedLinksList = new ArrayList<>(processedLinks);
        long recipeTicks = Math
            .max(0L, (long) Math.ceil(ExecutionPlan.itemTimeSec(processedLinksList, tier, topology, booster) * 20D));
        unit.setChainCooldownTicks(recipeTicks);
        unit.onBatchProcessed(batch);
        cluster.recordBatchLinks(processedLinks);
        cluster.addProcessedOre(batch);
        batchHost.addRealBatchThroughput(batch);
        return batch;
    }

    // ==================== 取料（IOF :306-319，I/O 经物流单元输入总线） ====================

    /** 一笔取料台账：来源总线、槽位、live 引用与扣减数量（回滚 = live.stackSize += amount）。 */
    private static final class InputTake {

        final MTEHatchInputBus bus;
        final int slot;
        final ItemStack live;
        final int amount;

        InputTake(MTEHatchInputBus bus, int slot, ItemStack live, int amount) {
            this.bus = bus;
            this.slot = slot;
            this.live = live;
            this.amount = amount;
        }
    }

    /**
     * 按剩余并行数从<b>物流单元自己的</b>输入总线收集全部非 OTHER 形态的物品
     * （SR-Cluster-r5 决策 3 中间态通用：ORE/CRUSHED/CRUSHED_PURIFIED/CRUSHED_CENTRIFUGED/
     * DUST_IMPURE/DUST_PURE/DUST/INGOT 均收，仅 {@link ClusterItemForms.OreForm#OTHER} 拒收），
     * 只登记台账不扣料：每源堆 take = min(remaining, stackSize)（IOF :309-318 取料口径），
     * live 引用与槽位记入 {@link InputTake}，实际扣减由 {@link #applyTakes} 执行。
     *
     * @return 登记的可取总数（≤parallel；即 min(parallel, 总线内非 OTHER 形态可取总数)）
     */
    private static int collectOreBatch(MTEBasicLogisticsUnit unit, int parallel, List<InputTake> takes) {
        int remaining = parallel;
        for (MTEHatchInputBus bus : GTUtility.validMTEList(unit.getLogisticsInputBusses())) {
            if (remaining <= 0) break;
            for (int i = 0, n = bus.getSizeInventory(); i < n && remaining > 0; i++) {
                ItemStack ore = bus.getStackInSlot(i);
                if (GTUtility.isStackInvalid(ore)) continue;
                if (ClusterItemForms.classify(ore) == ClusterItemForms.OreForm.OTHER) continue;

                int take = Math.min(remaining, ore.stackSize);
                takes.add(new InputTake(bus, i, ore, take));
                remaining -= take;
            }
        }
        return parallel - remaining;
    }

    /** 扣料执行（IOF :317 原式）：对每个 live 引用 stackSize -= take（0-size 槽由总线自身 tick 收口）。 */
    private static void applyTakes(List<InputTake> takes) {
        for (InputTake take : takes) {
            take.live.stackSize -= take.amount;
        }
    }

    /** 取料回滚：把已扣数量加回各 live 引用（未调 updateSlots，槽位引用未被 null 化，原样可逆）。 */
    private static void restoreInputs(List<InputTake> takes) {
        for (InputTake take : takes) {
            take.live.stackSize += take.amount;
        }
    }

    // ==================== 批流体台账（§3.6.5 逐物品真实配方扣液，r6 S2 直结输入仓） ====================

    /**
     * 本批配方流体需求台账：链加工单遍执行中对每个命中配方的物品累计实际流体需求
     * （普通水/蒸馏水/化浴液三口径分立），预检（{@link #isSatisfiable}）与提交实扣
     * （{@link #consume}）共用同一份数据，保证「预检的量 = 实扣的量」。
     *
     * <p>
     * r6 S2 去缓存：结算面为物流单元自身输入仓列表（1..2 枚，普通仓/ME 仓同口径），探测与实扣均经
     * {@link GTSRHatchFluidAccess}（hasEnoughAcross/depleteFluidAcross）；不同水种/化浴液可由多仓分别
     * 供给，不再受旧单 tank 单流体互斥约束（mixedChemFluids 保留：同批两种化浴介质视为配置矛盾）。
     */
    private static final class BatchFluidLedger {

        /** 普通水需求累计（mB：ORE_WASH 水路径配方量 + SIMPLE_WASH 每物品 100mB）。 */
        private int plainWaterMb;

        /** 普通水命中流体实例（按配方 representative 记账；SIMPLE_WASH 为普通水）。 */
        private Fluid plainWaterFluid;

        /** 蒸馏水需求累计（mB：ORE_WASH 蒸馏路径配方量，附录 B 200mB/物品）。 */
        private int distilledWaterMb;

        /** 蒸馏水命中流体实例（GTModHandler.getDistilledWater 口径）。 */
        private Fluid distilledWaterFluid;

        /** 化浴液需求（单一流体；汞 1000mB / 过硫酸钠 100mB 按命中配方累计）。 */
        private FluidStack chemFluid;

        /** 本批出现了互斥的两种化浴液需求（预检失败 → 整批零副作用）。 */
        private boolean mixedChemFluids;

        /** 按链步与命中配方累计需求（items = 本物品堆数量，rollOutputs 的 aTime 同口径）。 */
        void charge(ChainLink link, GTRecipe recipe, int items) {
            if (link == ChainLink.SIMPLE_WASH) {
                plainWaterMb += items * ClusterParams.SIMPLE_WASH_WATER_PER_ITEM_MB;
                if (plainWaterFluid == null) plainWaterFluid = plainWaterInstance();
                return;
            }
            if (link != ChainLink.ORE_WASH && link != ChainLink.CHEM_BATH) return;
            FluidStack rep = recipe.getRepresentativeFluidInput(0);
            if (rep == null || rep.getFluid() == null || rep.amount <= 0) return;
            if (link == ChainLink.ORE_WASH) {
                if (isDistilledFluid(rep.getFluid())) {
                    distilledWaterFluid = rep.getFluid();
                    distilledWaterMb += items * rep.amount;
                } else {
                    plainWaterFluid = rep.getFluid();
                    plainWaterMb += items * rep.amount;
                }
            } else {
                if (chemFluid == null) chemFluid = new FluidStack(rep.getFluid(), items * rep.amount);
                else if (chemFluid.getFluid() == rep.getFluid()) chemFluid.amount += items * rep.amount;
                else mixedChemFluids = true;
            }
        }

        /**
         * 预检：物流单元输入仓合计可得量能否覆盖累计需求（仅模拟探测，零副作用；
         * {@link GTSRHatchFluidAccess#hasEnoughAcross} 口径）。
         */
        boolean isSatisfiable(MTEBasicLogisticsUnit unit) {
            List<MTEHatchInput> hatches = unit.getLogisticsInputHatches();
            if (mixedChemFluids) return false;
            if (plainWaterMb > 0
                && !GTSRHatchFluidAccess.hasEnoughAcross(hatches, ledgerStack(plainWaterFluid, plainWaterMb)))
                return false;
            if (distilledWaterMb > 0
                && !GTSRHatchFluidAccess.hasEnoughAcross(hatches, ledgerStack(distilledWaterFluid, distilledWaterMb)))
                return false;
            if (chemFluid != null && chemFluid.amount > 0 && !GTSRHatchFluidAccess.hasEnoughAcross(hatches, chemFluid))
                return false;
            return true;
        }

        /**
         * 提交实扣（输出可接收后调用；isSatisfiable 已验存量，此处跨仓按台账量整扣——
         * 探测/实扣之间无外部写入（同 tick 主线程），口径一致）。
         */
        void consume(MTEBasicLogisticsUnit unit) {
            List<MTEHatchInput> hatches = unit.getLogisticsInputHatches();
            if (plainWaterMb > 0) {
                GTSRHatchFluidAccess.depleteFluidAcross(hatches, ledgerStack(plainWaterFluid, plainWaterMb));
            }
            if (distilledWaterMb > 0) {
                GTSRHatchFluidAccess.depleteFluidAcross(hatches, ledgerStack(distilledWaterFluid, distilledWaterMb));
            }
            if (chemFluid != null && chemFluid.amount > 0) {
                GTSRHatchFluidAccess.depleteFluidAcross(hatches, chemFluid);
            }
        }

        /** 台账 FluidStack 组装（防御：fluid 未记账时回退普通水，正常流程不触发）。 */
        private static FluidStack ledgerStack(Fluid fluid, int amountMb) {
            return new FluidStack(fluid != null ? fluid : plainWaterInstance(), amountMb);
        }

        /** 普通水流体实例（Materials.Water 优先，注册表兜底）。 */
        private static Fluid plainWaterInstance() {
            if (Materials.Water.mFluid != null) return Materials.Water.mFluid;
            return FluidRegistry.WATER;
        }
    }

    /** @return 该流体是否蒸馏水（GTModHandler 蒸馏水流体口径）。 */
    private static boolean isDistilledFluid(Fluid fluid) {
        FluidStack distilled = GTModHandler.getDistilledWater(1);
        return distilled != null && fluid == distilled.getFluid();
    }

    /** @return 该流体是否普通水（注册实例或名字口径，与物流单元判水一致）。 */
    private static boolean isPlainWaterFluid(Fluid fluid) {
        return fluid == FluidRegistry.WATER || "water".equals(fluid.getName());
    }

    // ==================== 链加工（IOF processStep :413-430） ====================

    /**
     * 逐 link 推进中产物：对每个 stack 先做形态约束过滤（并集口径见 {@link #acceptsForm}），
     * 命中则查配方（{@link #findLinkRecipe} 按 §3.6.5 的流体路径解析）——命中取
     * {@link #rollOutputs}（IOF :558-581 移植 + 增幅作用于 chance）、按命中配方累计流体需求
     * （{@link BatchFluidLedger#charge}）并把该 link 记入 {@code processedLinks}
     * （决策 12：冷却仅计实际加工链步），null 原样透传；每步尾 {@link #compress} 合并同类项
     * （IOF :583-599 移植）。SIMPLE_WASH 配方图缺失（GT++ 不在场）时该步整体透传。
     *
     * @param processedLinks 实际命中配方的链步集合（调用方持有，EnumSet 去重；方法内只增不改他项）
     * @param tier           集群结构层级下标（r6-S6 粉碎副产物乘率的档位来源；调用方已保证 ≥0）
     * @return 合并后的最终产物列表（调用方负责写入输出总线）
     */
    private static List<ItemStack> runChain(LogisticsChain chain, List<ItemStack> mid, MTEBasicLogisticsUnit unit,
        BatchFluidLedger fluids, BoosterState booster, EnumSet<ChainLink> processedLinks, int tier) {
        boolean seenReduction = false;
        for (ChainLink link : chain.getLinks()) {
            boolean firstReduction = false;
            if (link == ChainLink.CRUSH || link == ChainLink.HAMMER) {
                firstReduction = !seenReduction;
                seenReduction = true;
            }
            RecipeMap<?> map = link.getRecipeMap();

            List<ItemStack> output = new ArrayList<>(mid.size());
            for (ItemStack stack : mid) {
                ClusterItemForms.OreForm form = ClusterItemForms.classify(stack);
                if (map == null || !acceptsForm(link, form, firstReduction)) {
                    output.add(stack);
                    continue;
                }
                GTRecipe recipe = findLinkRecipe(link, map, GTUtility.copyOrNull(stack), unit);
                if (recipe != null) {
                    processedLinks.add(link);
                    fluids.charge(link, recipe, stack.stackSize);
                    output.addAll(rollOutputs(recipe, stack.stackSize, booster, link, tier));
                } else {
                    output.add(stack);
                }
            }
            mid = compress(output);
        }
        return mid;
    }

    /**
     * 各 link 的输入形态约束（IOF 实证 + 并集口径）：
     * CRUSH/HAMMER 首个（链中第 1 个破碎/锤砸步）只收 ORE，第 2+ 个收
     * ORE/CRUSHED/CRUSHED_PURIFIED/CRUSHED_CENTRIFUGED 全集；ORE_WASH 收 CRUSHED；
     * CHEM_BATH/THERMOCENTRIFUGE 收 CRUSHED/CRUSHED_PURIFIED；SIMPLE_WASH/CENTRIFUGE 收
     * DUST_IMPURE/DUST_PURE；SIFTER 收 CRUSHED_PURIFIED；MAGNETIC_SEPARATOR 收 DUST_PURE；
     * FURNACE 收任意非 OTHER 形态（§3.6.6-2：普通 DUST/INGOT 终态可达，不再被 OTHER 拒绝）。
     */
    private static boolean acceptsForm(ChainLink link, ClusterItemForms.OreForm form, boolean firstReduction) {
        switch (link) {
            case CRUSH:
            case HAMMER:
                if (firstReduction) return form == ClusterItemForms.OreForm.ORE;
                return form == ClusterItemForms.OreForm.ORE || form == ClusterItemForms.OreForm.CRUSHED
                    || form == ClusterItemForms.OreForm.CRUSHED_PURIFIED
                    || form == ClusterItemForms.OreForm.CRUSHED_CENTRIFUGED;
            case ORE_WASH:
                return form == ClusterItemForms.OreForm.CRUSHED;
            case CHEM_BATH:
            case THERMOCENTRIFUGE:
                return form == ClusterItemForms.OreForm.CRUSHED || form == ClusterItemForms.OreForm.CRUSHED_PURIFIED;
            case SIMPLE_WASH:
                // SR-终审 C1：与 FSM 并集表对齐（CRUSHED→PURIFIED / DUST_IMPURE、DUST_PURE→DUST）
                return form == ClusterItemForms.OreForm.CRUSHED || form == ClusterItemForms.OreForm.DUST_IMPURE
                    || form == ClusterItemForms.OreForm.DUST_PURE;
            case CENTRIFUGE:
                return form == ClusterItemForms.OreForm.DUST_IMPURE || form == ClusterItemForms.OreForm.DUST_PURE;
            case SIFTER:
                return form == ClusterItemForms.OreForm.CRUSHED_PURIFIED;
            case MAGNETIC_SEPARATOR:
                return form == ClusterItemForms.OreForm.DUST_PURE;
            case FURNACE:
                return form != ClusterItemForms.OreForm.OTHER;
            default:
                return false;
        }
    }

    /**
     * 各 link 查配方的流体匹配信号（§3.6.5 真实配方口径 + r6 S2 直结输入仓，仅匹配用；实扣走台账）：
     * ORE_WASH → 物流单元输入仓合计探得蒸馏水时先查蒸馏路径（蒸馏 MAX，命中即按配方 200mB 计），
     * 否则/未命中查普通水路径（水 MAX，命中按配方 1000mB 计）；SIMPLE_WASH → 水 100mB
     * （IOF :489）；CHEM_BATH → 输入仓实际非水流体 MAX（IOF :503-506 按仓内实际流体的口径，
     * 跨仓取首个探得的非水流体的泛化版；仓内无任何非水流体 → 无信号，化浴配方必有流体输入
     * → 必 miss 透传，不以「任意有液体」冒充）；其余 link 无流体信号。
     */
    private static GTRecipe findLinkRecipe(ChainLink link, RecipeMap<?> map, ItemStack stackCopy,
        MTEBasicLogisticsUnit unit) {
        switch (link) {
            case ORE_WASH: {
                FluidStack distilledProbe = GTModHandler.getDistilledWater(1);
                if (distilledProbe != null && distilledProbe.getFluid() != null
                    && GTSRHatchFluidAccess.hasEnoughAcross(unit.getLogisticsInputHatches(), distilledProbe)) {
                    GTRecipe distilled = findRecipe(map, stackCopy, GTModHandler.getDistilledWater(Integer.MAX_VALUE));
                    if (distilled != null) return distilled;
                }
                return findRecipe(map, stackCopy, Materials.Water.getFluid(Integer.MAX_VALUE));
            }
            case SIMPLE_WASH:
                return findRecipe(
                    map,
                    stackCopy,
                    Materials.Water.getFluid(ClusterParams.SIMPLE_WASH_WATER_PER_ITEM_MB));
            case CHEM_BATH: {
                Fluid available = firstNonWaterFluidAcross(unit);
                if (available == null) return findRecipe(map, stackCopy, null);
                return findRecipe(map, stackCopy, new FluidStack(available, Integer.MAX_VALUE));
            }
            default:
                return findRecipe(map, stackCopy, null);
        }
    }

    /**
     * 跨仓首个非水流体探测（CHEM_BATH 匹配信号）：逐仓 {@code getTankInfo(UNKNOWN)} 扫描
     * （GTSRHatchFluidAccess 同款 UNKNOWN 探测铁律），跳过普通水/蒸馏水口径流体，返回首个命中的
     * 流体实例；全为空/全为水系返回 null。普通仓单罐单流体、ME 仓多槽上报均兼容。
     */
    private static Fluid firstNonWaterFluidAcross(MTEBasicLogisticsUnit unit) {
        for (MTEHatchInput hatch : GTUtility.validMTEList(unit.getLogisticsInputHatches())) {
            if (hatch == null) continue;
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo info : tanks) {
                if (info == null || info.fluid == null || info.fluid.getFluid() == null) continue;
                Fluid fluid = info.fluid.getFluid();
                if (isPlainWaterFluid(fluid) || isDistilledFluid(fluid)) continue;
                return fluid;
            }
        }
        return null;
    }

    /** findRecipeQuery 查询（IOF 各步 :433-507 同式）；无流体信号的 link 不带 fluids 项。 */
    private static GTRecipe findRecipe(RecipeMap<?> map, ItemStack stackCopy, FluidStack fluidSignal) {
        if (fluidSignal != null) {
            return map.findRecipeQuery()
                .items(stackCopy)
                .fluids(fluidSignal)
                .find();
        }
        return map.findRecipeQuery()
            .items(stackCopy)
            .find();
    }

    /**
     * 配方产物掷取（IOF getOutputStack :558-581 移植 + §3.6.3 增幅 + r6-S6 粉碎副产物乘率）：
     * <ul>
     * <li>主产物（输出槽 0）chance += 主产物增益（{@link BoosterState#getPrimaryBonus()}，
     * 同类最高仅一生效）；副产物（槽 1+）chance += 副产物增益（加算）；
     * {@code booster == null} 按零增益；</li>
     * <li><b>粉碎副产物乘率（r6-S6）</b>：链步为 CRUSH 时，增幅加成并钳制后的<b>最终</b>
     * 副产物概率（仅槽 1+ 条目，不影响主产物）再乘 {@code CRUSH_BYPRODUCT_MULT_NORMAL}=0.1
     * （集群 tier0）/ {@code CRUSH_BYPRODUCT_MULT_STEEL}=0.5（钢级 tier≥1）；洗矿/离心等其他
     * 环节副产物不受影响；chance==10000 保底项不参与任何增益/乘率；</li>
     * <li>最终 chance 钳制 [0,1]；</li>
     * <li>概率项按二项分布的正态近似 nextGaussian（mean=aTime·p、std=sqrt(aTime·p·(1-p))，
     * 向上取整后乘 template.stackSize）；quantity≤0 的槽位不产出。</li>
     * </ul>
     */
    private static List<ItemStack> rollOutputs(GTRecipe recipe, int aTime, BoosterState booster, ChainLink link,
        int tier) {
        double primaryBonus = booster == null ? 0.0 : booster.getPrimaryBonus();
        double secondaryBonus = booster == null ? 0.0 : booster.getSecondaryBonus();
        boolean crushStep = link == ChainLink.CRUSH;
        double crushByproductMult = !crushStep ? 1.0
            : tier >= 1 ? ClusterParams.CRUSH_BYPRODUCT_MULT_STEEL : ClusterParams.CRUSH_BYPRODUCT_MULT_NORMAL;
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack template = recipe.getOutput(i);
            if (template == null) continue;

            int chance = recipe.getOutputChance(i);
            int quantity;
            if (chance == 10000) {
                quantity = aTime * template.stackSize;
            } else {
                double p = chance / 10000.0 + (i == 0 ? primaryBonus : secondaryBonus);
                p = Math.max(0.0, Math.min(1.0, p));
                if (i > 0 && crushStep) p *= crushByproductMult;
                // Normal-distribution approximation for probabilistic drops
                double mean = aTime * p;
                double std = Math.sqrt(aTime * p * (1 - p));
                quantity = (int) Math.ceil(std * RANDOM.nextGaussian() + mean);
                quantity *= template.stackSize;
            }
            if (quantity > 0) {
                outputs.add(GTUtility.copyAmountUnsafe(quantity, template));
            }
        }
        return outputs;
    }

    /**
     * 同类项合并（IOF doCompress :583-599 移植，无 IOF 的石粉湮灭开关）：按
     * GTUtility.stackToInt 压 id，{@code HashMap<Integer,Integer>.merge} 累加数量后经
     * intToStack + copyAmountUnsafe 重建堆（IOF 无 NBT 区分语义，照抄）。
     */
    private static List<ItemStack> compress(List<ItemStack> list) {
        HashMap<Integer, Integer> merged = new HashMap<>();
        for (ItemStack stack : list) {
            if (GTUtility.isStackInvalid(stack)) continue;
            int id = GTUtility.stackToInt(stack);
            if (id != 0) {
                merged.merge(id, stack.stackSize, Integer::sum);
            }
        }

        List<ItemStack> result = new ArrayList<>(merged.size());
        for (Map.Entry<Integer, Integer> entry : merged.entrySet()) {
            ItemStack template = GTUtility.intToStack(entry.getKey());
            if (template != null) {
                result.add(GTUtility.copyAmountUnsafe(entry.getValue(), template));
            }
        }
        return result;
    }

    // ==================== 产出回填（I/O 经物流单元输出总线） ====================

    /** 一笔实放台账：目标总线 + 物品 id + 实放数量（整批失败时按此扣回）。 */
    private static final class OutputLedger {

        final MTEHatchOutputBus bus;
        final int itemId;
        int amount;

        OutputLedger(MTEHatchOutputBus bus, int itemId, int amount) {
            this.bus = bus;
            this.itemId = itemId;
            this.amount = amount;
        }
    }

    /**
     * 产物写入<b>物流单元自己的</b>输出总线：每组产物依次对每个总线做 storePartial 模拟探测
     * （探测会扣减入参 stackSize，故用副本），整组放得下才实放（聚合器 tryOutputOre 同式，经
     * {@link GTSROutputBusCompat} 兼容 ME 总线 cache 满语义）。任一组全部总线都放不下 →
     * 回滚此前已实放的各组并返回 false（调用方再加回输入，整批零副作用）。逐组「探测→实放」
     * 之间无外部写入（同 tick 同线程），后续组的探测可见先续组的实放结果，无自我竞争误判。
     */
    private static boolean tryEmitOutputs(MTEBasicLogisticsUnit unit, List<ItemStack> outputs) {
        List<OutputLedger> ledger = new ArrayList<>();
        for (ItemStack out : outputs) {
            if (GTUtility.isStackInvalid(out)) continue;
            boolean placed = false;
            for (MTEHatchOutputBus bus : GTUtility.validMTEList(unit.getLogisticsOutputBusses())) {
                if (!GTSROutputBusCompat.storePartial(bus, GTUtility.copyOrNull(out), true)) continue;
                int amount = out.stackSize;
                GTSROutputBusCompat.storePartial(bus, out, false);
                ledger.add(new OutputLedger(bus, GTUtility.stackToInt(out), amount));
                placed = true;
                break;
            }
            if (!placed) {
                rollbackOutputs(ledger);
                return false;
            }
        }
        return true;
    }

    /**
     * 实放回滚：按台账在各总线内扫同 id 堆扣回登记数量（扣空槽置 null）。实放可能并入既有
     * 同类堆，回滚按物品多重集复原——槽位排布可能与回滚前有微小差异（GT 总线本就周期
     * compact，无语义影响）。
     */
    private static void rollbackOutputs(List<OutputLedger> ledger) {
        for (OutputLedger entry : ledger) {
            for (int i = 0, n = entry.bus.getSizeInventory(); i < n && entry.amount > 0; i++) {
                ItemStack slot = entry.bus.getStackInSlot(i);
                if (GTUtility.isStackInvalid(slot)) continue;
                if (GTUtility.stackToInt(slot) != entry.itemId) continue;
                int remove = Math.min(entry.amount, slot.stackSize);
                slot.stackSize -= remove;
                entry.amount -= remove;
                if (slot.stackSize <= 0) entry.bus.setInventorySlotContents(i, null);
            }
        }
    }
}
