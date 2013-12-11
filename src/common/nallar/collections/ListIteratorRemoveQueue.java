package nallar.collections;

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("ConstantConditions")
public class ListIteratorRemoveQueue<T> extends ConcurrentLinkedQueue<T> implements List<T> {
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			private T next;

			@Override
			public boolean hasNext() {
				next = ListIteratorRemoveQueue.this.peek();
				return next != null;
			}

			@Override
			public T next() {
				if (next == null) {
					throw new IllegalStateException("iterator.next() called without .hasNext()");
				}
				return next;
			}

			@Override
			public void remove() {
				if (ListIteratorRemoveQueue.this.poll() != next) {
					throw new IllegalStateException("Already removed");
				}
			}
		};
	}

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
