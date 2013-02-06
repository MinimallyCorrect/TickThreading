package me.nallar.tickthreading.collections;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class TreeHashSet extends TreeSet {
	private final Set internalHashSet = new HashSet();

	@Override
	public boolean contains(Object o) {
		return internalHashSet.contains(o);
	}

	@Override
	public boolean add(Object o) {
		if (internalHashSet.add(o)) {
			super.add(o);
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		if (internalHashSet.remove(o)) {
			super.remove(o);
			return true;
		}
		return false;
	}

	@Override
	public void clear() {
		super.clear();
		internalHashSet.clear();
	}
}
