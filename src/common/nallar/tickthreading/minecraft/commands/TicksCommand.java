package nallar.tickthreading.minecraft.commands;

import java.util.List;

import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.util.TableFormatter;
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

	@Override
	public String getCommandUsage(ICommandSender commandSender) {
		return "Usage: /ticks [b/d/e/l/t/r [radius]/?] [dimensionid]";
	}

	private static World getWorld(ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		if (!arguments.isEmpty()) {
			try {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.remove(arguments.size() - 1)));
			} catch (Exception ignored) {
			}
		} else if (commandSender instanceof Entity) {
			world = ((Entity) commandSender).worldObj;
		}
		return world;
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		String type = "t";
		if (!arguments.isEmpty()) {
			try {
				Integer.parseInt(arguments.get(0));
			} catch (NumberFormatException ignored) {
				type = arguments.remove(0);
			}
		}
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
		if ("d".equals(type)) {
			int region = 0;
			int worldId;
			try {
				worldId = Integer.parseInt(arguments.remove(0));
			} catch (NumberFormatException e) {
				throw e;
			} catch (Throwable t) {
				worldId = commandSender instanceof Entity ? ((Entity) commandSender).worldObj.dimensionId : 0;
			}
			if (arguments.size() == 2) {
				region = TickManager.getHashCode(Integer.parseInt(arguments.remove(0)), Integer.parseInt(arguments.remove(0)));
			} else if (commandSender instanceof Entity) {
				region = TickManager.getHashCode((Entity) commandSender);
			}
			sendChat(commandSender, String.valueOf(TickThreading.instance.getManager(DimensionManager.getWorld(worldId)).writeRegionDetails(new TableFormatter(commandSender), region)));
			return;
		}
		World world;
		try {
			world = getWorld(commandSender, arguments);
		} catch (Exception e) {
			world = null;
		}
		if (world == null) {
			sendChat(commandSender, usage());
			return;
		}
		if ("e".equals(type)) {
			TableFormatter tf = new TableFormatter(commandSender);
			TickManager tickManager = TickThreading.instance.getManager(world);
			tickManager.writeEntityStats(tf);
			tf.sb.append('\n');
			tickManager.fixDiscrepancies(tf.sb);
			sendChat(commandSender, String.valueOf(tf));
		} else if ("b".equals(type)) {
			sendChat(commandSender, String.valueOf(((WorldServer) world).writePendingBlockUpdatesStats(new TableFormatter(commandSender))));
		} else if ("t".equals(type)) {
			sendChat(commandSender, String.valueOf(TickThreading.instance.getManager(world).writeDetailedStats(new TableFormatter(commandSender))));
		} else if ("l".equals(type)) {
			sendChat(commandSender, String.valueOf(TickThreading.instance.getManager(world).writeTECounts(new TableFormatter(commandSender))));
		} else {
			sendChat(commandSender, usage());
		}
	}
}
