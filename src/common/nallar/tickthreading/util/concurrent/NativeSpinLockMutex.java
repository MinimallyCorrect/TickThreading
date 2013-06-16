package nallar.tickthreading.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

public final class NativeSpinLockMutex implements Lock {
	private static final Unsafe $ = UnsafeAccess.$;

	@Override
	public synchronized void lock() {
		//noinspection StatementWithEmptyBody
		while (!$.tryMonitorEnter(this)) {
			// Spin lock.
			// TODO: Could we instead work on something else here?
			// Avoids overhead of OS scheduling without doing nothing
			// might not be worth the effort to get it working correctly
		}
	}

	@Override
	public synchronized void unlock() {
		$.monitorExit(this);
	}

	@Override
	public synchronized boolean tryLock() {
		return $.tryMonitorEnter(this);
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
