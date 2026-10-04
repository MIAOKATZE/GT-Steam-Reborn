package com.miaokatze.gtsr.common.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.miaokatze.gtsr.common.dimension.prosperity.WorldProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime;

/** Administrator shortcut for the current generated encounter's boss activation condition. */
final class MissionCommand {

    private MissionCommand() {}

    static void execute(ICommandSender sender, String[] args) {
        if (args.length != 1) throw new WrongUsageException("/gtsr mission");
        EntityPlayerMP player = CommandBase.getCommandSenderAsPlayer(sender);
        if (player.worldObj.isRemote || !(player.worldObj.provider instanceof WorldProviderProsperityRuins)) {
            sender.addChatMessage(new ChatComponentText("此指令只能在繁荣遗迹维度的已生成结构内使用。"));
            return;
        }
        sender.addChatMessage(new ChatComponentText(RemasterRuntime.completeBossMission(player)));
    }
}
