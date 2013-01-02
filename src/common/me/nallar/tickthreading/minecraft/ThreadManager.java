package me.nallar.tickthreading.minecraft;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import me.nallar.tickthreading.Log;
import net.minecraft.server.ThreadMinecraftServer;
import net.minecraft.util.AxisAlignedBB;

class ThreadManager {
	private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private final String namePrefix;
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();
	private final AtomicInteger waiting = new AtomicInteger();
	private final Runnable killTask = new Runnable() {
		@Override
		public void run() {
		}
	};

	private void newThread(String name) {
		Thread workThread = new WorkThread();
		workThread.setName(name);
		workThread.setDaemon(true);
		workThread.start();
		workThreads.add(workThread);
	}

	public ThreadManager(int threads, String name) {
		namePrefix = name;
		addThreads(threads);
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

	public void runList(final List<? extends Runnable> tasks) {
		Runnable arrayRunnable = new Runnable() {
			private final AtomicInteger index = new AtomicInteger(0);
			private final int size = tasks.size();

			@Override
			public void run() {
				int c;
				while ((c = index.getAndIncrement()) < size) {
					tasks.get(c).run();
				}
				// TODO: Move this?
				AxisAlignedBB.getAABBPool().cleanPool();
			}
		};
		for (int i = 0, len = workThreads.size(); i < len; i++) {
			runBackground(arrayRunnable);
		}
		waitForCompletion();
	}

	public void run(Iterable<? extends Runnable> tasks) {
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

	private void addThreads(int number) {
		number += workThreads.size();
		for (int i = workThreads.size() + 1; i <= number; i++) {
			newThread(namePrefix + " - " + i);
		}
	}

	private void killThreads(int number) {
		for (int i = 0; i < number; i++) {
			taskQueue.add(killTask);
		}
	}

	public void stop() {
		killThreads(workThreads.size());
	}

	private class WorkThread extends ThreadMinecraftServer {
		public WorkThread() {
			super(null, "");
		}

		@Override
		public void run() {
			while (true) {
				try {
					Runnable runnable;
					synchronized (taskQueue) {
						runnable = taskQueue.take();
					}
					if (runnable == killTask) {
						workThreads.remove(this);
						return;
					}
					runnable.run();
				} catch (InterruptedException ignored) {
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
