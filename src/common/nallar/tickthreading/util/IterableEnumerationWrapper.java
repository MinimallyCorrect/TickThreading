package nallar.tickthreading.util;

import java.util.*;

public class IterableEnumerationWrapper<T> implements Iterator<T>, Iterable<T> {
	private final Enumeration<T> wrappedEnumeration;

	@Override
	public Iterator<T> iterator() {
		return this;
	}

	public IterableEnumerationWrapper(Enumeration<T> wrappedEnumeration) {
		this.wrappedEnumeration = wrappedEnumeration;
	}

	@Override
	public boolean hasNext() {
		return wrappedEnumeration.hasMoreElements();
	}

	@Override
	public T next() {
		return wrappedEnumeration.nextElement();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
