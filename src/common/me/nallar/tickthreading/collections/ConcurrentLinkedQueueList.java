package me.nallar.tickthreading.collections;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentLinkedQueueList extends ConcurrentLinkedQueue implements List {
	@Override
	public boolean addAll(int index, Collection c) {
		return false;
	}

	@Override
	public Object get(int index) {
		return null;
	}

	@Override
	public Object set(int index, Object element) {
		return null;
	}

	@Override
	public void add(int index, Object element) {
	}

	@Override
	public Object remove(int index) {
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
	public ListIterator listIterator() {
		return null;
	}

	@Override
	public ListIterator listIterator(int index) {
		return null;
	}

	@Override
	public List subList(int fromIndex, int toIndex) {
		return null;
	}
}
