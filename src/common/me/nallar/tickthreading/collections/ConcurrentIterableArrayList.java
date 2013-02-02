package me.nallar.tickthreading.collections;

import java.util.ArrayList;

public class ConcurrentIterableArrayList<T> extends ArrayList<T> {
	private final Object indexLock = new Object();
	private int index;

	public void reset() {
		synchronized (indexLock) {
			index = 0;
		}
	}

	public T next() {
		synchronized (indexLock) {
			return index < size() ? this.get(index++) : null;
		}
	}

	@Override
	public T remove(int index) {
		synchronized (indexLock) {
			if (index < this.index) {
				this.index--;
			}
			return super.remove(index);
		}
	}

	@Override
	public boolean remove(Object o) {
		for (int index = 0; index < size(); index++) {
			if (o.equals(get(index))) {
				remove(index);
				return true;
			}
		}
		return false;
	}
}
