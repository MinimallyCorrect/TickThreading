package me.nallar.tickthreading.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.ConcurrentIterableArrayList;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickthreading.minecraft.tickregion.EntityTickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TickRegion;
import me.nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion;
import me.nallar.tickthreading.util.TableFormatter;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.omg.CORBA.IntHolder;

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
	public final ArrayList<TileEntity> tileEntityList = new ArrayList<TileEntity>();
	public final ArrayList<Entity> entityList = new ArrayList<Entity>();
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
		threadManager = new ThreadManager(threads, "Entities in " + Log.name(world));
		this.world = world;
		this.regionSize = regionSize;
		shuffleCount = world.rand.nextInt(shuffleInterval);
	}

	public void setVariableTickRate(boolean variableTickRate) {
		this.variableTickRate = variableTickRate;
	}

	public TickRegion getTileEntityRegion(int hashCode) {
		return tileEntityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private TileEntityTickRegion getOrCreateRegion(TileEntity tileEntity) {
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

	public TickRegion getEntityRegion(int hashCode) {
		return entityCallables.get(hashCode);
	}

	@SuppressWarnings ("NumericCastThatLosesPrecision")
	private EntityTickRegion getOrCreateRegion(Entity entity) {
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

	public boolean add(TileEntity tileEntity) {
		return add(tileEntity, true);
	}

	public boolean add(TileEntity tileEntity, boolean newEntity) {
		TileEntityTickRegion tileEntityTickRegion = getOrCreateRegion(tileEntity);
		if (tileEntityTickRegion.add(tileEntity)) {
			tileEntity.tickRegion = tileEntityTickRegion;
			if (newEntity) {
				synchronized (tileEntityLock) {
					tileEntityList.add(tileEntity);
				}
			}
			return true;
		}
		return false;
	}

	public boolean add(Entity entity) {
		return add(entity, true);
	}

	public boolean add(Entity entity, boolean newEntity) {
		EntityTickRegion entityTickRegion = getOrCreateRegion(entity);
		if (entityTickRegion.add(entity)) {
			entity.tickRegion = entityTickRegion;
			if (newEntity) {
				synchronized (entityLock) {
					entityList.add(entity);
				}
				synchronized (entityClassToCountMap) {
					Class entityClass = entity.getClass();
					Integer count = entityClassToCountMap.get(entityClass);
					if (count == null) {
						count = 0;
					}
					entityClassToCountMap.put(entityClass, count + 1);
				}
			}
			return true;
		}
		return false;
	}

	public void batchRemoveEntities(Collection<Entity> entities) {
		ChunkProviderServer chunkProviderServer = (ChunkProviderServer) world.getChunkProvider();

		synchronized (entityLock) {
			entityList.removeAll(entities);
		}

		for (Entity entity : entities) {
			int x = entity.chunkCoordX;
			int z = entity.chunkCoordZ;

			if (entity.addedToChunk) {
				Chunk chunk = chunkProviderServer.getChunkIfExists(x, z);
				if (chunk != null) {
					chunk.removeEntity(entity);
				}
			}

			world.releaseEntitySkin(entity);

			if (entity.tickRegion != null) {
				entity.tickRegion.remove(entity);
				entity.tickRegion = null;
				Class entityClass = entity.getClass();
				synchronized (entityClassToCountMap) {
					Integer count = entityClassToCountMap.get(entityClass);
					if (count == null) {
						throw new IllegalStateException("Removed an entity which should not have been in the entityList");
					}
					entityClassToCountMap.put(entityClass, count - 1);
				}
			}
		}
	}

	public void batchRemoveTileEntities(Collection<TileEntity> tileEntities) {
		for (TileEntity tileEntity : tileEntities) {
			if (tileEntity.tickRegion != null) {
				tileEntity.tickRegion.remove(tileEntity);
				tileEntity.tickRegion = null;
				tileEntity.onChunkUnload();
			}
			if (lock) {
				unlock(tileEntity);
			}
		}
		synchronized (tileEntityLock) {
			tileEntityList.removeAll(tileEntities);
		}
	}

	public void remove(TileEntity tileEntity) {
		TileEntityTickRegion tileEntityTickRegion = tileEntity.tickRegion;
		if (tileEntityTickRegion == null) {
			tileEntityTickRegion = getOrCreateRegion(tileEntity);
		}
		tileEntityTickRegion.remove(tileEntity);
		removed(tileEntity);
	}

	public void remove(Entity entity) {
		EntityTickRegion entityTickRegion = entity.tickRegion;
		if (entityTickRegion == null) {
			entityTickRegion = getOrCreateRegion(entity);
		}
		entityTickRegion.remove(entity);
		removed(entity);
	}

	public void removed(TileEntity tileEntity) {
		tileEntity.tickRegion = null;
		synchronized (tileEntityLock) {
			tileEntityList.remove(tileEntity);
		}
		if (lock) {
			unlock(tileEntity);
		}
	}

	public void removed(Entity entity) {
		boolean removed;
		entity.tickRegion = null;
		synchronized (entityLock) {
			removed = entityList.remove(entity);
		}
		if (removed) {
			Class entityClass = entity.getClass();
			synchronized (entityClassToCountMap) {
				Integer count = entityClassToCountMap.get(entityClass);
				if (count == null) {
					throw new IllegalStateException("Removed an entity which should not have been in the entityList");
				}
				entityClassToCountMap.put(entityClass, count - 1);
			}
		}
	}

	public void unlock(TileEntity tileEntity) {
		int xPos = tileEntity.lastTTX;
		int yPos = tileEntity.lastTTY;
		int zPos = tileEntity.lastTTZ;
		if (tileEntity.xMinusLock != null) {
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos - 1, yPos, zPos);
			if (lockTileEntity != null) {
				lockTileEntity.xPlusLock = tileEntity.xMinusLock = null;
			}
		}
		if (tileEntity.xPlusLock != null) {
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos + 1, yPos, zPos);
			if (lockTileEntity != null) {
				lockTileEntity.xMinusLock = tileEntity.xPlusLock = null;
			}
		}
		if (tileEntity.zMinusLock != null) {
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos - 1);
			if (lockTileEntity != null) {
				lockTileEntity.zPlusLock = tileEntity.zMinusLock = null;
			}
		}
		if (tileEntity.zPlusLock != null) {
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos + 1);
			if (lockTileEntity != null) {
				lockTileEntity.zMinusLock = tileEntity.zPlusLock = null;
			}
		}
	}

	public final void lock(TileEntity tileEntity) {
		unlock(tileEntity);
		int maxPosition = (regionSize / 2) - 1;
		int xPos = tileEntity.xCoord;
		int yPos = tileEntity.yCoord;
		int zPos = tileEntity.zCoord;
		tileEntity.lastTTX = xPos;
		tileEntity.lastTTY = yPos;
		tileEntity.lastTTZ = zPos;
		int relativeXPos = (xPos % regionSize) / 2;
		int relativeZPos = (zPos % regionSize) / 2;
		boolean onMinusX = relativeXPos == 0;
		boolean onMinusZ = relativeZPos == 0;
		boolean onPlusX = relativeXPos == maxPosition;
		boolean onPlusZ = relativeZPos == maxPosition;
		if (onMinusX || onMinusZ || onPlusZ) { // minus X needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos - 1, yPos, zPos);
			if (lockTileEntity != null) {
				tileEntity.xMinusLock = lockTileEntity.thisLock;
				lockTileEntity.xPlusLock = tileEntity.thisLock;
			}
		}
		if (onPlusX || onMinusZ || onPlusZ) { // plus X needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos + 1, yPos, zPos);
			if (lockTileEntity != null) {
				tileEntity.xPlusLock = lockTileEntity.thisLock;
				lockTileEntity.xMinusLock = tileEntity.thisLock;
			}
		}
		if (onMinusZ || onMinusX || onPlusX) { // minus Z needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos - 1);
			if (lockTileEntity != null) {
				tileEntity.zMinusLock = lockTileEntity.thisLock;
				lockTileEntity.zPlusLock = tileEntity.thisLock;
			}
		}
		if (onPlusZ || onMinusX || onPlusX) { // plus Z needs locked
			TileEntity lockTileEntity = world.getTEWithoutLoad(xPos, yPos, zPos + 1);
			if (lockTileEntity != null) {
				tileEntity.zPlusLock = lockTileEntity.thisLock;
				lockTileEntity.zMinusLock = tileEntity.thisLock;
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

	public void fixDiscrepancies(TableFormatter tableFormatter) {
		final IntHolder intHolder = new IntHolder();

		{
			Set<Entity> contained = new HashSet<Entity>();
			Set<Entity> toRemove = new HashSet<Entity>();
			synchronized (entityList) {
				for (Entity e : entityList) {
					if (add(e, false)) {
						intHolder.value++;
					}
					if (!contained.add(e)) {
						toRemove.add(e);
						intHolder.value++;
					}
				}
				entityList.removeAll(toRemove);
			}
		}

		{
			Set<TileEntity> contained = new HashSet<TileEntity>();
			Set<TileEntity> toRemove = new HashSet<TileEntity>();
			synchronized (tileEntityList) {
				for (TileEntity te : tileEntityList) {
					if (add(te, false)) {
						intHolder.value++;
					}
					if (!contained.add(te)) {
						toRemove.add(te);
						intHolder.value++;
					}
				}
				tileEntityList.removeAll(toRemove);
			}
		}

		if (intHolder.value != 0) {
			tableFormatter.sb.append("Fixed ").append(intHolder.value).append(" discrepancies in tile/entity lists.");
		}
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
			stats.append('\n').append(world.getChunkProvider().makeString());
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
