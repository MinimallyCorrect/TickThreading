package me.nallar.tickthreading.collections;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

@SuppressWarnings ({"SuspiciousToArrayCall", "SuspiciousMethodCalls"})
public class NonBlockingLongSet extends AbstractSet<Long> implements Set<Long>, Serializable {
	private static final long serialVersionUID = 2354657854757543876L;
	private final NonBlockingHashMapLong<Boolean> m;
	private transient Set<Long> s;

	public NonBlockingLongSet() {
		m = new NonBlockingHashMapLong<Boolean>();
		s = m.keySet();
	}

	public NonBlockingHashMapLong.IteratorLong iteratorLong() {
		return (NonBlockingHashMapLong.IteratorLong) s.iterator();
	}

	@Override
	public void clear() {
		m.clear();
	}

	@Override
	public int size() {
		return m.size();
	}

	@Override
	public boolean isEmpty() {
		return m.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return m.containsKey(o);
	}

	@Override
	public boolean remove(Object o) {
		return m.remove(o) != null;
	}

	@Override
	public boolean add(Long e) {
		return m.put(e, Boolean.TRUE) == null;
	}

	@Override
	public Iterator<Long> iterator() {
		return s.iterator();
	}

	@Override
	public Object[] toArray() {
		return s.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return s.toArray(a);
	}

	@Override
	public String toString() {
		return s.toString();
	}

	@Override
	public int hashCode() {
		return s.hashCode();
	}

	@SuppressWarnings ("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object o) {
		return o == this || s.equals(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return s.containsAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return s.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return s.retainAll(c);
	}

	private void readObject(java.io.ObjectInputStream stream)
			throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		s = m.keySet();
	}
}
