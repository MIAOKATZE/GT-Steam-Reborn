package com.miaokatze.gtsr.common.machine.cluster;

/**
 * 集群参数中心：蒸汽集群系统全部数值常量的唯一存放处，其它类禁止散落硬编码数值。
 * <p>
 * 所有数值均取自 plan-prompt §6.2 拍板结果；数组下标一律对应 {@link ClusterTier} 序号（青铜/钢/钛/钨钢）。
 */
public final class ClusterParams {

    /** 私有构造，禁止实例化常量类。 */
    private ClusterParams() {}

    // ==================== 集群层级 ====================

    /** 集群层级总数（青铜/钢/钛/钨钢）。 */
    public static final int TIER_COUNT = 4;

    /** 各层级基准耗时因子，下标对应层级序号。 */
    public static final double[] TIER_TIME_FACTOR = { 1.0, 0.8, 0.6, 0.4 };

    /** 各层级蒸汽消耗倍率，下标对应层级序号。 */
    public static final double[] TIER_STEAM_MULT = { 1.0, 1.5, 2.5, 4.0 };

    /** 单元链基准蒸汽消耗（L/s）。 */
    public static final int BASE_CHAIN_STEAM_LPS = 8000;

    /** 链长蒸汽消耗增长倍率。 */
    public static final double CHAIN_LENGTH_MULT = 1.5;

    /** 单条物流链的最大链步数（服务端强制上限，plan §3.6.7）。 */
    public static final int CHAIN_MAX_LINKS = 16;

    /** 主段结构深度（格，plan §3.2.1/3.2.2：宽 29 × 高 15 × 主段深 20，含并入的 8 深基础延伸图案）。 */
    public static final int SEGMENT_DEPTH_MAIN = 20;

    /** 延伸段结构深度（格/段，plan §3.2.2）。 */
    public static final int SEGMENT_DEPTH_EXT = 8;

    // ==================== 增幅剂 ====================

    /** 各增幅剂的蒸汽惩罚倍率，下标对应 BoosterType 序号。 */
    public static final double[] BOOSTER_PENALTY_MULT = { 1.3, 1.4, 2.0, 1.6, 1.1 };

    /** 节汽增幅的蒸汽节省比例上限。 */
    public static final double STEAM_SAVER_CAP = 0.48;

    /** 各层级并行增幅点数，下标对应层级序号。 */
    public static final int[] BOOSTER_PARALLEL_VALUES = { 4, 8, 24, 48 };

    /** 各层级速度增幅百分比，下标对应层级序号。 */
    public static final int[] BOOSTER_SPEED_PCT = { 5, 10, 30, 40 };

    /** 速度/并行模块对其他增幅模块流体消耗的加成百分比，下标对应<b>施加方</b>层级序号。 */
    public static final int[] BOOSTER_SURCHARGE_PCT = { 5, 10, 30, 40 };

    /** 各层级主产出增幅百分比，下标对应层级序号。 */
    public static final int[] BOOSTER_PRIMARY_PCT = { 5, 10, 15, 20 };

    /** 各层级副产出增幅百分比，下标对应层级序号。 */
    public static final int[] BOOSTER_SECONDARY_PCT = { 1, 2, 4, 5 };

    /** 各层级节汽增幅百分比，下标对应层级序号。 */
    public static final int[] BOOSTER_SAVER_PCT = { 2, 4, 8, 12 };

    /** 增幅剂储罐容量（L）。 */
    public static final int BOOSTER_TANK_CAPACITY_L = 16_000;

    /** 并行增幅（锁定流体：硝酸）各层级增幅液每秒消耗（L/s），下标对应层级序号。 */
    public static final int[] AMPLIFIER_NITRIC_ACID_LPS = { 50, 200, 1000, 2000 };

    /** 速度增幅（锁定流体：盐酸）各层级增幅液每秒消耗（L/s），下标对应层级序号。 */
    public static final int[] AMPLIFIER_HYDROCHLORIC_ACID_LPS = { 60, 300, 1500, 3000 };

