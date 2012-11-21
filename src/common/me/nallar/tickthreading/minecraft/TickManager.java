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

public class TickManager {
	public final int tileEntityRegionSize;
	public final int entityRegionSize;
	private final Object processChangesLock = new Object();
	private final List<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final List<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toAddEntities = new ArrayList<Entity>();
	private final Map<Integer, TileEntityTickCallable> tileEntityCallables = new HashMap<Integer, TileEntityTickCallable>();
	private final Map<Integer, EntityTickCallable> entityCallables = new HashMap<Integer, EntityTickCallable>();
	private final List<TickCallable<Object>> tickCallables = new ArrayList<TickCallable<Object>>();
	private final ExecutorService tickExecutor = Executors.newCachedThreadPool();
	private final World world;

	public TickManager(World world, int tileEntityRegionSize, int entityRegionSize) {
		this.world = world;
		this.tileEntityRegionSize = tileEntityRegionSize;
		this.entityRegionSize = entityRegionSize;
	}

	public TickCallable getTileEntityCallable(int hashCode) {
		return tileEntityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickCallable getOrCreateCallable(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		TileEntityTickCallable callable = tileEntityCallables.get(hashCode);
		if (callable == null) {
			callable = new TileEntityTickCallable<Object>(world, "region: x" + tileEntity.xCoord / tileEntityRegionSize + ",z" + tileEntity.zCoord / tileEntityRegionSize, this, tileEntity.xCoord / tileEntityRegionSize, tileEntity.yCoord / tileEntityRegionSize);
			tileEntityCallables.put(hashCode, callable);
			tickCallables.add(callable);
		}
		return callable;
	}

	public TickCallable getEntityCallable(int hashCode) {
		return entityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickCallable getOrCreateCallable(Entity entity) {
		int regionX = (int) entity.posX / entityRegionSize;
		int regionY = (int) entity.posY / entityRegionSize;
		int hashCode = getHashCodeFromRegionCoords(regionX, regionY);
		EntityTickCallable callable = entityCallables.get(hashCode);
		if (callable == null) {
			callable = new EntityTickCallable<Object>(world, "region: x" + regionX + ",z" + regionY, this, regionX, regionY);
			entityCallables.put(hashCode, callable);
			tickCallables.add(callable);
		}
		return callable;
	}

	public int getHashCode(TileEntity tileEntity) {
		return getHashCode(tileEntity.xCoord, tileEntity.yCoord);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	public int getHashCode(Entity entity) {
		return getHashCode((int) entity.posX, (int) entity.posY);
	}

	public int getHashCode(int x, int y) {
		return getHashCodeFromRegionCoords(x / tileEntityRegionSize, y / tileEntityRegionSize);
	}

	public static int getHashCodeFromRegionCoords(int x, int y) {
		return x + (y << 16);
	}

	private synchronized void processChanges() {
		try {
			synchronized (processChangesLock) {
				for (TileEntity tileEntity : toRemoveTileEntities) {
					getOrCreateCallable(tileEntity).remove(tileEntity);
				}
				for (TileEntity tileEntity : toAddTileEntities) {
					getOrCreateCallable(tileEntity).add(tileEntity);
				}
				for (Entity entity : toRemoveEntities) {
					getOrCreateCallable(entity).remove(entity);
				}
				for (Entity entity : toAddEntities) {
					getOrCreateCallable(entity).add(entity);
				}
				toAddEntities.clear();
				toAddTileEntities.clear();
				toRemoveEntities.clear();
				toRemoveTileEntities.clear();
				Iterator<TickCallable<Object>> iterator = tickCallables.iterator();
				while (iterator.hasNext()) {
					TickCallable tickCallable = iterator.next();
					if (tickCallable.isEmpty()) {
						iterator.remove();
						if (tickCallable instanceof EntityTickCallable) {
							entityCallables.remove(tickCallable.hashCode);
						} else {
							tileEntityCallables.remove(tickCallable.hashCode);
						}
						tickCallable.die();
					}
				}
			}
		} catch (Exception e) {
			Log.severe("Exception occurred while processing entity changes: ", e);
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
		synchronized (processChangesLock) {
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
