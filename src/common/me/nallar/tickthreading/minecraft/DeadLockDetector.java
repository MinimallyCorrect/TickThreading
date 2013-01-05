package me.nallar.tickthreading.minecraft;

import java.util.Map;
import java.util.TreeMap;

import me.nallar.tickthreading.Log;
import net.minecraft.world.World;

public class DeadLockDetector {
	public static String lastJob = "";
	private final Map<World, TickManager> managerMap;
	private final Thread deadlockThread;

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
	}

	public boolean checkForDeadlocks() {
		long lastRunTime = 0;
		for (TickManager tickManager : managerMap.values()) {
			lastRunTime = Math.max(lastRunTime, tickManager.lastStartTime);
		}
		if (lastRunTime == 0) {
			return true;
		}
		int deadTime = (int) (System.currentTimeMillis() - lastRunTime);
		if (deadTime > 30000) {
			TreeMap<String, Thread> sortedThreads = new TreeMap<String, Thread>();
			StringBuilder sb = new StringBuilder();
			sb.append("The server appears to have deadlocked!")
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
			System.exit(1);
			return false;
		}
		return true;
	}
}