    /** 主产物增幅（锁定流体：硫酸）各层级增幅液每秒消耗（L/s），下标对应层级序号。 */
    public static final int[] AMPLIFIER_SULFURIC_ACID_LPS = { 80, 400, 2000, 4000 };

    /** 副产物增幅（锁定流体：氯化铵）各层级增幅液每秒消耗（L/s），下标对应层级序号。 */
    public static final int[] AMPLIFIER_AMMONIUM_CHLORIDE_LPS = { 20, 80, 300, 500 };

    /** 节汽增幅（锁定流体：SuperCoolant）各层级增幅液每秒消耗（L/s），下标对应层级序号。 */
    public static final int[] AMPLIFIER_SUPER_COOLANT_LPS = { 10, 50, 200, 400 };

    /** 节汽增幅冷却液流体 ID；解析失败回退 Materials.SuperCoolant，由使用方处理。 */
    public static final String BOOSTER_COOLANT_FLUID = "ic2coolant";

    /**
     * 按增幅类型取该层级的增幅液每秒消耗（L/s）：五表按 {@link BoosterType} ordinal 分发。
     *
     * @param type    增幅类型（非 null）
     * @param tierIdx 层级下标，越界按边界截断
     * @return 对应增幅液每秒消耗（L/s）
     */
    public static int amplifierFluidLps(BoosterType type, int tierIdx) {
        int idx = Math.max(0, Math.min(tierIdx, TIER_COUNT - 1));
        switch (type) {
            case SPEED:
                return AMPLIFIER_HYDROCHLORIC_ACID_LPS[idx];
            case PRIMARY_OUTPUT:
                return AMPLIFIER_SULFURIC_ACID_LPS[idx];
            case SECONDARY_OUTPUT:
                return AMPLIFIER_AMMONIUM_CHLORIDE_LPS[idx];
            case STEAM_SAVER:
                return AMPLIFIER_SUPER_COOLANT_LPS[idx];
            case PARALLEL:
            default:
                return AMPLIFIER_NITRIC_ACID_LPS[idx];
        }
    }

    // ==================== 预热与停机衰减 ====================

    /** 集群固定蒸汽消耗（L/s）：预热全额口径与运行保温下限共用（原 PREHEAT_STEAM_LPS，r6 重定义）。 */
    public static final int FIXED_CLUSTER_STEAM_LPS = 8000;

    /** 各层级固定蒸汽乘率，下标对应层级序号（供经济切片对固定蒸汽按档缩放）。 */
    public static final int[] FIXED_STEAM_TIER_MULT = { 1, 4, 16, 48 };

    /** 预热持续时间（秒）。 */
    public static final int PREHEAT_SECONDS = 30;

    /** 停机后进度衰减速度（%/秒）。 */
    public static final double SHUTDOWN_DECAY_PCT_PER_SEC = 1.0;

    /** 蒸汽断供后进度衰减速度（%/秒）。 */
    public static final double STEAM_LOSS_DECAY_PCT_PER_SEC = 0.5;

    // ==================== 辅助流体与持续供电 ====================

    /** 集群本体润滑剂消耗（L/s），下标对应集群层级序号（r6：按档取代旧恒定 LUBRICANT_LPS=10）。 */
    public static final int[] CLUSTER_LUBRICANT_LPS = { 20, 80, 500, 1000 };

    /** 物流单元润滑剂消耗（L/s），下标对应物流单元 unitStructureTier 序号。 */
    public static final int[] LOGISTICS_UNIT_LUBRICANT_LPS = { 20, 60, 300, 500 };

    /** 每批次清洗用水量（L）。 */
    public static final int WASH_WATER_PER_BATCH_L = 1000;

    /** 每批次化学浸浴液用量（L）。 */
    public static final int CHEM_BATH_FLUID_PER_BATCH_L = 1000;

