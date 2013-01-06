package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.tickregion.EntityTickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class TickManager {
	public final int regionSize;
	public boolean variableTickRate;
	public float averageTickLength = 0;
	public int lastTickLength = 0;
	public long lastStartTime = 0;
	public final World world;
	private final Map<Integer, TileEntityTickRegion> tileEntityCallables = new HashMap<Integer, TileEntityTickRegion>();
	private final Map<Integer, EntityTickRegion> entityCallables = new HashMap<Integer, EntityTickRegion>();
	private final ArrayList<TickRegion> tickRegions = new ArrayList<TickRegion>();
	private final ThreadManager threadManager;
	public final List<Entity> entityList = new ArrayList<Entity>();
	private final Map<Class<?>, Integer> entityClassToCountMap = new HashMap<Class<?>, Integer>();

	public TickManager(World world, int regionSize, int threads) {
		threadManager = new ThreadManager(threads == 0 ? (Runtime.getRuntime().availableProcessors() * 3) / 2 : threads, "Tick Thread for " + Log.name(world));
		this.world = world;
		this.regionSize = regionSize;
	}

	public void setVariableTickRate(boolean variableTickRate) {
		this.variableTickRate = variableTickRate;
	}

	public TickRegion getTileEntityCallable(int hashCode) {
		return tileEntityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickRegion getOrCreateCallable(TileEntity tileEntity) {
		int hashCode = getHashCode(tileEntity);
		synchronized (tickRegions) {
			TileEntityTickRegion callable = tileEntityCallables.get(hashCode);
			if (callable == null) {
				callable = new TileEntityTickRegion(world, this, tileEntity.xCoord / regionSize, tileEntity.zCoord / regionSize);
				tileEntityCallables.put(hashCode, callable);
				tickRegions.add(callable);
			}
			return callable;
		}
	}

	public TickRegion getEntityCallable(int hashCode) {
		return entityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickRegion getOrCreateCallable(Entity entity) {
		int regionX = (int) entity.posX / regionSize;
		int regionZ = (int) entity.posZ / regionSize;
		int hashCode = getHashCodeFromRegionCoords(regionX, regionZ);
		synchronized (tickRegions) {
			EntityTickRegion callable = entityCallables.get(hashCode);
			if (callable == null) {
				callable = new EntityTickRegion(world, this, regionX, regionZ);
				entityCallables.put(hashCode, callable);
				tickRegions.add(callable);
			}
			return callable;
		}
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

	private void processChanges() {
		try {
			synchronized (tickRegions) {
				Iterator<TickRegion> iterator = tickRegions.iterator();
				while (iterator.hasNext()) {
					TickRegion tickRegion = iterator.next();
					tickRegion.processChanges();
					if (tickRegion.isEmpty()) {
						iterator.remove();
						if (tickRegion instanceof EntityTickRegion) {
							entityCallables.remove(tickRegion.hashCode);
						} else {
							tileEntityCallables.remove(tickRegion.hashCode);
						}
						tickRegion.die();
					}
				}
			}
		} catch (Exception e) {
			Log.severe("Exception occurred while processing entity changes: ", e);
		}
	}

	public synchronized void add(TileEntity tileEntity) {
		getOrCreateCallable(tileEntity).add(tileEntity);
	}

	public synchronized void add(Entity entity) {
		getOrCreateCallable(entity).add(entity);
		synchronized (entityList) {
			if (!entityList.contains(entity)) {
				entityList.add(entity);
				Class entityClass = entity.getClass();
				Integer count = entityClassToCountMap.get(entityClass);
				if (count == null) {
					count = 0;
				}
				entityClassToCountMap.put(entityClass, count + 1);
			}
		}
	}

	public synchronized void remove(TileEntity tileEntity) {
		getOrCreateCallable(tileEntity).remove(tileEntity);
	}

	public synchronized void remove(Entity entity) {
		getOrCreateCallable(entity).remove(entity);
		removed(entity);
	}

	public void removed(Entity entity) {
		synchronized (entityList) {
			entityList.remove(entity);
			Class entityClass = entity.getClass();
			Integer count = entityClassToCountMap.get(entityClass);
			if (count == null) {
				count = 0;
			}
			entityClassToCountMap.put(entityClass, count - 1);
		}
	}

	public void doTick() {
		lastStartTime = DeadLockDetector.tick("TickManager.doTick: " + Log.name(world));
		threadManager.waitForCompletion();
		threadManager.runList(tickRegions);
		threadManager.waitForCompletion();
		threadManager.run(new Runnable() {
			@Override
			public void run() {
				processChanges();
			}
		});
		lastTickLength = (int) (System.currentTimeMillis() - lastStartTime);
		averageTickLength = ((averageTickLength * 127) + lastTickLength) / 128;
	}

	public void unload() {
		threadManager.stop();
		for (TickRegion tickRegion : tickRegions) {
			tickRegion.die();
		}
		tickRegions.clear();
		entityList.clear();
		entityClassToCountMap.clear();
	}

	public float getTickTime() {
		float maxTickTime = 0;
		for (TickRegion tickRegion : tickRegions) {
			float averageTickTime = tickRegion.getAverageTickTime();
			if (averageTickTime > maxTickTime) {
				maxTickTime = averageTickTime;
			}
		}
		return (maxTickTime > 55 && variableTickRate) ? 55 : maxTickTime;
	}

	public String getBasicStats() {
		return Log.name(world) + ": " + Math.min(1000 / averageTickLength, 20) + "tps, " + entityList.size() + " entities, load: " + (this.getTickTime() * 2) + "%\n";
	}

	public String getDetailedStats() {
		StringBuilder stats = new StringBuilder();
		stats.append("World: ").append(Log.name(world)).append('\n');
		stats.append("---- Slowest tick regions ----").append('\n');
		// TODO: Rewrite this
		float averageAverageTickTime = 0;
		float maxTickTime = 0;
		SortedMap<Float, TickRegion> sortedTickCallables = new TreeMap<Float, TickRegion>();
		for (TickRegion tickRegion : tickRegions) {
			float averageTickTime = tickRegion.getAverageTickTime();
			averageAverageTickTime += averageTickTime;
			sortedTickCallables.put(averageTickTime, tickRegion);
			if (averageTickTime > maxTickTime) {
				maxTickTime = averageTickTime;
			}
		}
		Collection<TickRegion> var = sortedTickCallables.values();
		TickRegion[] sortedTickCallablesArray = var.toArray(new TickRegion[var.size()]);
		for (int i = sortedTickCallablesArray.length - 1; i >= sortedTickCallablesArray.length - 6; i--) {
			if (i >= 0 && sortedTickCallablesArray[i].getAverageTickTime() > 3) {
				stats.append(sortedTickCallablesArray[i].getStats()).append('\n');
			}
		}
		averageAverageTickTime /= tickRegions.size();
		stats.append("---- World stats ----");
		stats.append("\nAverage tick time: ").append(averageAverageTickTime).append("ms");
		stats.append("\nMax tick time: ").append(maxTickTime).append("ms");
		stats.append("\nEffective tick time: ").append(lastTickLength).append("ms");
		stats.append("\nAverage effective tick time: ").append(averageTickLength).append("ms");
		return stats.toString();
	}

	public int getEntityCount(Class<?> clazz) {
		int count = 0;
		for (Map.Entry<Class<?>, Integer> entry : entityClassToCountMap.entrySet()) {
			if (clazz.isAssignableFrom(entry.getKey())) {
				count += entry.getValue();
			}
		}
		return count;
	}
}
