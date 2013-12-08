package nallar.tickthreading.util.concurrent;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public final class SimpleMutex implements Lock {
	private boolean locked = false;

	@Override
	public final synchronized void lock() {
		while (locked) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}
		locked = true;
	}

	@Override
	public final synchronized void unlock() {
		locked = false;
		notifyAll();
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
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException();
	}
}
