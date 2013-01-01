package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.Collection;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class LoadedEntityList<T> extends EntityList<T> {
	public LoadedEntityList(World world, Field overriddenField, TickManager manager) {
		super(world, overriddenField, manager);
	}

	public boolean tickAccess = false;

	@Override
	public boolean contains(Object o) {
		return manager.entityList.contains(o);
	}

	@Override
	public int size() {
		int res = tickAccess ? 0 : manager.entityList.size();
		tickAccess = false;
		return res;
	}

	@Override
	public T get(int index) {
		return (T) manager.entityList.get(index);
	}

	@Override
	public boolean add(T t) {
		manager.add((Entity) t);
		return true;
	}

	@Override
	public boolean remove(Object o) {
		manager.remove((Entity) o);
		return true;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return manager.entityList.containsAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return manager.entityList.retainAll(c);
	}

	@Override
	public String toString() {
		return manager.entityList.toString();
	}

	@Override
	public boolean removeAll(Collection c) {
		tickAccess = true;
		return super.removeAll(c);
	}
}
