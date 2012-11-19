package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import me.nallar.tickthreading.minecraft.tickthread.EntityTickThread;
import me.nallar.tickthreading.minecraft.tickthread.TileEntityTickThread;
import net.minecraft.src.Entity;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class ThreadManager {
	public final int regionSize;
	private CyclicBarrier tileEntityTickNotifyLatch = new CyclicBarrier(1);
	private CyclicBarrier tileEntityTickEndLatch = new CyclicBarrier(1);
	private CyclicBarrier entityTickNotifyLatch = new CyclicBarrier(1);
	private CyclicBarrier entityTickEndLatch = new CyclicBarrier(1);
	private final List<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final List<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toAddEntities = new ArrayList<Entity>();
	private final Map<Integer, TileEntityTickThread> tileEntityThreads = new HashMap<Integer, TileEntityTickThread>();
	private final Map<Integer, EntityTickThread> entityThreads = new HashMap<Integer, EntityTickThread>();
	private final World world;

	public ThreadManager(World world, int regionSize) {
		this.world = world;
		this.regionSize = regionSize;
	}

	// TODO: Don't use thread.stop() in unload(), use thread.interrupt() and make this return false.
	@SuppressWarnings ("SameReturnValue")
	public boolean waitForTileEntityTick() throws BrokenBarrierException {
		try {
			if (tileEntityTickNotifyLatch.await() == 0) {
				tileEntityTickNotifyLatch.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void endTileEntityTick() throws BrokenBarrierException {
		try {
			if (tileEntityTickEndLatch.await() == 0) {
				tileEntityTickEndLatch.reset();
				processChanges();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// TODO: Don't use thread.stop() in unload(), use thread.interrupt() and make this return false.
	@SuppressWarnings ("SameReturnValue")
	public boolean waitForEntityTick() throws BrokenBarrierException {
		try {
			if (entityTickNotifyLatch.await() == 0) {
				entityTickNotifyLatch.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void endEntityTick() throws BrokenBarrierException {
		try {
			if (entityTickEndLatch.await() == 0) {
				entityTickEndLatch.reset();
				processChanges();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickThread getThreadForTileEntity(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		TileEntityTickThread thread = tileEntityThreads.get(hashCode);
		if (thread == null) {
			thread = new TileEntityTickThread(world, "region: x" + tileEntity.xCoord / regionSize + ",z" + tileEntity.zCoord / regionSize, this, hashCode);
			tileEntityThreads.put(hashCode, thread);
		}
		return thread;
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickThread getThreadForEntity(Entity entity) {
		int hashCode = getHashCode(entity);
		EntityTickThread thread = entityThreads.get(hashCode);
		if (thread == null) {
			thread = new EntityTickThread(world, "region: x" + (int) entity.posX / regionSize + ",z" + (int) entity.posZ / regionSize, this, hashCode);
			entityThreads.put(hashCode, thread);
		}
		return thread;
	}

	public int getHashCode(TileEntity tileEntity) {
		return tileEntity.xCoord / regionSize + (tileEntity.zCoord / regionSize << 16);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	public int getHashCode(Entity entity) {
		return ((int) entity.posX) / regionSize + (((int) entity.posY) / regionSize << 16);
	}

	private synchronized void processChanges() {
		for (TileEntity tileEntity : toAddTileEntities) {
			getThreadForTileEntity(tileEntity).add(tileEntity);
		}
		for (TileEntity tileEntity : toRemoveTileEntities) {
			getThreadForTileEntity(tileEntity).remove(tileEntity);
		}
		for (Entity entity : toAddEntities) {
			getThreadForEntity(entity).add(entity);
		}
		for (Entity entity : toRemoveEntities) {
			getThreadForEntity(entity).remove(entity);
		}
	}

	public synchronized void add(TileEntity tileEntity) {
		toAddTileEntities.add(tileEntity);
	}

	public synchronized void add(Entity entity) {
		toAddEntities.add(entity);
	}

	public synchronized void remove(TileEntity tileEntity) {
		toRemoveTileEntities.add(tileEntity);
	}

	public synchronized void remove(Entity entity) {
		toRemoveEntities.add(entity);
	}

	public void unload() {
		for (Thread t : getThreads()) {
			t.stop();
		}
	}

	private Collection<Thread> getThreads() {
		Collection<Thread> collection = new ArrayList<Thread>();
		collection.addAll(tileEntityThreads.values());
		collection.addAll(entityThreads.values());
		return collection;
	}
}