    /** 简易洗矿每命中物品的普通水消耗（mB，附录 B；按实际命中物品数累计）。 */
    public static final int SIMPLE_WASH_WATER_PER_ITEM_MB = 100;

    /** 磁选单元持续供电需求（LV 电压档 EU/t），用户拍板：磁选需持续供电；合计 = 本值 × MAGNETIC_AMPERAGE。 */
    public static final int MAGNETIC_EU_PER_TICK = 32;

    /** 磁选单元供电安培数（LV × 1A = 32EU/t 合计）。 */
    public static final int MAGNETIC_AMPERAGE = 1;

    /** 热力离心单元持续供电需求（LV 电压档 EU/t），用户拍板：热力离心需持续供电。 */
    public static final int THERMOCENTRIFUGE_EU_PER_TICK = 32;

    /** 热力离心单元供电安培数（LV × 3A = 96EU/t 合计）。 */
    public static final int THERMOCENTRIFUGE_AMPERAGE = 3;

    // ==================== 粉碎副产物乘率 ====================

    /** 粉碎链步副产物乘率（常规口径）。 */
    public static final double CRUSH_BYPRODUCT_MULT_NORMAL = 0.1;

    /** 粉碎链步副产物乘率（钢级处理口径）。 */
    public static final double CRUSH_BYPRODUCT_MULT_STEEL = 0.5;

    // ==================== 物流单元 ====================

    /** 各层级物流单元基准并行数，下标对应层级序号。 */
    public static final int[] LOGISTICS_BASE_PARALLEL = { 4, 8, 24, 48 };

    /** 物流链步基础耗时（tick，8s=160t；无蒸汽消耗——见 {@link ChainLink} 基础表口径）。 */
    public static final int LOGISTICS_LINK_BASE_TICKS = 160;

    /** 各层级物流单元处理耗时（秒，0 表示即时完成），下标对应层级序号。 */
    public static final int[] LOGISTICS_TIME_SEC = { 10, 6, 2, 0 };

    /** 物流单元储罐容量（L）。 */
    public static final int LOGISTICS_TANK_CAPACITY_L = 16_000;

    /** 单元周期重连节流间隔（tick，NAC 范式）。 */
    public static final int RECONNECT_INTERVAL_TICKS = 20;

    // ==================== GUI ====================

    /** 集群 GUI 宽度（像素）。 */
    public static final int GUI_WIDTH = 620;

    /** 集群 GUI 高度（像素）。 */
    public static final int GUI_HEIGHT = 340;

    /**
     * 集群层级枚举：青铜(0) → 钢(1) → 钛(2) → 钨钢(3)，序号即层级下标。
     */
    public enum ClusterTier {

        /** 青铜层级。 */
        BRONZE,
        /** 钢层级。 */
        STEEL,
        /** 钛层级。 */
        TITANIUM,
        /** 钨钢层级。 */
        TUNGSTENSTEEL;

        /** @return 层级下标（等于 ordinal）。 */
        public int getIndex() {
            return ordinal();
        }

        /** @return 本地化键（gtsr.gui.cluster.tier.*）。 */
        public String getLangKey() {
            switch (this) {
                case STEEL:
                    return "gtsr.gui.cluster.tier.steel";
                case TITANIUM:
                    return "gtsr.gui.cluster.tier.titanium";
                case TUNGSTENSTEEL:
                    return "gtsr.gui.cluster.tier.tungstensteel";
                case BRONZE:
                default:
                    return "gtsr.gui.cluster.tier.bronze";
            }
        }

        /** @return 英文名称（用于日志与调试显示）。 */
        public String getEnglishName() {
            switch (this) {
                case STEEL:
                    return "Steel";
                case TITANIUM:
                    return "Titanium";
                case TUNGSTENSTEEL:
                    return "Tungstensteel";
                case BRONZE:
                default:
                    return "Bronze";
            }
        }

