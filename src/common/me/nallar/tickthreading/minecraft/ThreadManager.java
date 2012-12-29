package me.nallar.tickthreading.minecraft;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadManager {
	private final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();

	public ThreadManager(int threads) {
		for (int i = 0; i < threads; i++) {
			Thread workThread = new WorkThread();
			workThread.setDaemon(true);
			workThread.start();
			workThreads.add(workThread);
		}
	}

	public void run(Collection<? extends Runnable> tasks) {
		taskQueue.addAll(tasks);
		while (taskQueue.size() != 0) {
			try {
				synchronized (readyLock) {
					readyLock.wait(1);
				}
			} catch (InterruptedException ignored) {
			}
		}
	}

	public void stop() {
		for (Thread thread : workThreads) {
			taskQueue.add(null);
		}
		workThreads.clear();
	}

	private class WorkThread extends Thread {
		@Override
		public void run() {
			while (true) {
				try {
					Runnable runnable;
					synchronized (taskQueue) {
						runnable = taskQueue.take();
					}
					if (runnable == null) {
						return;
					}
					runnable.run();
				} catch (InterruptedException ignored) {
				}
				try {
					Thread.sleep(0, 10);
				} catch (InterruptedException ignored) {
				}
				synchronized (readyLock) {
					readyLock.notify();
				}
			}
		}
	}
}
