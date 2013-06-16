package nallar.collections;

import java.util.Iterator;

public class LinkedListIterateClear extends ConcurrentLinkedQueueList {
	public Iterator clearIterator() {
		return new ClearIterator(iterator());
	}

	private static class ClearIterator implements java.util.Iterator {
		final Iterator iterator;

		ClearIterator(final Iterator iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public Object next() {
			Object next = iterator.next();
			iterator.remove();
			return next;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
