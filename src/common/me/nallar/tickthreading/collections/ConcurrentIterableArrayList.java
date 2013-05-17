package me.nallar.tickthreading.collections;

import java.util.ArrayList;

public class ConcurrentIterableArrayList<T> extends ArrayList<T> {
	private int index;

	public synchronized void reset() {
		index = 0;
	}

	public synchronized T next() {
		return index < size() ? this.get(index++) : null;
	}

	@Override
	public synchronized T remove(int index) {
		if (index < this.index) {
			this.index--;
		}
		return super.remove(index);
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
