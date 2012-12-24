package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class LoadedEntityList<T> extends EntityList<T> {
	public LoadedEntityList(World world, Field overriddenField, TickManager manager) {
		super(world, overriddenField, manager);
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
