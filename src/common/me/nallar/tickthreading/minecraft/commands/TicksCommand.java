package me.nallar.tickthreading.minecraft.commands;

import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.src.CommandBase;
import net.minecraft.src.Entity;
import net.minecraft.src.ICommandSender;
import net.minecraft.src.World;

public class TicksCommand extends CommandBase {
	@Override
	public String getCommandName() {
		return "ticks";
	}

	public boolean canCommandSenderUseCommand(ICommandSender par1ICommandSender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender commandSender, String... arguments) {
		if (commandSender instanceof Entity) {
			World world = ((Entity) commandSender).worldObj;
			String stats = TickThreading.instance().getManager(world).getStats();
			while (stats != null) {
				int nlIndex = stats.indexOf("\n");
				String sent;
				if (nlIndex == -1) {
					sent = stats;
					stats = null;
				} else {
					sent = stats.substring(0, nlIndex);
					stats = stats.substring(nlIndex + 1);
				}
				commandSender.sendChatToPlayer(sent);
			}
		}
	}
}
