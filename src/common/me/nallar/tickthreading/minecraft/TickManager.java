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
	private final ArrayList<TileEntity> toRemoveTileEntities = new ArrayList<TileEntity>();
	private final ArrayList<Entity> toRemoveEntities = new ArrayList<Entity>();
	private final ArrayList<TileEntity> toAddTileEntities = new ArrayList<TileEntity>();
	private final ArrayList<Entity> toAddEntities = new ArrayList<Entity>();
	private final Map<Integer, TileEntityTickRegion> tileEntityCallables = new HashMap<Integer, TileEntityTickRegion>();
	private final Map<Integer, EntityTickRegion> entityCallables = new HashMap<Integer, EntityTickRegion>();
	private final ArrayList<TickRegion> tickRegions = new ArrayList<TickRegion>();
	private final ThreadManager threadManager;
	public final World world;
	public final List<Entity> entityList = new ArrayList<Entity>();

	public TickManager(World world, int regionSize, int threads) {
		threadManager = new ThreadManager(threads == 0 ? Runtime.getRuntime().availableProcessors() : threads, "Tick Thread for " + Log.name(world));
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
		TileEntityTickRegion callable = tileEntityCallables.get(hashCode);
		if (callable == null) {
			callable = new TileEntityTickRegion(world, this, tileEntity.xCoord / regionSize, tileEntity.zCoord / regionSize);
			tileEntityCallables.put(hashCode, callable);
			tickRegions.add(callable);
		}
		return callable;
	}

	public TickRegion getEntityCallable(int hashCode) {
		return entityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickRegion getOrCreateCallable(Entity entity) {
		int regionX = (int) entity.posX / regionSize;
		int regionZ = (int) entity.posZ / regionSize;
		int hashCode = getHashCodeFromRegionCoords(regionX, regionZ);
		EntityTickRegion callable = entityCallables.get(hashCode);
		if (callable == null) {
			callable = new EntityTickRegion(world, this, regionX, regionZ);
			entityCallables.put(hashCode, callable);
			tickRegions.add(callable);
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

	private void processChanges() {
		try {
			synchronized (this) {
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
			}
			Iterator<TickRegion> iterator = tickRegions.iterator();
			while (iterator.hasNext()) {
				TickRegion tickRegion = iterator.next();
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
		} catch (Exception e) {
			Log.severe("Exception occurred while processing entity changes: ", e);
		}
	}

	public synchronized void add(TileEntity tileEntity) {
		toAddTileEntities.add(tileEntity);
		toRemoveTileEntities.remove(tileEntity);
	}

	public synchronized void add(Entity entity) {
		toAddEntities.add(entity);
		synchronized (entityList) {
			if (!entityList.contains(entity)) {
				entityList.add(entity);
			}
		}
		toRemoveEntities.remove(entity);
	}

	public synchronized void remove(TileEntity tileEntity) {
		toRemoveTileEntities.add(tileEntity);
		toAddTileEntities.remove(tileEntity);
	}

	public synchronized void remove(Entity entity) {
		toRemoveEntities.add(entity);
		toAddEntities.remove(entity);
		removed(entity);
	}

	public void removed(Entity entity) {
		synchronized (entityList) {
			entityList.remove(entity);
			world.releaseEntitySkin(entity);
		}
	}

	public void doTick() {
		threadManager.waitForCompletion();
		threadManager.runList(tickRegions);
		threadManager.runBackground(new Runnable() {
			@Override
			public void run() {
				processChanges();
			}
		});
	}

	public void unload() {
		threadManager.stop();
		for (TickRegion tickRegion : tickRegions) {
			tickRegion.die();
		}
		tickRegions.clear();
		entityList.clear();
	}

	public float getTickTime() {
		float maxTickTime = 0;
		for (TickRegion tickRegion : tickRegions) {
			float averageTickTime = tickRegion.getAverageTickTime();
			if (averageTickTime > maxTickTime) {
				maxTickTime = averageTickTime;
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

	public String getBasicStats() {
		return Log.name(world) + ": " + (1000 / this.getEffectiveTickTime()) + "tps, " + entityList.size() + " entities, load: " + (this.getTickTime() / 2) + "%\n";
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
		stats.append("---- World stats ----").append('\n');
		stats.append("Average tick time: ").append(averageAverageTickTime).append('\n');
		stats.append("Max tick time: ").append(maxTickTime).append('\n');
		stats.append("Effective tick time: ").append((maxTickTime > 55) ? 55 : maxTickTime).append('\n');
		return stats.toString();
	}
}
