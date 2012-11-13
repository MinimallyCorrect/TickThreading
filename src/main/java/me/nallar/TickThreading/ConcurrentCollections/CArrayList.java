package me.nallar.tickthreading.concurrentcollections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Semi-concurrent arraylist.
 * Ignores comodification exceptions, some synchronisation (except not yet. Need to test)
 * TODO: Determine whether this should be changed to internally use CopyOnWriteArrayList
 */
public class CArrayList<E> extends ArrayList<E> {
	private static final long serialVersionUID = 8683452581122892189L;

	public CArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	public CArrayList() {
		super();
	}

	public CArrayList(Collection c) {
		super(c);
	}

	@Override
	public ListIterator listIterator(int index) {
		//TODO: Fix this? :P
		return new ArrayList(this).listIterator(index);
	}
}
