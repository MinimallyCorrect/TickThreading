package nallar.collections;

import java.util.*;

public class ComodificationDebugArrayList<T> extends ArrayList<T> {
	Throwable lastModification = null;

	public ComodificationDebugArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	public ComodificationDebugArrayList() {
		super();
	}

	public ComodificationDebugArrayList(Collection<? extends T> c) {
		super(c);
	}

	@Override
	public void ensureCapacity(int minCapacity) {
		super.ensureCapacity(minCapacity);
		lastModification = new Throwable();
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
				ComodificationDebugArrayList.this.remove(lastRet);
				if (lastRet < cursor) {
					cursor--;
				}
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ComodificationException(ComodificationDebugArrayList.this.lastModification);
			}
		}

		private void checkForComodification() {
			if (modCount != expectedModCount) {
				throw new ComodificationException(ComodificationDebugArrayList.this.lastModification);
			}
		}
	}

	public static class ComodificationException extends RuntimeException {
		public ComodificationException(Throwable cause) {
			super(cause);
		}
	}
}
