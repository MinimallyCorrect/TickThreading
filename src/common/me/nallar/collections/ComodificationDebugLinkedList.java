package me.nallar.collections;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class ComodificationDebugLinkedList<T> extends LinkedList<T> {
	Throwable lastModification = null;

	@Override
	public boolean add(T o) {
		lastModification = new Throwable();
		return super.add(o);
	}

	@Override
	public T remove(int index) {
		lastModification = new Throwable();
		return super.remove(index);
	}

	@Override
	public boolean remove(Object o) {
		lastModification = new Throwable();
		return super.remove(o);
	}

	@Override
	public Iterator<T> iterator() {
		return new DebugListIterator();
	}

	public class DebugListIterator implements Iterator<T> {
		int cursor = 0;
		int lastRet = -1;
		int expectedModCount = modCount;

		@Override
		public boolean hasNext() {
			return cursor != size();
		}

		@Override
		public T next() {
			checkForComodification();
			try {
				T next = get(cursor);
				lastRet = cursor++;
				return next;
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove() {
			if (lastRet == -1) {
				throw new IllegalStateException();
			}
			checkForComodification();

			try {
				ComodificationDebugLinkedList.this.remove(lastRet);
				if (lastRet < cursor) {
					cursor--;
				}
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		private void checkForComodification() {
			if (modCount != expectedModCount) {
				throw new ComodificationException(ComodificationDebugLinkedList.this.lastModification);
			}
		}
	}

	public static class ComodificationException extends RuntimeException {
		public ComodificationException(Throwable cause) {
			super(cause);
		}
	}
}
