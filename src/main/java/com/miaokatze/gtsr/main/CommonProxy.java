package com.miaokatze.gtsr.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.miaokatze.gtsr.Tags;
import com.miaokatze.gtsr.common.commands.GTSRCommand;
import com.miaokatze.gtsr.common.crossmod.ae2.GTSRAE2ExternalStorageHandler;
import com.miaokatze.gtsr.common.crossmod.waila.GTSRWailaCompat;
import com.miaokatze.gtsr.common.dimension.framework.DimensionInterferenceGuard;
import com.miaokatze.gtsr.common.dimension.framework.DimensionRegistrar;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.ProsperityBiomes;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRenderers;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;
import com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins;
import com.miaokatze.gtsr.common.dimension.shattered.WorldProviderShatteredLands;
import com.miaokatze.gtsr.common.dimension.shattered.biome.ShatteredBiomes;
import com.miaokatze.gtsr.common.loot.LootInjectionRunawaySingularity;
import com.miaokatze.gtsr.common.network.GTSRFXNet;
import com.miaokatze.gtsr.common.structure.GTSRRedstoneHatchLimitError;
import com.miaokatze.gtsr.common.terminal.AggregatorGuiHandler;
import com.miaokatze.gtsr.common.world.WorldGenRunawaySingularity;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.loader.BlockLoader;
import com.miaokatze.gtsr.loader.GTSRRecipeLoader;
import com.miaokatze.gtsr.loader.ItemLoader;
import com.miaokatze.gtsr.loader.MachineLoader;
import com.miaokatze.gtsr.register.CreativeTabManager;

import appeng.api.AEApi;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Mods;
import gregtech.api.structure.error.StructureErrorRegistry;

/**
 * 通用代理类
 * 处理服务端和客户端共有的逻辑，如配置加载、机器注册、创造模式物品栏初始化等。
 */
public class CommonProxy {

