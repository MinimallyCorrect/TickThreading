package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;

import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.Entity;
import net.minecraft.src.World;

public class LoadedEntityList<T> extends overrideList<T> {
	public LoadedEntityList(World world, Field overridenField, ThreadManager manager) {
		super(world, overridenField, manager);
	}

	public boolean add(T t) {
		manager.add((Entity) t);
		return true;
	}

	public boolean remove(Object o) {
		manager.remove((Entity) o);
		return true;
	}
}
