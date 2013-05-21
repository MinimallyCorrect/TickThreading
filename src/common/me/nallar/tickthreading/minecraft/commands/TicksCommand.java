package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class TicksCommand extends Command {
	public static String name = "ticks";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean requireOp() {
		return TickThreading.instance.requireOpForTicksCommand;
	}

	private static void usage(ICommandSender commandSender) {
		sendChat(commandSender, "Usage: /ticks [e/b/r [radius]/?] [dimensionid]");
	}

	private static World getWorld(ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		if (!arguments.isEmpty()) {
			try {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(arguments.size() - 1)));
				arguments.remove(arguments.size() - 1);
			} catch (Exception ignored) {
			}
		} else if (commandSender instanceof Entity) {
			world = ((Entity) commandSender).worldObj;
		}
		return world;
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		String type = arguments.isEmpty() ? "t" : arguments.remove(0);
		if ("r".equals(type)) {
			EntityPlayerMP entityPlayerMP;
			try {
				entityPlayerMP = (EntityPlayerMP) commandSender;
			} catch (ClassCastException e) {
				sendChat(commandSender, "/ticks r can only be used in game.");
				return;
			}
			WorldServer worldServer = (WorldServer) entityPlayerMP.worldObj;
			int side = arguments.isEmpty() ? 2 : Integer.valueOf(arguments.get(0));
			int count = 0;
			for (int x = entityPlayerMP.chunkCoordX - side, eX = x + side + side; x < eX; x++) {
				for (int z = entityPlayerMP.chunkCoordZ - side, eZ = z + side + side; z < eZ; z++) {
					PlayerInstance playerInstance = worldServer.getPlayerManager().getOrCreateChunkWatcher(x, z, false);
					if (playerInstance != null) {
						count++;
						playerInstance.forceUpdate();
					}
				}
			}
			sendChat(commandSender, "Refreshed " + count + " chunks");
			return;
		}
		World world;
		try {
			world = getWorld(commandSender, arguments);
		} catch (Exception e) {
			usage(commandSender);
			return;
		}
		if ("e".equals(type)) {
			TableFormatter tf = new TableFormatter(commandSender);
			TickManager tickManager = TickThreading.instance.getManager(world);
			tickManager.writeEntityStats(tf);
			tf.sb.append('\n');
			tickManager.fixDiscrepancies(tf);
			sendChat(commandSender, String.valueOf(tf));
		} else if ("b".equals(type)) {
			sendChat(commandSender, String.valueOf(((WorldServer) world).writePendingBlockUpdatesStats(new TableFormatter(commandSender))));
		} else if ("t".equals(type)) {
			sendChat(commandSender, String.valueOf(TickThreading.instance.getManager(world).writeDetailedStats(new TableFormatter(commandSender))));
		} else {
			usage(commandSender);
		}
	}
}
