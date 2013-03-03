package me.nallar.tickthreading.collections;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings ("ConstantConditions")
public class ConcurrentLinkedQueueList<T> extends ConcurrentLinkedQueue<T> implements List<T> {
	@Override
	public boolean addAll(Collection<? extends T> c) {
		return c == this || super.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection c) {
		return false;
	}

	@Override
	public T get(int index) {
		return null;
	}

	@Override
	public T set(int index, Object element) {
		return null;
	}

	@Override
	public void add(int index, Object element) {
	}

	@Override
	public T remove(int index) {
		return null;
	}

	@Override
	public int indexOf(Object o) {
		return 0;
	}

	@Override
	public int lastIndexOf(Object o) {
		return 0;
	}

	@Override
	public ListIterator<T> listIterator() {
		return null;
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		return null;
	}

	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		return null;
	}
}
