package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class LoadedTileEntityList<T> extends EntityList<T> {
	private final Iterable<T> emptyList = Collections.emptyList();

	public LoadedTileEntityList(World world, Field overriddenField, TickManager manager) {
		super(world, overriddenField, manager);
	}

	@Override
	public Iterator<T> iterator() {
		manager.doTick();
		return emptyList.iterator();
	}

	public boolean add(T t) {
		manager.add((TileEntity) t);
		return true;
	}

	public boolean remove(Object o) {
		manager.remove((TileEntity) o);
		return true;
	}
}
