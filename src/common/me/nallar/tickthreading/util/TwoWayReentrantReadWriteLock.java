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
	private int writeAccesses = 0;
	private int writeRequests = 0;
	private Thread writingThread = null;
	private Lock readLock = new SimpleLock() {
		@Override
		public void lock() {
			TwoWayReentrantReadWriteLock.this.lockRead();
		}

		@Override
		public void unlock() {
			TwoWayReentrantReadWriteLock.this.unlockRead();
		}
	};
	private Lock writeLock = new SimpleLock() {
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
		while (!canGrantReadAccess(callingThread)) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}

		readingThreads.put(callingThread,
				(getReadAccessCount(callingThread) + 1));
	}

	private boolean canGrantReadAccess(Thread callingThread) {
		return isWriter(callingThread) || !hasWriter() && (isReader(callingThread) || !hasWriteRequests());
	}

	public synchronized void unlockRead() {
		Thread callingThread = Thread.currentThread();
		if (!isReader(callingThread)) {
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
		while (!canGrantWriteAccess(callingThread)) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}
		writeRequests--;
		writeAccesses++;
		writingThread = callingThread;
	}

	public synchronized void unlockWrite() {
		if (!isWriter(Thread.currentThread())) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
					" hold the write lock on this ReadWriteLock");
		}
		writeAccesses--;
		if (writeAccesses == 0) {
			writingThread = null;
		}
		notifyAll();
	}

	private boolean canGrantWriteAccess(Thread callingThread) {
		return isOnlyReader(callingThread) || !hasReaders() && (writingThread == null || isWriter(callingThread));
	}

	private int getReadAccessCount(Thread callingThread) {
		Integer accessCount = readingThreads.get(callingThread);
		if (accessCount == null) {
			return 0;
		}
		return accessCount.intValue();
	}

	private boolean hasReaders() {
		return readingThreads.size() > 0;
	}

	private boolean isReader(Thread callingThread) {
		return readingThreads.get(callingThread) != null;
	}

	private boolean isOnlyReader(Thread callingThread) {
		return readingThreads.size() == 1 &&
				readingThreads.get(callingThread) != null;
	}

	private boolean hasWriter() {
		return writingThread != null;
	}

	private boolean isWriter(Thread callingThread) {
		return writingThread == callingThread;
	}

	private boolean hasWriteRequests() {
		return this.writeRequests > 0;
	}

	private abstract class SimpleLock implements Lock {
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
