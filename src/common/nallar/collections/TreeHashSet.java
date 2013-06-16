package nallar.collections;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class TreeHashSet<T> extends TreeSet<T> {
	private final Set internalHashSet = Collections.newSetFromMap(new ConcurrentHashMap());

	public Iterator concurrentIterator() {
		return internalHashSet.iterator();
	}

	@Override
	public boolean contains(Object o) {
		return internalHashSet.contains(o);
	}

	@Override
	public synchronized boolean add(T o) {
		if (internalHashSet.add(o)) {
			super.add(o);
			return true;
		}
		return false;
	}

	@Override
	public synchronized boolean remove(Object o) {
		if (internalHashSet.remove(o)) {
			super.remove(o);
			return true;
		}
		return false;
	}

	@Override
	public synchronized void clear() {
		super.clear();
		internalHashSet.clear();
	}
}
