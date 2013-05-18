package me.nallar.tickthreading.collections;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import me.nallar.tickthreading.util.EnumerationIteratorWrapper;

public class CopyOnWriteVector extends Vector {
	CopyOnWriteArrayList copyOnWriteArrayList = new CopyOnWriteArrayList();

	public CopyOnWriteVector(final int initialCapacity, final int capacityIncrement) {
		super(initialCapacity, capacityIncrement);
	}

	public CopyOnWriteVector(final int initialCapacity) {
		super(initialCapacity);
	}

	public CopyOnWriteVector() {
		super();
	}

	@Override
	public synchronized void copyInto(final Object[] anArray) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void trimToSize() {
	}

	@Override
	public synchronized void ensureCapacity(final int minCapacity) {
	}

	@Override
	public synchronized void setSize(final int newSize) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized int capacity() {
		return size();
	}

	@Override
	public Enumeration elements() {
		return new EnumerationIteratorWrapper(iterator());
	}

	@Override
	public synchronized Object elementAt(final int index) {
		return get(index);
	}

	@Override
	public synchronized Object firstElement() {
		return get(0);
	}

	@Override
	public synchronized Object lastElement() {
		return get(size() - 1);
	}

	@Override
	public synchronized void setElementAt(final Object obj, final int index) {
		set(index, obj);
	}

	@Override
	public synchronized void removeElementAt(final int index) {
		remove(index);
	}

	@Override
	public synchronized void insertElementAt(final Object obj, final int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	public synchronized void addElement(final Object obj) {
		add(obj);
	}

	@Override
	public synchronized boolean removeElement(final Object obj) {
		return remove(obj);
	}

	@Override
	public synchronized void removeAllElements() {
		clear();
	}

	@Override
	protected synchronized void removeRange(final int fromIndex, final int toIndex) {
		throw new UnsupportedOperationException();
	}

	public CopyOnWriteVector(final Collection c) {
		super(c);
	}

	@Override
	public List subList(final int fromIndex, final int toIndex) {
		return copyOnWriteArrayList.subList(fromIndex, toIndex);
	}

	@Override
	public int size() {
		return copyOnWriteArrayList.size();
	}

	@Override
	public boolean isEmpty() {
		return copyOnWriteArrayList.isEmpty();
	}

	@Override
	public boolean contains(final Object o) {
		return copyOnWriteArrayList.contains(o);
	}

	@Override
	public int indexOf(final Object o) {
		return copyOnWriteArrayList.indexOf(o);
	}

	@Override
	public int indexOf(final Object o, final int index) {
		return copyOnWriteArrayList.indexOf(o, index);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return copyOnWriteArrayList.lastIndexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o, final int index) {
		return copyOnWriteArrayList.lastIndexOf(o, index);
	}

	@Override
	public Object clone() {
		return copyOnWriteArrayList.clone();
	}

	@Override
	public Object[] toArray() {
		return copyOnWriteArrayList.toArray();
	}

	@Override
	public synchronized Object[] toArray(final Object[] a) {
		return copyOnWriteArrayList.toArray(a);
	}

	@Override
	public Object get(final int index) {
		return copyOnWriteArrayList.get(index);
	}

	@Override
	public Object set(final int index, final Object element) {
		return copyOnWriteArrayList.set(index, element);
	}

	@Override
	public boolean add(final Object o) {
		return copyOnWriteArrayList.add(o);
	}

	@Override
	public void add(final int index, final Object element) {
		copyOnWriteArrayList.add(index, element);
	}

	@Override
	public Object remove(final int index) {
		return copyOnWriteArrayList.remove(index);
	}

	@Override
	public boolean remove(final Object o) {
		return copyOnWriteArrayList.remove(o);
	}

	public boolean addIfAbsent(final Object o) {
		return copyOnWriteArrayList.addIfAbsent(o);
	}

	public boolean containsAll(final Collection c) {
		return copyOnWriteArrayList.containsAll(c);
	}

	public boolean removeAll(final Collection c) {
		return copyOnWriteArrayList.removeAll(c);
	}

	public boolean retainAll(final Collection c) {
		return copyOnWriteArrayList.retainAll(c);
	}

	public int addAllAbsent(final Collection c) {
		return copyOnWriteArrayList.addAllAbsent(c);
	}

	@Override
	public void clear() {
		copyOnWriteArrayList.clear();
	}

	@Override
	public boolean addAll(final Collection c) {
		return copyOnWriteArrayList.addAll(c);
	}

	@Override
	public boolean addAll(final int index, final Collection c) {
		return copyOnWriteArrayList.addAll(index, c);
	}

	@Override
	public String toString() {
		return copyOnWriteArrayList.toString();
	}

	@Override
	public boolean equals(final Object o) {
		return copyOnWriteArrayList.equals(o);
	}

	@Override
	public int hashCode() {
		return copyOnWriteArrayList.hashCode();
	}

	@Override
	public Iterator iterator() {
		return copyOnWriteArrayList.iterator();
	}

	@Override
	public ListIterator listIterator() {
		return copyOnWriteArrayList.listIterator();
	}

	@Override
	public ListIterator listIterator(final int index) {
		return copyOnWriteArrayList.listIterator(index);
	}
}
