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

import me.nallar.tickthreading.Log;

public final class SimpleMutex implements Lock {
	protected boolean locked = false;
	int waiting = 0;

	@Override
	public synchronized void lockInterruptibly() throws InterruptedException {
		if (!locked) {
			locked = true;
			return;
		}
		waiting++;
		do {
			wait(1L);
		} while (locked);
		waiting--;
		locked = true;
	}

	@Override
	public synchronized void lock() {
		if (!locked) {
			locked = true;
			return;
		}
		try {
			waiting++;
			do {
				wait(1L);
			} while (locked);
			waiting--;
			locked = true;
		} catch (InterruptedException ex) {
			// For better performance, we just assume interruption won't happen...
			Log.severe("Interrupted while locking", ex);
		}
	}

	@Override
	public synchronized void unlock() {
		locked = false;
		if (waiting != 0) {
			notify();
		}
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
