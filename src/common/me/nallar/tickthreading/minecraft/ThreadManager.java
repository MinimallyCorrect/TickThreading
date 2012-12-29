package me.nallar.tickthreading.minecraft;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.nallar.tickthreading.Log;

public class ThreadManager {
	private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();
	private final AtomicInteger waiting = new AtomicInteger(0);

	public ThreadManager(int threads, String name) {
		for (int i = 0; i < threads; i++) {
			Thread workThread = new WorkThread();
			workThread.setName(name + " - " + (i + 1));
			workThread.setDaemon(true);
			workThread.start();
			workThreads.add(workThread);
		}
	}

	public void waitForCompletion() {
		while (waiting.get() > 0) {
			try {
				synchronized (readyLock) {
					readyLock.wait(0, 100);
				}
			} catch (InterruptedException ignored) {
			}
		}
	}

	public void run(final List<? extends Runnable> tasks) {
		Runnable arrayRunnable = new Runnable() {
			private AtomicInteger index = new AtomicInteger(0);
			private final int size = tasks.size();
			@Override
			public void run() {
				int c;
				while ((c = index.getAndIncrement()) < size) {
					tasks.get(c).run();
				}
			}
		};
		for (int i = 0, len = workThreads.size(); i < len; i++) {
			runBackground(arrayRunnable);
		}
		waitForCompletion();
	}

	public void run(Collection<? extends Runnable> tasks) {
		for (Runnable runnable : tasks) {
			runBackground(runnable);
		}
		waitForCompletion();
	}

	public void runBackground(Runnable runnable) {
		if (taskQueue.add(runnable)) {
			waiting.incrementAndGet();
		} else {
			Log.severe("Failed to add " + runnable);
		}
	}

	public void stop() {
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
					runnable.run();
				} catch (InterruptedException ignored) {
				} catch (ThreadDeath rethrown) {
					throw rethrown;
				} catch (Exception e) {
					Log.severe("Unhandled exception in worker thread", e);
				}
				if (waiting.decrementAndGet() == 0) {
					synchronized (readyLock) {
						readyLock.notify();
					}
				}
			}
		}
	}
}
