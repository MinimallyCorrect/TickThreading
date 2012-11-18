package me.nallar.tickthreading.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ListIterator;

/**
 * Semi-concurrent ArrayList.
 * Ignores comodification exceptions, some synchronisation (except not yet. Need to test)
 * TODO: Determine whether this should be changed to internally use CopyOnWriteArrayList
 */
@SuppressWarnings ("UnusedDeclaration")
public class CArrayList<E> extends ArrayList<E> {
	private static final long serialVersionUID = 8683452581122892189L;

	public CArrayList(int initialCapacity) {
		super(initialCapacity);
	}

	public CArrayList() {
		super();
	}

	public CArrayList(Collection<? extends E> c) {
		super(c);
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		//TODO: Fix this? :P
		return new ArrayList<E>(this).listIterator(index);
	}
}
