package com.miaokatze.gtsr.common.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.miaokatze.gtsr.common.dimension.framework.DimensionRegistrar;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimTeleporter;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSite;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.config.Config;

/** Both commands use the same bounded lookup; teleport arrives above the world's build ceiling. */
public final class RuinLocateCommand {

    private static final int RADIUS_CELLS = 32;
    private static final int MAX_CANDIDATES = 8192;
    private static final int TELEPORT_Y = 260;
    private static final String[] CHINESE_NAMES = { "崩垣铸造战场", "巢识沉降工厂", "锅炉圣所", "织网工坊", "裂弦哨塔", "镜铠兵营", "共振钟站", "铁路扳道室",
        "河岸泵房", "幸存者工棚", "沿岸吊机", "熄火祭坛", "战壕急救站", "灰烬焚炭窑", "裂隙观测所", "旧道地磅", "阀门检修院", "根木档案亭", "根木采液架", "沼泽栈桥", "旧道朝圣拱",
        "断轨信号桥", "菌覆储藏窖", "锅炉礼拜堂", "断裂输水渠", "齿轮花园", "探路者驿营" };

    private RuinLocateCommand() {}

    public static boolean handles(String name) {
        return "locate".equalsIgnoreCase(name) || "tplocate".equalsIgnoreCase(name);
    }

    public static List<String> names() {
        List<String> result = new ArrayList<>();
        result.addAll(RemasterCatalog.ids());
        result.add("hanging_great_tree");
        return result;
    }

    public static int kindFor(String input) {
        String name = input.trim()
            .replace(' ', '_')
            .toLowerCase(Locale.ROOT);
        if (name.startsWith("echo_")) name = name.substring(5);
        if ("hanging_great_tree".equals(name) || "垂天巨树".equals(name) || "遗忘之湖".equals(name)) return 27;
        for (int kind = 0; kind < RuinSite.NAMES.length; kind++) {
            if (RuinSite.NAMES[kind].equals(name) || CHINESE_NAMES[kind].equals(name)
                || StatCollector.translateToLocal("lore.entry.structures." + RuinSite.NAMES[kind] + ".title")
                    .replace(' ', '_')
                    .equalsIgnoreCase(name))
                return kind;
        }
        return -1;
    }

    public static void execute(ICommandSender sender, String[] args) {
        if (args.length < 3 || !"A".equalsIgnoreCase(args[1]))
            throw new WrongUsageException("/gtsr " + args[0] + " A <structure> (Tab)");
        StringBuilder requested = new StringBuilder(args[2]);
        for (int i = 3; i < args.length; i++) requested.append(' ')
            .append(args[i]);
        int kind = kindFor(requested.toString());
        String requestedId = kind >= 0 && kind < 27 ? RuinSite.NAMES[kind]
            : requested.toString()
                .trim()
                .replace(' ', '_')
                .toLowerCase(Locale.ROOT);
        if (kind < 0 && !RemasterCatalog.ids()
            .contains(requestedId)) throw new WrongUsageException("未知遗址：" + requested + "；请使用 Tab 选择名称");
        if ("forgotten_lake_court".equals(requestedId)) kind = 27;
        int dimension = Config.prosperityDimId;
        if (dimension < 0 || !Config.planDimension.prosperityDimension
            || DimensionRegistrar.defForDimension(dimension) == null) {
            say(sender, "繁荣蒸汽失落维度未启用，无法定位。");
            return;
        }
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        if (player.worldObj.isRemote) return;
        WorldServer loaded = DimensionManager.getWorld(dimension);
        // All dimension worlds share the save's world seed. Query does not initialize a dimension.
        long seed = loaded == null ? MinecraftServer.getServer()
            .worldServerForDimension(0)
            .getSeed() : loaded.getSeed();
        int ox = MathHelper.floor_double(player.posX), oz = MathHelper.floor_double(player.posZ);
        RuinSite site = null;
        RemasterSite remaster = null;
        int x, z;
        String display;
        if (kind == 27) {
            int[] center = new int[2], arrival = new int[2];
            if (!GTSRVoronoiRiverField.nearestSanzuArrival(seed, ox, oz, center, arrival)) {
                say(sender, "搜索范围内未找到带安全入口的垂天巨树。");
                return;
            }
            x = arrival[0];
            z = arrival[1];
            display = "垂天巨树";
        } else {
            final WorldServer observed = loaded;
            remaster = RemasterPlanner.nearest(
                seed,
                requestedId,
                ox,
                oz,
                12,
                s -> observed == null
                    || com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterWorldgen.allowed(observed, s));
            if (loaded != null) {
                int cellChunks = kind < 2 ? 96 : kind < 7 ? 24 : 8;
                for (RuinSite saved : RuinsEncounterData.get(loaded)
                    .existingSitesNear(ox, oz, (RADIUS_CELLS + 1) * cellChunks * 16 + 288)) {
                    if (kind >= 0 && saved.kind == kind
                        && saved.seed == seed
                        && (site == null || distance(saved, ox, oz) < distance(site, ox, oz))) site = saved;
                }
            }
            if (site != null && remaster != null) {
                double dx = remaster.entryX() - (double) ox, dz = remaster.entryZ() - (double) oz;
                if (distance(site, ox, oz) <= dx * dx + dz * dz) remaster = null;
                else site = null;
            }
            if (site == null && remaster == null) {
                say(sender, "搜索范围内未找到该遗址；请换一个探索位置后重试。");
                return;
            }
            x = remaster == null ? site.entryX() : remaster.entryX();
            z = remaster == null ? site.entryZ() : remaster.entryZ();
            display = kind >= 0 ? CHINESE_NAMES[kind] : requestedId;
        }
        say(
            sender,
            "搜索范围内最近的" + display
                + "：入口 X="
                + x
                + "，Z="
                + z
                + "，距离约 "
                + Math.round(Math.hypot(x - (double) ox, z - (double) oz))
                + " 格。");
        boolean teleport = "tplocate".equalsIgnoreCase(args[0]);
        if (!teleport) return;
        if (loaded == null) {
            DimensionManager.initDimension(dimension);
            loaded = DimensionManager.getWorld(dimension);
        }
        if (loaded == null) {
            say(sender, "维度初始化失败，未移动玩家。");
            return;
        }
        final int tx = x, ty = TELEPORT_Y, tz = z;
        final WorldServer destination = loaded;
        if (player.dimension != dimension) {
            player.mcServer.getConfigurationManager()
                .transferPlayerToDimension(player, dimension, new GTSRDimTeleporter(destination) {

                    @Override
                    public void placeInPortal(Entity entity, double a, double b, double c, float yaw) {
                        entity.setLocationAndAngles(tx + .5, ty, tz + .5, yaw, 0F);
                        entity.motionX = entity.motionY = entity.motionZ = 0;
                    }
                });
        }
        player.playerNetServerHandler.setPlayerLocation(tx + .5, ty, tz + .5, player.rotationYaw, 0F);
        player.motionX = player.motionY = player.motionZ = 0;
        player.fallDistance = 0;
        say(sender, "已传送至" + display + "定位坐标上空（" + tx + "，" + ty + "，" + tz + "）。");
    }

    private static double distance(RuinSite s, int x, int z) {
        double dx = s.entryX() - (double) x, dz = s.entryZ() - (double) z;
        return dx * dx + dz * dz;
    }

    private static void say(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(message));
    }
}
