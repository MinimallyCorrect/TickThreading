package me.nallar.tickthreading.minecraft;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import me.nallar.tickthreading.Log;

public class LockController {
	private static final Map<Class, Lock> locks = new ConcurrentHashMap<Class, Lock>();
	private static final Lock noLock = new Lock() {
		@Override
		public void lock() {
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
		}

		@Override
		public boolean tryLock() {
			return false;
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			return false;
		}

		@Override
		public void unlock() {
		}

		@Override
		public Condition newCondition() {
			return null;
		}
	};

	static {
		String lockedClasses = "";
		try {
			lockedClasses = Resources.toString(Resources.getResource("lockableClasses.txt"), Charsets.UTF_8);
			lockedClasses = lockedClasses.replace("\r\n", "\n").trim().replace('\n', ',');
		} catch (IOException e) {
			Log.severe("Failed to read lockedClasses", e);
		}
		locks.clear();
		if (!lockedClasses.isEmpty()) {
			String[] lockedClassesList = lockedClasses.split(",\\s*");
			Class clazz;
			for (String lockedClass : lockedClassesList) {
				try {
					clazz = Class.forName(lockedClass);
				} catch (ClassNotFoundException e) {
					Log.info("Class " + lockedClass + " not found, not locking.");
					continue;
				}
				locks.put(clazz, new ReentrantLock());
				Log.info("Added class lock for " + clazz);
			}
		}
	}

	public static Lock getLock(Class clazz) {
		while (true) {
			Lock lock = locks.get(clazz);
			if (lock != null) {
				return lock;
			}
			for (Class implemented : clazz.getInterfaces()) {
				lock = locks.get(implemented);
				if (lock != null) {
					return lock;
				}
			}
			Class superClass = clazz.getSuperclass();
			if (superClass != null) {
				clazz = superClass;
				continue;
			}
			return noLock;
		}
	}

	public static Lock lock(Object o) {
		Lock lock = locks.get(o.getClass());
		if (lock == null) {
			// by checking twice we avoid synchronizing unless we don't already have the lock
			Class clazz = o.getClass();
			synchronized (locks) {
				if (!locks.containsKey(clazz)) {
					lock = getLock(clazz);
					locks.put(clazz, lock);
				} else {
					lock = locks.get(clazz);
				}
			}
		}
		lock.lock();
		return lock;
	}
}
