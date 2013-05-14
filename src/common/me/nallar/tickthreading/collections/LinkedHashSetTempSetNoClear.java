package me.nallar.tickthreading.collections;

import java.util.Iterator;
import java.util.LinkedHashSet;

public class LinkedHashSetTempSetNoClear<T> extends LinkedHashSet<T> {
	private boolean iterating = false;
	private final LinkedHashSet<T> toAdd = new LinkedHashSet<T>();
	private final LinkedHashSet<Object> toRemove = new LinkedHashSet<Object>();

	@Override
	public synchronized boolean add(final T t) {
		if (iterating) {
			boolean contains = super.contains(t);
			return (toRemove.remove(t) && contains) || (!contains && toAdd.add(t));
		}
		return super.add(t);
	}

	@Override
	public synchronized boolean remove(final Object o) {
		if (iterating) {
			return super.contains(o) && toRemove.add(o) || toAdd.remove(o);
		}
		return super.remove(o);
	}

	@Override
	public synchronized boolean contains(final Object o) {
		return iterating ? super.contains(o) && !toRemove.contains(o) || toAdd.contains(o) : super.contains(o);
	}

	@Override
	public int size() {
		return iterating ? super.size() + toAdd.size() - toRemove.size() : super.size();
	}

	@Override
	public Iterator<T> iterator() {
		if (!Thread.holdsLock(this)) {
			throw new IllegalStateException("Must lock this to iterate.");
		}
		return super.iterator();
	}

	public synchronized Iterator<T> startIteration() {
		if (iterating) {
			throw new IllegalStateException("Already iterating.");
		}
		iterating = true;
		return super.iterator();
	}

	public synchronized void done() {
		if (!iterating) {
			throw new IllegalStateException("Not iterating yet.");
		}
		iterating = false;
		super.addAll(toAdd);
		super.removeAll(toRemove);
		toAdd.clear();
		toRemove.clear();
	}

	public boolean isIterating() {
		return iterating;
	}
}
