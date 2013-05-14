package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class LoadedTileEntityList<T> extends EntityList<T> {
	public LoadedTileEntityList(World world, Field overriddenField, TickManager manager) {
		super(world, overriddenField, manager, manager.tileEntityList);
		manager.tileEntityLock = this;
	}

	@Override
	public boolean add(T t) {
		manager.add((TileEntity) t, true);
		return true;
	}

	@Override
	public boolean remove(Object o) {
		manager.remove((TileEntity) o);
		return true;
	}

	@Override
	public T remove(int index) {
		TileEntity removed = (TileEntity) get(index);
		manager.remove(removed);
		return (T) removed;
	}
}
