package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
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
		World world = null;
		if (arguments.size() > 0) {
			world = DimensionManager.getWorld(Integer.valueOf(arguments.get(0)));
		} else if (commandSender instanceof Entity) {
			world = ((Entity) commandSender).worldObj;
		}
		if (world == null) {
			Log.info("Usage: /ticks [dimensionid]");
			return;
		}
		sendChat(commandSender, TickThreading.instance.getManager(world).getDetailedStats());
	}
}
