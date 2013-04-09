package me.nallar.tickthreading.collections;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

public class HashSetReplaceIterateTempListClear<T> extends HashSet<T> {
	private volatile boolean defer = false;
	private final LinkedList<T> deferred = new LinkedList<T>();

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
		defer = true;
		return super.iterator();
	}

	@Override
	public synchronized void clear() {
		super.clear();
		defer = false;
		addAll(deferred);
		deferred.clear();
	}
}
