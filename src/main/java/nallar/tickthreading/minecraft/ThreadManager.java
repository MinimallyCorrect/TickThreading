package nallar.tickthreading.minecraft;

import cpw.mods.fml.common.FMLCommonHandler;
import nallar.collections.ConcurrentIterableArrayList;
import nallar.exception.ThreadStuckError;
import nallar.tickthreading.Log;
import nallar.tickthreading.util.FakeServerThread;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ThreadManager {
	public static final Map<Long, String> threadIdToManager = new ConcurrentHashMap<Long, String>();
	private static final Profiler profiler = MinecraftServer.getServer().theProfiler;
	private final boolean isServer = FMLCommonHandler.instance().getEffectiveSide().isServer();
	private final LinkedBlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
	private String parentName;
	private final String name;
	private final Set<Thread> workThreads = new HashSet<Thread>();
	private final Object readyLock = new Object();
	private final AtomicInteger waiting = new AtomicInteger();
	private final Runnable killTask = new KillRunnable();
	private long endTime = 0;
	private final Runnable workerTask = new Runnable() {
		@SuppressWarnings("FieldRepeatedlyAccessedInMethod")
		@Override
		public void run() {
			try {
				while (true) {
					try {
						try {
							Runnable runnable = taskQueue.take();
							if (runnable == killTask) {
								workThreads.remove(Thread.currentThread());
								return;
							}
							runnable.run();
						} catch (InterruptedException ignored) {
						} catch (Throwable t) {
							Log.severe("Unhandled exception in worker thread " + Thread.currentThread().getName(), t);
						}
					} catch (ThreadStuckError ignored) {
					}
					if (waiting.decrementAndGet() == 0) {
						endTime = System.nanoTime();
						synchronized (readyLock) {
							readyLock.notify();
						}
					}
				}
			} finally {
				threadIdToManager.remove(Thread.currentThread().getId());
			}
		}
	};

	private void newThread(String name) {
		Thread workThread = isServer ? new FakeServerThread(workerTask, name, true) : new Thread(workerTask);
		workThread.start();
		threadIdToManager.put(workThread.getId(), this.name);
		workThreads.add(workThread);
	}

	public ThreadManager(int threads, String name) {
		this.name = name;
		DeadLockDetector.threadManagers.add(this);
		addThreads(threads);
	}

	public boolean isWaiting() {
		return waiting.get() > 0;
	}

	public long waitForCompletion() {
		profiler.startSection(name);
		synchronized (readyLock) {
			while (waiting.get() > 0) {
				try {
					readyLock.wait(1L);
				} catch (InterruptedException ignored) {
				}
			}
		}
		profiler.endSection();
		return endTime;
	}

	private final ConcurrentLinkedQueue<TryRunnable> tryRunnableQueue = new ConcurrentLinkedQueue<TryRunnable>();

	public void runDelayableList(final ConcurrentIterableArrayList<? extends TryRunnable> tasks) {
		tasks.reset();
		Runnable arrayRunnable = new DelayableRunnable(tasks, tryRunnableQueue);
		for (int i = 0, len = workThreads.size(); i < len; i++) {
			run(arrayRunnable);
		}
	}

	/*
	TODO - Use this once RingBuffer class is working - it is big, don't want to allocate it unless used.
	private RingBuffer<TryRunnable> getRingBuffer() {
		RingBuffer<TryRunnable> ringBuffer = this.ringBuffer;
		if (ringBuffer == null) {
			this.ringBuffer = ringBuffer = new RingBuffer<TryRunnable>();
		}
		return ringBuffer;
	}
	*/

	public void run(Iterable<? extends Runnable> tasks) {
		for (Runnable runnable : tasks) {
			run(runnable);
		}
	}

	public void runAll(Runnable runnable) {
		for (int i = 0; i < size(); i++) {
			run(runnable);
		}
	}

	public void run(Runnable runnable) {
		if (taskQueue.add(runnable)) {
			waiting.incrementAndGet();
		} else {
			Log.severe("Failed to add " + runnable);
		}
		if (parentName == null) {
			String pName = threadIdToManager.get(Thread.currentThread().getId());
			parentName = pName == null ? "none" : pName;
		}
	}

	private void addThreads(int number) {
		number += workThreads.size();
		for (int i = workThreads.size() + 1; i <= number; i++) {
			newThread(name + " - " + i);
		}
	}

	private void killThreads(int number) {
		for (int i = 0; i < number; i++) {
			taskQueue.add(killTask);
		}
	}

	public int size() {
		return workThreads.size();
	}

	public void stop() {
		DeadLockDetector.threadManagers.remove(this);
		killThreads(workThreads.size());
	}

	public String getName() {
		return name;
	}

	public String getParentName() {
		return parentName;
	}

	private static class KillRunnable implements Runnable {
		KillRunnable() {
		}

		@Override
		public void run() {
		}
	}

	private static class DelayableRunnable implements Runnable {
		private final ConcurrentIterableArrayList<? extends TryRunnable> tasks;
		private final ConcurrentLinkedQueue<TryRunnable> tryRunnableQueue;

		public DelayableRunnable(ConcurrentIterableArrayList<? extends TryRunnable> tasks, ConcurrentLinkedQueue<TryRunnable> tryRunnableQueue) {
			this.tasks = tasks;
			this.tryRunnableQueue = tryRunnableQueue;
		}

		@Override
		public void run() {
			TryRunnable r;
			while ((r = tasks.next()) != null) {
				if (!r.run()) {
					tryRunnableQueue.add(r);
				}
			}
			while ((r = tryRunnableQueue.poll()) != null) {
				if (!r.run()) {
					tryRunnableQueue.add(r);
				}
			}
		}
	}
}
