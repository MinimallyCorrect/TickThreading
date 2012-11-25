package me.nallar.tickthreading.minecraft.commands;

import net.minecraft.src.CommandBase;
import net.minecraft.src.ICommandSender;

abstract class Command extends CommandBase {
	static void sendChat(ICommandSender commandSender, String message) {
		while (message != null) {
			int nlIndex = message.indexOf("\n");
			String sent;
			if (nlIndex == -1) {
				sent = message;
				message = null;
			} else {
				sent = message.substring(0, nlIndex);
				message = message.substring(nlIndex + 1);
			}
			commandSender.sendChatToPlayer(sent);
		}
	}
}
