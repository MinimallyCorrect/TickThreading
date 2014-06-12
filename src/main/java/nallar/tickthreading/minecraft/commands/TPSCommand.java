package nallar.tickthreading.minecraft.commands;

import com.google.common.base.Strings;
import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.util.ChatFormat;
import nallar.tickthreading.util.TableFormatter;
import nallar.tickthreading.util.VersionUtil;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.*;

public class TPSCommand extends Command {
	public static String name = "tps";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public String getCommandUsage(ICommandSender commandSender) {
		return "Usage: /tps";
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		StatsHolder statsHolder = new StatsHolder();
		TableFormatter tf = new TableFormatter(commandSender);
		tf.sb.append(VersionUtil.TTVersionString()).append('\n');
		tf
				.heading("")
				.heading("E")
				.heading("TE")
				.heading("C")
				.heading("P")
				.heading("L");
		for (TickManager tickManager : TickThreading.instance.getManagers()) {
			tickManager.writeStats(tf, statsHolder);
		}
		tf
				.row("Overall")
				.row(statsHolder.entities)
				.row(statsHolder.tileEntities)
				.row(statsHolder.chunks)
				.row(MinecraftServer.getServerConfigurationManager(MinecraftServer.getServer()).getCurrentPlayerCount())
				.row(TableFormatter.formatDoubleWithPrecision((MinecraftServer.getTickTime() * 100) / MinecraftServer.getTargetTickTime(), 2) + '%');
		tf.finishTable();
		tf.sb.append('\n').append(getTPSString(commandSender instanceof EntityPlayer));
		sendChat(commandSender, tf.toString());
	}

	private static final int tpsWidth = 40;

	private static String getTPSString(boolean withColour) {
		double tps = MinecraftServer.getTPS();
		double targetTPS = MinecraftServer.getTargetTPS();
		double difference = Math.abs(targetTPS - tps);
		int charsFirst = (int) Math.round((tps / targetTPS) * tpsWidth);
		int charsAfter = tpsWidth - charsFirst;
		StringBuilder sb = new StringBuilder();
		sb
				.append(' ')
				.append(TableFormatter.formatDoubleWithPrecision(tps, 2))
				.append(" TPS [ ")
				.append(withColour ? getColourForDifference(difference, targetTPS) : "")
				.append(Strings.repeat("#", charsFirst))
				.append(Strings.repeat("~", charsAfter))
				.append(withColour ? ChatFormat.RESET : "")
				.append(" ] ");
		return sb.toString();
	}

	private static String getColourForDifference(double difference, double targetTPS) {
		switch ((int) (difference / (targetTPS / 4))) {
			case 0:
				return ChatFormat.GREEN.toString();
			case 1:
				return ChatFormat.YELLOW.toString();
			case 2:
				return ChatFormat.RED.toString();
			case 3:
				return ChatFormat.RED.toString() + ChatFormat.BOLD;
			default:
				return ChatFormat.MAGIC.toString();
		}
	}

	public static StatsHolder populateStatsHolder() {
		TPSCommand.StatsHolder statsHolder = new TPSCommand.StatsHolder();
		for (TickManager tickManager : TickThreading.instance.getManagers()) {
			tickManager.recordStats(statsHolder);
		}
		return statsHolder;
	}

	public static class StatsHolder {
		public int chunks;
		public int entities;
		public int tileEntities;
	}
}
