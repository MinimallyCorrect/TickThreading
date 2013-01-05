package me.nallar.tickthreading.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Derived from http://tutorials.jenkov.com/java-concurrency/read-write-locks.html#full
 */
public class TwoWayReentrantReadWriteLock implements ReadWriteLock {
	private final Map<Thread, Integer> readingThreads = new HashMap<Thread, Integer>();
	private volatile int writeAccesses = 0;
	private volatile int writeRequests = 0;
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
				wait(0, 100);
			} catch (InterruptedException ignored) {
			}
		}

		readingThreads.put(callingThread,
				(getReadAccessCount(callingThread) + 1));
	}

	private boolean cantGrantReadAccess(Thread callingThread) {
		return isNotWriter(callingThread) && (hasWriter() || (isNotReader(callingThread) && hasWriteRequests()));
	}

	public synchronized void unlockRead() {
		Thread callingThread = Thread.currentThread();
		if (isNotReader(callingThread)) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
					" hold a read lock on this ReadWriteLock");
		}
		int accessCount = getReadAccessCount(callingThread);
		if (accessCount == 1) {
			readingThreads.remove(callingThread);
		} else {
			readingThreads.put(callingThread, (accessCount - 1));
		}
		notifyAll();
	}

	public synchronized void lockWrite() {
		writeRequests++;
		Thread callingThread = Thread.currentThread();
		while (cantGrantWriteAccess(callingThread)) {
			try {
				wait(0, 100);
			} catch (InterruptedException ignored) {
			}
		}
		writeRequests--;
		writeAccesses++;
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
		return readingThreads.size() > 0;
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

	private abstract static class SimpleLock implements Lock {
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
