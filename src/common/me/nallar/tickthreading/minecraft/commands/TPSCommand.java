package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import me.nallar.tickthreading.util.VersionUtil;
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
		TableFormatter tf = new TableFormatter(commandSender);
		tf.sb.append(VersionUtil.versionString()).append('\n');
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
		tf
				.row("Overall")
				.row(MinecraftServer.getTPS())
				.row("")
				.row("")
				.row("")
				.row(TableFormatter.formatDoubleWithPrecision(MinecraftServer.getTickTime() * 2, 3) + '%');
		tf.finishTable();
		sendChat(commandSender, tf.toString());
	}
}
