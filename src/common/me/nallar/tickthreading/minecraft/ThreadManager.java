package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.tickcallables.EntityTickCallable;
import me.nallar.tickthreading.minecraft.tickcallables.TickCallable;
import me.nallar.tickthreading.minecraft.tickcallables.TileEntityTickCallable;
import net.minecraft.src.Entity;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class ThreadManager {
	public final int tileEntityRegionSize;
	public final int entityRegionSize;

	private final Object changeProcessLock = new Object();

	private final List<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final List<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toAddEntities = new ArrayList<Entity>();

	private final Map<Integer, TileEntityTickCallable> tileEntityRunnables = new HashMap<Integer, TileEntityTickCallable>();
	private final Map<Integer, EntityTickCallable> entityRunnables = new HashMap<Integer, EntityTickCallable>();
	private final List<TickCallable<Object>> tickCallables = new ArrayList<TickCallable<Object>>();

	private final ExecutorService tickExecutor = Executors.newCachedThreadPool();

	private final World world;

	public ThreadManager(World world, int tileEntityRegionSize, int entityRegionSize) {
		this.world = world;
		this.tileEntityRegionSize = tileEntityRegionSize;
		this.entityRegionSize = entityRegionSize;
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickCallable getRunnableForTileEntity(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		TileEntityTickCallable runnable = tileEntityRunnables.get(hashCode);
		if (runnable == null) {
			runnable = new TileEntityTickCallable<Object>(world, "region: x" + tileEntity.xCoord / tileEntityRegionSize + ",z" + tileEntity.zCoord / tileEntityRegionSize, this, hashCode);
			tileEntityRunnables.put(hashCode, runnable);
			tickCallables.add(runnable);
		}
		return runnable;
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickCallable getRunnableForEntity(Entity entity) {
		int hashCode = getHashCode(entity);
		EntityTickCallable runnable = entityRunnables.get(hashCode);
		if (runnable == null) {
			runnable = new EntityTickCallable<Object>(world, "region: x" + (int) entity.posX / tileEntityRegionSize + ",z" + (int) entity.posZ / tileEntityRegionSize, this, hashCode);
			entityRunnables.put(hashCode, runnable);
			tickCallables.add(runnable);
		}
		return runnable;
	}

	public int getHashCode(TileEntity tileEntity) {
		return tileEntity.xCoord / tileEntityRegionSize + (tileEntity.zCoord / tileEntityRegionSize << 16);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	public int getHashCode(Entity entity) {
		return ((int) entity.posX) / entityRegionSize + (((int) entity.posY) / entityRegionSize << 16);
	}

	private synchronized void processChanges() {
		try{
			synchronized (changeProcessLock) {
				for (TileEntity tileEntity : toAddTileEntities) {
					getRunnableForTileEntity(tileEntity).add(tileEntity);
				}
				for (TileEntity tileEntity : toRemoveTileEntities) {
					getRunnableForTileEntity(tileEntity).remove(tileEntity);
				}
				for (Entity entity : toAddEntities) {
					getRunnableForEntity(entity).add(entity);
				}
				for (Entity entity : toRemoveEntities) {
					getRunnableForEntity(entity).remove(entity);
				}
				Iterator<TickCallable<Object>> iterator = tickCallables.iterator();
				while(iterator.hasNext()){
					TickCallable tickCallable = iterator.next();
					if(tickCallable.isEmpty()){
						iterator.remove();
						if(tickCallable instanceof EntityTickCallable){
							entityRunnables.remove(tickCallable.hashCode);
						} else {
							tileEntityRunnables.remove(tickCallable.hashCode);
						}
					}
				}
			}
		} catch(Exception e) {
			Log.severe("Exception occured while processing entity changes: ", e);
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

	public void doTick() {
		synchronized (changeProcessLock) {
			try {
				tickExecutor.invokeAll(tickCallables);
			} catch (InterruptedException e) {
				Log.warning("Interrupted while ticking: ", e);
			}
		}
		tickExecutor.submit(new Runnable() {
			@Override
			public void run() {
				processChanges();
			}
		});
	}

	public void unload() {
		tickExecutor.shutdown();
	}
}
