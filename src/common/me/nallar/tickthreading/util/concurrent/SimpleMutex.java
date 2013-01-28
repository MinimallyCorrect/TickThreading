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

public class SimpleMutex implements Lock {
	protected boolean locked = false;

	@Override
	public synchronized void lockInterruptibly() throws InterruptedException {
		try {
			while (locked) {
				wait();
			}
			locked = true;
		} catch (InterruptedException ex) {
			notify();
			throw ex;
		}
	}

	@Override
	public synchronized void lock() {
		try {
			while (locked) {
				wait();
			}
			locked = true;
		} catch (InterruptedException ex) {
			notify();
		}
	}

	@Override
	public synchronized void unlock() {
		locked = false;
		notify();
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
