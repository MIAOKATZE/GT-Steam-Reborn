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
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

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
 * {@code processedLinks}（EnumSet 去重——链内重复出现的同一 link 时间只计一次），本批配方时间按
 * {@code itemTimeSec(processedLinks, ...)} 计算
 * （仅计实际加工的链步；全透传批 = 纯物流段时间）。T4 口径注记：单步 T_i 的 0.2 tick 下限在
 * 「÷同类模块数」与「÷档位时间除数」之后施加——重复放置同类加工模块只把 T_i 压到下限为止，
 * 与去重无叠加效应。吞吐/蒸汽 C 聚合口径不变（蒸汽 C_i 侧按 T11 ×同类模块数累计）。
 *
 * <p>
 * 真实配方逐物品扣液（§3.6.5 + r6 S2，IOF :413-539 范式）：不再按「链含洗矿/化洗每批扣固定 1000L」，
 * 而是链加工单遍执行中对每个将实际处理的物品按命中配方累计流体需求（ORE_WASH 按命中配方
 * 蒸馏水 200mB 或普通水 1000mB，输入仓合计探得蒸馏水时优先蒸馏路径；CHEM_BATH 按配方汞 1000mB
 * 或过硫酸钠 100mB，以输入仓实际非水流体匹配；SIMPLE_WASH 按命中配方的实际流体输入累计）。任一不足
 * →整批零副作用（此时尚未扣料/扣流体/产出）；实扣推迟到输出可接收、事务提交之后；失败回滚
 * 路径零扣。配方查找失败/形态不接受/SIMPLE_WASH 图缺失 → 原样透传（§3.6.5-5）。
 *
 * <p>
 * 零副作用事务：门控（含批流体预检与输出预检）未过即返 0 且无任何状态变化；输出预检走
 * 「逐组探测→实放→台账→立即按台账回滚」（probe-place-undo，复用最终发放同一实现），证明整批
 * 放得下才继续扣料；扣料虽按 IOF 口径改写输入总线 live 引用的 stackSize，但预检失败时输入原样
 * 未动，整批表现为未发生。链加工全程只操作取料副本。
 *
 * <p>
 * 配方运行绑定（r-logi-power-bind）：预检通过的批不再当场发放产出——扣料吞入、配方流体实扣、
 * 配方时间写入后整批产出 {@link MTEBasicLogisticsUnit#stashPendingOutputs 暂存}于物流单元，
 * 待配方进度读零后由单元自身 onPostTick 经 {@link #emitPendingOutputs} 排空（总线满则逐 t
 * 空转重试，零消耗零丢料）。单元输出总线仅本执行器写入，开批预检结论在排空前有效；持有暂存
 * 产出的单元拒绝开新批（防覆盖丢产出）。
 *
 * <p>
 * 增益（§3.6.3 + r6-S6 + T2/T6 重定义）：{@link BoosterState} 的主产物增益（多模块加算）与副产物
 * 增益（加算）分别加到主/副产物 chance 上；总概率 p ≥ 1 时 floor(p) 份整份输出保底复制 + 余数概率
 * 再 roll 一份（100% 主产物不再跳过增幅），p &lt; 1 钳制 [0,1] 后按历史正态近似掷取。
 * 粉碎链步（仅 CRUSH，不含 HAMMER 锻造）的副产物条目在增幅后最终概率上再乘粉碎乘率（tier0 ×0.1 / tier≥1 ×0.5）。
 *
 * <p>
 * 配方时间模型（SR-Cluster-r6 S3 + r-logi-power-bind 调序，批冷却语义重定义；T4 改 ceil 口径）：
 * 每单元 {@code chainCooldownTicks} 在成功批提交后写为本批<b>配方时间</b>（tick）＝
 * {@code ceil(itemTimeSec(processedLinks) × 20)}（内部允许小数 tick，按 1t=0.05s 向上取整、
 * 余数计整 tick），结果至少 1 tick（ExecutionPlan
 * 时间口径，含物流段时间；空 processedLinks 即纯物流时间），总控结算先统一 -20（decrementChainCooldowns，
 * 关电/断供收尾路径同样照减）再对冷却 ≤0 的单元开批；该值同时驱动物流单元配方运行进度与
 * 工作态窗口（见 MTEBasicLogisticsUnit.onBatchProcessed）。
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
     * 事务流程：门控（主控+单元启用+物理电源/链可执行/tier/暂存产出未排空/配方时间）→ 低温门控
     * （热量不足取料前零副作用返 0）→ 取料登记（不扣料）→ 链加工（副本单遍执行 + 逐物品真实配方
     * 流体需求累计 + processedLinks 跟踪）→ 批流体预检（不足整批零副作用）→ 输出预检
     * （probe-place-undo：整批逐组实放再按台账立即回滚，放不下整批零副作用）→ 扣料（吞入）→
     * 配方流体实扣 → 配方时间/记账/吞吐/处理窗口开窗 → 整批产出暂存（不再当场发放，
     * 进度读零后由单元 onPostTick 排空）。
     *
     * @param cluster   集群总控（拓扑、tier 与累计记账入口）
     * @param unit      物流单元（链、I/O 总线、输入仓流体结算面、配方时间与暂存产出字段持有者）
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

        // 2) 暂存产出未排空（r-logi-power-bind）：在飞产出/待排空产出仍占着输出预检结论，
        // 开新批会覆盖 stash 丢产出——拒绝开批（零副作用）
        if (unit.hasPendingOutputs()) return 0;

        // 3) 配方时间未到（r6 S3：批冷却即本批配方时间，由调用方按 20t 递减，本方法不递减）
        if (unit.getChainCooldownTicks() > 0) return 0;

        // 4) 低温门控（§3.6.4 取料前，决策 2）：热量不满 → 直接返 0 零副作用——不取料/不加工/
        // 不输出/不扣批流体/不记吞吐/不停机（低温不再吞批，热量回满后自动恢复开批）
        if (batchHost.heatFraction() < 1.0) return 0;

        // 5) 并行与输入：从物流单元自己的输入总线收集全部非 OTHER 形态的物品（决策 3：
        // ORE 与粉碎矿/污浊粉等全部中间态均收），只登记台账不扣料
        int runningLinks = 0;
        for (MTEBasicLogisticsUnit logistics : topology.getLogisticsUnits()) {
            if (logistics != null && logistics.isWorkInProgress()) runningLinks++;
        }
        BoosterState booster = BoosterState.aggregate(topology.getBoosterUnits(), Math.max(1, runningLinks));
        int parallel = ExecutionPlan.effectiveParallel(tier, booster);
        List<InputTake> takes = new ArrayList<>();
        unit.beginMEBusProcessing();
        int batch;
        try {
            batch = collectOreBatch(unit, parallel, takes);
        } finally {
            if (takes.isEmpty()) unit.endMEBusProcessing(cluster);
        }
        boolean meWindow = !takes.isEmpty();
        if (batch <= 0) return 0;

        // 6) 链加工（副本单遍执行，逐物品累计真实配方流体需求 + processedLinks 跟踪；此点零副作用）
        List<ItemStack> mid = new ArrayList<>(batch);
        for (InputTake take : takes) {
            for (int i = 0; i < take.amount; i++) {
                mid.add(GTUtility.copyAmountUnsafe(1, take.live));
            }
        }
        BatchFluidLedger fluids = new BatchFluidLedger();
        EnumSet<ChainLink> processedLinks = EnumSet.noneOf(ChainLink.class);
        List<ItemStack> outputs = runChain(chain, mid, unit, fluids, booster, processedLinks, tier);

        // 7) 批流体预检（§3.6.5-3）：本批将处理物品的累计需求任一不足 → 整批零副作用
        if (!fluids.isSatisfiable(unit)) {
            if (meWindow) unit.endMEBusProcessing(cluster);
            return 0;
        }

        // 8) 输出预检（r-logi-power-bind，probe-place-undo）：整批逐组证明放得下后立即按台账
        // 回滚——最终发放推迟到配方进度读零，若此刻放不下则后续排空必卡死，故开批前整批证明；
        // ME 条目只模拟不落地（realPlaceMe=false），回滚精确；
        // 失败（任一组无处可放，实放部分已内部回滚）→ 整批零副作用（输入未扣、流体未扣）
        List<OutputLedger> probeLedger = tryEmitOutputs(unit, outputs, false);
        if (probeLedger == null) {
            if (meWindow) unit.endMEBusProcessing(cluster);
            return 0;
        }
        rollbackOutputs(probeLedger);

        // 9) 扣料（IOF :306-319 口径：live 引用 stackSize -= take；0-size 槽由总线自身 tick 收口）
        // ——此后输入已吞入，在飞批次仅能经 MTEBasicLogisticsUnit.abortPendingRun 中止（吞料）
        applyTakes(takes);
        if (meWindow) unit.endMEBusProcessing(cluster);

        // 10) 提交：预检已证明输出可接收，实扣配方流体（§3.6.5-4，r6 S2：直接对物流单元输入仓
        // 跨仓结算）；此点之后不再回滚
        fluids.consume(unit);
        unit.markDirty();

        // 11) 配方时间与记账：整链时间按 1 tick=0.05s 向上取整，余数计 1 tick，最低 1 tick。
        List<ChainLink> processedLinksList = new ArrayList<>(processedLinks);
        long recipeTicks = Math.max(
            1L,
            (long) Math.ceil(ExecutionPlan.itemTimeSec(processedLinksList, tier, topology, booster) * 20D - 1e-9));
        unit.setChainCooldownTicks(recipeTicks);
        unit.onBatchProcessed(batch);

        // 12) 产出暂存（r-logi-power-bind）：不再当场发放——整批产出移交单元，进度读零后由单元
        // onPostTick 经 emitPendingOutputs 排空（放不下时空转重试，零消耗零丢料）
        unit.stashPendingOutputs(outputs);
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

        /** 普通水需求累计（mB：ORE_WASH/SIMPLE_WASH 命中配方的实际流体输入量）。 */
        private int plainWaterMb;

        /** 普通水命中流体实例（按配方 representative 记账；SIMPLE_WASH 同样按配方流体分类）。 */
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
            if (link != ChainLink.ORE_WASH && link != ChainLink.CHEM_BATH && link != ChainLink.SIMPLE_WASH) return;
            FluidStack rep = recipe.getRepresentativeFluidInput(0);
            if (rep == null || rep.getFluid() == null || rep.amount <= 0) return;
            if (link == ChainLink.ORE_WASH || link == ChainLink.SIMPLE_WASH) {
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
     * 否则/未命中查普通水路径（水 MAX，命中按配方 1000mB 计）；SIMPLE_WASH → 普通水信号（仅用于配方查询，实扣按配方流体输入）
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
                return findRecipe(map, stackCopy, Materials.Water.getFluid(ClusterParams.SIMPLE_WASH_RECIPE_PROBE_MB));
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
     * 配方产物掷取（IOF getOutputStack :558-581 移植 + §3.6.3 增幅 + r6-S6 粉碎副产物乘率，
     * T2/T6 重定义）：
     * <ul>
     * <li>主产物（输出槽 0）p = chance + 主产物增益之和（{@link BoosterState#getPrimaryBonus()}，
     * T6 起多模块加算）；副产物（槽 1+）p = chance + 副产物增益之和（加算）；
     * {@code booster == null} 按零增益；</li>
     * <li><b>p ≥ 1（T2/T6）</b>：floor(p) 份<b>整份输出保底复制</b>（原输出几件就多几件），
     * 余数再按概率复制一份——chance==10000 的保底主产物不再跳过增幅；</li>
     * <li><b>粉碎副产物乘率（r6-S6）</b>：链步为 CRUSH 时，副产物（仅槽 1+，不影响主产物）的
     * 增幅后概率先乘 {@code CRUSH_BYPRODUCT_MULT_NORMAL}=0.1（集群 tier0）/
     * {@code CRUSH_BYPRODUCT_MULT_STEEL}=0.5（钢级 tier1）/ {@code CRUSH_BYPRODUCT_MULT_HIGH_TIER}
     * =1.0 无削弱（钛级及以上 tier≥2）再判定；洗矿/离心等其他环节副产物不受影响；</li>
     * <li><b>p &lt; 1</b>：钳制 [0,1] 后按二项分布的正态近似 nextGaussian（mean=aTime·p、
     * std=sqrt(aTime·p·(1-p))，向上取整后乘 template.stackSize）——历史口径保留；</li>
     * <li>quantity≤0 的槽位不产出。</li>
     * </ul>
     */
    private static List<ItemStack> rollOutputs(GTRecipe recipe, int aTime, BoosterState booster, ChainLink link,
        int tier) {
        double primaryBonus = booster == null ? 0.0 : booster.getPrimaryBonus();
        double secondaryBonus = booster == null ? 0.0 : booster.getSecondaryBonus();
        boolean crushStep = link == ChainLink.CRUSH;
        double crushByproductMult = !crushStep ? 1.0
            : tier >= 2 ? ClusterParams.CRUSH_BYPRODUCT_MULT_HIGH_TIER
                : tier >= 1 ? ClusterParams.CRUSH_BYPRODUCT_MULT_STEEL : ClusterParams.CRUSH_BYPRODUCT_MULT_NORMAL;
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack template = recipe.getOutput(i);
            if (template == null) continue;

            int chance = recipe.getOutputChance(i);
            int quantity;
            {
                double p = chance / 10000.0 + (i == 0 ? primaryBonus : secondaryBonus);
                if (i > 0 && crushStep) p *= crushByproductMult;
                // T2/T6：增幅后概率可超过 100%（主/副产物皆然）——floor(p) 份整份输出保底复制
                // （原输出几件就多几件），余数再按概率 roll 一份；p<1 保持原正态近似口径。
                if (p >= 1.0D) {
                    int guaranteed = (int) Math.floor(p);
                    double remainder = p - guaranteed;
                    quantity = aTime * template.stackSize * guaranteed;
                    if (remainder > 0D && RANDOM.nextDouble() < remainder) quantity += aTime * template.stackSize;
                } else {
                    p = Math.max(0.0, Math.min(1.0, p));
                    // Normal-distribution approximation for probabilistic drops
                    double mean = aTime * p;
                    double std = Math.sqrt(aTime * p * (1 - p));
                    quantity = (int) Math.ceil(std * RANDOM.nextGaussian() + mean);
                    quantity *= template.stackSize;
                }
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
     * 产物写入/输出预检共用实现（普通总线 probe-place-ledger + ME 总线兜底双趟）：
     * <ol>
     * <li><b>趟 1（普通总线）</b>：每组产物依次对每个普通总线做 storePartial 模拟探测（探测会
     * 扣减入参 stackSize，故用副本），整组放得下才实放（<b>实放同样传副本</b>——入参
     * {@code outputs} 各堆尺寸保持不变，预检回滚与暂存重试共用同一列表）并记台账；后续组的
     * 探测可见先续组的实放结果，累积可放性成立。</li>
     * <li><b>趟 2（ME 总线兜底）</b>：趟 1 无处可放的组改投 ME 总线——先逐组模拟探测<b>全部</b>
     * 选定可行目标，再一次性实放。ME 实放走 addToCache 无容量门控（{@link GTSROutputBusCompat}
     * 类注释），探测过即实放必成；且回滚台账<b>永不含 ME 条目</b>（ME cache 不在 mInventory 槽，
     * {@link #rollbackOutputs} 对 ME 恒 no-op）——失败路径上 ME 零实放，杜绝预检/排空重试对
     * ME cache 的凭空多发与双份。</li>
     * </ol>
     * 趟 1 任一组普通放不下转入趟 2，趟 2 任一组全部 ME 探测失败 → 回滚已实放的普通条目并
     * 返回 null（普通零残留；ME 未动）。组落点偏好普通总线、ME 仅兜底。
     *
     * @param realPlaceMe 预检（false）= ME 只模拟不落地（台账只含普通条目，立即回滚即
     *                    probe-place-undo 精确还原）；排空实发（true）= ME 全组探测齐备后真实落地
     * @return 实放台账（仅普通条目；ME 不入账）；null = 放不下（已内部回滚普通部分）
     */
    private static List<OutputLedger> tryEmitOutputs(MTEBasicLogisticsUnit unit, List<ItemStack> outputs,
        boolean realPlaceMe) {
        var buses = GTUtility.validMTEList(unit.getLogisticsOutputBusses());
        List<OutputLedger> ledger = new ArrayList<>();
        List<ItemStack> meOverflow = new ArrayList<>();
        // 趟 1：普通总线逐组探测→实放（副本）→台账
        for (ItemStack out : outputs) {
            if (GTUtility.isStackInvalid(out)) continue;
            boolean placed = false;
            for (MTEHatchOutputBus bus : buses) {
                if (bus instanceof MTEHatchOutputBusME) continue;
                if (!GTSROutputBusCompat.storePartial(bus, GTUtility.copyOrNull(out), true)) continue;
                int amount = out.stackSize;
                GTSROutputBusCompat.storePartial(bus, GTUtility.copyOrNull(out), false);
                ledger.add(new OutputLedger(bus, GTUtility.stackToInt(out), amount));
                placed = true;
                break;
            }
            if (!placed) meOverflow.add(out);
        }
        // 趟 2：ME 兜底——先全部模拟探测选定目标（任一失败即整趟零落地回滚普通台账），再一次性实放
        MTEHatchOutputBus[] meTargets = new MTEHatchOutputBus[meOverflow.size()];
        for (int g = 0; g < meOverflow.size(); g++) {
            ItemStack out = meOverflow.get(g);
            for (MTEHatchOutputBus bus : buses) {
                if (!(bus instanceof MTEHatchOutputBusME)) continue;
                if (!GTSROutputBusCompat.storePartial(bus, GTUtility.copyOrNull(out), true)) continue;
                meTargets[g] = bus;
                break;
            }
            if (meTargets[g] == null) {
                rollbackOutputs(ledger);
                return null;
            }
        }
        if (realPlaceMe) {
            for (int g = 0; g < meOverflow.size(); g++) {
                GTSROutputBusCompat.storePartial(meTargets[g], GTUtility.copyOrNull(meOverflow.get(g)), false);
            }
        }
        return ledger;
    }

    /**
     * 排空暂存产出（配方运行绑定，MTEBasicLogisticsUnit.onPostTick 配方进度读零后逐 t 调用；
     * 包级静态供单元包内直调）：普通总线探测-实放-失败回滚 + ME 全组探测齐备后一次性实放，
     * 全部组放得下才算成功；任一组放不下则普通部分按台账回滚（ME 未动）并返回 false，调用方
     * 保留暂存下 tick 重试（输出总线满 = 空转等排空，零消耗零丢料）。实放传副本，入参列表
     * 尺寸不受影响，重试与回滚共用同一暂存列表。
     *
     * @return true = 整批产出已全部实放（调用方清空暂存）；false = 空间不足（暂存保留重试）
     */
    static boolean emitPendingOutputs(MTEBasicLogisticsUnit unit, List<ItemStack> outputs) {
        return tryEmitOutputs(unit, outputs, true) != null;
    }

    /**
     * 实放回滚：按台账在各<b>普通</b>总线内扫同 id 堆扣回登记数量（扣空槽置 null）。台账按构造
     * 不变量永不含 ME 条目（ME cache 不在槽表、扣回恒 no-op——见 {@link #tryEmitOutputs} 趟 2），
     * 回滚精确。实放可能并入既有同类堆，回滚按物品多重集复原——槽位排布可能与回滚前有微小
     * 差异（GT 总线本就周期 compact，无语义影响）。
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
