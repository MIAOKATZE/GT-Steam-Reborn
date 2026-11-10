package com.miaokatze.gtsr.common.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.framework.DimensionRegistrar;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimTeleporter;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.DirectWorldSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.CommonProxy;

/** OP4 tools. The retained Prosperity dimension is implicit; retired dimension letters are rejected. */
public class GTSRCommand extends CommandBase {

    /** 第 7 参合法色名（与 TileRunawaySingularity 16 原版染料色表、Tab 补全同源）。 */
    private static final String[] VALID_COLORS = { "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black" };

    private static String joinValidColors() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VALID_COLORS.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(VALID_COLORS[i]);
        }
        return sb.toString();
    }

    @Override
    public String getCommandName() {
        return "gtsr";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return StatCollector.translateToLocal("gtsr.command.usage");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && "mission".equalsIgnoreCase(args[0])) {
            MissionCommand.execute(sender, args);
            return;
        }
        if (args.length >= 1 && RuinLocateCommand.handles(args[0])) {
            RuinLocateCommand.execute(sender, args);
            return;
        }
        if (args.length >= 1 && "structure".equalsIgnoreCase(args[0])) {
            processStructure(sender, args);
            return;
        }
        if (args.length >= 1 && isDimensionTeleport(args[0])) {
            processTpdim(sender, args);
            return;
        }
        if (args.length >= 1 && "diag".equalsIgnoreCase(args[0])) {
            processDiag(sender, args);
            return;
        }

        // —— singularity：S5 前原逻辑逐字节保持（args[0] 即子命令名，参数校验矩阵不动）——
        if (args.length < 6 || args.length > 8 || !args[0].equalsIgnoreCase("singularity")) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        double range = parseClampedDouble(args[1], 0.5D, 128.0D);
        double speed = parseClampedDouble(args[2], 0.0D, 100.0D);
        double damage = parseClampedDouble(args[3], 0.0D, 1000.0D);
        int duration = parseDuration(args[4]);
        int special = parseSpecial(args[5]);
        String color = args.length >= 7 ? args[6] : "white";
        if (!TileRunawaySingularity.isValidColor(color)) {
            // SR-BUG-01：非法颜色改走用法错误通道（原数值错误通道会误报「不是有效数字」），
            // 错误提示直接列出 16 个合法色名（与 TileRunawaySingularity 色表、Tab 补全同源）
            throw new WrongUsageException(getCommandUsage(sender) + "  [color] must be one of: " + joinValidColors());
        }
        double fxRadius = args.length >= 8 ? parseClampedDouble(args[7], 0.5D, 128.0D) : 10.0D;

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player.worldObj.isRemote) {
            return;
        }

        TileRunawaySingularity.spawnSingularity(
            player.worldObj,
            (int) player.posX,
            (int) player.posY,
            (int) player.posZ,
            range,
            speed,
            damage,
            duration,
            special,
            color,
            fxRadius,
            TileRunawaySingularity.SingularityType.RUNAWAY); // 命令生成默认失控分型（机器失控等其他情况口径）

        sender.addChatMessage(
            new ChatComponentText(
                "Singularity spawned: range " + range
                    + ", speed "
                    + speed
                    + ", damage "
                    + damage
                    + ", duration "
                    + (duration == -1 ? "NA" : duration)
                    + ", special "
                    + (special == TileRunawaySingularity.ATTRIBUTE_NULL ? "null"
                        : special == TileRunawaySingularity.ATTRIBUTE_ONLY_PULL ? "onlypull"
                            : special == TileRunawaySingularity.ATTRIBUTE_NULL_PLUS ? "nullplus"
                                : special == TileRunawaySingularity.ATTRIBUTE_NATURE ? "nature" : special)
                    + ", color "
                    + color
                    + ", fxRadius "
                    + fxRadius
                    // T7 顺手项：回执补充分型字样（命令生成固定 RUNAWAY，与 TileRunawaySingularity 三分类口径一致）
                    + ", type "
                    + TileRunawaySingularity.SingularityType.RUNAWAY));
    }

    /**
     * /gtsr structure &lt;name&gt;：脚下三轴 floor 为原点，CityBlockResolver（String 键→Block）+
     * DirectWorldSink 全量放置（写前强制 chunk load）。
     * 校验顺序与 singularity 同型（先参数后玩家）：未知名/缺参 → WrongUsageException（含 Tab 提示）；
     * 控制台等无实体发送者 → getCommandSenderAsPlayer 的「需要玩家」错误。
     */
    private void processStructure(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        final String name = args[1];
        final StructureRegistry.Entry entry = StructureRegistry.get(name);
        if (entry == null) {
            throw new WrongUsageException(
                getCommandUsage(sender) + "  unknown structure: " + name + " (use Tab completion to list valid names)");
        }
        if (entry.dimension == StructureRegistry.Dimension.PROSPERITY) {
            sender.addChatMessage(new ChatComponentText("旧繁荣结构手动放置已暂停；当前仅开放三类 Boss 标准场景，请使用 locate / tplocate。"));
            return;
        }
        final EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player.worldObj.isRemote) {
            return;
        }
        final int x = MathHelper.floor_double(player.posX);
        final int y = MathHelper.floor_double(player.posY);
        final int z = MathHelper.floor_double(player.posZ);
        // 种子：worldSeed+原点+变体名确定性派生（GTSRWorldgenHash 口径），同种子同坐标重放逐方块一致
        final long seed = GTSRWorldgenHash.blockSeed(player.worldObj.getSeed(), x, y, z) ^ (long) name.hashCode();
        // String 逻辑键解析层（S-A6 单点修复，A4A5 报告 §9-3 缺口销号）：城变体 placer（CityVariants.place）
        // 与 outpost placer 经 CityVariants.blockKeyOf 产出 String 契约键，裸 DirectWorldSink 的
        // instanceof Block 门会整链丢弃（26 城变体指令通道 v1.20.28 起放置为空）——指令通道与
        // 世界生成通道（ProsperityWorldGenerator 城链）同源挂 CityBlockResolver（键表含 X/Z GT5U 两键；
        // GT 字段 null 时 resolve 落空 → 该格跳过，既有防御链）。outpost placer 自包 resolver，双层
        // 包裹幂等（Block 直通）；RuinedMachinePlacer/WorldGenShatteredRuins 产出 Block 实例，直通不受影响。
        final CountingSink sink = new CountingSink(new CityBlockResolver(new DirectWorldSink(player.worldObj)));
        entry.placer.place(sink, x, y, z, seed);
        final String footprint = sink.count == 0 ? "0x0x0"
            : (sink.maxX - sink.minX + 1) + "x" + (sink.maxY - sink.minY + 1) + "x" + (sink.maxZ - sink.minZ + 1);
        sender.addChatMessage(
            new ChatComponentText(
                "Structure placed: " + name
                    + " at ("
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") footprint "
                    + footprint
                    + ", blocks "
                    + sink.count));
    }

    /** Teleport only to the retained configured dimension; an optional biome starts at argument 1. */
    private void processTpdim(ICommandSender sender, String[] args) {
        if (args.length > 1 && ("A".equalsIgnoreCase(args[1]) || "B".equalsIgnoreCase(args[1])))
            throw new WrongUsageException("gtsr.command.dimension_removed");
        final String which = "prosperity";
        final int dimId = Config.prosperityDimId;
        // 禁用/未注册（id<0，或 DimensionRegistrar 冲突/总开关场景记 -1 不注册）→ 报错回执且玩家原地不动
        if (dimId < 0 || DimensionRegistrar.defForDimension(dimId) == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    "tpdim failed: dimension " + which + " (id " + dimId + ") is disabled or not registered"));
            return;
        }
        final EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (player.worldObj.isRemote) {
            return;
        }
        // 目标世界未加载先 initDimension（initDimension 返回 void，需回取 getWorld）；失败不动玩家
        WorldServer target = DimensionManager.getWorld(dimId);
        if (target == null) {
            DimensionManager.initDimension(dimId);
            target = DimensionManager.getWorld(dimId);
        }
        if (target == null) {
            sender.addChatMessage(new ChatComponentText("tpdim failed: dimension " + dimId + " world init failed"));
            return;
        }
        if (args.length == 1) {
            // —— 基线（9735cef）逐字路径：落点 (0.5, (0,0) 地表 y+1, 0.5) 由 GTSRDimTeleporter.placeInPortal 负责 ——
            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, dimId, new GTSRDimTeleporter(target));
            sender
                .addChatMessage(new ChatComponentText("tpdim: sent player to dimension " + dimId + " (" + which + ")"));
            return;
        }
        processTpdimBiome(sender, args, player, dimId, which, target);
    }

    /** Bounded biome lookup in Prosperity. Failed lookup leaves the player in place. */
    private void processTpdimBiome(ICommandSender sender, String[] args, EntityPlayerMP player, int dimId, String which,
        WorldServer targetWorld) {
        final String rawName = joinTrailingArgs(args, 1);
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forWorld(targetWorld);
        final GTSRBiomeAuthority.BiomeId target = authority.findRosterByName(rawName);
        if (target == null) {
            throw new WrongUsageException(
                getCommandUsage(sender) + "  unknown biome '"
                    + rawName
                    + "' for dimension "
                    + which
                    + " (degraded="
                    + authority.degraded()
                    + "; available: "
                    + completionNamesFor(which)
                    + " — use Tab)");
        }
        if (!authority.isBound()) {
            // 维度世界刚 initDimension 成功却仍未绑定（理论不可达；L1 未就位时宁报不猜，不指野点）
            sender.addChatMessage(
                new ChatComponentText("tpdim failed: biome authority for dimension " + dimId + " is not bound yet"));
            return;
        }
        // S5：搜索原点 = 玩家当前 chunk（原硬编码 0,0 命中的是"离世界原点最近"的带，玩家深处
        // 再传送会被拉回原点附近；nearestBiomeChunk 的 origin 本就参数化，4 家 selector 路径同一调用点跟随）
        final int originChunkX = MathHelper.floor_double(player.posX) >> 4;
        final int originChunkZ = MathHelper.floor_double(player.posZ) >> 4;
        if (target == GTSRBiomeAuthority.BiomeId.SANZU_RIVER) {
            // roster-only 群系：链身份面（4 家 selector）结构性解析不到 ⇒ 环带搜索恒 NOT_FOUND，
            // 走 RVF 湖格几何的指令层定位通道（见 processTpdimSanzuLake）
            processTpdimSanzuLake(sender, player, targetWorld, dimId, which, rawName);
            return;
        }
        final long t0 = System.nanoTime();
        final GTSRBiomeAuthority.NearestBiomeChunk hit = authority.nearestBiomeChunk(
            target,
            originChunkX,
            originChunkZ,
            Config.tpdimBiomeSearchMaxRadiusChunks,
            Config.tpdimBiomeSearchMaxSteps);
        final long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (hit.biome == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    "tpdim failed: 该维度内未找到该群系 '" + rawName
                        + "'（已搜半径 "
                        + Config.tpdimBiomeSearchMaxRadiusChunks
                        + " chunk"
                        + (hit.status == GTSRBiomeAuthority.NearestStatus.STEP_LIMIT
                            ? "，步数上限 " + Config.tpdimBiomeSearchMaxSteps + " 先到，评估 " + hit.steps + " chunk"
                            : "，评估 " + hit.steps + " chunk")
                        + "，耗时 "
                        + ms
                        + "ms）"));
            return;
        }
        // 落点：命中 chunk 中心列 + 确定性列顶（heightAt 实心填充含顶块 ⇒ 站立位 = heightAt+1，
        // 与 teleporter 地表 y+1 同一约定；本片不为了找地面强制加载/生成 chunk，见类注释）
        final int bx = hit.chunkX * 16 + 8;
        final int bz = hit.chunkZ * 16 + 8;
        final int ly = columnTopSafeY(targetWorld, dimId, bx, bz) + 1;
        // 就地复核（判据 2 的传送侧断言）：中心块坐标经 L1 ordinalAt 必须解析回目标群系
        final GTSRBiomeAuthority.Resolution check = authority.ordinalAt(bx, bz);
        player.mcServer.getConfigurationManager()
            .transferPlayerToDimension(player, dimId, new GTSRDimTeleporter(targetWorld) {

                @Override
                public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float rotationYaw) {
                    entity.setLocationAndAngles(bx + 0.5D, ly, bz + 0.5D, rotationYaw, 0.0F);
                    entity.motionX = entity.motionY = entity.motionZ = 0.0D;
                }
            });
        sender.addChatMessage(
            new ChatComponentText(
                "tpdim: sent player to dimension " + dimId
                    + " ("
                    + which
                    + ") biome '"
                    + rawName
                    + "'"
                    + " chunk=("
                    + hit.chunkX
                    + ","
                    + hit.chunkZ
                    + ") block=("
                    + bx
                    + ","
                    + ly
                    + ","
                    + bz
                    + ")"
                    + " rings="
                    + hit.ringsScanned
                    + " steps="
                    + hit.steps
                    + " ms="
                    + ms
                    + (hit.status == GTSRBiomeAuthority.NearestStatus.STEP_LIMIT ? " [步数上限截断，落点未证全局最近]" : "")
                    + " verify="
                    + (check.resolved() && check.biomeId == target ? check.biomeId.name() : "MISMATCH")));
    }

    /**
     * S5：SANZU_RIVER（遗忘之川/湖）专属定位分支——该群系是 roster-only（selector=false），
     * 平面列由 populate 后置写入，链身份面（GenLayer 4 家 selector）永远解析不到 ⇒ 环带搜索
     * 恒 NOT_FOUND（结构性，非半径问题）。本分支是指令层定位通道：直接走 RVF 的湖格几何
     * （{@link GTSRVoronoiRiverField#nearestSanzuArrival}——7×7 站窗内取<b>带安全干滩</b>的最近
     * 活湖并给出滩带干列，避岛心树干与岛底柱；P25 S5b 起湿兜底已删、无干滩湖整湖跳过），
     * <b>不动 L1 身份面</b>（ordinalAt 单出口约束原样）、
     * <b>零 Chunk byte 读</b>（类头硬约束保持）、全程确定性纯函数。
     * 未命中（主干带外全死湖等）→ NOT_FOUND 文案出口，玩家不动。
     */
    private void processTpdimSanzuLake(ICommandSender sender, EntityPlayerMP player, WorldServer targetWorld, int dimId,
        String which, String rawName) {
        final long t0 = System.nanoTime();
        final long worldSeed = targetWorld.getSeed();
        final int[] center = new int[2];
        final int[] col = new int[2];
        final boolean lakeHit = GTSRVoronoiRiverField.nearestSanzuArrival(
            worldSeed,
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.posZ),
            center,
            col);
        final long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (!lakeHit) {
            sender.addChatMessage(
                new ChatComponentText(
                    "tpdim failed: 该维度内未找到该群系 '" + rawName
                        + "'（已搜半径 49 湖站格（7×7 站 × LAKE_INTERVAL "
                        + (int) GTSRVoronoiRiverField.LAKE_INTERVAL
                        + "），耗时 "
                        + ms
                        + "ms）"));
            return;
        }
        // 落点 = 滩带列；y 走 columnTopSafeY 的水柱感知（滩带含水缘列时螺旋避让/站水面）
        final int bx = col[0];
        final int bz = col[1];
        final int ly = columnTopSafeY(targetWorld, dimId, bx, bz) + 1;
        // 就地复核走 populate 同源谓词 isSanzuColumn（ordinalAt 对 roster-only 群系结构性不可见）
        final boolean verify = GTSRVoronoiRiverField.isSanzuColumn(worldSeed, bx, bz);
        player.mcServer.getConfigurationManager()
            .transferPlayerToDimension(player, dimId, new GTSRDimTeleporter(targetWorld) {

                @Override
                public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float rotationYaw) {
                    entity.setLocationAndAngles(bx + 0.5D, ly, bz + 0.5D, rotationYaw, 0.0F);
                    entity.motionX = entity.motionY = entity.motionZ = 0.0D;
                }
            });
        sender.addChatMessage(
            new ChatComponentText(
                "tpdim: sent player to dimension " + dimId
                    + " ("
                    + which
                    + ") biome '"
                    + rawName
                    + "'"
                    + " lakeCenter=("
                    + center[0]
                    + ","
                    + center[1]
                    + ") block=("
                    + bx
                    + ","
                    + ly
                    + ","
                    + bz
                    + ") ms="
                    + ms
                    + " verify=SANZU_RIVER:"
                    + verify
                    + "（populate 谓词；滩带落点，湖心树干/岛底柱避让）"));
    }

    /** Prosperity terrain height with the existing water-column avoidance; no retired dimension fallback. */
    private static int columnTopSafeY(WorldServer world, int dimId, int x, int z) {
        final long seed = world.getSeed();
        if (!populateWaterColumnAt(seed, x, z)) {
            return ProsperityTerrainProfile.heightAt(seed, x, z);
        }
        // 螺旋 16 列（字面量表，S5 任务包钉的 ≤16 列预算）：环 1 正交→对角，环 2 距离 2 → √5
        final int[][] spiral = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 }, { 1, -1 },
            { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, { 1, 2 }, { -1, 2 }, { 1, -2 }, { -1, -2 } };
        for (int i = 0; i < spiral.length; i++) {
            final int nx = x + spiral[i][0];
            final int nz = z + spiral[i][1];
            if (!populateWaterColumnAt(seed, nx, nz)) {
                return ProsperityTerrainProfile.heightAt(seed, nx, nz);
            }
        }
        // 16 列内无干列：站水面（湖面 = SEA_LEVEL = 68，调用方 +1）
        return ProsperityTerrainProfile.SEA_LEVEL - 1;
    }

    /**
     * <b>populate 同源水列谓词</b>（S5，dim78）：巨湖 = {@code ChunkProviderProsperityRuins.fillSanzuLakes}
     * 现行置水门的严格同源（P27-L D1·4a 起该门已换 {@link GTSRVoronoiRiverField#sanzuShoreWaterAt}
     * 单一出口，v1.20.53 P30 I3 骑手把本谓词从旧式 {@code lakeAt < LAKE_SHORE} 对齐过来，消掉
     * 「逐字同式」漂移表述所对应的口径差）∧ {@code heightAt < SEA_LEVEL}（Profile 公开出口，
     * 不抄第二份判定）。<b>[P25 D7 残潭退役]</b>原沼泽残潭腿（{@code swampRiverPoolColumnAt}，
     * v1.20.42 P22 A1b）已随 RVF 该族删除而摘除；河道支路的 populate 池水柱不在本谓词内
     * （批2 S2 已将 wetAt 置死，不为其新立真值）。
     */
    private static boolean populateWaterColumnAt(long worldSeed, int x, int z) {
        return GTSRVoronoiRiverField.sanzuShoreWaterAt(worldSeed, x, z)
            && ProsperityTerrainProfile.heightAt(worldSeed, x, z) < ProsperityTerrainProfile.SEA_LEVEL;
    }

    /** 第 {@code from} 参起到末尾以单空格重 join（1.7.10 服务端无引号感知切分的兼容层）。 */
    private static String joinTrailingArgs(String[] args, int from) {
        final StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** 该维当前可解析的补全名单（下划线单 token 形态，逗号连接；错误回执与 tab 同源，取 dimKey 账本）。 */
    private static String completionNamesFor(String which) {
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final List<String> names = authority.rosterBiomeNames();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(
                names.get(i)
                    .replace(' ', '_'));
        }
        return sb.length() == 0 ? "none (degraded)" : sb.toString();
    }

    /** Observe the retained dimension without initializing it. Dimension letters are no longer accepted. */
    private void processDiag(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        final List<String> lines = new ArrayList<>();
        lines.add(diagLineFor(Config.prosperityDimId, GTSRBiomeAuthority.DIM_KEY_PROSPERITY));
        for (final String line : lines) {
            sender.addChatMessage(new ChatComponentText(line));
        }
        sender.addChatMessage(new ChatComponentText(CommonProxy.DiagAssembly.bootSummaryLine()));
    }

    /** 单维诊断行（世界未加载时 mgr=null，buildEntryDiagLine 自身的降级列口径照实呈现）。 */
    private static String diagLineFor(int dimId, String dimKey) {
        if (dimId < 0 || DimensionRegistrar.defForDimension(dimId) == null) {
            return "[GTSR][diag] dim=" + dimId + " def=" + dimKey + " (disabled or not registered)";
        }
        final WorldServer world = DimensionManager.getWorld(dimId);
        final GTSRWorldChunkManager mgr = world != null && world.getWorldChunkManager() instanceof GTSRWorldChunkManager
            ? (GTSRWorldChunkManager) world.getWorldChunkManager()
            : null;
        return GTSRChunkProviderBase.buildEntryDiagLine(dimId, dimKey, mgr);
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                "singularity",
                "structure",
                "tpdim",
                "diag",
                "locate",
                "tplocate",
                "mission");
        }
        if (args.length == 2 && RuinLocateCommand.handles(args[0])) return getListOfStringsMatchingLastWord(
            args,
            RuinLocateCommand.names()
                .toArray(new String[0]));
        if (args.length == 2 && "structure".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (StructureRegistry.Entry entry : StructureRegistry.all()) {
                if (entry.dimension == StructureRegistry.Dimension.ANY) names.add(entry.name);
            }
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }
        if (args.length == 2 && isDimensionTeleport(args[0])) {
            // P14：第 2 参补全该维 roster 群系名。1.7.10 服务端切分是指令级的 String.split(" ")
            // （无引号感知，CommandHandler.executeCommand 字节码实证），补全词必须是单 token，
            // 故给出下划线形态（Rusted_Steppe）；解析层（findRosterByName）同时接受
            // 下划线/空格/引号重 join 三种输入，见类注释。名单来自 L1 账本 rosterBiomeNames()
            // 单一真值（与错误回执同源）；取 forDimKey 而非 forDimension——配槽发生在 preInit，
            // 维度世界尚未首载时 BY_DIM_ID 还没有索引，账本本身已经可读。
            final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
            final List<String> names = authority.rosterBiomeNames();
            final String[] tokens = new String[names.size()];
            for (int i = 0; i < names.size(); i++) {
                tokens[i] = names.get(i)
                    .replace(' ', '_');
            }
            return getListOfStringsMatchingLastWord(args, tokens);
        }
        if ("singularity".equalsIgnoreCase(args[0])) {
            if (args.length == 5) {
                // B2-03：三档补全原各提前一位——第 5 参是 duration，仅 NA 是关键词（参数索引不含平移）
                return getListOfStringsMatchingLastWord(args, "NA");
            }
            if (args.length == 6) {
                // 第 6 参特殊状态关键词与 parseSpecial 同源（0-999 数值段不列）
                return getListOfStringsMatchingLastWord(args, "0", "null", "onlypull", "nullplus", "nature");
            }
            if (args.length == 7) {
                // 色名表与非法颜色报错同源（VALID_COLORS）
                return getListOfStringsMatchingLastWord(args, VALID_COLORS);
            }
        }
        return null;
    }

    private static boolean isDimensionTeleport(String subcommand) {
        return "tpdim".equalsIgnoreCase(subcommand);
    }

    private double parseClampedDouble(String arg, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(arg);
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
        if (value < min || value > max) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
        return value;
    }

    private int parseClampedInt(String arg, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
        if (value < min || value > max) {
            throw new NumberInvalidException("commands.generic.num.invalid", arg);
        }
        return value;
    }

    private int parseDuration(String arg) {
        if (arg.equalsIgnoreCase("NA")) {
            return -1;
        }
        return parseClampedInt(arg, 1, 360000);
    }

    /**
     * 第 5 参：特殊状态；null → -1（纯动画，不吸引/不破坏/不吸收任何方块与实体），
     * onlypull → -2（只牵引不吸收：不吸收方块、不处理掉落物、牵引力度减半、伤害照常），
     * nullplus → -3（null 基础上无电弧无粒子，光片/辉光保留），否则 0-999 整数
     */
    private int parseSpecial(String arg) {
        if (arg.equalsIgnoreCase("null")) {
            return TileRunawaySingularity.ATTRIBUTE_NULL;
        }
        if (arg.equalsIgnoreCase("onlypull")) {
            return TileRunawaySingularity.ATTRIBUTE_ONLY_PULL;
        }
        if (arg.equalsIgnoreCase("nullplus")) {
            return TileRunawaySingularity.ATTRIBUTE_NULL_PLUS;
        }
        if (arg.equalsIgnoreCase("nature")) {
            return TileRunawaySingularity.ATTRIBUTE_NATURE;
        }
        return parseClampedInt(arg, 0, 999);
    }

    /** 放置计数 Sink：委托 {@link DirectWorldSink}，统计被接受写入数与实测包围盒（回执 footprint 用）。 */
    private static final class CountingSink implements BlockSink {

        private final BlockSink delegate;
        private int count;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        CountingSink(BlockSink delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if (!this.delegate.setBlock(x, y, z, block, meta, flags)) {
                return false;
            }
            this.count++;
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
            return true;
        }
    }
}
