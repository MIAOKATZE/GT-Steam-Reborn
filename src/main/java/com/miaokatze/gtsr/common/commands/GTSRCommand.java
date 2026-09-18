package com.miaokatze.gtsr.common.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.framework.DimensionRegistrar;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimTeleporter;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.DirectWorldSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.config.Config;

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
 * <li>/gtsr tpdim &lt;A|B&gt;——A=解析 {@link Config#prosperityDimId}、B=解析 {@link Config#shatteredDimId}；
 * 禁用/未注册（id&lt;0 或 {@link DimensionRegistrar#defForDimension} 无 def，冲突/总开关关闭场景）报错回执且玩家
 * 原地不动（事务顺序：先解析后移动）；目标世界未加载先 {@link DimensionManager#initDimension}；
 * 传送 = transferPlayerToDimension + {@link GTSRDimTeleporter}（落点 (0.5, (0,0) 地表 y+1, 0.5)
 * 由 teleporter.placeInPortal 负责，地表求法即 WorldGenRunawaySingularity.findSurfaceY 同款下扫）。</li>
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
            + " | /gtsr tpdim <A|B>";
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
     * /gtsr tpdim &lt;A|B&gt;：A=prosperityDimId、B=shatteredDimId。
     * 事务顺序：解析 id → 禁用/未注册守卫（不动玩家）→ 取玩家 → 目标世界未加载先 initDimension → 传送。
     */
    private void processTpdim(ICommandSender sender, String[] args) {
        if (args.length != 2) {
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
        // 落点 (0.5, (0,0) 地表 y+1, 0.5) 由 GTSRDimTeleporter.placeInPortal 负责（无门传送）
        player.mcServer.getConfigurationManager()
            .transferPlayerToDimension(player, dimId, new GTSRDimTeleporter(target));
        sender.addChatMessage(new ChatComponentText("tpdim: sent player to dimension " + dimId + " (" + which + ")"));
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "singularity", "structure", "tpdim");
        }
        if (args.length == 2 && "structure".equalsIgnoreCase(args[0])) {
            // StructureRegistry 排序名单（S4a/S4b 变体动态读取，不硬编码）
            return getListOfStringsMatchingLastWord(
                args,
                StructureRegistry.names()
                    .toArray(new String[0]));
        }
        if (args.length == 2 && "tpdim".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "A", "B");
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
