package me.nallar.tickthreading.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import me.nallar.tickthreading.Log;

/**
 * Derived from http://tutorials.jenkov.com/java-concurrency/read-write-locks.html#full
 */
public class TwoWayReentrantReadWriteLock implements ReadWriteLock {
	private final Map<Thread, Integer> readingThreads = new HashMap<Thread, Integer>();
	private volatile int writeAccesses = 0;
	private volatile int writeRequests = 0;
	protected boolean fair = true;
	private volatile Thread writingThread = null;
	private final Lock readLock = new SimpleLock() {
		@Override
		public void lock() {
			TwoWayReentrantReadWriteLock.this.lockRead();
		}

		@Override
		public void unlock() {
			TwoWayReentrantReadWriteLock.this.unlockRead();
		}
	};
	private final Lock writeLock = new SimpleLock() {
		@Override
		public void lock() {
			TwoWayReentrantReadWriteLock.this.lockWrite();
		}

		@Override
		public void unlock() {
			TwoWayReentrantReadWriteLock.this.unlockWrite();
		}
	};

	@Override
	public Lock readLock() {
		return readLock;
	}

	@Override
	public Lock writeLock() {
		return writeLock;
	}

	public synchronized void lockRead() {
		Thread callingThread = Thread.currentThread();
		while (cantGrantReadAccess(callingThread)) {
			try {
				wait(0, 10000);
			} catch (InterruptedException ignored) {
			}
		}

		readingThreads.put(callingThread,
				(getReadAccessCount(callingThread) + 1));
	}

	private boolean cantGrantReadAccess(Thread callingThread) {
		return isNotWriter(callingThread) && (hasWriter() || (isNotReader(callingThread) && (fair && hasWriteRequests())));
	}

	public synchronized void unlockRead() {
		Thread callingThread = Thread.currentThread();
		Integer accessCount_ = readingThreads.get(callingThread);
		if (accessCount_ == null) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
					" hold a read lock on this ReadWriteLock");
		}
		if (accessCount_ == 1) {
			readingThreads.remove(callingThread);
			if (readingThreads.isEmpty()) {
				notify();
			}
		} else {
			readingThreads.put(callingThread, (accessCount_ - 1));
		}
	}

	public synchronized void lockWrite() {
		writeRequests++;
		//debug("lockwrite");
		Thread callingThread = Thread.currentThread();
		while (cantGrantWriteAccess(callingThread)) {
			try {
				wait(0, 10000);
			} catch (InterruptedException ignored) {
			}
		}
		writeRequests--;
		writeAccesses++;
		if (writingThread != null && writeAccesses == 1) {
			throw new IllegalStateException("Writing thread was already set when granting write access");
		}
		writingThread = callingThread;
	}

	public synchronized void unlockWrite() {
		if (isNotWriter(Thread.currentThread())) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
					" hold the write lock on this ReadWriteLock");
		}
		writeAccesses--;
		if (writeAccesses == 0) {
			writingThread = null;
		}
		notifyAll();
		//debug("unlockwrite");
	}

	private boolean cantGrantWriteAccess(Thread callingThread) {
		return isNotOnlyReader(callingThread) && (hasReaders() || (writingThread != null && isNotWriter(callingThread)));
	}

	private int getReadAccessCount(Thread callingThread) {
		Integer accessCount = readingThreads.get(callingThread);
		if (accessCount == null) {
			return 0;
		}
		return accessCount;
	}

	private boolean hasReaders() {
		return !readingThreads.isEmpty();
	}

	private boolean isNotReader(Thread callingThread) {
		return readingThreads.get(callingThread) == null;
	}

	private boolean isNotOnlyReader(Thread callingThread) {
		return readingThreads.size() != 1 ||
				readingThreads.get(callingThread) == null;
	}

	private boolean hasWriter() {
		return writingThread != null;
	}

	private boolean isNotWriter(Thread callingThread) {
		return writingThread != callingThread;
	}

	private boolean hasWriteRequests() {
		return this.writeRequests > 0;
	}

	private void debug(String pos) {
		Log.info(pos + ", r: " + readingThreads.size() + ", w: " + (writingThread == null) + ", wa: " + writeAccesses + ", wr: " + writeRequests);
	}

	private abstract static class SimpleLock implements Lock {
		SimpleLock() {
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			lock();
		}

		@Override
		public boolean tryLock() {
			throw new UnsupportedOperationException("You dun goofed! TwoWayReentrantReadWriteLock doesn't support this.");
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			throw new UnsupportedOperationException("You dun goofed! TwoWayReentrantReadWriteLock doesn't support this.");
		}

		@Override
		public Condition newCondition() {
			throw new UnsupportedOperationException("You dun goofed! TwoWayReentrantReadWriteLock doesn't support this.");
		}
	}
}
