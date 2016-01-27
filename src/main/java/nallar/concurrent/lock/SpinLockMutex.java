package nallar.concurrent.lock;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public final class SpinLockMutex implements Lock {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(SpinLockMutex.class.getFields()[0]);
	private volatile int locked = 0;

	@Override
	public synchronized void lock() {
		//noinspection StatementWithEmptyBody
		while (!$.compareAndSwapInt(this, index, 0, 1)) {
			// Spin lock.
			// TODO: Could we instead work on something else here?
			// Avoids overhead of OS scheduling without doing nothing
			// might not be worth the effort to get it working correctly
		}
	}

	@Override
	public synchronized void unlock() {
		if (locked == 0) {
			throw new IllegalStateException("Unlocked " + this + " before it was locked.");
		}
		locked = 0;
	}

	@Override
	public synchronized boolean tryLock() {
		return $.compareAndSwapInt(this, index, 0, 1);
	}

	@Override
	public synchronized void lockInterruptibly() throws InterruptedException {
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
