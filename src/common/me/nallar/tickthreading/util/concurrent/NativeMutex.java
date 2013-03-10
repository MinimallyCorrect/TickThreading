package me.nallar.tickthreading.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import me.nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

public final class NativeMutex implements Lock {
	private static final Unsafe $ = UnsafeAccess.$;

	@Override
	public void lockInterruptibly() throws InterruptedException {
		$.monitorEnter(this);
	}

	@Override
	public void lock() {
		$.monitorEnter(this);
	}

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
