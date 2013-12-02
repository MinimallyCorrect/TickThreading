package nallar.collections;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("ConstantConditions")
public class ConcurrentQueueList<T> extends ConcurrentLinkedQueue<T> implements List<T> {
	@Override
	public boolean addAll(Collection<? extends T> c) {
		return c == this || super.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T get(int index) {
		if (index == 0) {
			return this.peek();
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public T set(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void add(int index, Object element) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T remove(int index) {
		if (index == 0) {
			return this.poll();
		}
		throw new UnsupportedOperationException();
	}

	@Override
	public int indexOf(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int lastIndexOf(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator<T> listIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<T> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException();
	}
}