    /**
     * 预初始化阶段 (PreInit)
     * 在此阶段读取配置文件，并将机器注册任务添加到 GregTech 的处理队列中。
     */
    public void preInit(FMLPreInitializationEvent event) {
        // 配置文件迁移到 config/gtsr/gtsr.cfg：新路径不存在且旧路径 (config/gtsr.cfg) 存在时复制一次（标准复制，不覆盖已存在目标），旧文件保留原位
        File newConfigFile = new File(event.getModConfigurationDirectory(), "gtsr/gtsr.cfg");
        File oldConfigFile = event.getSuggestedConfigurationFile();
        if (!newConfigFile.exists() && oldConfigFile.exists()) {
            new File(event.getModConfigurationDirectory(), "gtsr").mkdirs();
            try {
                Files.copy(oldConfigFile.toPath(), newConfigFile.toPath());
            } catch (IOException e) {
                GTSteamReborn.LOG.warn("旧配置文件 (config/gtsr.cfg) 迁移到新路径 (config/gtsr/gtsr.cfg) 失败，将使用默认配置重新生成: ", e);
            }
        }
        Config.synchronizeConfiguration(newConfigFile);
        // dim1 S3：繁荣四空气材料 handler 必须在 GTSR 自身首次触碰 gregtech Materials 类之前登记
        // （此处为 CommonProxy.preInit 最早处）。Materials.add 仅登记；GT 于自身 preInit 的
        // Materials.init()（参考库 GTMod.java:337）末尾回调 onMaterialsInit 完成注册——gtsr 为
        // required-before:gregtech，本时序先于 GT preInit，回调必然可达（plan S3 ①）。
        try {
            Materials.add(new GTSRProsperityAirMaterials());
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[GTSR] prosperity air material handler registration failed", t);
        }
        GTSRFXNet.init();
        // BetterQuesting 可选集成探测（BQ 缺席时静默降级；反射探测不加载 BQ 类）
        com.miaokatze.gtsr.crossmod.bq.BqCompat.detect();

        GTSteamReborn.LOG.info("GTSteamReborn 开始初始化 (版本: " + Tags.VERSION + ")");

        try {
            ItemLoader.initItems();
            GTSteamReborn.LOG.info("[0/3] 物品注册完成。");
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[0/3] 物品注册过程中发生严重错误，请检查日志", t);
        }

        try {
            BlockLoader.initBlocks();
            GTSteamReborn.LOG.info("[0/3] 方块注册完成。");
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[0/3] 方块注册过程中发生严重错误，请检查日志", t);
        }

        // ═══ P9 / L7 生物层注册（plan §5 P9；本片在此段只<b>新增</b>调用，不改任何既有顺序）═══
        // 必须在下面的群系挂接之前：GTSRBiomeBase 的填充是"第一次读 spawn 列表时"惰性发生的，
        // 但策略注入要在那之前就位，否则空跑的注册链会让群系带着清空态被后续读取（详见基类注释）。
        // 关掉 Config.prosperityCreaturesEnabled 即整段跳过 ⇒ 与 P8 基线逐位一致。
        // 渲染器只在 client 分支加载（专用服不触碰 net.minecraft.client.*；本仓 common 侧跨侧
        // 引用已有先例 MTECrustMatterAggregator:1743 的 GTMod.clientProxy()）。
        try {
            GTSRCreatureRegistry.preInit();
            if (FMLCommonHandler.instance()
                .getSide()
                .isClient()) {
                GTSRCreatureRenderers.registerAll();
            }
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[GTSR] 生物层（L7）注册过程中发生严重错误，本维刷怪表保持清空态", t);
        }

        // 维度框架注册（dim1 S1）+ 繁荣群系挂接（S2）：注册编排照 plan §1.1 架构图——BlockLoader 之后。
        // S2：ProsperityBiomes.init 在 def 注册前构造 4 群系（构造即占 biomeList 槽，构造前经空闲校验，
        // 被占跳过+告警降级运行）并按权重表 45/30/15/10 挂入 def（GTSRWorldChunkManager 消费，空表回退保持）。
        // 冲突检测顺序 = 总开关 → isDimensionRegistered(dimId) → providerId 占用探测 → 记 -1 禁用告警；
        // 成功日志锚点 [GTSR] dimension <key> registered: dimId=... providerId=...（plan §6.1 grep 点）。
        // 不注册任何 IWorldGenerator（S4/S6 责任）。
        try {
            final GTSRDimensionDef prosperityDef = new GTSRDimensionDef(
                "prosperity-ruins",
                "Prosperity Ruins",
                0x50524F53L,
                Config.prosperityDimId,
                Config.prosperityProviderId,
                () -> Config.planDimension.prosperityDimension,
                // dim1 S4b：专属 Provider/ChunkProvider 替换 S1 Skeleton 占位（照 shattered 段样式；
                // ChunkProvider = ProsperityTerrainProfile.heightAt 高度场，与古代城同源纯函数）
                WorldProviderProsperityRuins.class,
                ChunkProviderProsperityRuins::new);
            // B2（GenLayer 迁移收尾）：S-A2 时代的 BiomeZoneSelector 接线（cell 级权重掷骰 + 边带
            // 12% 碎斑，盐 0x5A4F4E45）已随身份带职责退役——群系身份自 B1 起唯一出口是
            // GTSRWorldChunkManager 背后的 GTSRGenLayerChain（等权 + Zoom×{@code DEFAULT_ZOOM_LEVELS}
            // 当前 5 档 + Smooth），def 不再挂
            // 任何选择策略； prosperityBiomes.init 的权重挂接仅剩元数据意义。
            ProsperityBiomes.init(prosperityDef);
            final GTSRDimensionDef shatteredDef = new GTSRDimensionDef(
                "shattered-lands",
                "Shattered Lands",
                0x53484C53L,
                Config.shatteredDimId,
                Config.shatteredProviderId,
                () -> Config.planDimension.shatteredDimension,
                // dim79 重做 S-B1：完整正常地形 ChunkProvider 替换浮岛 ChunkProviderShatteredLands
                // （ShatteredTerrainProfile.heightAt 高度场，基准 64 / 钳制 36..96，零 MC 纯函数）
                WorldProviderShatteredLands.class,
                ChunkProviderShatteredGrounds::new);
            // dim79 重做 S-B3 的 selector 挂接（盐 0x5A4F4E46）同随 B2 退役（见上段 dim78 注释）；
            // dim79 群系身份同样走 GenLayer 链（seed 已由 def.seedSalt 分维域分离）。
            // dim1 S6a → S-B3：四群系（190..193 权重 40/30/20/10）在 def 注册前挂接（BlockLoader 已注册 shattered* 方块）
            ShatteredBiomes.init(shatteredDef);
            DimensionRegistrar.preInitDimensions(prosperityDef, shatteredDef);
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[GTSR] 维度框架注册过程中发生严重错误", t);
        }

        Runnable registerRunnable = () -> {
            GTSteamReborn.LOG.info("[1/3] 开始执行机器注册流程...");
            try {
                MachineLoader.initMachines();
                GTSteamReborn.LOG.info("[1/3] 机器注册流程执行完毕。");
            } catch (Throwable t) {
                GTSteamReborn.LOG.error("[1/3] 机器注册过程中发生严重错误，请检查日志", t);
            }
            // 红石仓数量超限结构错误原型注册（客户端按 id 反序列化分发需要 registry 有原型）
            StructureErrorRegistry.register(new GTSRRedstoneHatchLimitError());
        };

        // 使用 sAfterGTPreload 队列（GT PreInit 末尾执行），而非 sAfterGTLoad（GT Init 末尾执行）。
        // 这是 GT5U 官方推荐模式（参考 ggfab/GigaGramFab.java:65-98）：
        // - sAfterGTPreload 执行时 sPreloadStarted=true、sPostloadStarted=false，MTE 注册构造函数阶段检查通过
        // - GT 自身的 MTE 已在 LoaderMetaTileEntities.run()（GTMod.java:325）中注册完毕，避免 ID 冲突
        // - 机器注册时机更早，避免错过 GT Init 阶段的 RecipeMap/NEI 处理窗口（根因 B 修复）
        // MachineLoader.initMachines() 只做 MTE 注册（构造函数 + ItemList.set），不查询 GT 配方，符合 sAfterGTPreload 使用场景
        GregTechAPI.sAfterGTPreload.add(registerRunnable);
        GTSteamReborn.LOG.info("[1/3] 已将机器注册任务加入 GregTech PreInit 加载队列。");

        // tier 通道指示物是 GT5U casing 族方块；Block+ItemBlock 实际在 GT preInit 注册（GTMod.java:274-275 → :324
        // LoaderGTBlockFluid.run()，其 run() 于 LoaderGTBlockFluid.java:735-750 实例化 sBlockCasings 族，
        // GTGenericBlock 构造即 GameRegistry.registerBlock）。
        // GTItemIterator（GT Init）仅为物品注册表兼容扫描，不执行注册调用；sAfterGTPreload（GTMod.java:342）虽是更早安全点，
        // 但通道注册仍须晚于 GT Init 末尾的 GTStructureChannels.register()，故挂 sAfterGTLoad（GTMod.java:402）。
        // gtsr 为 required-before:gregtech，自身 Init 仍早于 GT Init；preInit 直接注册会因 ItemStack.getItem()==null 使
        // gtnhlib ItemStackMap.computeIfAbsent 返回 null，在 StructureLib ChannelDescription.item 内 NPE 启动崩溃。
        GregTechAPI.sAfterGTLoad.add(CommonProxy::registerClusterTierChannel);
    }

