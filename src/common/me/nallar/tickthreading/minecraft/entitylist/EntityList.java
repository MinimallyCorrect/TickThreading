package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.World;

/*
* Used to override World.loadedTileEntityList.
* */
public abstract class EntityList<T> extends ArrayList<T> {
	final ThreadManager manager;

	EntityList(World world, Field overridenField, ThreadManager manager) {
		this.manager = manager;
		overridenField.setAccessible(true);
		try {
			//This should hopefully avoid leaving the world in a bad state if something goes wrong.
			this.addAll((Collection) overridenField.get(world));
			overridenField.set(world, this);
		} catch (Exception e) {
			throw new RuntimeException("Failed to override " + overridenField.getName() + " in world " + world.getWorldInfo().getWorldName(), e);
		}
	}

	@Override
	public boolean contains(Object o) {
		return false;
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public abstract boolean add(T t);

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean changed = false;
		for (T t : c) {
			changed |= add(t);
		}
		return changed;
	}

	@Override
	public abstract boolean remove(Object o);

	public void unload() {
		manager.unload();
	}
}
