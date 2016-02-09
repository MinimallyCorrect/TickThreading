package nallar.tickthreading.concurrent.lock;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public final class SimpleMutex implements Lock {
	private boolean locked = false;

	@Override
	public synchronized void lock() {
		while (locked) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}
		locked = true;
	}

	@Override
	public synchronized void unlock() {
		locked = false;
		notify();
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized boolean tryLock() {
		if (!locked) {
			locked = true;
			return true;
		}
		return false;
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
