package me.nallar.tickthreading.minecraft.entitylist;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.BrokenBarrierException;

import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class LoadedTileEntityList<T> extends EntityList<T> {
	private final Iterable<T> emptyList = Collections.emptyList();

	public LoadedTileEntityList(World world, Field overridenField, ThreadManager manager) {
		super(world, overridenField, manager);
	}

	@Override
	public Iterator<T> iterator() {
		try {
			manager.waitForTileEntityTick();
			manager.endTileEntityTick();
		} catch (BrokenBarrierException e) {
			e.printStackTrace();
		}
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
