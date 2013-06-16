package nallar.tickthreading.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

public final class NativeMutex implements Lock {
	private static final Unsafe $ = UnsafeAccess.$;

	@Override
	public void lockInterruptibly() throws InterruptedException {
		$.monitorEnter(this);
	}

	/**
	 * If using NativeMutex.lock() on an object which is always an instance of NativeMutex,
	 * use the lockToSynchronized patch to improve performance.
	 */
	@Override
	public void lock() {
		$.monitorEnter(this);
	}

	/**
	 * If using NativeMutex.unlock() on an object which is always an instance of NativeMutex,
	 * use the lockToSynchronized patch to improve performance.
	 */
	@Override
	public void unlock() {
		$.monitorExit(this);
	}

	@Override
	public boolean tryLock() {
		return $.tryMonitorEnter(this);
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
