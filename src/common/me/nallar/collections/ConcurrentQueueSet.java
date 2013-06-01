package me.nallar.collections;

import java.util.HashSet;
import java.util.LinkedList;

public class ConcurrentQueueSet<T> {
	private final HashSet<T> set = new HashSet<T>();
	private final LinkedList<T> queue = new LinkedList<T>();

	public synchronized boolean add(T t) {
		if (set.add(t)) {
			queue.add(t);
			return true;
		}
		return false;
	}

	public synchronized T take() {
		T t = queue.poll();
		if (t == null) {
			return null;
		}
		set.remove(t);
		return t;
	}

	public int size() {
		return queue.size();
	}

	public boolean isEmpty() {
		return queue.isEmpty();
	}
}
