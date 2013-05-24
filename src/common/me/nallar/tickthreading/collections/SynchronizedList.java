package me.nallar.tickthreading.collections;

public class SynchronizedList<T> extends ConcurrentUnsafeIterableArrayList<T> {
	@Override
	public synchronized boolean add(final T t) {
		return super.add(t);
	}

	@Override
	public T remove(final int index) {
		return super.remove(index);
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
