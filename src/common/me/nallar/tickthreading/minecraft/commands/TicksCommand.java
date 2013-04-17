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
	public boolean canCommandSenderUseCommand(ICommandSender commandSender) {
		return !TickThreading.instance.requireOpForTicksCommand || super.canCommandSenderUseCommand(commandSender);
	}

	private void usage(ICommandSender commandSender) {
		sendChat(commandSender, "Usage: /ticks [e/b/?] [dimensionid]");
	}

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		try {
			if (!arguments.isEmpty()) {
				try {
					world = DimensionManager.getWorld(Integer.valueOf(arguments.get(arguments.size() - 1)));
					arguments.remove(arguments.size() - 1);
				} catch (Exception ignored) {
				}
			} else if (commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
		} catch (Exception e) {
			usage(commandSender);
		}
		String type = arguments.isEmpty() ? "t" : arguments.get(0);
		if ("r".equals(type)) {
			EntityPlayerMP entityPlayerMP = (EntityPlayerMP) commandSender;
			WorldServer worldServer = (WorldServer) entityPlayerMP.worldObj;
			PlayerInstance playerInstance = worldServer.getPlayerManager().getOrCreateChunkWatcher(entityPlayerMP.chunkCoordX, entityPlayerMP.chunkCoordZ, false);
			sendChat(commandSender, "Refreshed chunks at " + playerInstance);
			if (playerInstance != null) {
				playerInstance.forceUpdate();
			}
		} else if ("e".equals(type)) {
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
