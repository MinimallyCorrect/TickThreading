package nallar.collections;

import nallar.tickthreading.util.BooleanThreadLocalDefaultFalse;

import java.util.*;

public class HashSetReplaceIterateTempListNoClear<T> extends HashSet<T> {
	private volatile boolean defer = false;
	private final LinkedList<T> deferred = new LinkedList<T>();
	private static final Iterator emptyIterator = Collections.emptyList().iterator();
	private final BooleanThreadLocalDefaultFalse noDefer = new BooleanThreadLocalDefaultFalse();

	@Override
	public synchronized boolean add(T t) {
		if (defer) {
			return !contains(t) && deferred.add(t);
		} else {
			return super.add(t);
		}
	}

	@Override
	public synchronized Iterator<T> iterator() {
		if (defer) {
			noDefer.set(true);
			return emptyIterator;
		}
		defer = true;
		return super.iterator();
	}

	@Override
	public synchronized void clear() {
		if (noDefer.get()) {
			noDefer.set(false);
			return;
		}
		defer = false;
		addAll(deferred);
		deferred.clear();
	}
}
