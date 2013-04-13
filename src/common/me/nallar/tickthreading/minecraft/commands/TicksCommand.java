package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
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

	@Override
	public void processCommand(ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		boolean entities = false;
		try {
			if (!arguments.isEmpty() && "e".equals(arguments.get(0))) {
				entities = true;
				arguments.remove(0);
			}
			if (!arguments.isEmpty()) {
				try {
					world = DimensionManager.getWorld(Integer.valueOf(arguments.get(0)));
				} catch (Exception ignored) {
				}
			} else if (commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
		} catch (Exception e) {
			sendChat(commandSender, "Usage: /ticks [e?] [dimensionid]");
			return;
		}
		if (entities) {
			TableFormatter tf = new TableFormatter(commandSender);
			TickManager tickManager = TickThreading.instance.getManager(world);
			tickManager.writeEntityStats(tf);
			tf.sb.append('\n');
			tickManager.fixDiscrepancies(tf);
			sendChat(commandSender, String.valueOf(tf));
		} else {
			sendChat(commandSender, String.valueOf(TickThreading.instance.getManager(world).writeDetailedStats(new TableFormatter(commandSender))));
		}
	}
}
