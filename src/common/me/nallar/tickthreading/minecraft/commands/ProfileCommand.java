package me.nallar.tickthreading.minecraft.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
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
	public boolean requireOp() {
		return TickThreading.instance.requireOpForProfileCommand;
	}

	@Override
	public void processCommand(final ICommandSender commandSender, List<String> arguments) {
		World world = null;
		int time_ = 7;
		boolean entity_;
		boolean location_ = false;
		Integer x = null;
		Integer z = null;
		try {
			if (arguments.isEmpty()) {
				throw new Exception();
			}
			entity_ = "e".equals(arguments.get(0));
			if ("c".equals(arguments.get(0))) {
				entity_ = true;
				location_ = true;
				if (arguments.size() > 2) {
					x = Integer.valueOf(arguments.remove(1));
					z = Integer.valueOf(arguments.remove(1));
				}
			}
			if (arguments.size() > 1) {
				time_ = Integer.valueOf(arguments.get(1));
			}
			if (arguments.size() > 2) {
				world = DimensionManager.getWorld(Integer.valueOf(arguments.get(2)));
				if (world == null) {
					throw new NullPointerException();
				}
			}
		} catch (Exception e) {
			sendChat(commandSender, "Usage: /profile [type=a/e/(c [x] [z])] [time=7] [dimensionid=current dimension]");
			return;
		}
		final int time = time_;
		final boolean entity = entity_;
		final boolean location = location_;
		if (location) {
			if (commandSender instanceof Entity) {
				Entity e = (Entity) commandSender;
				x = e.chunkCoordX;
				z = e.chunkCoordZ;
				world = e.worldObj;
			} else {
				world = DimensionManager.getWorld(0);
			}
		}
		final TickManager manager = TickThreading.instance.getManager(world == null ? DimensionManager.getWorld(0) : world);
		final List<World> worlds = new ArrayList<World>();
		if (world == null) {
			Collections.addAll(worlds, DimensionManager.getWorlds());
		} else {
			worlds.add(world);
		}
		final int hashCode = x != null ? manager.getHashCode(x, z) : (commandSender instanceof Entity ? manager.getHashCode((Entity) commandSender) : 0);
		if (entity) {
			final EntityTickProfiler entityTickProfiler = EntityTickProfiler.ENTITY_TICK_PROFILER;
			if (!entityTickProfiler.startProfiling(new Runnable() {
				@Override
				public void run() {
					if (location) {
						manager.getEntityRegion(hashCode).profilingEnabled = false;
						manager.getTileEntityRegion(hashCode).profilingEnabled = false;
					}
					sendChat(commandSender, entityTickProfiler.writeData(new TableFormatter(commandSender)).toString());
				}
			}, location ? ProfilingState.CHUNK : ProfilingState.GLOBAL, time, worlds)) {
				sendChat(commandSender, "Someone else is currently profiling.");
			}
			if (location) {
				manager.profilingEnabled = false;
				manager.getEntityRegion(hashCode).profilingEnabled = true;
				manager.getTileEntityRegion(hashCode).profilingEnabled = true;
			}
			sendChat(commandSender, "Profiling for " + time + " seconds in " + (world == null ? "all worlds " : Log.name(world)) + (location ? " at chunk coords " + x + ',' + z : ""));
			return;
		}
		if (Timings.enabled) {
			sendChat(commandSender, "Someone else is currently profiling, please wait and try again.");
			return;
		}
		Timings.enabled = true;
		Runnable profilingRunnable = new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1000 * time);
				} catch (InterruptedException ignored) {
				}
				Timings.enabled = false;
				try {
					Thread.sleep(100 * time);
				} catch (InterruptedException ignored) {
				}
				sendChat(commandSender, String.valueOf(Timings.writeData(new TableFormatter(commandSender))));
				Timings.clear();
			}
		};
		Thread profilingThread = new Thread(profilingRunnable);
		profilingThread.setName("TT Profiler");
		sendChat(commandSender, "Profiling for " + time + " seconds");
		profilingThread.start();
	}

	public static enum ProfilingState {
		NONE,
		GLOBAL,
		CHUNK
	}
}
