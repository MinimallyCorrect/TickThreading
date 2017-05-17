package org.minimallycorrect.tickthreading.concurrent.collection;

import org.minimallycorrect.tickthreading.util.ReflectUtil;
import org.minimallycorrect.tickthreading.util.unsafe.UnsafeAccess;
import sun.misc.Unsafe;

import java.util.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ConcurrentUnsafeIterableArrayList<T> extends ArrayList<T> {
	private static final long serialVersionUID = 0;
	private static final Unsafe $ = UnsafeAccess.$;
	private static final long elementDataIndex = $.objectFieldOffset(ReflectUtil.getField(ArrayList.class, "elementData"));

	public ConcurrentUnsafeIterableArrayList(final int initialCapacity) {
		super(initialCapacity);
	}

	public ConcurrentUnsafeIterableArrayList() {
		super();
	}

	public ConcurrentUnsafeIterableArrayList(final Collection<? extends T> c) {
		super(c);
	}

	public Object[] elementData() {
		return (Object[]) $.getObject(this, elementDataIndex);
	}

	public java.util.Iterator<T> unsafeIterator() {
		return new Iterator(elementData(), size());
	}

	public Iterable<T> unsafe() {
		return this::unsafeIterator;
	}

	private static class Iterator<T> implements java.util.Iterator<T> {
		final Object[] elementData;
		int index;
		private Object next;

		Iterator(final Object[] elementData, int start) {
			this.elementData = elementData;
			if (start >= elementData.length) {
				start = elementData.length - 1;
			}
			index = start;
			getNext();
		}

		@SuppressWarnings("FieldRepeatedlyAccessedInMethod")
		private Object getNext() {
			Object l = next;
			Object o;
			int i = index;
			if (i == -1) {
				next = null;
			} else {
				do {
					o = elementData[i];
				} while (--i != -1 && (o == null || o == l));
				index = i;
				next = o;
			}
			return l;
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public T next() {
			Object o = getNext();
			if (o == null) {
				throw new IllegalStateException("Must call hasNext before next");
			}
			return (T) o;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
