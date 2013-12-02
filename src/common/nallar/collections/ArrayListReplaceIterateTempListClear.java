package nallar.collections;

import nallar.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

public class ArrayListReplaceIterateTempListClear<T> extends ArrayList<T> {
	private volatile boolean defer = false;
	private final LinkedList<T> deferred = new LinkedList<T>();
	private static final Iterator emptyIterator = Collections.emptyList().iterator();
	private static final Unsafe $ = UnsafeAccess.$;

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
		$.monitorEnter(this);
		if (defer) {
			return emptyIterator;
		}
		defer = true;
		return super.iterator();
	}

	@Override
	public synchronized void clear() {
		super.clear();
		defer = false;
		addAll(deferred);
		deferred.clear();
		$.monitorExit(this);
	}
}
