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
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.shattered.ShatteredTerrainProfile;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.CommonProxy;

/**
 * /gtsr 调试指令集（dim1 S5 扩展为三子命令，processCommand 按 args[0] 分派）：
 * <ul>
 * <li>/gtsr singularity &lt;range&gt; &lt;speed/20tick&gt; &lt;damage/20tick&gt; &lt;durationTicks|NA&gt;
 * &lt;special|null|onlypull|nullplus|nature&gt; [color] [fxRadius]——在命令发送者位置生成失控奇点
 * （S5 前原逻辑逐字节保持：args[0] 本就是子命令名，参数索引 1..7 不含平移）。</li>
 * <li>/gtsr structure &lt;name&gt;——以发送者脚下 (floor(x),floor(y),floor(z)) 为原点，经
 * {@link CityBlockResolver}（String 逻辑键→Block，含 X/Z GT5U 两键）+ {@link DirectWorldSink}
 * （写前强制 chunk load，跨 chunk 全量放置）执行 {@link StructureRegistry}
 * 对应 placer；回执实测 footprint（w×h×l）与放置方块计数；未知名报 WrongUsageException 并提示用 Tab。</li>
 * <li>/gtsr tpdim &lt;A|B&gt; [群系名]——A=解析 {@link Config#prosperityDimId}、B=解析 {@link Config#shatteredDimId}；
 * 禁用/未注册（id&lt;0 或 {@link DimensionRegistrar#defForDimension} 无 def，冲突/总开关关闭场景）报错回执且玩家
 * 原地不动（事务顺序：先解析后移动）；目标世界未加载先 {@link DimensionManager#initDimension}；
 * <b>不填群系名 = P14 前行为逐字保持</b>：传送 = transferPlayerToDimension + {@link GTSRDimTeleporter}
 * （落点 (0.5, (0,0) 地表 y+1, 0.5) 由 teleporter.placeInPortal 负责，地表求法即 WorldGenRunawaySingularity
 * .findSurfaceY 同款下扫）。
 * <b>填群系名（P14，plan §5「tpdim 群系定位传送」）</b>：名字经 {@link GTSRBiomeAuthority#findRosterByName}
 * 按 roster 账本解析（大小写不敏感；接受 {@code Rusted_Steppe} / {@code Rusted Steppe} / 引号形态——
 * 1.7.10 服务端命令切分是无引号感知的 {@code String.split(" ")}（CommandHandler.executeCommand 字节码实证），
 * 故第 2 参起的全部尾随 token 以空格重 join 后再匹配，tab 补全因此给出<b>单 token 下划线形态</b>）；
 * 定位走 {@link GTSRBiomeAuthority#nearestBiomeChunk} 环带步进（身份只经 L1 账本，禁读 Chunk byte /
 * 禁 getBiomeGenForCoords 逐格试探；半径与步数上界见 Config 两键），命中 chunk 中心列
 * (cx*16+8, cz*16+8)，y = 对应 {@code *TerrainProfile.heightAt}+1（确定性纯函数列顶，不强制加载 chunk；
 * 基岩以上实心填充至 heightAt（含）⇒ 站立位恒为 heightAt+1，与 teleporter 地表 y+1 同约定）。
 * 搜索失败/权威未绑定/降级态一律<b>不动玩家</b>并回可读错误（含已搜半径）。</li>
 * <li>/gtsr diag [A|B]（P14 顺手项）——把 P12 进维 {@code [GTSR][diag]} 诊断行直接打给发送者：
 * 调 {@link GTSRChunkProviderBase#buildEntryDiagLine}（生产同一实现体，内容段经 DiagAssembly 注入的
 * 供给器拼入）+ {@link CommonProxy.DiagAssembly#bootSummaryLine()}（LoadComplete 同款组装）。
 * 纯观测：不 initDimension、不强载维度，世界未加载时按 buildEntryDiagLine 的 mgr=null 降级口径出列。</li>
 * </ul>
 * singularity 语义（保持原文）：speed=每20tick吸收方块数，damage=每20tick伤害值，durationTicks=tick 数；
 * duration 为 NA 表示无限。special=特殊状态（0-999），null=纯动画（不吸引/不破坏/不吸收任何方块与实体），
 * onlypull=只牵引不吸收（不吸收方块、不处理掉落物、牵引力度减半、伤害照常），
 * nullplus=null 基础上无电弧无粒子（吸积盘/电弧跳过），光片/辉光保留，
 * nature=自然生成专用（不吸引/伤害实体，只牵引破坏掉落物+吸收方块，挖后爆炸）。
 * color=16 原版染料色之一，省略默认 white；fxRadius=光效半径 [0.5,128]，省略默认 10。
 * 分型：命令生成的奇点固定 type=RUNAWAY（失控，机器自愈/回收/onRemoval 逻辑零触碰，独立存活至 duration 自毁）。
 * 调试默认：10 1 1 600 0 white（范围 10、每20tick吸1块、每20tick 1点伤害、600 tick=30秒、事件 0、白色）。
 * 需要 OP 权限等级 4。
 */
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
        return "/gtsr singularity <range> <speed/20tick> <damage/20tick> <durationTicks|NA> <special|null|onlypull|nullplus|nature> [color] [fxRadius]"
            + " | /gtsr structure <name>"
            + " | /gtsr tpdim <A|B> [biome]"
            + " | /gtsr diag [A|B]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && "structure".equalsIgnoreCase(args[0])) {
            processStructure(sender, args);
            return;
        }
        if (args.length >= 1 && "tpdim".equalsIgnoreCase(args[0])) {
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

    /**
     * /gtsr tpdim &lt;A|B&gt; [群系名]：A=prosperityDimId、B=shatteredDimId。
     * 事务顺序：解析 id → 禁用/未注册守卫（不动玩家）→ 取玩家 → 目标世界未加载先 initDimension → 传送。
     * <b>P14 判据 1 的路径钉</b>：{@code args.length == 2}（不填群系名）时，自守卫起到本方法结束的全部
     * 语句与基线 9735cef 逐字同序（解析→守卫→取玩家→世界→transferPlayerToDimension+GTSRDimTeleporter
     * 原类→同款回执文案）；群系分支只在 {@code args.length > 2} 时可达。
     */
    private void processTpdim(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        final String which = args[1].toUpperCase();
        final int dimId;
        if ("A".equalsIgnoreCase(args[1])) {
            dimId = Config.prosperityDimId;
        } else if ("B".equalsIgnoreCase(args[1])) {
            dimId = Config.shatteredDimId;
        } else {
            throw new WrongUsageException(
                getCommandUsage(sender) + "  [tpdim] must be A (prosperity) or B (shattered)");
        }
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
        if (args.length == 2) {
            // —— 基线（9735cef）逐字路径：落点 (0.5, (0,0) 地表 y+1, 0.5) 由 GTSRDimTeleporter.placeInPortal 负责 ——
            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, dimId, new GTSRDimTeleporter(target));
            sender
                .addChatMessage(new ChatComponentText("tpdim: sent player to dimension " + dimId + " (" + which + ")"));
            return;
        }
        processTpdimBiome(sender, args, player, dimId, which, target);
    }

    /**
     * P14 群系定位分支（仅 args.length &gt; 2 可达）。失败语义一律<b>不动玩家</b>：
     * 未知名 → WrongUsageException（带该维可解析名单与 Tab 提示）；权威未绑定 / 降级态解析不出 →
     * 可读错误回执；环带搜索未命中 → 「该维度内未找到该群系（已搜半径 R chunk）」。
     * 命中后传送复用 {@link GTSRDimTeleporter}（无门体系不变），仅以匿名子类覆写 placeInPortal
     * 把落点改为「命中 chunk 中心列 + heightAt+1」——不改 GTSRDimTeleporter 本体（基线 (0,0)
     * 列扫路径原样保留给无群系名分支）。
     */
    private void processTpdimBiome(ICommandSender sender, String[] args, EntityPlayerMP player, int dimId, String which,
        WorldServer targetWorld) {
        final String rawName = joinTrailingArgs(args, 2);
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimension(dimId);
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
        final long t0 = System.nanoTime();
        final GTSRBiomeAuthority.NearestBiomeChunk hit = authority
            .nearestBiomeChunk(target, 0, 0, Config.tpdimBiomeSearchMaxRadiusChunks, Config.tpdimBiomeSearchMaxSteps);
        final long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (hit.biome == null) {
            sender.addChatMessage(
                new ChatComponentText(
                    "tpdim failed: 该维度内未找到该群系 '" + rawName
                        + "（已搜半径 "
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
     * 命中列的可站立顶高（纯函数，零 chunk 读取）：dim78={@link ProsperityTerrainProfile#heightAt}、
     * dim79={@link ShatteredTerrainProfile#heightAt}，种子口径与 provider 一致（{@code world.getSeed()}，
     * seedSalt 只进 ChunkProvider 掷骰不进高度场）。def 缺失（理论不可达，上游已守卫）按繁荣侧兜底。
     */
    private static int columnTopSafeY(WorldServer world, int dimId, int x, int z) {
        final String dimKey = DimensionRegistrar.defForDimension(dimId) == null ? null
            : DimensionRegistrar.defForDimension(dimId)
                .getKey();
        if (GTSRBiomeAuthority.DIM_KEY_SHATTERED.equals(dimKey)) {
            return ShatteredTerrainProfile.heightAt(world.getSeed(), x, z);
        }
        return ProsperityTerrainProfile.heightAt(world.getSeed(), x, z);
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
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(
            "A".equalsIgnoreCase(which) ? GTSRBiomeAuthority.DIM_KEY_PROSPERITY : GTSRBiomeAuthority.DIM_KEY_SHATTERED);
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

    /**
     * /gtsr diag [A|B]（P14 顺手项）：把 P12 的 {@code [GTSR][diag]} 内容段直接打到聊天。
     * 组装零复制——诊断行走 {@link GTSRChunkProviderBase#buildEntryDiagLine}（进维同款唯一实现体，
     * 内容段经 DiagAssembly.install 注入的供给器拼入），boot 行走
     * {@link CommonProxy.DiagAssembly#bootSummaryLine()}。纯观测：不 initDimension、不强载维度。
     */
    private void processDiag(ICommandSender sender, String[] args) {
        if (args.length > 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        final String only = args.length == 2 ? args[1] : null;
        if (only != null && !"A".equalsIgnoreCase(only) && !"B".equalsIgnoreCase(only)) {
            throw new WrongUsageException(
                getCommandUsage(sender) + "  [diag] must be A (prosperity) or B (shattered) or omitted");
        }
        final List<String> lines = new ArrayList<>();
        if (only == null || "A".equalsIgnoreCase(only)) {
            lines.add(diagLineFor(Config.prosperityDimId, GTSRBiomeAuthority.DIM_KEY_PROSPERITY));
        }
        if (only == null || "B".equalsIgnoreCase(only)) {
            lines.add(diagLineFor(Config.shatteredDimId, GTSRBiomeAuthority.DIM_KEY_SHATTERED));
        }
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
            return getListOfStringsMatchingLastWord(args, "singularity", "structure", "tpdim", "diag");
        }
        if (args.length == 2 && "structure".equalsIgnoreCase(args[0])) {
            // StructureRegistry 排序名单（S4a/S4b 变体动态读取，不硬编码）
            return getListOfStringsMatchingLastWord(
                args,
                StructureRegistry.names()
                    .toArray(new String[0]));
        }
        if (args.length == 2 && ("tpdim".equalsIgnoreCase(args[0]) || "diag".equalsIgnoreCase(args[0]))) {
            return getListOfStringsMatchingLastWord(args, "A", "B");
        }
        if (args.length == 3 && "tpdim".equalsIgnoreCase(args[0])
            && ("A".equalsIgnoreCase(args[1]) || "B".equalsIgnoreCase(args[1]))) {
            // P14：第 2 参补全该维 roster 群系名。1.7.10 服务端切分是指令级的 String.split(" ")
            // （无引号感知，CommandHandler.executeCommand 字节码实证），补全词必须是单 token，
            // 故给出下划线形态（Rusted_Steppe）；解析层（findRosterByName）同时接受
            // 下划线/空格/引号重 join 三种输入，见类注释。名单来自 L1 账本 rosterBiomeNames()
            // 单一真值（与错误回执同源）；取 forDimKey 而非 forDimension——配槽发生在 preInit，
            // 维度世界尚未首载时 BY_DIM_ID 还没有索引，账本本身已经可读。
            final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(
                "A".equalsIgnoreCase(args[1]) ? GTSRBiomeAuthority.DIM_KEY_PROSPERITY
                    : GTSRBiomeAuthority.DIM_KEY_SHATTERED);
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
