package nallar.collections;

import nallar.unsafe.UnsafeUtil;

import java.util.*;

public class ConcurrentLinkedQueueList<T> extends LinkedList<T> {
	private final ConcurrentQueueList<T> internalList = new ConcurrentQueueList<T>();

	@Override
	public T getFirst() {
		return internalList.get(0);
	}

	@Override
	public T getLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public T removeFirst() {
		T t = internalList.poll();
		if (t == null) {
			throw new NoSuchElementException();
		}
		return t;
	}

	@Override
	public T removeLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addFirst(T t) {
		internalList.add(t);
	}

	@Override
	public void addLast(T t) {
		internalList.add(t);
	}

	@Override
	public boolean contains(Object o) {
		return internalList.contains(o);
	}

	@Override
	public int size() {
		return internalList.size();
	}

	@Override
	public boolean add(T t) {
		return internalList.add(t);
	}

	@Override
	public boolean remove(Object o) {
		return internalList.remove(o);
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		return internalList.addAll(c);
	}

	@Override
	public boolean addAll(int index, Collection<? extends T> c) {
		return internalList.addAll(index, c);
	}

	@Override
	public void clear() {
		internalList.clear();
	}

	@Override
	public T get(int index) {
		return internalList.get(index);
	}

	@Override
	public T set(int index, T element) {
		return internalList.set(index, element);
	}

	@Override
	public void add(int index, T element) {
		internalList.add(index, element);
	}

	@Override
	public T remove(int index) {
		return internalList.remove(index);
	}

	@Override
	public int indexOf(Object o) {
		return internalList.indexOf(o);
	}

	@Override
	public int lastIndexOf(Object o) {
		return internalList.lastIndexOf(o);
	}

	@Override
	public T peek() {
		return internalList.peek();
	}

	@Override
	public T element() {
		return internalList.element();
	}

	@Override
	public T poll() {
		return internalList.poll();
	}

	@Override
	public T remove() {
		return internalList.remove();
	}

	@Override
	public boolean offer(T t) {
		return internalList.offer(t);
	}

	@Override
	public boolean offerFirst(T t) {
		addFirst(t);
		return true;
	}

	@Override
	public boolean offerLast(T t) {
		addLast(t);
		return true;
	}

	@Override
	public T peekFirst() {
		return internalList.peek();
	}

	@Override
	public T peekLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public T pollFirst() {
		return internalList.poll();
	}

	@Override
	public T pollLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void push(T t) {
		internalList.add(t);
	}

	@Override
	public T pop() {
		return removeFirst();
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		return internalList.remove(o);
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		return internalList.remove(o);
	}

	@Override
	public Iterator<T> iterator() {
		return internalList.iterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		return internalList.listIterator(index);
	}

	@Override
	public Iterator<T> descendingIterator() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Object clone() {
		throw UnsafeUtil.throwIgnoreChecked(new CloneNotSupportedException());
	}

	@Override
	public Object[] toArray() {
		return internalList.toArray();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		return internalList.toArray(a);
	}
}
