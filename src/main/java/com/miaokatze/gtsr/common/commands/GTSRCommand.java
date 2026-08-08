package com.miaokatze.gtsr.common.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;

/**
 * /gtsr 指令：在命令发送者位置生成失控奇点。
 * 用法：/gtsr singularity <range> <speed/20tick> <damage/20tick> <durationTicks|NA> <special|null|onlypull> [color]
 * [fxRadius]
 * speed=每20tick吸收方块数，damage=每20tick伤害值，durationTicks=tick 数；duration 为 NA 表示无限。
 * special=特殊状态（0-999），null=纯动画（不吸引/不破坏/不吸收任何方块与实体），
 * onlypull=只牵引不吸收（不吸收方块、不处理掉落物、牵引力度减半、伤害照常），
 * nullplus=null 基础上无电弧无粒子（吸积盘/电弧跳过），光片/辉光保留。
 * color=16 原版染料色之一，省略默认 white；fxRadius=光效半径 [0.5,128]，省略默认 10。
 * 调试默认：10 1 1 600 0 white（范围 10、每20tick吸1块、每20tick 1点伤害、600 tick=30秒、事件 0、白色）。
 * 需要 OP 权限等级 4。
 */
public class GTSRCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtsr";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtsr singularity <range> <speed/20tick> <damage/20tick> <durationTicks|NA> <special|null|onlypull> [color] [fxRadius]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
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
            // 非法颜色走数值错误通道（也可换 WrongUsageException，语义等价）
            throw new NumberInvalidException("commands.generic.num.invalid", color);
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
            fxRadius);

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
                            : special == TileRunawaySingularity.ATTRIBUTE_NULL_PLUS ? "nullplus" : special)
                    + ", color "
                    + color
                    + ", fxRadius "
                    + fxRadius));
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "singularity");
        }
        if (args.length == 5) {
            return getListOfStringsMatchingLastWord(args, "NA", "0", "null", "onlypull", "nullplus", "nature");
        }
        if (args.length == 6) {
            return getListOfStringsMatchingLastWord(
                args,
                "white",
                "orange",
                "magenta",
                "light_blue",
                "yellow",
                "lime",
                "pink",
                "gray",
                "silver",
                "cyan",
                "purple",
                "blue",
                "brown",
                "green",
                "red",
                "black");
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
}
