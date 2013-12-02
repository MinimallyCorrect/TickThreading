package nallar.tickthreading.minecraft;

import nallar.collections.ConcurrentIterableArrayList;
import nallar.collections.ConcurrentUnsafeIterableArrayList;
import nallar.collections.ContainedRemoveSet;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.commands.TPSCommand;
import nallar.tickthreading.minecraft.tickregion.EntityTickRegion;
import nallar.tickthreading.minecraft.tickregion.TickRegion;
import nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.TableFormatter;
import nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;

public final class TickManager {
	private static final byte lockXPlus = 1 << 1;
	private static final byte lockXMinus = 1 << 2;
	private static final byte lockZPlus = 1 << 3;
	private static final byte lockZMinus = 1 << 4;
	public static final int regionSize = TickThreading.instance.regionSize;
	public static final int regionSizePower = 31 - Integer.numberOfLeadingZeros(regionSize);
	public boolean profilingEnabled = false;
	private double averageTickLength = 0;
	private long lastTickLength = 0;
	private long lastStartTime = 0;
	private static final int shuffleInterval = 3600;
	private int shuffleCount;
	private final boolean waitForCompletion;
	private final WorldServer world;
	public final ConcurrentUnsafeIterableArrayList<TileEntity> tileEntityList = new ConcurrentUnsafeIterableArrayList<TileEntity>();
	public final ConcurrentUnsafeIterableArrayList<Entity> entityList = new ConcurrentUnsafeIterableArrayList<Entity>();
	public Object tileEntityLock = new Object();
	public Object entityLock = new Object();
	private final Map<Integer, TileEntityTickRegion> tileEntityCallables = new HashMap<Integer, TileEntityTickRegion>();
	private final Map<Integer, EntityTickRegion> entityCallables = new HashMap<Integer, EntityTickRegion>();
	private final ConcurrentIterableArrayList<TickRegion> tickRegions = new ConcurrentIterableArrayList<TickRegion>();
	private final ThreadManager threadManager;
	private final Map<Class<?>, Integer> entityClassToCountMap = new ConcurrentHashMap<Class<?>, Integer>();
	private final ConcurrentLinkedQueue<TickRegion> removalQueue = new ConcurrentLinkedQueue<TickRegion>();

	public TickManager(WorldServer world, int threads, boolean waitForCompletion) {
		this.waitForCompletion = waitForCompletion;
		threadManager = new ThreadManager(threads, "Entities in " + Log.name(world));
		this.world = world;
		shuffleCount = world.rand.nextInt(shuffleInterval);
	}

	public TileEntityTickRegion getTileEntityRegion(int hashCode) {
		return tileEntityCallables.get(hashCode);
	}

	@SuppressWarnings("NumericCastThatLosesPrecision")
	private TileEntityTickRegion getOrCreateRegion(TileEntity tileEntity) {
		int regionX = tileEntity.xCoord >> regionSizePower;
		int regionZ = tileEntity.zCoord >> regionSizePower;
		int hashCode = getHashCodeFromRegionCoords(regionX, regionZ);
		TileEntityTickRegion callable = tileEntityCallables.get(hashCode);
		if (callable == null) {
			synchronized (tickRegions) {
				callable = tileEntityCallables.get(hashCode);
				if (callable == null) {
					callable = new TileEntityTickRegion(world, this, regionX, regionZ);
					tileEntityCallables.put(hashCode, callable);
					tickRegions.add(callable);
				}
			}
		}
		return callable;
	}

	public EntityTickRegion getEntityRegion(int hashCode) {
		return entityCallables.get(hashCode);
	}

