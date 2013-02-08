package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.ConcurrentIterableArrayList;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickthreading.minecraft.tickregion.EntityTickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion;
import me.nallar.tickthreading.util.TableFormatter;
import me.nallar.tickthreading.util.concurrent.SimpleMutex;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;

public final class TickManager {
	public final int regionSize;
	public boolean variableTickRate;
	public boolean profilingEnabled = false;
	public double averageTickLength = 0;
	public long lastTickLength = 0;
	public long lastStartTime = 0;
	private static final int shuffleInterval = 800;
	private int shuffleCount;
	private final boolean waitForCompletion;
	public final EntityTickProfiler entityTickProfiler = new EntityTickProfiler();
	public final World world;
	public final List<TileEntity> tileEntityList = new ArrayList<TileEntity>();
	public final List<Entity> entityList = new ArrayList<Entity>();
	public Object tileEntityLock = new Object();
	public Object entityLock = new Object();
	private final Map<Integer, TileEntityTickRegion> tileEntityCallables = new HashMap<Integer, TileEntityTickRegion>();
	private final Map<Integer, EntityTickRegion> entityCallables = new HashMap<Integer, EntityTickRegion>();
	private final ConcurrentIterableArrayList<TickRegion> tickRegions = new ConcurrentIterableArrayList<TickRegion>();
	private final ThreadManager threadManager;
	private final Map<Class<?>, Integer> entityClassToCountMap = new HashMap<Class<?>, Integer>();
	private final boolean lock = TickThreading.instance.lockRegionBorders;

