package nallar.collections;

import java.util.*;

public class CLinkedHashSet<T> extends LinkedHashSet<T> {
	public CLinkedHashSet(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public CLinkedHashSet(int initialCapacity) {
		super(initialCapacity);
	}

	public CLinkedHashSet() {
		super();
	}

	public CLinkedHashSet(Collection<? extends T> c) {
		super(c);
	}

	@Override
	public synchronized boolean add(T t) {
		return super.add(t);
	}

	@Override
	public synchronized boolean remove(Object o) {
		return super.remove(o);
	}

	@Override
	public synchronized void clear() {
		super.clear();
	}
}