	@SuppressWarnings("NumericCastThatLosesPrecision")
	private EntityTickRegion getOrCreateRegion(Entity entity) {
		int regionX = (entity.chunkCoordX << 4) >> regionSizePower;
		int regionZ = (entity.chunkCoordZ << 4) >> regionSizePower;
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

	public static int getHashCode(TileEntity tileEntity) {
		return getHashCode(tileEntity.xCoord, tileEntity.zCoord);
	}

	public static int getHashCode(Entity entity) {
		return getHashCode(entity.chunkCoordX << 4, entity.chunkCoordZ << 4);
	}

	public static int getHashCode(int x, int z) {
		return getHashCodeFromRegionCoords(x >> regionSizePower, z >> regionSizePower);
	}

	public static int getHashCodeFromRegionCoords(int x, int z) {
		return x + (z << 16);
	}

	public void queueForRemoval(TickRegion tickRegion) {
		removalQueue.add(tickRegion);
	}

	private void processChanges() {
		TickRegion tickRegion;
		while ((tickRegion = removalQueue.poll()) != null) {
			if (tickRegion.isEmpty()) {
				synchronized (tickRegions) {
					if ((tickRegion instanceof EntityTickRegion ? entityCallables.remove(tickRegion.hashCode) : tileEntityCallables.remove(tickRegion.hashCode)) == tickRegion) {
						tickRegions.remove(tickRegion);
					}
				}
			}
		}
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

	public void batchRemoveEntities(HashSet<Entity> entities) {
		entities = safeCopyClear(entities);
		if (entities == null) {
			return;
		}
		synchronized (entityLock) {
			entityList.removeAll(entities);
		}

		ChunkProviderServer chunkProviderServer = world.theChunkProviderServer;
		for (Entity entity : entities) {
			if (entity == null) {
				continue;
			}
			int x = entity.chunkCoordX;
			int z = entity.chunkCoordZ;

			if (entity.addedToChunk) {
				Chunk chunk = chunkProviderServer.getChunkIfExists(x, z);
				if (chunk != null) {
					chunk.removeEntity(entity);
				}
			}

			world.onEntityRemoved(entity);

			EntityTickRegion tickRegion = entity.tickRegion;
			if (tickRegion != null) {
				tickRegion.remove(entity);
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

	public void batchRemoveTileEntities(HashSet<TileEntity> tileEntities) {
		tileEntities = safeCopyClear(tileEntities);
		if (tileEntities == null) {
			return;
		}
		for (TileEntity tileEntity : tileEntities) {
			TileEntityTickRegion tickRegion = tileEntity.tickRegion;
			if (tickRegion != null) {
				tickRegion.remove(tileEntity);
				tileEntity.tickRegion = null;
				tileEntity.onChunkUnload();
			}
			unlock(tileEntity);
		}
		synchronized (tileEntityLock) {
			tileEntityList.removeAll(tileEntities);
		}
	}

	private static <T> HashSet<T> safeCopyClear(HashSet<T> c) {
		synchronized (c) {
			if (c.isEmpty()) {
				return null;
			}
			HashSet<T> copy = new HashSet<T>(c);
			c.clear();
			return copy;
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
		unlock(tileEntity);
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

	/* Oh, Java.
	Explicit MONITORENTER/EXIT should really be part of the language, there are just some cases like this where synchronized blocks get too messy,
	and performance is bad if using other locks.
	.lock/unlock calls here are replaced with MONITORENTER/EXIT.
	 */
	void unlock(TileEntity tE) {
		Lock lock = tE.lockManagementLock;
		lock.lock();
		byte locks = tE.usedLocks;
		boolean xM = ((locks & lockXMinus) != 0) || tE.xMinusLock != null;
		boolean xP = ((locks & lockXPlus) != 0) || tE.xPlusLock != null;
		boolean zM = ((locks & lockZMinus) != 0) || tE.zMinusLock != null;
		boolean zP = ((locks & lockZPlus) != 0) || tE.zPlusLock != null;
		tE.xPlusLock = null;
		tE.xMinusLock = null;
		tE.zPlusLock = null;
		tE.zMinusLock = null;
		tE.usedLocks = 0;
		if (tE.thisLock == null) {
			lock.unlock();
			return;
		}
		int xPos = tE.lastTTX;
		int yPos = tE.lastTTY;
		int zPos = tE.lastTTZ;
		TileEntity xMTE = xM ? world.getTEWithoutLoad(xPos - 1, yPos, zPos) : null;
		TileEntity xPTE = xP ? world.getTEWithoutLoad(xPos + 1, yPos, zPos) : null;
		TileEntity zMTE = zM ? world.getTEWithoutLoad(xPos, yPos, zPos - 1) : null;
		TileEntity zPTE = zP ? world.getTEWithoutLoad(xPos, yPos, zPos + 1) : null;
		if (xMTE == null) {
			xM = false;
		}
		if (xPTE == null) {
			xP = false;
		}
		if (zMTE == null) {
			zM = false;
		}
		if (zPTE == null) {
			zP = false;
		}
		lock.unlock();
		if (!(xM || xP || zM || zP)) {
			return;
		}
		if (xP) {
			xPTE.lockManagementLock.lock();
		}
		if (zP) {
			zPTE.lockManagementLock.lock();
		}
		lock.lock();
		if (zM) {
			zMTE.lockManagementLock.lock();
		}
		if (xM) {
			xMTE.lockManagementLock.lock();
		}

		if (xM) {
			xMTE.usedLocks &= ~lockXPlus;
			xMTE.xPlusLock = null;
		}
		if (xP) {
			xPTE.usedLocks &= ~lockXMinus;
			xPTE.xMinusLock = null;
		}
		if (zM) {
			zMTE.usedLocks &= ~lockZPlus;
			zMTE.zPlusLock = null;
		}
		if (zP) {
			zPTE.usedLocks &= ~lockZMinus;
			zPTE.zMinusLock = null;
		}
		if (xM) {
			xMTE.lockManagementLock.unlock();
		}
		if (zM) {
			zMTE.lockManagementLock.unlock();
		}
		lock.unlock();
		if (zP) {
			zPTE.lockManagementLock.unlock();
		}
		if (xP) {
			xPTE.lockManagementLock.unlock();
		}
	}

	/*
	 .lock/unlock calls here are replaced with MONITORENTER/EXIT.
	*/
	public final void lock(TileEntity tE) {
		unlock(tE);
		Lock lock = tE.lockManagementLock;
		lock.lock();
		int maxPosition = (regionSize / 2) - 1;
		int xPos = tE.xCoord;
		int yPos = tE.yCoord;
		int zPos = tE.zCoord;
		tE.lastTTX = xPos;
		tE.lastTTY = yPos;
		tE.lastTTZ = zPos;
		int relativeXPos = (xPos % regionSize) / 2;
		int relativeZPos = (zPos % regionSize) / 2;
		boolean onMinusX = relativeXPos == 0;
		boolean onMinusZ = relativeZPos == 0;
		boolean onPlusX = relativeXPos == maxPosition;
		boolean onPlusZ = relativeZPos == maxPosition;
		boolean xM = onMinusX || onMinusZ || onPlusZ;
		boolean xP = onPlusX || onMinusZ || onPlusZ;
		boolean zM = onMinusZ || onMinusX || onPlusX;
		boolean zP = onPlusZ || onMinusX || onPlusX;
		TileEntity xMTE = xM ? world.getTEWithoutLoad(xPos - 1, yPos, zPos) : null;
		TileEntity xPTE = xP ? world.getTEWithoutLoad(xPos + 1, yPos, zPos) : null;
		TileEntity zMTE = zM ? world.getTEWithoutLoad(xPos, yPos, zPos - 1) : null;
		TileEntity zPTE = zP ? world.getTEWithoutLoad(xPos, yPos, zPos + 1) : null;
		if (xMTE == null) {
			xM = false;
		}
		if (xPTE == null) {
			xP = false;
		}
		if (zMTE == null) {
			zM = false;
		}
		if (zPTE == null) {
			zP = false;
		}
		lock.unlock();
		if (!(xM || xP || zM || zP)) {
			return;
		}
		Lock thisLock = tE.thisLock;
		if (xP) {
			xPTE.lockManagementLock.lock();
		}
		if (zP) {
			zPTE.lockManagementLock.lock();
		}
		lock.lock();
		if (zM) {
			zMTE.lockManagementLock.lock();
		}
		if (xM) {
			xMTE.lockManagementLock.lock();
		}

		byte usedLocks = tE.usedLocks;

		if (xM) {
			Lock otherLock = xMTE.thisLock;
			if (otherLock != null) {
				xMTE.usedLocks |= lockXPlus;
				tE.xMinusLock = otherLock;
			}
			if (thisLock != null) {
				usedLocks |= lockXMinus;
				xMTE.xPlusLock = thisLock;
			}
		}
		if (xP) {
			Lock otherLock = xPTE.thisLock;
			if (otherLock != null) {
				xPTE.usedLocks |= lockXMinus;
				tE.xPlusLock = otherLock;
			}
			if (thisLock != null) {
				usedLocks |= lockXPlus;
				xPTE.xMinusLock = thisLock;
			}
		}
		if (zM) {
			Lock otherLock = zMTE.thisLock;
			if (otherLock != null) {
				zMTE.usedLocks |= lockZPlus;
				tE.zMinusLock = otherLock;
			}
			if (thisLock != null) {
				usedLocks |= lockZMinus;
				zMTE.zPlusLock = thisLock;
			}
		}
		if (zP) {
			Lock otherLock = zPTE.thisLock;
			if (otherLock != null) {
				zPTE.usedLocks |= lockZMinus;
				tE.zPlusLock = otherLock;
			}
			if (thisLock != null) {
				usedLocks |= lockZPlus;
				zPTE.zMinusLock = thisLock;
			}
		}
		tE.usedLocks = usedLocks;

		if (xM) {
			xMTE.lockManagementLock.unlock();
		}
		if (zM) {
			zMTE.lockManagementLock.unlock();
		}
		lock.unlock();
		if (zP) {
			zPTE.lockManagementLock.unlock();
		}
		if (xP) {
			xPTE.lockManagementLock.unlock();
		}
	}

	public void doTick() {
		boolean previousProfiling = world.theProfiler.profilingEnabled;
		lastStartTime = System.nanoTime();
		threadManager.waitForCompletion();
		if (previousProfiling) {
			world.theProfiler.profilingEnabled = false;
		}
		threadManager.runList(tickRegions);
		if (previousProfiling || waitForCompletion) {
			postTick();
		}
		if (previousProfiling) {
			world.theProfiler.profilingEnabled = true;
		}
	}

	public void tickEnd() {
		if (!waitForCompletion) {
			postTick();
		}
	}

	private void postTick() {
		lastTickLength = (threadManager.waitForCompletion() - lastStartTime);
		averageTickLength = ((averageTickLength * 127) + lastTickLength) / 128;
		if (!removalQueue.isEmpty()) {
			threadManager.run(new Runnable() {
				@Override
				public void run() {
					processChanges();
					if (shuffleCount++ % shuffleInterval == 0) {
						synchronized (tickRegions) {
							Collections.shuffle(tickRegions);
							if (tickRegions.removeAll(Collections.singleton(null))) {
								Log.severe("Something broke, tickRegions for " + world.getName() + " contained null!");
							}
						}
						MinecraftServer.runQueue.add(new FixDiscrepanciesTask());
					}
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
			tickRegions.clear();
		}
		entityList.clear();
		entityClassToCountMap.clear();
		UnsafeUtil.clean(this);
	}

	public void fixDiscrepancies(StringBuilder sb) {
		long startTime = System.nanoTime();
		int fixed = 0;
		int missingEntities = 0;
		int missingTiles = 0;
		int duplicateEntities = 0;
		int duplicateTiles = 0;
		int invalidTiles = 0;
		int unloadedEntities = 0;
		int unloadedTiles = 0;

		ChunkProviderServer chunkProviderServer = world.theChunkProviderServer;

		{
			Set<Entity> contained = new HashSet<Entity>();
			Set<Entity> toRemove = new ContainedRemoveSet<Entity>();
			List<Entity> unloaded = new ArrayList<Entity>();
			synchronized (entityLock) {
				for (Entity e : entityList) {
					if (add(e, false)) {
						missingEntities++;
						fixed++;
					} else if (!contained.add(e)) {
						toRemove.add(e);
						duplicateEntities++;
						fixed++;
					} else if (e instanceof IProjectile || e instanceof EntityCreature || e instanceof EntityMob) {
						synchronized (e) {
							Chunk chunk = world.getChunkIfExists(e.chunkCoordX, e.chunkCoordZ);
							if (chunk == null || !chunk.entityLists[e.chunkCoordY].contains(e)) {
								unloaded.add(e);
								unloadedEntities++;
							}
						}
					}
				}
				for (Entity e : unloaded) {
					remove(e);
				}
				entityList.removeAll(toRemove);
			}
		}

		{
			Set<TileEntity> contained = new HashSet<TileEntity>();
			Set<TileEntity> toRemove = new ContainedRemoveSet<TileEntity>();
			List<TileEntity> copy = new ArrayList<TileEntity>(tileEntityList.size());
			synchronized (tileEntityLock) {
				for (TileEntity te : tileEntityList) {
					copy.add(te);
					if (add(te, false)) {
						missingTiles++;
						fixed++;
					}
					if (!contained.add(te)) {
						toRemove.add(te);
						duplicateTiles++;
						fixed++;
					}
				}
				tileEntityList.removeAll(toRemove);
			}

			for (TileEntity te : copy) {
				Chunk chunk;
				boolean invalid = te.isInvalid();
				if (te.yCoord < 0 || te.yCoord > 255) {
					sb.append("TileEntity ").append(Log.toString(te)).append(" has an invalid y coordinate.\n");
					invalid = true;
				}
				if (invalid || (chunk = chunkProviderServer.getChunkIfExists(te.xCoord >> 4, te.zCoord >> 4)) == null || chunk.getChunkBlockTileEntity(te.xCoord & 15, te.yCoord, te.zCoord & 15) != te) {
					if (invalid) {
						invalidTiles++;
						sb.append("Removed ").append(Log.toString(te)).append(" as it is invalid.\n");
					} else {
						unloadedTiles++;
						te.invalidate();
						sb.append("Removed ").append(Log.toString(te)).append(" as it should have been unloaded.\n");
					}
					fixed++;
					remove(te);
				}
			}
		}

		int totalSize = tickRegions.size();
		int tESize = tileEntityCallables.size();
		int eSize = entityCallables.size();
		if (eSize + tESize != totalSize) {
			sb.append("TickRegion list size mismatch, total: ").append(totalSize).append(", te: ").append(tESize).append(", e: ").append(eSize).append(", combined: ").append(tESize + eSize);
			if (fixed != 0) {
				sb.append('\n');
			}
		}

		if (fixed != 0) {
			sb.append("Found and fixed ").append(fixed).append(" discrepancies in tile/entity lists in ").append(Log.name(world))
					.append("\ntiles - invalid: ").append(invalidTiles).append(", missing: ").append(missingTiles).append(", duplicate: ").append(duplicateTiles).append(", unloaded: ").append(unloadedTiles)
					.append("\nentities -  missing: ").append(missingEntities).append(", duplicate: ").append(duplicateEntities).append(", unloaded: ").append(unloadedEntities)
					.append("\nTook ").append((System.nanoTime() - startTime) / 1000000l).append("ms");
		}
	}

	public void recordStats(final TPSCommand.StatsHolder statsHolder) {
		statsHolder.entities += entityList.size();
		statsHolder.tileEntities += tileEntityList.size();
		statsHolder.chunks += world.theChunkProviderServer.getLoadedChunkCount();
	}

	public void writeStats(TableFormatter tf, final TPSCommand.StatsHolder statsHolder) {
		long timeTotal = 0;
		double time = Double.NaN;
		try {
			long[] tickTimes = MinecraftServer.getServer().getTickTimes(world);
			for (long tick : tickTimes) {
				timeTotal += tick;
			}
			time = (timeTotal) / (double) tickTimes.length;
			if (time == 0) {
				time = 0.1;
			}
		} catch (NullPointerException ignored) {
		}
		int entities = entityList.size();
		statsHolder.entities += entities;
		int tileEntities = tileEntityList.size();
		statsHolder.tileEntities += tileEntities;
		int chunks = world.theChunkProviderServer.getLoadedChunkCount();
		statsHolder.chunks += chunks;
		tf
				.row(Log.name(world))
				.row(entities)
				.row(tileEntities)
				.row(chunks)
				.row(world.playerEntities.size())
				.row(TableFormatter.formatDoubleWithPrecision((time * 100f) / MinecraftServer.getTargetTickTime(), 2) + '%');
	}

	public TableFormatter writeDetailedStats(TableFormatter tf) {
		@SuppressWarnings("MismatchedQueryAndUpdateOfStringBuilder")
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
			averageAverageTickTime /= tickRegions.size();
			stats.append("\n---- World stats ----");
			stats.append('\n').append(world.getChunkProvider().makeString());
			stats.append("\nAverage tick time: ").append(averageAverageTickTime).append("ms");
			stats.append("\nMax tick time: ").append(maxTickTime).append("ms");
			stats.append("\nEffective tick time: ").append(lastTickLength / 1000000f).append("ms");
			stats.append("\nAverage effective tick time: ").append((float) averageTickLength / 1000000).append("ms");
			stats.append("\nGlobal TPS: ").append(TableFormatter.formatDoubleWithPrecision(MinecraftServer.getTPS(), 2));
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

	public TableFormatter writeRegionDetails(final TableFormatter tf, final int hashCode) {
		int x = 0;
		int z = 0;
		TileEntityTickRegion tileEntityTickRegion = getTileEntityRegion(hashCode);
		if (tileEntityTickRegion != null) {
			tileEntityTickRegion.dump(tf);
			x = tileEntityTickRegion.regionX;
			z = tileEntityTickRegion.regionZ;
		}
		EntityTickRegion entityTickRegion = getEntityRegion(hashCode);
		if (entityTickRegion != null) {
			entityTickRegion.dump(tf);
			x = entityTickRegion.regionX;
			z = entityTickRegion.regionZ;
		}
		if (entityTickRegion == null && tileEntityTickRegion == null) {
			tf.sb.append("tickRegion for ").append(hashCode).append(" does not exist");
		} else {
			tf.sb.append("Dumped tickRegions for ").append(hashCode).append(": ").append(x).append(", ").append(z);
		}
		return tf;
	}

	public TableFormatter writeTECounts(final TableFormatter tf) {
		final Map<Class, ComparableIntegerHolder> counts = new HashMap<Class, ComparableIntegerHolder>() {
			@Override
			public ComparableIntegerHolder get(Object key_) {
				Class key = (Class) key_;
				ComparableIntegerHolder value = super.get(key);
				if (value == null) {
					value = new ComparableIntegerHolder();
					put(key, value);
				}
				return value;
			}
		};
		for (TileEntity tileEntity : tileEntityList.unsafe()) {
			if (tileEntity == null) {
				continue;
			}
			counts.get(tileEntity.getClass()).value++;
		}
		List<Class> sortedKeys = CollectionsUtil.sortedKeys(counts, 15);
		tf
				.heading("Type")
				.heading("Number");
		for (Class clazz : sortedKeys) {
			tf
					.row(clazz.getName())
					.row(counts.get(clazz).value);
		}
		tf.finishTable();
		return tf;
	}

	private class ComparableIntegerHolder implements Comparable<ComparableIntegerHolder> {
		public int value;

		ComparableIntegerHolder() {
		}

		@Override
		public int compareTo(final ComparableIntegerHolder comparableIntegerHolder) {
			int otherValue = comparableIntegerHolder.value;
			return (value < otherValue) ? -1 : ((value == otherValue) ? 0 : 1);
		}
	}

	private class FixDiscrepanciesTask implements Runnable {
		FixDiscrepanciesTask() {
		}

		@Override
		public void run() {
			StringBuilder sb = new StringBuilder();
			fixDiscrepancies(sb);
			if (sb.length() > 0) {
				Log.severe(sb.toString());
			}
		}
	}
}
