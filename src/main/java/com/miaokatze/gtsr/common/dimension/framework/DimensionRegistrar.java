package com.miaokatze.gtsr.common.dimension.framework;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 维度注册/冲突检测/回滚唯一入口（dim1 S1）。
 * <p>
 * 冲突检测顺序（照 plan §S1 / 01 §2.2 resolveDimSlot 语义）：
 * <ol>
 * <li>总开关（planDimension.*）——关闭即禁用，不触碰 DimensionManager；</li>
 * <li>{@code DimensionManager.isDimensionRegistered(dimId)}——维度 ID 已被其他 mod 占用即禁用；</li>
 * <li>providerId 占用探测——{@code registerProviderType} 对已占用 ID 返回 false 即禁用并回滚；</li>
 * <li>{@code registerDimension} 抛异常——回滚 providerType 注册，记 -1 禁用告警。</li>
 * </ol>
 * 任一冲突均记 -1 禁用（维度不存在，其余功能零影响），不回退到 getNextFreeDimId。
 * <p>
 * 本切片不注册任何 IWorldGenerator（S4/S6 责任）。
 */
public final class DimensionRegistrar {

    /** 已成功注册维度的 def 查找表：resolvedDimId → def（供 WorldProvider 运行期回查 def）。 */
    private static final Map<Integer, GTSRDimensionDef> DEF_BY_DIM_ID = new HashMap<>();

    private DimensionRegistrar() {}

    /**
     * preInit 维度注册入口：按序解析并注册各 def（冲突记 -1 禁用告警）。
     */
    public static void preInitDimensions(GTSRDimensionDef... defs) {
        for (GTSRDimensionDef def : defs) {
            registerOne(def);
        }
    }

    private static void registerOne(GTSRDimensionDef def) {
        // 1) 总开关：关闭即禁用（解析保持 -1），不触碰 DimensionManager
        if (!def.isMasterSwitchEnabled()) {
            GTSteamReborn.LOG
                .info("[GTSR] dimension " + def.getKey() + " disabled by master switch (planDimension config)");
            return;
        }
        // 2) 维度 ID 冲突：已被其他 mod 注册即禁用（不抢占、不迁移 ID）
        if (DimensionManager.isDimensionRegistered(def.getRequestedDimId())) {
            GTSteamReborn.LOG.warn(
                "[GTSR] dimension " + def.getKey()
                    + " disabled: dimId="
                    + def.getRequestedDimId()
                    + " is already registered by another provider (check config prosperity/shatteredDimId)");
            return;
        }
        // 3) providerId 占用探测：registerProviderType 对已占用 ID 返回 false
        if (!DimensionManager.registerProviderType(def.getRequestedProviderId(), def.getProviderClass(), false)) {
            GTSteamReborn.LOG.warn(
                "[GTSR] dimension " + def.getKey()
                    + " disabled: providerId="
                    + def.getRequestedProviderId()
                    + " is already occupied (check config prosperity/shatteredProviderId)");
            return;
        }
        // 4) 维度注册 + 失败回滚：任何异常都回滚 providerType 并记 -1，维度不存在
        try {
            DimensionManager.registerDimension(def.getRequestedDimId(), def.getRequestedProviderId());
        } catch (Throwable t) {
            DimensionManager.unregisterProviderType(def.getRequestedProviderId());
            GTSteamReborn.LOG
                .error("[GTSR] dimension " + def.getKey() + " registration failed, rolled back and disabled", t);
            return;
        }
        def.markRegistered(def.getRequestedDimId(), def.getRequestedProviderId());
        DEF_BY_DIM_ID.put(def.getResolvedDimId(), def);
        GTSteamReborn.LOG.info(
            "[GTSR] dimension " + def.getKey()
                + " registered: dimId="
                + def.getResolvedDimId()
                + " providerId="
                + def.getResolvedProviderId());
    }

    /** 运行期按维度 ID 回查 def（WorldProvider 构造 ChunkManager/Generator 用）；未注册返回 null。 */
    public static GTSRDimensionDef defForDimension(int dimId) {
        return DEF_BY_DIM_ID.get(dimId);
    }
}
