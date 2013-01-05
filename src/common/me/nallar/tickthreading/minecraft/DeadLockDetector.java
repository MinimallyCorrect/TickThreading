package me.nallar.tickthreading.minecraft;

import java.util.EnumSet;
import java.util.Map;
import java.util.TreeMap;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickthreading.Log;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

public class DeadLockDetector {
	private static volatile String lastJob = "";
	private static volatile long lastTickTime = 0;
	private final Map<World, TickManager> managerMap;
	private final Thread deadlockThread;
	private static final ITickHandler tickHandler = new ITickHandler() {
		@Override
		public void tickStart(EnumSet<TickType> type, Object... tickData) {
			tick("Tick start");
		}

		@Override
		public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		}

		@Override
		public EnumSet<TickType> ticks() {
			return null;
		}

		@Override
		public String getLabel() {
			return null;
		}

		;
	};

	public DeadLockDetector(Map<World, TickManager> managerMap) {
		this.managerMap = managerMap;
		deadlockThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (checkForDeadlocks()) {
					try {
						Thread.sleep(4000);
					} catch (InterruptedException ignored) {
					}
				}
			}
		});
		deadlockThread.setDaemon(true);
		deadlockThread.setName("Deadlock Detector");
		deadlockThread.start();
		TickRegistry.registerTickHandler(tickHandler, Side.SERVER);
		TickRegistry.registerTickHandler(tickHandler, Side.CLIENT);
	}

	public static synchronized long tick(String name) {
		lastJob = name;
		return lastTickTime = System.currentTimeMillis();
	}

	public boolean checkForDeadlocks() {
		if (lastTickTime == 0) {
			return true;
		}
		int deadTime = (int) (System.currentTimeMillis() - lastTickTime);
		if (deadTime > 30000 && MinecraftServer.getServer().isServerRunning()) {
			TreeMap<String, Thread> sortedThreads = new TreeMap<String, Thread>();
			StringBuilder sb = new StringBuilder();
			sb.append("The server appears to have deadlocked.")
					.append("\nLast tick ").append(deadTime).append("ms ago.")
					.append("\nTicking: ").append(lastJob).append('\n');
			Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
			for (Thread thread : traces.keySet()) {
				sortedThreads.put(thread.getName(), thread);
			}
			for (Thread thread : sortedThreads.values()) {
				sb.append("Current Thread: ").append(thread.getName()).append('\n').append("    PID: ").append(thread.getId())
						.append(" | Alive: ").append(thread.isAlive()).append(" | State: ").append(thread.getState())
						.append("    Stack:").append('\n');
				for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
					sb.append("        ").append(stackTraceElement.toString()).append('\n');
				}
			}
			Log.severe(sb.toString());
			for (World world : managerMap.keySet()) {
				TickThreading.instance().onWorldUnload(new WorldEvent.Unload(world));
			}
			if (TickThreading.instance().exitOnDeadlock) {
				System.exit(1);
			}
			return false;
		}
		return true;
	}
}
