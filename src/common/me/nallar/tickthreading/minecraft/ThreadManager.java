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
	public final int tileEntityRegionSize;
	public final int entityRegionSize;
	private int lastNumberOfEntityThreads = 0;
	private int lastNumberOfTileEntityThreads = 0;
	private volatile CyclicBarrier tileEntityTickNotifyBarrier = new CyclicBarrier(1);
	private volatile CyclicBarrier tileEntityTickEndBarrier = new CyclicBarrier(1);
	private volatile CyclicBarrier entityTickNotifyBarrier = new CyclicBarrier(1);
	private volatile CyclicBarrier entityTickEndBarrier = new CyclicBarrier(1);
	private volatile CyclicBarrier processChangesBarrier = new CyclicBarrier(1);
	private final List<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final List<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toAddEntities = new ArrayList<Entity>();
	private final Map<Integer, TileEntityTickThread> tileEntityThreads = new HashMap<Integer, TileEntityTickThread>();
	private final Map<Integer, EntityTickThread> entityThreads = new HashMap<Integer, EntityTickThread>();
	private final World world;

	public ThreadManager(World world, int tileEntityRegionSize, int entityRegionSize) {
		this.world = world;
		this.tileEntityRegionSize = tileEntityRegionSize;
		this.entityRegionSize = entityRegionSize;
	}

	// TODO: Don't use thread.stop() in unload(), use thread.interrupt() and make this return false.
	@SuppressWarnings ("SameReturnValue")
	public boolean waitForTileEntityTick() throws BrokenBarrierException {
		try {
			if (tileEntityTickNotifyBarrier.await() == 0) {
				tileEntityTickNotifyBarrier.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void endTileEntityTick() throws BrokenBarrierException {
		try {
			if (tileEntityTickEndBarrier.await() == 0) {
				tileEntityTickEndBarrier.reset();
				processChanges();
			}else{
				processChangesBarrier.await();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// TODO: Don't use thread.stop() in unload(), use thread.interrupt() and make this return false.
	@SuppressWarnings ("SameReturnValue")
	public boolean waitForEntityTick() throws BrokenBarrierException {
		try {
			if (entityTickNotifyBarrier.await() == 0) {
				entityTickNotifyBarrier.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}

	public void endEntityTick() throws BrokenBarrierException {
		try {
			if (entityTickEndBarrier.await() == 0) {
				entityTickEndBarrier.reset();
			}
			processChangesBarrier.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickThread getThreadForTileEntity(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		TileEntityTickThread thread = tileEntityThreads.get(hashCode);
		if (thread == null) {
			thread = new TileEntityTickThread(world, "region: x" + tileEntity.xCoord / tileEntityRegionSize + ",z" + tileEntity.zCoord / tileEntityRegionSize, this, hashCode);
			tileEntityThreads.put(hashCode, thread);
		}
		return thread;
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickThread getThreadForEntity(Entity entity) {
		int hashCode = getHashCode(entity);
		EntityTickThread thread = entityThreads.get(hashCode);
		if (thread == null) {
			thread = new EntityTickThread(world, "region: x" + (int) entity.posX / tileEntityRegionSize + ",z" + (int) entity.posZ / tileEntityRegionSize, this, hashCode);
			entityThreads.put(hashCode, thread);
		}
		return thread;
	}

	public int getHashCode(TileEntity tileEntity) {
		return tileEntity.xCoord / tileEntityRegionSize + (tileEntity.zCoord / tileEntityRegionSize << 16);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	public int getHashCode(Entity entity) {
		return ((int) entity.posX) / entityRegionSize + (((int) entity.posY) / entityRegionSize << 16);
	}

	private synchronized void processChanges() throws BrokenBarrierException, InterruptedException {
		List<Thread> toStartThreads = new ArrayList<Thread>();
		for (TileEntity tileEntity : toAddTileEntities) {
			TileEntityTickThread tileEntityTickThread = getThreadForTileEntity(tileEntity);
			tileEntityTickThread.add(tileEntity);
			if(!tileEntityTickThread.isAlive() && !toStartThreads.contains(tileEntityTickThread)){
				toStartThreads.add(tileEntityTickThread);
			}
		}
		for (TileEntity tileEntity : toRemoveTileEntities) {
			getThreadForTileEntity(tileEntity).remove(tileEntity);
		}
		for (Entity entity : toAddEntities) {
			EntityTickThread entityTickThread = getThreadForEntity(entity);
			entityTickThread.add(entity);
			if(!entityTickThread.isAlive() && !toStartThreads.contains(entityTickThread)){
				toStartThreads.add(entityTickThread);
			}
		}
		for (Entity entity : toRemoveEntities) {
			getThreadForEntity(entity).remove(entity);
		}
		boolean entityThreadsChange = entityThreads.size() != lastNumberOfEntityThreads;
		boolean tileEntityThreadsChange = tileEntityThreads.size() != lastNumberOfTileEntityThreads;
		if(tileEntityThreadsChange || entityThreadsChange){
			if(entityThreadsChange){
				entityTickEndBarrier = new CyclicBarrier(entityThreads.size());
				entityTickNotifyBarrier = new CyclicBarrier(entityThreads.size());
				lastNumberOfEntityThreads = entityThreads.size();
			}
			if(tileEntityThreadsChange){
				tileEntityTickEndBarrier = new CyclicBarrier(tileEntityThreads.size() + 1);
				tileEntityTickNotifyBarrier = new CyclicBarrier(tileEntityThreads.size() + 1);
				lastNumberOfTileEntityThreads = tileEntityThreads.size();
			}
			CyclicBarrier oldProcessChangesBarrier = processChangesBarrier;
			processChangesBarrier = new CyclicBarrier(tileEntityThreads.size() + entityThreads.size() + 1);
			oldProcessChangesBarrier.await();
		} else {
			processChangesBarrier.await();
		}

		for(Thread toStart : toStartThreads){
			toStart.start();
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
		for (Thread thread : getThreads()) {
			thread.stop();
		}
	}

	private Collection<Thread> getThreads() {
		Collection<Thread> collection = new ArrayList<Thread>();
		collection.addAll(tileEntityThreads.values());
		collection.addAll(entityThreads.values());
		return collection;
	}
}
