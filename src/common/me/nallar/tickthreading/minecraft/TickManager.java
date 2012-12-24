package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.tickcallables.EntityTickCallable;
import me.nallar.tickthreading.minecraft.tickcallables.TickCallable;
import me.nallar.tickthreading.minecraft.tickcallables.TileEntityTickCallable;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class TickManager {
	public final int regionSize;
	public boolean variableTickRate;
	private final Object tickLock = new Object();
	private final List<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final List<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final List<Entity> toAddEntities = new ArrayList<Entity>();
	private final Map<Integer, TileEntityTickCallable> tileEntityCallables = new HashMap<Integer, TileEntityTickCallable>();
	private final Map<Integer, EntityTickCallable> entityCallables = new HashMap<Integer, EntityTickCallable>();
	private final List<TickCallable<Object>> tickCallables = new ArrayList<TickCallable<Object>>();
	private final ExecutorService tickExecutor = Executors.newCachedThreadPool();
	public final World world;

	public TickManager(World world, int regionSize) {
		this.world = world;
		this.regionSize = regionSize;
	}

	public void setVariableTickRate(boolean variableTickRate) {
		this.variableTickRate = variableTickRate;
	}

	public TickCallable getTileEntityCallable(int hashCode) {
		return tileEntityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickCallable getOrCreateCallable(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		TileEntityTickCallable callable = tileEntityCallables.get(hashCode);
		if (callable == null) {
			callable = new TileEntityTickCallable<Object>(world, this, tileEntity.xCoord / regionSize, tileEntity.zCoord / regionSize);
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
		int regionX = (int) entity.posX / regionSize;
		int regionZ = (int) entity.posZ / regionSize;
		int hashCode = getHashCodeFromRegionCoords(regionX, regionZ);
		EntityTickCallable callable = entityCallables.get(hashCode);
		if (callable == null) {
			callable = new EntityTickCallable<Object>(world, this, regionX, regionZ);
			entityCallables.put(hashCode, callable);
			tickCallables.add(callable);
		}
		return callable;
	}

	public int getHashCode(TileEntity tileEntity) {
		return getHashCode(tileEntity.xCoord, tileEntity.zCoord);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	public int getHashCode(Entity entity) {
		return getHashCode((int) entity.posX, (int) entity.posZ);
	}

	int getHashCode(int x, int z) {
		return getHashCodeFromRegionCoords(x / regionSize, z / regionSize);
	}

	public static int getHashCodeFromRegionCoords(int x, int z) {
		return x + (z << 16);
	}

	private synchronized void processChanges() {
		try {
			synchronized (tickLock) {
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
					TickCallable<Object> tickCallable = iterator.next();
					if (tickCallable.isEmpty()) {
						iterator.remove();
						if (tickCallable instanceof EntityTickCallable) {
							entityCallables.remove(tickCallable.hashCode);
						} else {
							tileEntityCallables.remove(tickCallable.hashCode);
						}
						tickCallable.die();
					} else {
						tickCallable.processChanges();
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
		synchronized (tickLock) {
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

	public float getTickTime() {
		float maxTickTime = 0;
		synchronized (tickLock) {
			for (TickCallable<Object> tickCallable : tickCallables) {
				float averageTickTime = tickCallable.getAverageTickTime();
				if (averageTickTime > maxTickTime) {
					maxTickTime = averageTickTime;
				}
			}
		}
		return (maxTickTime > 55) ? 55 : maxTickTime;
	}

	public float getEffectiveTickTime() {
		float tickTime = getTickTime();
		if (tickTime < 50) {
			tickTime = 50;
		} else if (tickTime > 55 && variableTickRate) {
			tickTime = 55;
		}
		return tickTime;
	}

	public String getDetailedStats() {
		StringBuilder stats = new StringBuilder();
		stats.append("World: ").append(Log.name(world)).append("\n");
		stats.append("---- Slowest tick regions ----").append("\n");
		float averageAverageTickTime = 0;
		float maxTickTime = 0;
		SortedMap<Float, TickCallable<Object>> sortedTickCallables = new TreeMap<Float, TickCallable<Object>>();
		synchronized (tickLock) {
			for (TickCallable<Object> tickCallable : tickCallables) {
				float averageTickTime = tickCallable.getAverageTickTime();
				averageAverageTickTime += averageTickTime;
				sortedTickCallables.put(averageTickTime, tickCallable);
				if (averageTickTime > maxTickTime) {
					maxTickTime = averageTickTime;
				}
			}
		}
		Collection<TickCallable<Object>> var = sortedTickCallables.values();
		TickCallable[] sortedTickCallablesArray = var.toArray(new TickCallable[var.size()]);
		for (int i = sortedTickCallablesArray.length - 1; i >= sortedTickCallablesArray.length - 6; i--) {
			if (sortedTickCallablesArray[i].getAverageTickTime() > 3) {
				stats.append(sortedTickCallablesArray[i].getStats()).append("\n");
			}
		}
		averageAverageTickTime /= tickCallables.size();
		stats.append("---- World stats ----").append("\n");
		stats.append("Average tick time: ").append(averageAverageTickTime).append("\n");
		stats.append("Max tick time: ").append(maxTickTime).append("\n");
		stats.append("Effective tick time: ").append((maxTickTime > 55) ? 55 : maxTickTime).append("\n");
		return stats.toString();
	}
}
