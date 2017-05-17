package org.minimallycorrect.tickthreading.concurrent.lock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public final class FIFOMutex implements Lock {
	private final AtomicBoolean locked = new AtomicBoolean(false);
	private final Queue<Thread> waiters = new ConcurrentLinkedQueue<>();

	@Override
	public void lock() {
		boolean wasInterrupted = false;
		Thread current = Thread.currentThread();
		waiters.add(current);

		while (waiters.peek() != current || !locked.compareAndSet(false, true)) {
			LockSupport.park();
			if (Thread.interrupted()) {
				wasInterrupted = true;
			}
		}

		waiters.remove();
		if (wasInterrupted) {
			current.interrupt();
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean tryLock() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unlock() {
		locked.set(false);
		LockSupport.unpark(waiters.peek());
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException();
	}
}
