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
 * 用法：/gtsr singularity <range> <speed/20tick> <damage/20tick> <durationTicks|NA> <eventId>
 * speed=每20tick吸收方块数，damage=每20tick伤害值，durationTicks=tick 数；duration 为 NA 表示无限。
 * 调试默认：10 1 1 600 0（范围 10、每20tick吸1块、每20tick 1点伤害、600 tick=30秒、事件 0）。
 * 需要 OP 权限等级 4。
 */
public class GTSRCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "gtsr";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/gtsr singularity <range> <speed/20tick> <damage/20tick> <durationTicks|NA> <eventId>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 6 || !args[0].equalsIgnoreCase("singularity")) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        double range = parseClampedDouble(args[1], 0.5D, 128.0D);
        double speed = parseClampedDouble(args[2], 0.01D, 100.0D);
        double damage = parseClampedDouble(args[3], 0.0D, 1000.0D);
        int duration = parseDuration(args[4]);
        int eventId = parseClampedInt(args[5], 0, 999);

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
            eventId);

        sender.addChatMessage(
            new ChatComponentText(
                "Singularity spawned: range " + range
                    + ", speed "
                    + speed
                    + ", damage "
                    + damage
                    + ", duration "
                    + (duration == -1 ? "NA" : duration)
                    + ", event "
                    + eventId));
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "singularity");
        }
        if (args.length == 5) {
            return getListOfStringsMatchingLastWord(args, "NA", "0");
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
}
