package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.util.GTSROutputBusCompat;

import gregtech.api.enums.Materials;
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
 * :432-509、getOutputStack :558-581、doCompress :583-599。
 *
 * <p>
 * 冷却模型：每单元 {@code chainCooldownTicks}（20 - 新批耗时 tick，{@link
 * MTEBasicLogisticsUnit#getChainCooldownTicks()}），总控每 20t 调用本方法一次并统一 -20，
 * ≤0 且全部门控通过时瞬时执行一批并重置冷却（本方法不递减冷却，由调用方负责）。
 *
 * <p>
 * 零副作用事务：门控（含批流体不足）未过即返 0 且无任何状态变化；扣料（步骤 5）虽按
 * IOF 口径改写输入总线 live 引用的 stackSize，但 updateSlots 推迟到提交点——产出探测失败
 * （输出满）时把已扣数量原样加回、已实放的产物按 (bus,itemId,amount) 台账回滚，整批
 * 表现为未发生。链加工全程只操作取料副本，透传路径不动输入总线原 stack 引用。
 *
 * <p>
 * 并行切片契约依赖（同包同批落地）：{@code BoosterState.aggregate(List)/getSpeedBonus()}、
 * {@code ExecutionPlan.effectiveParallel/itemTimeSec/chainThroughputPerSec}、
 * {@link LogisticsChain#isExecutable(ClusterTopology)}，以及总控公开方法
 * {@code getClusterInputBusses()/getClusterOutputBusses()/updateClusterSlots()/
 * setLastThroughputOrePerSec(double)}——主代理接线。
 *
 * <p>
 * 线程模型：仅服务器主线程调用（与 IOF / 集群其余部分一致）。
 */
public final class ClusterChainExecutor {

    /** 概率副产物的正态近似随机源（IOF :145 同款 XSTR；静态单例，仅主线程使用）。 */
    private static final XSTR RANDOM = new XSTR();

    private ClusterChainExecutor() {}

    /**
     * 推进一个物流单元的链批处理（每秒由总控 runChains 调用一次；冷却未到直接返回）。
     * 冷却模型：每单元 chainCooldownTicks 字段（20-新批耗时 tick），总控每 20t 调用时统一 -20，≤0 且
     * 全部门控通过则瞬时执行一批并重置冷却。
     *
     * @param cluster 集群总控（输入/输出总线、拓扑、tier 与记账入口）
     * @param unit    物流单元（链、双 tank、冷却字段持有者）
     * @return 本批实际处理矿数（0=未执行）
     */
    public static int executeBatch(MTESteamMineralLogisticsCluster cluster, MTEBasicLogisticsUnit unit) {
        if (cluster == null || unit == null) return 0;
        LogisticsChain chain = unit.getChain();
        if (chain == null || chain.isEmpty()) return 0;

        // 1) 门控：开机 + 链可执行 + tier 有效
        int tier = cluster.getStructureTierIndex();
        ClusterTopology topology = cluster.getTopology();
        if (!cluster.isMachineEnabled() || !chain.isExecutable(topology) || tier < 0) return 0;

        // 2) 冷却未到（冷却由调用方递减，本方法不递减）
        if (unit.getChainCooldownTicks() > 0) return 0;

        // 3) 并行与输入：从总控输入总线收集形态为 ORE 的物品，可取总数 total，batch=min(parallel,total)
        BoosterState booster = BoosterState.aggregate(topology.getBoosterUnits());
        int parallel = ExecutionPlan.effectiveParallel(tier, booster);
        List<InputTake> takes = new ArrayList<>();
        int batch = collectOreBatch(cluster, parallel, takes);
        if (batch <= 0) return 0;

        // 4) 批流体：洗矿需水 tank ≥1000L、化洗需化浴 tank ≥1000L（hasBatchFluids 复用）。
        // 不足返 0（状态色由 unit.getUnitStatus 既有逻辑呈现；此点尚未扣料，天然零副作用）
        boolean needWater = chain.countOf(ChainLink.ORE_WASH) > 0;
        boolean needChemBath = chain.countOf(ChainLink.CHEM_BATH) > 0;
        if (!unit.hasBatchFluids(needWater, needChemBath)) return 0;

        // 5) 扣料：按 IOF :306-319 从输入总线 live 引用扣 batch 个——先 copyAmountUnsafe
        // 生成 batch 个 size-1 副本（IOF :316 先 copy 后扣的同序），再 ore.stackSize -= take；
        // updateSlots 推迟到提交点，保证产出探测失败时 0-size 槽引用仍在、可原样加回
        List<ItemStack> mid = new ArrayList<>(batch);
        for (InputTake take : takes) {
            for (int i = 0; i < take.amount; i++) {
                mid.add(GTUtility.copyAmountUnsafe(1, take.live));
            }
        }
        applyTakes(takes);

        // 6) 链加工：batch 个 stackSize=1 副本逐 link 推进（形态过滤→查配方→null 透传），
        // 每步尾 doCompress 合并同类项（IOF :583-599）
        List<ItemStack> outputs = runChain(chain, mid, unit);

        // 7) 产出：合并产物列表写入总控输出总线——整组放得下才实放（聚合器 tryOutputOre
        // 探测-实放范式，经 GTSROutputBusCompat 兼容 ME 总线）；任一组无处可放即整批
        // 回滚（已实放部分按台账扣回 + 输入加回），输出满零副作用
        if (!tryEmitOutputs(cluster, outputs)) {
            restoreInputs(takes);
            return 0;
        }

        // 提交：总控侧 updateSlots 收口（把扣空的输入槽 null 化/压缩；此点之后不再回滚）
        cluster.updateClusterSlots();

        // 批流体实扣（此前仅做门槛判定；每批 1000L，水/化浴液各按需）
        if (needWater) unit.getWaterTank()
            .drain(ClusterParams.WASH_WATER_PER_BATCH_L, true);
        if (needChemBath) unit.getChemBathTank()
            .drain(ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L, true);
        unit.markDirty();

        // 8) 冷却与记账
        List<ChainLink> links = chain.getLinks();
        long cooldown = Math.max(20L, (long) (ExecutionPlan.itemTimeSec(links, tier, topology, booster) * 20L));
        unit.setChainCooldownTicks(cooldown);
        cluster.addProcessedOre(batch);
        cluster.setLastThroughputOrePerSec(ExecutionPlan.chainThroughputPerSec(links, tier, topology, booster));
        return batch;
    }

    // ==================== 取料（IOF :306-319） ====================

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
     * 按剩余并行数从总控输入总线收集形态为 {@link ClusterItemForms.OreForm#ORE} 的物品，
     * 只登记台账不扣料：每源堆 take = min(remaining, stackSize)（IOF :309-318 取料口径），
     * live 引用与槽位记入 {@link InputTake}，实际扣减由 {@link #applyTakes} 执行。
     *
     * @return 登记的可取总数（≤parallel；即 min(parallel, 总线内 ORE 可取总数)）
     */
    private static int collectOreBatch(MTESteamMineralLogisticsCluster cluster, int parallel, List<InputTake> takes) {
        int remaining = parallel;
        for (MTEHatchInputBus bus : GTUtility.validMTEList(cluster.getClusterInputBusses())) {
            if (remaining <= 0) break;
            for (int i = 0, n = bus.getSizeInventory(); i < n && remaining > 0; i++) {
                ItemStack ore = bus.getStackInSlot(i);
                if (GTUtility.isStackInvalid(ore)) continue;
                if (ClusterItemForms.classify(ore) != ClusterItemForms.OreForm.ORE) continue;

                int take = Math.min(remaining, ore.stackSize);
                takes.add(new InputTake(bus, i, ore, take));
                remaining -= take;
            }
        }
        return parallel - remaining;
    }

    /** 扣料执行（IOF :317 原式）：对每个 live 引用 stackSize -= take（updateSlots 由提交点统一收口）。 */
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

    // ==================== 链加工（IOF processStep :413-430） ====================

    /**
     * 逐 link 推进中产物：对每个 stack 先做形态约束过滤（并集口径见 {@link #acceptsForm}），
     * 命中则查配方——命中取 {@link #rollOutputs}（IOF :558-581 移植），null 原样透传；
     * 每步尾 {@link #compress} 合并同类项（IOF :583-599 移植）。SIMPLE_WASH 配方图缺失
     * （GT++ 不在场，{@link ChainLink#getRecipeMap()} 返回 null）时该步整体透传。
     *
     * @return 合并后的最终产物列表（调用方负责写入输出总线）
     */
    private static List<ItemStack> runChain(LogisticsChain chain, List<ItemStack> mid, MTEBasicLogisticsUnit unit) {
        boolean seenReduction = false;
        for (ChainLink link : chain.getLinks()) {
            boolean firstReduction = false;
            if (link == ChainLink.CRUSH || link == ChainLink.HAMMER) {
                firstReduction = !seenReduction;
                seenReduction = true;
            }
            RecipeMap<?> map = link.getRecipeMap();
            FluidStack fluidSignal = fluidSignal(link, unit);

            List<ItemStack> output = new ArrayList<>(mid.size());
            for (ItemStack stack : mid) {
                ClusterItemForms.OreForm form = ClusterItemForms.classify(stack);
                if (map == null || !acceptsForm(link, form, firstReduction)) {
                    output.add(stack);
                    continue;
                }
                GTRecipe recipe = findRecipe(map, GTUtility.copyOrNull(stack), fluidSignal);
                if (recipe != null) {
                    output.addAll(rollOutputs(recipe, stack.stackSize));
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
     * FURNACE 收任意非 OTHER 形态。
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
     * 各 link 查配方的流体匹配信号（仅匹配用，批流体已在门槛/提交两处单独处理）：
     * ORE_WASH → 蒸馏水 MAX（IOF :446）；SIMPLE_WASH → 水 100L（IOF :489）；
     * CHEM_BATH → 化浴 tank 当前流体 1000L 副本（IOF :503-506 按仓内实际流体的口径）；
     * 其余 link 无流体信号。
     */
    private static FluidStack fluidSignal(ChainLink link, MTEBasicLogisticsUnit unit) {
        switch (link) {
            case ORE_WASH:
                return GTModHandler.getDistilledWater(Integer.MAX_VALUE);
            case SIMPLE_WASH:
                return Materials.Water.getFluid(100);
            case CHEM_BATH: {
                FluidStack chem = unit.getChemBathTank()
                    .getFluid();
                return chem == null ? null : new FluidStack(chem, ClusterParams.CHEM_BATH_FLUID_PER_BATCH_L);
            }
            default:
                return null;
        }
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
     * 配方产物掷取（IOF getOutputStack :558-581 移植）：chance==10000 → quantity = aTime ×
     * template.stackSize；概率项按二项分布的正态近似 nextGaussian（mean=aTime·p、
     * std=sqrt(aTime·p·(1-p))，向上取整后乘 template.stackSize）。quantity≤0 的槽位不产出。
     */
    private static List<ItemStack> rollOutputs(GTRecipe recipe, int aTime) {
        List<ItemStack> outputs = new ArrayList<>();
        for (int i = 0; i < recipe.mOutputs.length; i++) {
            ItemStack template = recipe.getOutput(i);
            if (template == null) continue;

            int chance = recipe.getOutputChance(i);
            int quantity;
            if (chance == 10000) {
                quantity = aTime * template.stackSize;
            } else {
                // Normal-distribution approximation for probabilistic drops
                double p = chance / 10000.0;
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

    // ==================== 产出回填（聚合器 tryOutputOre 探测-实放范式） ====================

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
     * 产物写入总控输出总线：每组产物依次对每个总线做 storePartial 模拟探测（探测会扣减入参
     * stackSize，故用副本），整组放得下才实放（聚合器 tryOutputOre 同式，经
     * {@link GTSROutputBusCompat} 兼容 ME 总线 cache 满语义）。任一组全部总线都放不下 →
     * 回滚此前已实放的各组并返回 false（调用方再加回输入，整批零副作用）。逐组「探测→实放」
     * 之间无外部写入（同 tick 同线程），后续组的探测可见先续组的实放结果，无自我竞争误判。
     */
    private static boolean tryEmitOutputs(MTESteamMineralLogisticsCluster cluster, List<ItemStack> outputs) {
        List<OutputLedger> ledger = new ArrayList<>();
        for (ItemStack out : outputs) {
            if (GTUtility.isStackInvalid(out)) continue;
            boolean placed = false;
            for (MTEHatchOutputBus bus : GTUtility.validMTEList(cluster.getClusterOutputBusses())) {
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