    private static void registerClusterTierChannel() {
        StructureLibAPI.registerChannelDescription("tier", "gtsr", "gtsr.structurelib.channel.tier.desc");
        java.util.List<org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer>> family = com.miaokatze.gtsr.common.machine.cluster.ClusterStructureDef
            .casingFamily();
        for (int tier = 1; tier <= 4; tier++) {
            org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer> casing = family.get(tier - 1);
            ItemStack indicator = new ItemStack(casing.getLeft(), 1, casing.getRight());
            if (indicator.getItem() == null) {
                GTSteamReborn.LOG.warn("tier 通道第 {} 档指示物无 Item（casing 方块未就绪），跳过该档注册", tier);
                continue;
            }
            StructureLibAPI.registerChannelItem("tier", "gtsr", tier, indicator);
        }
    }

    /**
     * 初始化阶段 (Init)
     * 在此阶段完成创造模式物品栏的初始化，并注册服务端 Tick 事件处理器。
     */
    @SuppressWarnings({ "unused" })
    public void init(FMLInitializationEvent event) {
        // dim1 S3 dev 探针（S3 验收②）：init 晚于 GT preInit 的 Materials.init()/流体注册管线，
        // 此处回读 FluidRegistry 与静态持有者，逐材料输出非空证据（行为级验证 defer S8 冒烟）。
        GTSRProsperityAirMaterials.logRegistrationProbe();

        GTSteamReborn.LOG.info("[2/3] 开始初始化创造模式物品栏...");

        CreativeTabManager.initCreativeTab();
        GTSteamReborn.LOG.info(
            "[2/3] 创造模式物品栏初始化完成，当前包含 " + CreativeTabManager.getItemsToAdd()
                .size() + " 个物品。");

        // 注册 FML 原生 IGuiHandler：聚合器终端配置界面双端 openGui 配对（terminal-native-ui M7，
        // 手持枢纽终端右击打开；服务端 Container + 客户端 Gui 经 main/ClientProxy 静态委托）
        NetworkRegistry.INSTANCE.registerGuiHandler(AggregatorGuiHandler.modInstance(), new AggregatorGuiHandler());
        // 注：钻井/蒸汽/蓄水三个枢纽状态界面已迁 terminal-native-ui 轨 A
        // （TerminalNet.sendOpen + 客户端 displayGuiScreen），对应 MUI2 factory 注册已随旧轨删除；
        // 聚合器终端配置界面已迁 FML 原生 IGuiHandler 轨 B（AggregatorGuiHandler），MUI2 factory 注册已删除；
        // 集群终端界面已迁轨 A（MTESteamMineralLogisticsCluster.openClusterTerminal → TerminalNet.sendOpen），
        // MUI2 factory 注册已删除。

        // 注册自然生成：失控奇点 nature 词条（主世界+下界，频率见配置 singularitySpawnFrequency）
        GameRegistry.registerWorldGenerator(new WorldGenRunawaySingularity(), 0);

        // dim1 S4a：繁荣维度世界生成编排器（残缺机器 5 机型 + 地表散布；古代城为 S4b 挂点）。
        // 构造时向 StructureRegistry 登记 5 机型变体并输出注册证据日志（plan S4a 验收 grep 锚点）。
        GameRegistry.registerWorldGenerator(new ProsperityWorldGenerator(), 1);

        // dim1 S6b → S-B4：破碎遗迹散布（1/48 chunk，3×3 独碑岩缺角平台 + husk_small/husk_tall 骨架，无 TE 无箱子）。
        // 构造时向 StructureRegistry 登记 2 骨架变体并输出注册证据日志（plan S6b 验收 grep 锚点）。
        GameRegistry.registerWorldGenerator(new WorldGenShatteredRuins(), 1);

        // R1（维度干涉收口）：事件守卫双 bus 注册（TerrainGen Populate DENY + PotentialSpawns
        // 声明白名单），dim78/79 之外的维度零触碰。防线的其余三层：GameRegistryMixin（防线 1）、
        // GTSRChunkProviderBase 不再主动 post Populate Pre/Post（防线 2 前置）、
        // GTSRBiomeBase.effectiveSpawnableList 声明真值过滤（防线 4）。
        // R4：dim79 强制雷暴 + 附加雷控制器已按用户裁决移除（留档文字在计划文档；
        // 全维度禁雨由各群系 setDisableRain 承担）。
        DimensionInterferenceGuard.register();

        // Waila 跨 mod 兼容：外置 isModLoaded 守卫；Waila 缺失时不加载兼容类（详见 GTSRWailaCompat）
        if (Loader.isModLoaded(Mods.Waila.ID)) {
            GTSRWailaCompat.init();
        }
    }

