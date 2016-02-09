package nallar.tickthreading.concurrent.lock;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public final class NotReallyAMutex implements Lock {
	public static final NotReallyAMutex lock = new NotReallyAMutex();

	@Override
	public void lockInterruptibly() throws InterruptedException {
	}

	@Override
	public void lock() {
	}

	@Override
	public void unlock() {
	}

	@Override
	public boolean tryLock() {
		return true;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		return true;
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException();
	}
}
