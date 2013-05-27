package me.nallar.collections;

public class ListSet extends SynchronizedList {
	@Override
	public synchronized boolean add(final Object o) {
		return !this.contains(o) && super.add(o);
	}
}