    /**
     * 后初始化阶段 (PostInit)
     * 处理与其他模组的交互或完成最终设置，如注册测试配方。
     */
    @SuppressWarnings({ "unused" })
    public void postInit(FMLPostInitializationEvent event) {
        GTSteamReborn.LOG.info("[3/3] 开始注册 GTSR 配方...");
        try {
            new GTSRRecipeLoader().run();
            GTSteamReborn.LOG.info("[3/3] GTSR 配方注册完成。");
            // 注入蒸汽纠缠奇点到村庄/地牢/矿井/要塞箱子战利品（~2% 抽取概率，每次 1 个）
            LootInjectionRunawaySingularity.init();
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[3/3] GTSR 配方注册过程中发生错误", t);
        }
        AEApi.instance()
            .registries()
            .externalStorage()
            .addExternalStorageInterface(new GTSRAE2ExternalStorageHandler());
    }

    /**
     * 服务器启动阶段
     * 用于注册服务器端命令。
     */
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new GTSRCommand());
    }

    /**
     * 服务器启动完成阶段
     * BQ 任务线注入（迁移自 serverStarting）。整个 ServerStarting 波次——含 BQ default
     * load 与任何第三方整库重载（GTNH 2.9.0-beta-3 专用服 dreamcraft "Modpack has been
     * updated" 整库重载案例）——结束后、tick 与玩家登录前执行，注入不再被覆盖。
     * 守卫必须在调用方：BQ 缺席时 BqQuestInjector 类链接即会触发 BQ 类型解析，
     * 唯有先经零 BQ 引用的 BqCompat 短路才能保证注入器类根本不加载。
     */
    public void serverStarted(FMLServerStartedEvent event) {
        if (com.miaokatze.gtsr.crossmod.bq.BqCompat.isBqLoaded()) {
            com.miaokatze.gtsr.crossmod.bq.BqQuestInjector.inject();
        }
    }

    /**
     * 模组加载完成阶段
     * 如果之前注册失败，可以在此处进行最后的补救尝试。
     */
    public void loadComplete(cpw.mods.fml.common.event.FMLLoadCompleteEvent event) {
        // P12（L8）：装配进维一次性诊断行的内容段供给器 + 打一行 [GTSR][diag] boot 汇总
        // （降级三态/owner 快照/开关/roster/贴图）。纯观测：任何异常只 WARN，不影响加载链。
        try {
            DiagAssembly.install();
            GTSteamReborn.LOG.info(DiagAssembly.bootSummaryLine());
        } catch (Throwable t) {
            GTSteamReborn.LOG.warn("[GTSR][diag] boot summary install failed (observability only)", t);
        }
    }

    /**
     * P12（L8）诊断装配体（进维诊断行内容段 + LoadComplete boot 汇总 + 贴图基线核对）。
     * <p>
     * <b>为什么是 CommonProxy 的嵌套类、而不是框架基类的方法</b>：surface_checks 的各 era BASE 树是
     * 「{@code cp -a} 当前树 + 按清单还原 era 文件」——框架基类会连同 scatter/roster/manager/
     * PlacementGate 一起进 BASE 编译面，基类里任何对"P12 才新增的跨文件符号"的引用都会把
     * P4/P5/P6/P7c-BASE 编译面打崩（实测 P4-BASE 1 error、P5-BASE 9 error ⇒ 对拍退化并误报
     * 88 行漂移）。本类不在任何 BASE 编译清单内，装配引用新符号零污染；框架基类的诊断行只留
     * "历代都存在"的核心列 + {@code setDiagSupplement} 注入缝。
     * <p>
     * <b>工具链同一实现体</b>：{@code tools/dim1/DiagLineCheck} 经
     * {@code Class.forName("com.miaokatze.gtsr.main.CommonProxy$DiagAssembly")} 反射调用本类
     * （直接 import 会令工具 javac 隐式编译 CommonProxy 本体、牵出 gregtech/AE2 依赖，实测
     * NoClassDefFoundError）。本类与 CommonProxy 外部类互不引用，类加载互不牵连。
     */
    public static final class DiagAssembly {

        private DiagAssembly() {}

        /** 注入进维诊断行的内容段（macro/chain/identity/scatter/structure/creature/textures；roster 在框架核心列）。 */
        public static void install() {
            GTSRChunkProviderBase.setDiagSupplement(
                dimKey -> "macro=" + macroBandFor(dimKey)
                    + " chain="
                    + genChainFor(dimKey)
                    + " identity="
                    + identityModeFor(dimKey)
                    + " scatter=["
                    + ProsperitySurfaceScatter.diagSummary()
                    + "]"
                    + " structure=[budget="
                    + Config.prosperityStructureBudgetPerChunk
                    + " windowRepeatCap="
                    + PlacementGate.familyWindowRepeatCap(PlacementGate.FAMILY_MACHINE)
                    + " familyGap="
                    + Config.prosperityStructureFamilyGapChunks
                    + " ruinWindowRepeatCap="
                    + PlacementGate.familyWindowRepeatCap(PlacementGate.FAMILY_RUIN)
                    + " familyGapMax="
                    + PlacementGate.SPACING_GAP_MAX
                    + "]"
                    + " creature=["
                    + GTSRCreatureRoster.diagSummary(dimKey)
                    + "]"
                    + " textures="
                    + textureBaselineDiag());
        }

        /** LoadComplete 一次性汇总行：两维降级态 + owner 快照 + 关键开关 + roster/贴图基线。 */
        public static String bootSummaryLine() {
            final GTSRBiomeAuthority prosperity = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
            final GTSRBiomeAuthority shattered = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
            return "[GTSR][diag] boot" + " dim78="
                + prosperity.degraded()
                + "("
                + prosperity.allocatedCount()
                + "/"
                + prosperity.rosterSize()
                + ", occupant="
                + occupantOrDash(prosperity)
                + ")"
                + " dim79="
                + shattered.degraded()
                + "("
                + shattered.allocatedCount()
                + "/"
                + shattered.rosterSize()
                + ", occupant="
                + occupantOrDash(shattered)
                + ")"
                + " creatures="
                + Config.prosperityCreaturesEnabled
                + " ruins="
                + Config.prosperityRuinsEnabled
                + " laySurfaceWhenDegraded="
                + GTSRChunkProviderBase.laySurfaceWhenDegraded()
                + " roster="
                + StructureRegistry.names()
                    .size()
                + " textures="
                + textureBaselineDiag();
        }

        /** 该维 macro 带尺度（与 {@code GTSRWorldChunkManager.macroBandChunksFor} 同一 Config 出处）。 */
        private static String macroBandFor(String dimKey) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(dimKey)) {
                return String.valueOf(Config.prosperityBiomeMacroBandChunks);
            }
            if (GTSRBiomeAuthority.DIM_KEY_SHATTERED.equals(dimKey)) {
                return String.valueOf(Config.shatteredBiomeMacroBandChunks);
            }
            return "NA";
        }

        /**
         * B1 GenLayer 链观测列（{@code chain=}）：{@code ZOOM<n>} = 该维名册有已配槽成员
         * （manager 将以等权 id 数组建链，zoom 次数取 {@link GTSRGenLayerChain#DEFAULT_ZOOM_LEVELS}）；
         * {@code NA} = 该维不是 dim78/dim79 或名册零配槽（EMPTY，无链）。
         */
        private static String genChainFor(String dimKey) {
            if (!GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(dimKey)
                && !GTSRBiomeAuthority.DIM_KEY_SHATTERED.equals(dimKey)) {
                return "NA";
            }
            return GTSRBiomeAuthority.forDimKey(dimKey)
                .allocatedCount() > 0 ? "ZOOM" + GTSRGenLayerChain.DEFAULT_ZOOM_LEVELS : "NA";
        }

        /**
         * B1 身份面观测列（{@code identity=}）：{@code coarse-center} = chunk 身份取链粗层的
         * <b>chunk 中心块</b> {@code ((chunkX<<4)+8, (chunkZ<<4)+8)}（B1 定稿口径）；
         * {@code NA} = 无链维度（与 {@link #genChainFor} 同判）。
         */
        private static String identityModeFor(String dimKey) {
            return "NA".equals(genChainFor(dimKey)) ? "NA" : "coarse-center";
        }

        private static String occupantOrDash(GTSRBiomeAuthority authority) {
            final String occupants = authority.occupantSummary();
            return occupants.isEmpty() ? "-" : "[" + occupants + "]";
        }

        /**
         * 维度贴图基线核对（P0 资产链的运行时只读复述，两档诚实出口）：
         * <ol>
         * <li>repo 场景（dev/离线 harness，批次目录 {@code tools/artgen/<batch>/manifest.json}
         * 可读）：只扫 OUTPUTS 数组区段、逐名探测 classpath 资源，输出 {@code present/declared}
         * （现基线 49/49；全文正则会误收 _manifest/SEEDS 的 file 字段，实测 54，故限定区段）；</li>
         * <li>打包场景 manifest 不随 jar 分发：输出 {@code NA(blockTexFiles=<目录 png 实数>)}，
         * 宁显式 NA 不伪造 49（登记给 P13：贴图清单若需游戏内可见，须先入资源）。</li>
         * </ol>
         */
        public static String textureBaselineDiag() {
            final java.util.List<String> declared = new java.util.ArrayList<>();
            for (final String batch : new String[] { "tools/artgen/dim7879", "tools/artgen/dim1" }) {
                final java.nio.file.Path manifest = java.nio.file.Paths.get(batch, "manifest.json");
                final String raw;
                try {
                    if (!java.nio.file.Files.isReadable(manifest)) {
                        return "NA(blockTexFiles=" + countBlockTexFiles() + ")";
                    }
                    raw = new String(java.nio.file.Files.readAllBytes(manifest), "UTF-8");
                } catch (Throwable t) {
                    return "NA(blockTexFiles=" + countBlockTexFiles() + ")";
                }
                final String outputs = extractOutputsArray(raw);
                if (outputs == null) {
                    return "NA(blockTexFiles=" + countBlockTexFiles() + ")";
                }
                final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"file\"\s*:\s*\"([^\"]+)\"")
                    .matcher(outputs);
                while (m.find()) {
                    declared.add(m.group(1));
                }
            }
            final ClassLoader cl = CommonProxy.class.getClassLoader();
            int present = 0;
            for (final String name : declared) {
                if (cl.getResource("assets/gtsr/textures/blocks/" + name) != null) {
                    present++;
                }
            }
            return present + "/" + declared.size();
        }

        /** 取 {@code "OUTPUTS": [ ... ]} 数组区段（括号计数配对；失败回 null）。 */
        private static String extractOutputsArray(String raw) {
            final int key = raw.indexOf("\"OUTPUTS\"");
            if (key < 0) {
                return null;
            }
            final int open = raw.indexOf('[', key);
            if (open < 0) {
                return null;
            }
            int depth = 0;
            for (int i = open; i < raw.length(); i++) {
                final char c = raw.charAt(i);
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return raw.substring(open, i + 1);
                    }
                }
            }
            return null;
        }

        /** gtsr 方块贴图目录 png 实数（目录型 classpath 文件列举 / jar 型条目枚举；失败回 -1）。 */
        public static int countBlockTexFiles() {
            final ClassLoader cl = CommonProxy.class.getClassLoader();
            try {
                final java.net.URL dir = cl.getResource("assets/gtsr/textures/blocks");
                if (dir == null) {
                    return -1;
                }
                if ("file".equals(dir.getProtocol())) {
                    try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files
                        .list(java.nio.file.Paths.get(dir.toURI()))) {
                        return (int) s.filter(
                            p -> p.getFileName()
                                .toString()
                                .endsWith(".png"))
                            .count();
                    }
                }
                if ("jar".equals(dir.getProtocol())) {
                    final java.net.JarURLConnection c = (java.net.JarURLConnection) dir.openConnection();
                    final String entryName = c.getEntryName();
                    final String prefix = entryName == null ? "assets/gtsr/textures/blocks/"
                        : (entryName.endsWith("/") ? entryName : entryName + "/");
                    int n = 0;
                    final java.util.Enumeration<java.util.jar.JarEntry> e = c.getJarFile()
                        .entries();
                    while (e.hasMoreElements()) {
                        final String name = e.nextElement()
                            .getName();
                        if (name.startsWith(prefix) && name.endsWith(".png")) {
                            n++;
                        }
                    }
                    return n;
                }
            } catch (Throwable ignored) {
                // 观测出口失败只回 -1，不抛（同「日志不得改变行为」）
            }
            return -1;
        }
    }
}