        /**
         * 按下标取层级。
         *
         * @param idx 层级下标
         * @return 对应层级；越界时回退到 BRONZE
         */
        public static ClusterTier get(int idx) {
            ClusterTier[] tiers = values();
            if (idx < 0 || idx >= tiers.length) {
                return BRONZE;
            }
            return tiers[idx];
        }
    }

    /**
     * 增幅剂类型枚举：并行、速度、主产出、副产出、节汽。
     * <p>
     * 顺序冻结：拓扑快照协议以 {@code 8 + ordinal()} 编码增幅槽 typeId（MTESteamMineralLogisticsCluster
     * ↔ ClusterGuiSync 两端），仅允许尾部追加，不得重排/插队/改名。
     */
    public enum BoosterType {

        /** 并行增幅剂。 */
        PARALLEL,
        /** 速度增幅剂。 */
        SPEED,
        /** 主产出增幅剂。 */
        PRIMARY_OUTPUT,
        /** 副产出增幅剂。 */
        SECONDARY_OUTPUT,
        /** 节汽增幅剂。 */
        STEAM_SAVER;

        /** @return 本地化键（gtsr.gui.cluster.booster.*）。 */
        public String getLangKey() {
            switch (this) {
                case SPEED:
                    return "gtsr.gui.cluster.booster.speed";
                case PRIMARY_OUTPUT:
                    return "gtsr.gui.cluster.booster.primary";
                case SECONDARY_OUTPUT:
                    return "gtsr.gui.cluster.booster.secondary";
                case STEAM_SAVER:
                    return "gtsr.gui.cluster.booster.steam_saver";
                case PARALLEL:
                default:
                    return "gtsr.gui.cluster.booster.parallel";
            }
        }

        /** @return 增幅液本地化键（gtsr.gui.cluster.booster.fluid.*，后缀与 getLangKey 一致）。 */
        public String getFluidLangKey() {
            switch (this) {
                case SPEED:
                    return "gtsr.gui.cluster.booster.fluid.speed";
                case PRIMARY_OUTPUT:
                    return "gtsr.gui.cluster.booster.fluid.primary";
                case SECONDARY_OUTPUT:
                    return "gtsr.gui.cluster.booster.fluid.secondary";
                case STEAM_SAVER:
                    return "gtsr.gui.cluster.booster.fluid.steam_saver";
                case PARALLEL:
                default:
                    return "gtsr.gui.cluster.booster.fluid.parallel";
            }
        }

        /**
         * 取指定层级下本增幅剂的数值（从上方四档表取值）。
         *
         * @param tierIdx 层级下标（0 至 TIER_COUNT-1），越界时按边界截断
         * @return 对应四档表中的数值
         */
        public int getBoosterValue(int tierIdx) {
            int idx = Math.max(0, Math.min(tierIdx, TIER_COUNT - 1));
            switch (this) {
                case SPEED:
                    return BOOSTER_SPEED_PCT[idx];
                case PRIMARY_OUTPUT:
                    return BOOSTER_PRIMARY_PCT[idx];
                case SECONDARY_OUTPUT:
                    return BOOSTER_SECONDARY_PCT[idx];
                case STEAM_SAVER:
                    return BOOSTER_SAVER_PCT[idx];
                case PARALLEL:
                default:
                    return BOOSTER_PARALLEL_VALUES[idx];
            }
        }

        /** @return 本增幅剂的蒸汽惩罚倍率（BOOSTER_PENALTY_MULT[ordinal]）。 */
        public double getPenaltyMultiplier() {
            return BOOSTER_PENALTY_MULT[ordinal()];
        }
    }

    /**
     * 蒸汽种类折算与层级门控（供 S8 蒸汽经济切片使用）：折算系数表示相对普通 Steam 的能量密度，
     * 等效消耗 = 本种类流量 ÷ {@link #getDivisor()}（如 DenseSuperheatedSteam 1L 折合普通蒸汽 2000L，
     * 等效口径下消耗除以 2000）。
     */
    public enum SteamGrade {

