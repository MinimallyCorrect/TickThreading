package me.nallar.tickthreading.minecraft;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.nallar.tickthreading.Log;

public class ThreadManager {
	private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();
	private final AtomicInteger completed = new AtomicInteger(0);

	public ThreadManager(int threads, String name) {
		for (int i = 0; i < threads; i++) {
			Thread workThread = new WorkThread();
			workThread.setName(name + " - " + (i + 1));
			workThread.setDaemon(true);
			workThread.start();
			workThreads.add(workThread);
		}
	}

	public void run(Collection<? extends Runnable> tasks) {
		completed.addAndGet(-tasks.size());
		taskQueue.addAll(tasks);
		while (completed.get() < 0) {
			try {
				synchronized (readyLock) {
					readyLock.wait(0, 100);
				}
			} catch (InterruptedException ignored) {
			}
		}
	}

	public void runBackground(Runnable runnable) {
		completed.decrementAndGet();
		taskQueue.add(runnable);
	}

	public void stop() {
		for (Thread thread : workThreads) {
			taskQueue.add(null);
		}
		try {
			Thread.sleep(50);
		} catch (InterruptedException ignored) {
		}
		for (Thread thread : workThreads) {
			thread.stop();
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
				} catch (ThreadDeath rethrown) {
					throw rethrown;
				} catch (Exception e) {
					Log.severe("Unhandled exception in worker thread", e);
				}
				if (completed.incrementAndGet() == 0) {
					synchronized (readyLock) {
						readyLock.notify();
					}
				}
			}
		}
	}
}
