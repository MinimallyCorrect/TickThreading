package me.nallar.tickthreading.collections;

import java.util.concurrent.ArrayBlockingQueue;

public class RunnableArrayBlockingQueue extends ArrayBlockingQueue<Runnable> {
	public RunnableArrayBlockingQueue(int capacity) {
		super(capacity);
	}

	/**
	 * Used internally by the ThreadPoolExecutor.
	 * Making offer block makes the threadpool block, but still run in another thread,
	 * unlike callerRunsPolicy.
	 */
	@Override
	public boolean offer(Runnable r) {
		boolean added = false;
		while (!added) {
			try {
				this.put(r);
				added = true;
			} catch (InterruptedException ignored) {
			}
		}
		return true;
	}
}
