package nallar.collections;

import nallar.tickthreading.Log;

import java.util.Collection;

public class SynchronizedList<T> extends ConcurrentUnsafeIterableArrayList<T> {
	public SynchronizedList(final int initialCapacity) {
		super(initialCapacity);
	}

	public SynchronizedList() {
		super();
	}

	public SynchronizedList(final Collection<? extends T> c) {
		super(c);
	}

	@Override
	public synchronized boolean add(final T t) {
		if (t == null) {
			Log.severe("Tried to add null to SynchronizedList", new Throwable());
		}
		return super.add(t);
	}

	@Override
	public synchronized T remove(final int index) {
		return super.remove(index);
	}

	@Override
	public synchronized boolean remove(final Object o) {
		return super.remove(o);
	}

	@Override
	public synchronized <T1> T1[] toArray(final T1[] a) {
		return super.toArray(a);
	}

	@Override
	public synchronized Object[] toArray() {
		return super.toArray();
	}
}
