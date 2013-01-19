package me.nallar.tickthreading.minecraft.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.nallar.tickthreading.Log;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

abstract class Command extends CommandBase {
	static void sendChat(ICommandSender commandSender, String message) {
		Log.info(message);
		while (message != null) {
			int nlIndex = message.indexOf('\n');
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

	@Override
	public final void processCommand(ICommandSender commandSender, String... argumentsArray) {
		processCommand(commandSender, new ArrayList<String>(Arrays.asList(argumentsArray)));
	}

	protected abstract void processCommand(ICommandSender commandSender, List<String> arguments);
}
