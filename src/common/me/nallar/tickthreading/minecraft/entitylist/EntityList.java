package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import me.nallar.collections.ConcurrentUnsafeIterableArrayList;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.world.World;

/*
* Used to override World.loadedTile/EntityList.
* */
public abstract class EntityList<T> extends ArrayList<T> {
	private static boolean warnedIterateTransform = false;
	public final TickManager manager;
	public final ConcurrentUnsafeIterableArrayList<T> innerList;

	EntityList(World world, Field overriddenField, TickManager manager, ConcurrentUnsafeIterableArrayList<T> innerList) {
		this.manager = manager;
		this.innerList = innerList;
		overriddenField.setAccessible(true);
		try {
			ArrayList worldList = (ArrayList) overriddenField.get(world);
			if (worldList.getClass() != ArrayList.class) {
				Log.severe("Another mod has replaced an entity list with " + Log.toString(worldList));
			}
		} catch (Throwable t) {
			Log.severe("Failed to get " + overriddenField.getName() + " in world " + world.getName());
		}
		try {
			//This should hopefully avoid leaving the world in a bad state if something goes wrong.
			this.addAll((Collection<? extends T>) overriddenField.get(world));
			overriddenField.set(world, this);
		} catch (Exception e) {
			throw new RuntimeException("Failed to override " + overriddenField.getName() + " in world " + world.getName(), e);
		}
	}

	@Override
	public abstract boolean add(T t);

	@Override
	public abstract boolean remove(Object o);

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean changed = false;
		for (T t : c) {
			changed |= add(t);
		}
		return changed;
	}

	@Override
	public boolean removeAll(Collection c) {
		boolean changed = false;
		for (Object t : c) {
			changed |= remove(t);
		}
		return changed;
	}

	@Override
	public Iterator<T> iterator() {
		if (!Thread.holdsLock(this)) {
			if (!warnedIterateTransform) {
				Log.warning("Unsafe entity list iteration", new Throwable());
				warnedIterateTransform = true;
			}
			return innerList.unsafeIterator();
		}
		return innerList.iterator();
	}

	@Override
	public ListIterator<T> listIterator() {
		if (!Thread.holdsLock(this)) {
			throw new IllegalStateException("Must synchronize to iterate over EntityList.");
		}
		return innerList.listIterator();
	}

	@Override
	public ListIterator<T> listIterator(int index) {
		if (!Thread.holdsLock(this)) {
			throw new IllegalStateException("Must synchronize to iterate over EntityList.");
		}
		return innerList.listIterator(index);
	}

	@Override
	public boolean contains(Object o) {
		return innerList.contains(o);
	}

	@Override
	public void trimToSize() {
		innerList.trimToSize();
	}

	@Override
	public void ensureCapacity(final int minCapacity) {
		innerList.ensureCapacity(minCapacity);
	}

	@Override
	public boolean isEmpty() {
		return innerList.isEmpty();
	}

	@Override
	public int indexOf(final Object o) {
		return innerList.indexOf(o);
	}

	@Override
	public int lastIndexOf(final Object o) {
		return innerList.lastIndexOf(o);
	}

	@SuppressWarnings ("CloneDoesntCallSuperClone")
	@Override
	public Object clone() {
		return innerList.clone();
	}

	@Override
	public Object[] toArray() {
		return innerList.toArray();
	}

	@Override
	public <T1> T1[] toArray(final T1[] a) {
		return innerList.toArray(a);
	}

	@Override
	public T set(final int index, final T element) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void add(final int index, final T element) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public T remove(final int index) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void clear() {
		innerList.clear();
	}

	@Override
	public boolean addAll(final int index, final Collection<? extends T> c) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected void removeRange(final int fromIndex, final int toIndex) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public List<T> subList(final int fromIndex, final int toIndex) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int size() {
		return innerList.size();
	}

	@Override
	public T get(int index) {
		return innerList.get(index);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return innerList.containsAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return innerList.retainAll(c);
	}

	@Override
	public String toString() {
		return innerList.toString();
	}
}
