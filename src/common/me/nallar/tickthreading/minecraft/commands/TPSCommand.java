package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

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
		TableFormatter tf = new TableFormatter();
		StringBuilder tpsReport = tf.sb;
		tf
				.heading("World")
				.heading("TPS")
				.heading("Entities")
				.heading("Tiles")
				.heading("Chunks")
				.heading("Load");
		for (TickManager tickManager : TickThreading.instance.getManagers()) {
			tickManager.writeStats(tf);
		}
		tf.finishTable();
		float usedTime = MinecraftServer.getTickTime();
		tpsReport.append("\nUsed time per tick: ").append(usedTime).append("ms")
				.append("\nOverall TPS: ").append(MinecraftServer.getTPS())
				.append("\nOverall load: ").append(usedTime * 2).append('%');
		sendChat(commandSender, tpsReport.toString());
	}
}
