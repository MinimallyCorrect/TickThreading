package nallar.concurrent.lock;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class TwoWayReentrantReadWriteLock implements ReadWriteLock {
	private final Map<Thread, Integer> readingThreads = new HashMap<>();
	private int writeAccesses = 0;
	private int writeRequests = 0;
	private int readRequests = 0;
	private Thread writingThread = null;
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

	public final synchronized void lockRead() {
		Thread callingThread = Thread.currentThread();
		readRequests++;
		while (writingThread != callingThread && writingThread != null) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}
		readRequests--;

		Integer count = readingThreads.get(callingThread);
		readingThreads.put(callingThread, count == null ? 1 : count + 1);
	}

	public final synchronized void unlockRead() {
		Thread callingThread = Thread.currentThread();
		Integer accessCount_ = readingThreads.get(callingThread);
		if (accessCount_ == null) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
				" hold a read lock on this ReadWriteLock");
		}
		if (accessCount_ == 1) {
			readingThreads.remove(callingThread);
			if (writeRequests > 0 && readingThreads.isEmpty()) {
				notify();
			}
		} else {
			readingThreads.put(callingThread, (accessCount_ - 1));
		}
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod")
	public final synchronized void lockWrite() {
		writeRequests++;
		Thread callingThread = Thread.currentThread();
		int size;
		while ((writingThread != callingThread && writingThread != null) || ((size = readingThreads.size()) > 1 || (size == 1 && readingThreads.get(callingThread) == null))) {
			try {
				wait();
			} catch (InterruptedException ignored) {
			}
		}
		writeRequests--;
		writeAccesses++;
		writingThread = callingThread;
	}

	public final synchronized void unlockWrite() {
		if (writingThread != Thread.currentThread()) {
			throw new IllegalMonitorStateException("Calling Thread does not" +
				" hold the write lock on this ReadWriteLock");
		}
		writeAccesses--;
		if (writeAccesses == 0) {
			writingThread = null;
		}
		if (writeRequests > 0 || readRequests > 0) {
			notifyAll();
		}
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
