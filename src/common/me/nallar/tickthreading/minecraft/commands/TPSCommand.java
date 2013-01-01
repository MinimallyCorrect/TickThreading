package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.command.ICommandSender;

public class TPSCommand extends Command {
	public static String name = "tps";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender par1ICommandSender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		StringBuilder tpsReport = new StringBuilder();
		tpsReport.append("---- TPS Report ----\n");
		for (TickManager tickManager : TickThreading.instance().getManagers()) {
			tpsReport.append(Log.name(tickManager.world)).append(": ").append(1000 / tickManager.getEffectiveTickTime()).append("tps, load: ");
			tpsReport.append(tickManager.getTickTime() / 2).append("%\n");
		}
		sendChat(commandSender, tpsReport.toString());
	}
}
