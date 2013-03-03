package me.nallar.tickthreading.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.Lock;

public class LockArrayList<T> extends ArrayList<T> {
	public Lock lock;

	public LockArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	public LockArrayList() {
		super();
	}

	public LockArrayList(Collection<? extends T> c) {
		super(c);
	}

	@Override
	public boolean remove(Object o) {
		lock.lock();
		try {
			return super.remove(o);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean add(T o) {
		lock.lock();
		try {
			return super.add(o);
		} finally {
			lock.unlock();
		}
	}
}
