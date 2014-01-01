package nallar.collections;

import java.util.*;

public class SynchronizedPriorityQueue<T> extends PriorityQueue<T> {
	@Override
	public synchronized T remove() {
		return super.remove();
	}

	@Override
	public synchronized boolean add(T t) {
		return super.add(t);
	}
}
