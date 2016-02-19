package nallar.tickthreading.concurrent.scheduling;

import nallar.tickthreading.concurrent.collection.ConcurrentIterableArrayList;
import nallar.tickthreading.exception.ThreadStuckError;
import nallar.tickthreading.log.Log;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/*
TODO 19/02/2016: Rework this class? Do we still need this, or is there a better solution?

Was used in previous TT versions to split off work to multiple threads then join afterwards.
 */
public final class ThreadManager {
	public static final Map<Long, String> threadIdToManager = new ConcurrentHashMap<>();
	private static final Runnable killTask = () -> {
		throw new ThreadDeath();
	};
	private final ArrayBlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(100);
	private final String name;
	private final Set<Thread> workThreads = new HashSet<>();
	private final Object readyLock = new Object();
	private final AtomicInteger waiting = new AtomicInteger();
	private final ConcurrentLinkedQueue<TryRunnable> tryRunnableQueue = new ConcurrentLinkedQueue<>();
	private final Runnable workerTask = this::workerTask;
	private String parentName;

	public ThreadManager(int threads, String name) {
		this.name = name;
		//DeadLockDetector.threadManagers.add(this);
		addThreads(threads);
	}

	private void workerTask() {
		try {
			while (true) {
				try {
					Runnable runnable = taskQueue.take();
					if (runnable == killTask) {
						workThreads.remove(Thread.currentThread());
						return;
					}
					try {
						runnable.run();
					} catch (ThreadStuckError | ThreadDeath ignored) {
					}
				} catch (InterruptedException ignored) {
				} catch (Throwable t) {
					Log.error("Unhandled exception in worker thread " + Thread.currentThread().getName(), t);
				}
				if (waiting.decrementAndGet() == 0) {
					synchronized (readyLock) {
						readyLock.notify();
					}
				}
			}
		} finally {
			threadIdToManager.remove(Thread.currentThread().getId());
		}
	}

	private void addThread(String name) {
		Thread workThread = null; //new FakeServerThread(workerTask, name, true);
		workThread.start();
		threadIdToManager.put(workThread.getId(), this.name);
		workThreads.add(workThread);
	}

	public boolean isWaiting() {
		return waiting.get() > 0;
	}

	public void waitForCompletion() {
		synchronized (readyLock) {
			while (waiting.get() > 0) {
				try {
					readyLock.wait(1L);
				} catch (InterruptedException ignored) {
				}
			}
		}
	}

	public void runDelayableList(final ConcurrentIterableArrayList<? extends TryRunnable> tasks) {
		tasks.reset();
		Runnable arrayRunnable = new DelayableRunnable(tasks, tryRunnableQueue);
		for (int i = 0, len = workThreads.size(); i < len; i++) {
			run(arrayRunnable);
		}
	}

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
			Log.error("Failed to add " + runnable);
		}
		if (parentName == null) {
			String pName = threadIdToManager.get(Thread.currentThread().getId());
			parentName = pName == null ? "none" : pName;
		}
	}

	private void addThreads(int count) {
		count += workThreads.size();
		for (int i = workThreads.size() + 1; i <= count; i++) {
			addThread(name + " - " + i);
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
		//DeadLockDetector.threadManagers.remove(this);
		killThreads(workThreads.size());
	}

	public String getName() {
		return name;
	}

	public String getParentName() {
		return parentName;
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
			while ((r = tasks.next()) != null || (r = tryRunnableQueue.poll()) != null) {
				if (!r.run()) {
					tryRunnableQueue.add(r);
				}
			}
		}
	}
}
