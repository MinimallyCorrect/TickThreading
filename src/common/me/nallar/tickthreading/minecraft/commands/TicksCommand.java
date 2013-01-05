package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import cpw.mods.fml.common.FMLCommonHandler;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class TicksCommand extends Command {
	public static String name = "ticks";

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
		if (commandSender instanceof Entity) {
			World world = ((Entity) commandSender).worldObj;
			if (arguments.contains("all")) {

			} else {
				sendChat(commandSender, TickThreading.instance().getManager(world).getDetailedStats());
			}
		} else {
			Log.info("/ticks must be used by an entity - it provides stats on the current world");
			// TODO: get world by name from console.
		}
	}
}
