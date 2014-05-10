package nallar.collections;

import java.util.*;

public class SynchronizedSet<T> extends HashSet<T> {
	public SynchronizedSet() {
		super();
	}

	@Override
	public synchronized boolean equals(final Object o) {
		return super.equals(o);
	}

	@Override
	public synchronized int hashCode() {
		return super.hashCode();
	}

	@Override
	public synchronized boolean removeAll(final Collection<?> c) {
		return super.removeAll(c);
	}

	public SynchronizedSet(final Collection<? extends T> c) {
		super(c);
	}

	public SynchronizedSet(final int initialCapacity, final float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public SynchronizedSet(final int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public synchronized boolean contains(final Object o) {
		return super.contains(o);
	}

	@Override
	public synchronized Object[] toArray() {
		return super.toArray();
	}

	@Override
	public synchronized <T> T[] toArray(T[] ts) {
		return super.toArray(ts);
	}

	@Override
	public synchronized boolean add(final T o) {
		return super.add(o);
	}

	@Override
	public synchronized boolean remove(final Object o) {
		return super.remove(o);
	}

	@Override
	public synchronized boolean containsAll(final Collection<?> c) {
		return super.containsAll(c);
	}

	@Override
	public synchronized boolean addAll(final Collection<? extends T> c) {
		return super.addAll(c);
	}

	@Override
	public synchronized boolean retainAll(final Collection<?> c) {
		return super.retainAll(c);
	}

	@Override
	public synchronized void clear() {
		super.clear();
	}

	@Override
	public synchronized Object clone() {
		return super.clone();
	}
}
