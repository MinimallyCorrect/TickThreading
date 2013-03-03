/*
  File: Mutex.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

  History:
  Date       Who                What
  11Jun1998  dl               Create public version
*/
package me.nallar.tickthreading.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import me.nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

public final class SpinLockMutex implements Lock {
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long index = $.objectFieldOffset(SpinLockMutex.class.getFields()[0]);
	public volatile int locked = 0;

	@Override
	public synchronized void lock() {
		//noinspection StatementWithEmptyBody
		while (!$.compareAndSwapInt(this, index, 0, 1)) {
			// Spin lock.
			// TODO: Could we instead work on something else here?
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
