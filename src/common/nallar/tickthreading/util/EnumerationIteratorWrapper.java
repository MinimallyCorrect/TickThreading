package nallar.tickthreading.util;

import java.util.Enumeration;
import java.util.Iterator;

public class EnumerationIteratorWrapper<T> implements Enumeration<T> {
	private final Iterator<T> iterator;

	public EnumerationIteratorWrapper(final Iterator<T> iterator) {
		this.iterator = iterator;
	}

	@Override
	public boolean hasMoreElements() {
		return iterator.hasNext();
	}

	@Override
	public T nextElement() {
		return iterator.next();
	}
}
