package me.nallar.tickthreading.minecraft;

import java.util.Map;

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
				while (true) {
					try {
						Thread.sleep(4000);
					} catch (InterruptedException ignored) {
					}
					checkForDeadlocks();
				}
			}
		});
		deadlockThread.setDaemon(true);
		deadlockThread.setName("Deadlock Detector");
		deadlockThread.start();
	}

	public void checkForDeadlocks() {
		long lastRunTime = 0;
		for (TickManager tickManager : managerMap.values()) {
			lastRunTime = Math.max(lastRunTime, tickManager.lastStartTime);
		}
		if (lastRunTime == 0) {
			return;
		}
		int deadTime = (int) (System.currentTimeMillis() - lastRunTime);
		if (deadTime > 10000) {
			StringBuilder sb = new StringBuilder();
			sb.append("The server appears to have deadlocked!")
					.append("\nTicking: ").append(lastJob);
			Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
			for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
				Thread thread = entry.getKey();
				sb.append("Current Thread: ").append(thread.getName()).append("    PID: ").append(thread.getId())
						.append(" | Alive: ").append(thread.isAlive()).append(" | State: ").append(thread.getState())
						.append("    Stack:");
				for (StackTraceElement stackTraceElement : entry.getValue()) {
					sb.append("        ").append(stackTraceElement.toString());
				}
			}
			Log.severe(sb.toString());
		}
	}
}
