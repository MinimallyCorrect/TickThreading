package nallar.concurrent.collection;

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class TreeHashSet<T> extends TreeSet<T> {
	private static final long serialVersionUID = 0;
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