        /** 普通 Steam：×1（流体注册名 steam）。 */
        STEAM(1, "steam"),
        /** 过热蒸汽 SuperheatedSteam：÷2（流体注册名 ic2superheatedsteam）。 */
        SUPERHEATED_STEAM(2, "ic2superheatedsteam"),
        /** 超临界蒸汽 SupercriticalSteam：÷4（流体注册名 supercriticalsteam）。 */
        SUPERCRITICAL_STEAM(4, "supercriticalsteam"),
        /** 密集蒸汽 DenseSteam：÷1000（流体注册名 densesteam）。 */
        DENSE_STEAM(1000, "densesteam"),
        /** 密集过热蒸汽 DenseSuperheatedSteam：÷2000（流体注册名 densesuperheatedsteam）。 */
        DENSE_SUPERHEATED_STEAM(2000, "densesuperheatedsteam"),
        /** 密集超临界蒸汽 DenseSupercriticalSteam：÷4000（流体注册名 densesupercriticalsteam）。 */
        DENSE_SUPERCRITICAL_STEAM(4000, "densesupercriticalsteam");

        /** 折算除数：等效普通 Steam 流量 = 本种类流量 ÷ 该值（普通 Steam 为 1，即原样计收）。 */
        private final int divisor;

        /** 流体注册名（与耐压蒸汽输入仓白名单、MTEOverpressureTurbineInputHatch 六名一致）。 */
        private final String fluidName;

        SteamGrade(int divisor, String fluidName) {
            this.divisor = divisor;
            this.fluidName = fluidName;
        }

        /** @return 折算除数（等效普通 Steam 口径的除数）。 */
        public int getDivisor() {
            return divisor;
        }

        /** @return 流体注册名（仓室白名单与结算端共用同一命名事实来源）。 */
        public String getFluidName() {
            return fluidName;
        }

        /**
         * 解析本种类对应流体（null 安全，S8 结算端使用）：
         * <ul>
         * <li>普通/致密三档 → GT Materials 的 mGas 字段（{@code Materials.Steam} 与
         * {@code Materials.DenseSteam/DenseSuperheatedSteam/DenseSupercriticalSteam}，
         * 与既有 r5 结算 {@code Materials.Steam.getGas} 同一流体对象）；</li>
         * <li>过热/超临界 → FluidRegistry 按注册名解析；任一环节未注册返回 null（该种类跳过）。</li>
         * </ul>
         *
         * @return 对应流体；未注册时 null
         */
        public net.minecraftforge.fluids.Fluid resolveFluid() {
            switch (this) {
                case STEAM:
                    return gregtech.api.enums.Materials.Steam.mGas;
                case SUPERHEATED_STEAM:
                    return net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
                case SUPERCRITICAL_STEAM:
                    return net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
                case DENSE_STEAM:
                    return gregtech.api.enums.Materials.DenseSteam.mGas;
                case DENSE_SUPERHEATED_STEAM:
                    return gregtech.api.enums.Materials.DenseSuperheatedSteam.mGas;
                case DENSE_SUPERCRITICAL_STEAM:
                    return gregtech.api.enums.Materials.DenseSupercriticalSteam.mGas;
                default:
                    return null;
            }
        }

        /**
         * 集群层级门控（r6-S8 用户拍板定稿，仅两档特殊）：青铜全收；钨钢仅收 SupercriticalSteam 与
         * DenseSupercriticalSteam；其余层级（钢/钛等）同规拒收原始低压两档 Steam 与 DenseSteam
         * （即接受 Superheated/Supercritical 及全部 Dense 变体）。
         *
         * @param tier 集群层级
         * @return 该层级是否接受本蒸汽种类
         */
        public boolean isAcceptedBy(ClusterTier tier) {
            if (tier == ClusterTier.TUNGSTENSTEEL) {
                return this == SUPERCRITICAL_STEAM || this == DENSE_SUPERCRITICAL_STEAM;
            }
            if (tier == ClusterTier.BRONZE) return true;
            return this != STEAM && this != DENSE_STEAM;
        }
    }
}
