package me.nallar.tickthreading.minecraft.commands;

import java.util.List;

import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class ProfileCommand extends Command {
	public static String name = "profile";

	@Override
	public String getCommandName() {
		return name;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender commandSender) {
		return !TickThreading.instance.requireOpForProfileCommand || super.canCommandSenderUseCommand(commandSender);
	}

	@Override
	public void processCommand(final ICommandSender commandSender, List<String> arguments) {
		World world = DimensionManager.getWorld(0);
		long time_ = 10;
		boolean entity_ = false;
		try {
			if (!arguments.isEmpty()) {
				entity_ = "e".equals(arguments.get(0));
			}
			if (arguments.size() > 1) {
				time_ = Integer.valueOf(arguments.get(1));
			}
			if (arguments.size() > 2) {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(2)));
			} else if (commandSender instanceof Entity) {
				world = ((Entity) commandSender).worldObj;
			}
		} catch (Exception e) {
			world = null;
		}
		if (world == null) {
			sendChat(commandSender, "Usage: /profile [type=a/e] [time=10] [dimensionid=current dimension]");
			return;
		}
		final TickManager manager = TickThreading.instance.getManager(world);
		final long time = time_;
		final boolean entity = entity_;
		if (entity) {
			manager.profilingEnabled = true;
		} else {
			Timings.enabled = true;
		}
		Runnable profilingRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000 * time);
				} catch (InterruptedException ignored) {
				}
				if (entity) {
					manager.profilingEnabled = false;
				} else {
					Timings.enabled = false;
				}
				try {
					Thread.sleep(100 * time);
				} catch (InterruptedException ignored) {
				}
				if (entity) {
					sendChat(commandSender, String.valueOf(manager.entityTickProfiler.writeData(new TableFormatter(commandSender))));
				} else {
					sendChat(commandSender, String.valueOf(Timings.writeData(new TableFormatter(commandSender))));
				}
				manager.entityTickProfiler.clear();
			}
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TT Profiler");
		sendChat(commandSender, "Profiling for " + time + " seconds in " + Log.name(world));
		profilingThread.start();
	}
}