	public TickManager(World world, int regionSize, int threads, boolean waitForCompletion) {
		this.waitForCompletion = waitForCompletion;
		threadManager = new ThreadManager(threads, "Tile/Entity Tick for " + Log.name(world));
		this.world = world;
		this.regionSize = regionSize;
		shuffleCount = world.rand.nextInt(shuffleInterval);
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
			synchronized (tickRegions) {
				callable = tileEntityCallables.get(hashCode);
				if (callable == null) {
					callable = new TileEntityTickRegion(world, this, tileEntity.xCoord / regionSize, tileEntity.zCoord / regionSize);
					tileEntityCallables.put(hashCode, callable);
					tickRegions.add(callable);
				}
			}
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
			synchronized (tickRegions) {
				callable = entityCallables.get(hashCode);
				if (callable == null) {
					callable = new EntityTickRegion(world, this, regionX, regionZ);
					entityCallables.put(hashCode, callable);
					tickRegions.add(callable);
				}
			}
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

	public int getHashCode(int x, int z) {
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
				if (shuffleCount++ % shuffleInterval == 0) {
					Collections.shuffle(tickRegions);
				}
			}
		} catch (Exception e) {
			Log.severe("Exception occurred while processing entity changes: ", e);
		}
	}

	public void add(TileEntity tileEntity) {
		getOrCreateCallable(tileEntity).add(tileEntity);
		synchronized (tileEntityLock) {
			if (!tileEntityList.contains(tileEntity)) {
				tileEntityList.add(tileEntity);
			}
		}
	}

	public void add(Entity entity) {
		getOrCreateCallable(entity).add(entity);
		boolean added;
		synchronized (entityLock) {
			if (added = !entityList.contains(entity)) {
				entityList.add(entity);
			}
		}
		if (added) {
			synchronized (entityClassToCountMap) {
				Class entityClass = entity.getClass();
				Integer count = entityClassToCountMap.get(entityClass);
				if (count == null) {
					count = 0;
				}
				entityClassToCountMap.put(entityClass, count + 1);
			}
		}
	}

	public void batchRemove(Collection<TileEntity> tileEntities) {
		for (TileEntity tileEntity : tileEntities) {
			getOrCreateCallable(tileEntity).remove(tileEntity);
			if (lock) {
				unlock(tileEntity);
			}
			tileEntity.onChunkUnload();
		}
		synchronized (tileEntityLock) {
			tileEntityList.removeAll(tileEntities);
		}
	}

	public void remove(TileEntity tileEntity) {
		getOrCreateCallable(tileEntity).remove(tileEntity);
		removed(tileEntity);
	}

	public void remove(Entity entity) {
		getOrCreateCallable(entity).remove(entity);
		removed(entity);
	}

	public void removed(TileEntity tileEntity) {
		synchronized (tileEntityLock) {
			tileEntityList.remove(tileEntity);
		}
		if (lock) {
			unlock(tileEntity);
		}
	}

	public void unlock(TileEntity tileEntity) {
		ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
		int xPos = tileEntity.lastTTX;
		int yPos = tileEntity.lastTTY;
		int zPos = tileEntity.lastTTZ;
		int maxPosition = (regionSize / 2) - 1;
		int relativeXPos = (xPos % regionSize) / 2;
		int relativeZPos = (zPos % regionSize) / 2;
		// Locking orders - lock most +ve first
		// If this order is not followed, we may deadlock.
		if (relativeXPos == 0) { // minus X needs locked
			synchronized (tileEntity) { // Lock this (+ve) first
				TileEntity lockTileEntity = world.getTEWithoutLoad(xPos - 1, yPos, zPos);
				if (lockTileEntity != null) {
					synchronized (lockTileEntity) {
						lockTileEntity.xPlusLock = tileEntity.xMinusLock = null;
					}
				}
			}
		} else if (relativeXPos == maxPosition) { // plus X needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos + 1, yPos, zPos);
			if (lockTileEntity != null) {
				synchronized (lockTileEntity) { // Lock other (+ve) first
					synchronized (tileEntity) {
						lockTileEntity.xMinusLock = tileEntity.xPlusLock = null;
					}
				}
			}
		}
		if (relativeZPos == 0) { // minus Z needs locked
			synchronized (tileEntity) { // Lock this (+ve) first
				TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos - 1);
				if (lockTileEntity != null) {
					synchronized (lockTileEntity) {
						lockTileEntity.zPlusLock = tileEntity.zMinusLock = null;
					}
				}
			}
		} else if (relativeZPos == maxPosition) { // plus Z needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos + 1);
			if (lockTileEntity != null) {
				synchronized (lockTileEntity) { // Lock other (+ve) first
					synchronized (tileEntity) {
						lockTileEntity.zMinusLock = tileEntity.zPlusLock = null;
					}
				}
			}
		}
	}

	public final void lock(TileEntity tileEntity) {
		int maxPosition = (regionSize / 2) - 1;
		int xPos = tileEntity.xCoord;
		int yPos = tileEntity.yCoord;
		int zPos = tileEntity.zCoord;
		if (tileEntity.xPlusLock != null || tileEntity.xMinusLock != null || tileEntity.zPlusLock != null || tileEntity.zMinusLock != null) {
			unlock(tileEntity);
		}
		tileEntity.lastTTX = xPos;
		tileEntity.lastTTY = yPos;
		tileEntity.lastTTZ = zPos;
		int relativeXPos = (xPos % regionSize) / 2;
		int relativeZPos = (zPos % regionSize) / 2;
		// Locking orders - lock most +ve first
		// If this order is not followed, we may deadlock.
		if (relativeXPos == 0) { // minus X needs locked
			synchronized (tileEntity) { // Lock this (+ve) first
				TileEntity lockTileEntity = world.getTEWithoutLoad(xPos - 1, yPos, zPos);
				if (lockTileEntity != null) {
					synchronized (lockTileEntity) {
						if (tileEntity.xMinusLock == null) {
							lockTileEntity.xPlusLock = tileEntity.xMinusLock = new SimpleMutex();
						}
					}
				}
			}
		} else if (relativeXPos == maxPosition) { // plus X needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos + 1, yPos, zPos);
			if (lockTileEntity != null) {
				synchronized (lockTileEntity) { // Lock other (+ve) first
					synchronized (tileEntity) {
						if (tileEntity.xPlusLock == null) {
							lockTileEntity.xMinusLock = tileEntity.xPlusLock = new SimpleMutex();
						}
					}
				}
			}
		}
		if (relativeZPos == 0) { // minus Z needs locked
			synchronized (tileEntity) { // Lock this (+ve) first
				TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos - 1);
				if (lockTileEntity != null) {
					synchronized (lockTileEntity) {
						if (tileEntity.zMinusLock == null) {
							lockTileEntity.zPlusLock = tileEntity.zMinusLock = new SimpleMutex();
						}
					}
				}
			}
		} else if (relativeZPos == maxPosition) { // plus Z needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos + 1);
			if (lockTileEntity != null) {
				synchronized (lockTileEntity) { // Lock other (+ve) first
					synchronized (tileEntity) {
						if (tileEntity.zPlusLock == null) {
							lockTileEntity.zMinusLock = tileEntity.zPlusLock = new SimpleMutex();
						}
					}
				}
			}
		}
	}

	public void removed(Entity entity) {
		boolean removed;
		synchronized (entityLock) {
			removed = entityList.remove(entity);
		}
		if (removed) {
			synchronized (entityClassToCountMap) {
				Class entityClass = entity.getClass();
				Integer count = entityClassToCountMap.get(entityClass);
				if (count == null) {
					throw new IllegalStateException("Removed an entity which should not have been in the entityList");
				}
				entityClassToCountMap.put(entityClass, count - 1);
			}
		}
	}

	public void doTick() {
		boolean previousProfiling = world.theProfiler.profilingEnabled;
		lastStartTime = DeadLockDetector.tick("TickManager.doTick: " + Log.name(world));
		threadManager.waitForCompletion();
		if (previousProfiling) {
			world.theProfiler.profilingEnabled = false;
		}
		threadManager.runList(tickRegions);
		if (previousProfiling || waitForCompletion) {
			lastTickLength = (threadManager.waitForCompletion() - lastStartTime);
			averageTickLength = ((averageTickLength * 127) + lastTickLength) / 128;
			threadManager.run(new Runnable() {
				@Override
				public void run() {
					processChanges();
				}
			});
		}
		if (previousProfiling) {
			world.theProfiler.profilingEnabled = true;
		}
		if (profilingEnabled) {
			entityTickProfiler.tick();
		}
	}

	public void tickEnd() {
		if (!waitForCompletion) {
			lastTickLength = (threadManager.waitForCompletion() - lastStartTime);
			averageTickLength = ((averageTickLength * 127) + lastTickLength) / 128;
			threadManager.run(new Runnable() {
				@Override
				public void run() {
					processChanges();
				}
			});
		}
	}

	public void unload() {
		threadManager.stop();
		synchronized (tickRegions) {
			for (TickRegion tickRegion : tickRegions) {
				tickRegion.die();
			}
		}
		tickRegions.clear();
		entityList.clear();
		entityClassToCountMap.clear();
		UnsafeUtil.clean(this);
	}

	public void writeStats(TableFormatter tf) {
		long timeTotal = 0;
		double time = Double.NaN;
		try {
			long[] tickTimes = MinecraftServer.getServer().worldTickTimes.get(world.provider.dimensionId);
			for (long tick : tickTimes) {
				timeTotal += tick;
			}
			time = (timeTotal) / (double) tickTimes.length;
			if (time == 0) {
				time = 0.1;
			}
		} catch (NullPointerException ignored) {
		}
		tf
				.row(Log.name(world))
				.row(Math.min(1000000000 / time, MinecraftServer.getTargetTPS()))
				.row(String.valueOf(entityList.size()))
				.row(String.valueOf(tileEntityList.size()))
				.row(world instanceof WorldServer ? String.valueOf(((WorldServer) world).theChunkProviderServer.getLoadedChunkCount()) : "0")
				.row(TableFormatter.formatDoubleWithPrecision((time * 100f) / MinecraftServer.getTargetTickTime(), 3) + '%');
	}

	public TableFormatter writeDetailedStats(TableFormatter tf) {
		@SuppressWarnings ("MismatchedQueryAndUpdateOfStringBuilder")
		StringBuilder stats = tf.sb;
		stats.append("World: ").append(Log.name(world)).append('\n');
		stats.append("---- Slowest tick regions ----").append('\n');
		// TODO: Rewrite this
		float averageAverageTickTime = 0;
		float maxTickTime = 0;
		SortedMap<Float, TickRegion> sortedTickCallables = new TreeMap<Float, TickRegion>();
		synchronized (tickRegions) {
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
			tf
					.heading("")
					.heading("X")
					.heading("Z")
					.heading("N")
					.heading("Time");
			for (int i = sortedTickCallablesArray.length - 1; i >= sortedTickCallablesArray.length - 7; i--) {
				if (i >= 0 && sortedTickCallablesArray[i].getAverageTickTime() > 0.2) {
					sortedTickCallablesArray[i].writeStats(tf);
				}
			}
			tf.finishTable();
			stats.append('\n');
			averageAverageTickTime /= tickRegions.size();
			stats.append("---- World stats ----");
			stats.append("\nAverage tick time: ").append(averageAverageTickTime).append("ms");
			stats.append("\nMax tick time: ").append(maxTickTime).append("ms");
			stats.append("\nEffective tick time: ").append(lastTickLength / 1000000f).append("ms");
			stats.append("\nAverage effective tick time: ").append((float) averageTickLength / 1000000).append("ms");
		}
		return tf;
	}

	public TableFormatter writeEntityStats(TableFormatter tf) {
		tf
				.heading("Main")
				.heading("Map")
				.heading("Region")
				.heading("Player");
		tf
				.row(entityList.size())
				.row(getTotalEntityCountFromMap())
				.row(getTotalEntityCountFromRegions())
				.row(getEntityCount(EntityPlayer.class));
		tf.finishTable();
		return tf;
	}

	private int getTotalEntityCountFromRegions() {
		int count = 0;
		for (EntityTickRegion entityTickRegion : entityCallables.values()) {
			count += entityTickRegion.size();
		}
		return count;
	}

	private int getTotalEntityCountFromMap() {
		int count = 0;
		for (Map.Entry<Class<?>, Integer> entry : entityClassToCountMap.entrySet()) {
			count += entry.getValue();
		}
		return count;
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
