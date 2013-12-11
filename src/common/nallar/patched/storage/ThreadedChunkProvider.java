package nallar.patched.storage;

import com.google.common.collect.ImmutableSetMultimap;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import nallar.collections.ConcurrentQueueSet;
import nallar.patched.annotation.FakeExtend;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.ChunkGarbageCollector;
import nallar.tickthreading.minecraft.DeadLockDetector;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.BooleanThreadLocalDefaultFalse;
import nallar.tickthreading.util.DoNothingRunnable;
import nallar.tickthreading.util.ServerThreadFactory;
import nallar.tickthreading.util.concurrent.NativeMutex;
import nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 * This is a replacement for ChunkProviderServer
 * Instead of attempting to patch a class with many different implementations,
 * this replaces it with an implementation which is intended to be compatible
 * with both Forge and MCPC+.
 */
@FakeExtend
public abstract class ThreadedChunkProvider extends ChunkProviderServer implements IChunkProvider {
	/**
	 * You may also use a synchronized block on generateLock,
	 * and are encouraged to unless tryLock() is required.
	 * This works as NativeMutex uses JVM monitors internally.
	 */
	public final NativeMutex generateLock = new NativeMutex();
	private static final ThreadPoolExecutor chunkLoadThreadPool;
	private final IChunkProvider generator; // Mojang shouldn't use the same interface for  :(

	static {
		chunkLoadThreadPool = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ServerThreadFactory("Async ChunkLoader"));
		chunkLoadThreadPool.allowCoreThreadTimeOut(true);
	}

	private static final Runnable doNothingRunnable = new DoNothingRunnable();
	private static final int populationRange = 1;
	private final NonBlockingHashMapLong<AtomicInteger> chunkLoadLocks = new NonBlockingHashMapLong<AtomicInteger>();
	private final LongHashMap<Chunk> chunks = new LongHashMap<Chunk>();
	private final LongHashMap<Chunk> loadingChunks = new LongHashMap<Chunk>();
	private final LongHashMap<Chunk> unloadingChunks = new LongHashMap<Chunk>();
	private final ConcurrentQueueSet<Long> unloadStage0 = new ConcurrentQueueSet<Long>();
	private final ConcurrentLinkedQueue<QueuedUnload> unloadStage1 = new ConcurrentLinkedQueue<QueuedUnload>();
	private final IChunkLoader loader;
	private final WorldServer world;
	private final Chunk defaultEmptyChunk;
	private final BooleanThreadLocalDefaultFalse inUnload = new BooleanThreadLocalDefaultFalse();
	private final BooleanThreadLocalDefaultFalse worldGenInProgress;
	private boolean loadChunksInProvideChunk = true;
	private int loadChunksInProvideChunkTicks = 0;
	private int overloadCount = 0;
	private int saveTicks = 0;
	private int maxChunksToSave = 192;
	private int lastChunksSaved = -1;
	private Chunk lastChunk;
	// Mojang compatiblity fields.
	public final IChunkProvider currentChunkProvider;
	public Set<Long> chunksToUnload;
	public final List<Chunk> loadedChunks;
	public final IChunkLoader currentChunkLoader;
	@SuppressWarnings("UnusedDeclaration")
	public boolean loadChunkOnProvideRequest;

	public ThreadedChunkProvider(WorldServer world, IChunkLoader loader, IChunkProvider generator) {
		super(world, loader, generator); // This call will be removed by javassist.
		currentChunkProvider = this.generator = generator;
		this.world = world;
		currentChunkLoader = this.loader = loader;
		loadedChunks = Collections.synchronizedList(new ArrayList<Chunk>());
		world.emptyChunk = defaultEmptyChunk = new EmptyChunk(world, 0, 0);
		world.worldGenInProgress = worldGenInProgress = new BooleanThreadLocalDefaultFalse();
		world.inImmediateBlockUpdate = new BooleanThreadLocalDefaultFalse();
	}

	@Declare
	public static void onChunkLoad(Chunk chunk, Runnable runnable) {
		if (runnable instanceof nallar.tickthreading.util.ChunkLoadRunnable) {
			((nallar.tickthreading.util.ChunkLoadRunnable) runnable).onLoad(chunk);
		} else {
			runnable.run();
		}
	}

	@Override
	@Declare
	public WorldServer getWorld() {
		return world;
	}

	@Override
	@Declare
	public List<Chunk> getLoadedChunks() {
		return loadedChunks;
	}

	@Override
	public boolean unloadQueuedChunks() {
		return generator.unloadQueuedChunks();
	}

	@SuppressWarnings({"ConstantConditions", "FieldRepeatedlyAccessedInMethod"})
	@Override
	@Declare
	public void tick() {
		int ticks = world.tickCount;
		// Handle unload requests
		final ConcurrentQueueSet<Long> unloadStage0 = this.unloadStage0;
		if (!unloadStage0.isEmpty()) {
			ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = world.getPersistentChunks();
			PlayerManager playerManager = world.getPlayerManager();
			ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(0, 0);
			Long key_;
			int done = 0;
			while (++done != 75 && (key_ = unloadStage0.take()) != null) {
				long key = key_;
				int x = (int) key;
				int z = (int) (key >> 32);
				chunkCoordIntPair.chunkXPos = x;
				chunkCoordIntPair.chunkZPos = z;
				Chunk chunk = chunks.getValueByKey(key);
				if (chunk == null) {
					continue;
				}
				synchronized (chunk) {
					if (chunk.partiallyUnloaded || !chunk.queuedUnload || unloadingChunks.containsItem(key)) {
						continue;
					}
					if (persistentChunks.containsKey(chunkCoordIntPair) || playerManager.getOrCreateChunkWatcher(x, z, false) != null || !fireBukkitUnloadEvent(chunk)) {
						chunk.queuedUnload = false;
						continue;
					}
					if (lastChunk == chunk) {
						lastChunk = null;
					}
					chunk.partiallyUnloaded = true;
					chunk.onChunkUnloadTT();
					chunk.pendingBlockUpdates = world.getPendingBlockUpdates(chunk, false);
					loadedChunks.remove(chunk);
					chunks.remove(key);
					synchronized (unloadingChunks) {
						unloadingChunks.put(key, chunk);
						unloadStage1.add(new QueuedUnload(key, ticks));
					}
				}
			}

			if (loader != null) {
				loader.chunkTick();
			}
		}

		handleUnloadQueue(ticks - 3);

		if (ticks > 1200 && !world.multiverseWorld && world.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading
				&& loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(world).isEmpty()
				&& (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(world.getDimension()))) {
			DimensionManager.unloadWorld(world.getDimension());
		}

		if (ticks % TickThreading.instance.chunkGCInterval == 0) {
			ChunkGarbageCollector.garbageCollect(world);
		}

		if (loadChunksInProvideChunkTicks++ == 200) {
			loadChunksInProvideChunk = false;
			int loaded = 0;
			int possible = world.getPersistentChunks().size();
			for (ChunkCoordIntPair chunkCoordIntPair : world.getPersistentChunks().keySet()) {
				if (getChunkAt(chunkCoordIntPair.chunkXPos, chunkCoordIntPair.chunkZPos, doNothingRunnable) == null) {
					loaded++;
				}
				possible++;
			}
			if (loaded > 0) {
				Log.info("Loaded " + loaded + '/' + possible + " persistent chunks in " + Log.name(world));
			}
		}
	}

	private void handleUnloadQueue(long queueThreshold) {
		handleUnloadQueue(queueThreshold, false);
	}

	private synchronized void handleUnloadQueue(long queueThreshold, boolean full) {
		int done = 0;
		// Handle unloading stage 1
		{
			QueuedUnload queuedUnload;
			while ((queuedUnload = unloadStage1.peek()) != null && queuedUnload.ticks <= queueThreshold && (full || ++done <= 200)) {
				long key = queuedUnload.key;
				synchronized (unloadingChunks) {
					if (!unloadStage1.remove(queuedUnload)) {
						continue;
					}
				}
				finalizeUnload(key);
			}
		}
	}

	private boolean finalizeUnload(long key) {
		Chunk chunk;
		synchronized (unloadingChunks) {
			// Don't remove here, as the chunk should be in the unloadingChunks map so that getChunkIfExists will still return it if used by a mod during chunk saving.
			chunk = unloadingChunks.getValueByKey(key);
		}
		if (chunk == null) {
			return false;
		}
		if (!chunk.partiallyUnloaded) {
			return false;
		}
		synchronized (chunk) {
			if (chunk.alreadySavedAfterUnload) {
				return false;
			}
			chunk.alreadySavedAfterUnload = true;
			try {
				boolean notInUnload = !inUnload.getAndSet(true);
				boolean notWorldGen = !worldGenInProgress.getAndSet(true);
				saveChunk(chunk);
				safeSaveExtraChunkData(chunk);
				if (notWorldGen) {
					worldGenInProgress.set(false);
				}
				if (notInUnload) {
					inUnload.set(false);
				}
				if (chunks.containsItem(key)) {
					Log.severe("Failed to unload chunk " + key + ", it was reloaded during unloading");
				}
			} finally {
				if (unloadingChunks.remove(key) != chunk) {
					Log.severe("While unloading " + chunk + " it was replaced/removed from the unloadingChunks map.");
				}
			}
		}

		return true;
	}

	// Public visibility as it will be accessed from net.minecraft.whatever, not actually this class
	// (Inner classes are not remapped in patching)
	public static class QueuedUnload {
		public final int ticks;
		public final long key;

		public QueuedUnload(long key, int ticks) {
			this.key = key;
			this.ticks = ticks;
		}
	}

	@Override
	@Declare
	public boolean safeToGenerate() {
		return worldGenInProgress.get() == Boolean.FALSE && !Thread.holdsLock(generateLock);
	}

	@Override
	public boolean chunkExists(int x, int z) {
		long key = key(x, z);
		return chunks.containsItem(key) || (worldGenInProgress.get() == Boolean.TRUE && (loadingChunks.containsItem(key) || (inUnload.get() == Boolean.TRUE && unloadingChunks.containsItem(key))));
	}

	@Override
	@Declare
	public boolean chunksExist(int minX, int minZ, int maxX, int maxZ) {
		boolean worldGenInProgress = this.worldGenInProgress.get() == Boolean.TRUE;
		boolean inUnload = worldGenInProgress && this.inUnload.get() == Boolean.TRUE;
		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				long key = key(x, z);
				if (!(chunks.containsItem(key) || (worldGenInProgress && (loadingChunks.containsItem(key) || (inUnload && unloadingChunks.containsItem(key)))))) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	@Declare
	public boolean unloadChunk(int x, int z) {
		if (world.getPersistentChunks().keySet().contains(new ChunkCoordIntPair(x, z))) {
			return false;
		}
		long hash = key(x, z);
		Chunk chunk = chunks.getValueByKey(hash);
		if (chunk == null) {
			return false;
		}
		chunk.queuedUnload = true;
		return unloadStage0.add(hash);
	}

	@Override
	@Declare
	public void unloadChunkForce(long hash) {
		if (unloadStage0.add(hash)) {
			Chunk chunk = chunks.getValueByKey(hash);
			if (chunk != null) {
				chunk.queuedUnload = true;
			}
		}
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int x, int z) {
		unloadChunk(x, z);
	}

	@Override
	public void unloadAllChunks() {
		if (loadedChunks.size() > world.getPersistentChunks().size()) {
			synchronized (loadedChunks) {
				for (Chunk chunk : loadedChunks) {
					unloadStage0.add(key(chunk.xPosition, chunk.zPosition));
				}
			}
		}
	}

	@Deprecated
	public void unloadChunkImmediately(int x, int z, boolean save) {
		/** I lied, doesn't unload it immediately. **/
		unloadChunk(x, z);
	}

	@Deprecated
	public Chunk regenerateChunk(int x, int z) {
		long key = key(x, z);
		AtomicInteger lock = getLock(key);
		synchronized (lock) {
			try {
				Chunk chunk = getChunkIfExists(x, z);
				if (chunk != null) {
					finalizeUnload(key);
					do {
						chunk = getChunkIfExists(x, z);
						if (chunk == null) {
							continue;
						}
						chunk.queuedUnload = true;
						synchronized (chunk) {
							if (chunk.partiallyUnloaded || unloadingChunks.containsItem(key)) {
								continue;
							}
							if (!fireBukkitUnloadEvent(chunk)) {
								Log.warning("Bukkit cancelled chunk unload for regeneration unload of " + x + ", " + z, new Throwable());
							}
							if (lastChunk == chunk) {
								lastChunk = null;
							}
							chunk.partiallyUnloaded = true;
							chunk.onChunkUnloadTT();
							chunk.pendingBlockUpdates = world.getPendingBlockUpdates(chunk, false);
							loadedChunks.remove(chunk);
							chunks.remove(key);
							synchronized (unloadingChunks) {
								unloadingChunks.put(key, chunk);
								unloadStage1.add(new QueuedUnload(key, 0));
							}
						}
					} while (false);
					finalizeUnload(key);
				}
				return getChunkAtInternal(x, z, true, true);
			} finally {
				lock.decrementAndGet();
			}
		}
	}

	@Override
	@Declare
	public void cacheChunk(int x, int z) {
		if (!((AnvilChunkLoader) loader).isChunkCacheFull()) {
			chunkLoadThreadPool.execute(new ChunkCacheRunnable(this, x, z));
		}
	}

	@Override
	@Declare
	public void cacheChunkInternal(int x, int z) {
		AnvilChunkLoader anvilChunkLoader = (AnvilChunkLoader) loader;
		if (anvilChunkLoader.isChunkCacheFull()) {
			return;
		}
		long key = key(x, z);
		final AtomicInteger lock = getLock(key);
		try {
			synchronized (lock) {
				if (chunks.containsItem(key) || loadingChunks.containsItem(key) || unloadingChunks.containsItem(key)) {
					return;
				}
				anvilChunkLoader.cacheChunk(world, x, z);
			}
		} finally {
			if (lock.decrementAndGet() == 0) {
				loadingChunks.remove(key);
			}
		}
	}

	@Override
	public final Chunk provideChunk(int x, int z) {
		Chunk chunk = getChunkIfExists(x, z);

		if (chunk != null) {
			return chunk;
		}

		if (loadChunksInProvideChunk) {
			return getChunkAtInternal(x, z, true, false);
		}
		/* else {
			Log.warning("Didn't load a chunk at " + x + ',' + z, new Throwable());
		} */

		return defaultEmptyChunk;
	}

	@Override
	public final Chunk loadChunk(int x, int z) {
		Chunk chunk = getChunkAt(x, z, true, false, null);
		chunk.queuedUnload = false;
		return chunk;
	}

	@Override
	@Declare
	public final Chunk getChunkAt(final int x, final int z, final Runnable runnable) {
		return getChunkAt(x, z, true, false, runnable);
	}

	@Override
	@Declare
	public final Chunk getChunkAt(final int x, final int z, boolean allowGenerate, final Runnable runnable) {
		return getChunkAt(x, z, allowGenerate, false, runnable);
	}

	@Override
	@Declare
	public final Chunk getChunkIfExists(int x, int z) {
		Chunk chunk = lastChunk;
		if (chunk != null && chunk.xPosition == x && chunk.zPosition == z) {
			return chunk;
		}
		long key = key(x, z);
		chunk = chunks.getValueByKey(key);
		if (chunk == null && worldGenInProgress.get() == Boolean.TRUE) {
			chunk = loadingChunks.getValueByKey(key);
			if (chunk == null && inUnload.get() == Boolean.TRUE) {
				chunk = unloadingChunks.getValueByKey(key);
			}
		}
		if (chunk == null) {
			return null;
		}
		lastChunk = chunk;
		return chunk;
	}

	@Override
	@Declare
	public final Chunk getChunkAt(final int x, final int z, boolean allowGenerate, boolean regenerate, final Runnable runnable) {
		Chunk chunk = getChunkIfExists(x, z);

		if (chunk != null) {
			if (runnable != null) {
				ThreadedChunkProvider.onChunkLoad(chunk, runnable);
			}
			return chunk;
		}

		if (runnable != null) {
			if (world.unloaded) {
				throw new IllegalStateException("Trying to load chunks in an unloaded world.");
			}
			chunkLoadThreadPool.execute(new ChunkLoadRunnable(x, z, allowGenerate, regenerate, runnable, this));
			return null;
		}

		return getChunkAtInternal(x, z, allowGenerate, regenerate);
	}

	@SuppressWarnings("ConstantConditions")
	private Chunk getChunkAtInternal(final int x, final int z, boolean allowGenerate, boolean regenerate) {
		if (!safeToGenerate()) {
			return defaultEmptyChunk;
		}

		long key = key(x, z);
		Chunk chunk;

		final AtomicInteger lock = getLock(key);
		boolean wasGenerated = false;
		try {
			boolean inLoadingMap = false;

			// Lock on the lock for this chunk - prevent multiple instances of the same chunk
			synchronized (lock) {
				chunk = chunks.getValueByKey(key);
				if (chunk != null) {
					return chunk;
				}
				chunk = loadingChunks.getValueByKey(key);
				if (regenerate) {
					if (!allowGenerate) {
						throw new IllegalArgumentException();
					}
					loadingChunks.put(key, defaultEmptyChunk);
				} else if (chunk == null) {
					finalizeUnload(key);
					chunk = safeLoadChunk(x, z);
					if (chunk != null && (chunk.xPosition != x || chunk.zPosition != z)) {
						Log.severe("Chunk at " + chunk.xPosition + ',' + chunk.zPosition + " was stored at " + x + ',' + z + "\nResetting this chunk.");
						chunk = null;
					}
					if (chunk == null) {
						loadingChunks.put(key, defaultEmptyChunk);
						if (!allowGenerate) {
							return null;
						} else if (generator == null) {
							return defaultEmptyChunk;
						}
					} else {
						loadingChunks.put(key, chunk);
						inLoadingMap = true;
						if (!world.loadEventFired) {
							Log.warning("Loaded chunk before world load event fired, this can cause many issues, including loss of multiblock data.", new Throwable());
						}
					}
				} else if (chunk != defaultEmptyChunk) {
					inLoadingMap = true;
				}
			}
			// Unlock this chunk - avoids a deadlock
			// Thread A - requests chunk A - needs genned
			// Thread B - requests chunk B - needs genned
			// In thread A, redpower tries to load chunk B
			// because its marble gen is buggy.
			// Thread B is now waiting for the generate lock,
			// Thread A is waiting for the lock on chunk B

			// Lock the generation lock - ChunkProviderGenerate isn't threadsafe at all

			boolean locked = true;
			generateLock.lock();
			try {
				synchronized (lock) {
					chunk = chunks.getValueByKey(key);
					if (chunk != null) {
						return chunk;
					}
					worldGenInProgress.set(Boolean.TRUE);
					try {
						chunk = loadingChunks.getValueByKey(key);
						if (chunk == null) {
							Log.severe("Failed to load chunk " + chunk + " at " + x + ',' + z + " as it is missing from the loading chunks map.");
							return defaultEmptyChunk;
						}
						if (chunk == defaultEmptyChunk) {
							try {
								chunk = generator.provideChunk(x, z);
								if (chunk == null) {
									Log.severe("Null chunk was generated for " + x + ',' + z + " by " + generator);
									return defaultEmptyChunk;
								}
								chunk.isTerrainPopulated = false;
								wasGenerated = true;
							} catch (Throwable t) {
								Log.severe("Failed to generate a chunk in " + Log.name(world) + " at chunk coords " + x + ',' + z);
								throw UnsafeUtil.throwIgnoreChecked(t);
							}
						} else {
							if (generator != null) {
								generator.recreateStructures(x, z);
							}
						}

						if (!inLoadingMap) {
							loadingChunks.put(key, chunk);
						}

						locked = false;
						generateLock.unlock();
						chunk.threadUnsafeChunkLoad();

						chunks.put(key, chunk);
					} finally {
						worldGenInProgress.set(Boolean.FALSE);
					}
				}
			} finally {
				if (locked) {
					generateLock.unlock();
				}
			}
		} finally {
			if (lock.decrementAndGet() == 0) {
				loadingChunks.remove(key);
			}
		}

		loadedChunks.add(chunk);
		chunk.onChunkLoad();
		fireBukkitLoadEvent(chunk, wasGenerated);
		chunkLoadLocks.remove(key);

		synchronized (generateLock) {
			tryPopulateChunks(chunk);
		}

		return chunk;
	}

	private void populate(Chunk chunk) {
		synchronized (chunk) {
			int x = chunk.xPosition;
			int z = chunk.zPosition;
			if (chunk.isTerrainPopulated) {
				Log.warning("Attempted to populate chunk " + x + ',' + z + " which is already populated.");
				return;
			}
			if (generator != null) {
				generator.populate(this, x, z);
				fireBukkitPopulateEvent(chunk);
				GameRegistry.generateWorld(x, z, world, generator, this);
				chunk.setChunkModified(); // It may have been modified in generator.populate/GameRegistry.generateWorld.
			}
			//noinspection ConstantConditions
			if (chunk.isTerrainPopulated) {
				Log.warning("Chunk " + chunk + " had its isTerrainPopulated field set to true incorrectly by external code while populating");
			}
			chunk.isTerrainPopulated = true;
		}
	}

	private void tryPopulateChunks(Chunk centerChunk) {
		int cX = centerChunk.xPosition;
		int cZ = centerChunk.zPosition;
		for (int x = cX - populationRange; x <= cX + populationRange; x++) {
			for (int z = cZ - populationRange; z <= cZ + populationRange; z++) {
				Chunk chunk = getChunkFastUnsafe(x, z);
				if (chunk != null && !chunk.queuedUnload && !chunk.partiallyUnloaded && !chunk.isTerrainPopulated && checkChunksExistLoadedNormally(x - populationRange, x + populationRange, z - populationRange, z + populationRange)) {
					populate(chunk);
				}
			}
		}
	}

	public boolean checkChunksExistLoadedNormally(int minX, int maxX, int minZ, int maxZ) {
		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				Chunk chunk = getChunkFastUnsafe(x, z);
				if (chunk == null || chunk.queuedUnload || chunk.partiallyUnloaded) {
					return false;
				}
			}
		}

		return true;
	}

	@Override
	@Declare
	public Chunk getChunkFastUnsafe(int x, int z) {
		return chunks.getValueByKey(key(x, z));
	}

	private AtomicInteger getLock(long key) {
		AtomicInteger lock = chunkLoadLocks.get(key);
		if (lock != null) {
			lock.incrementAndGet();
			return lock;
		}
		AtomicInteger newLock = new AtomicInteger(1);
		lock = chunkLoadLocks.putIfAbsent(key, newLock);
		if (lock != null) {
			lock.incrementAndGet();
			return lock;
		}
		return newLock;
	}

	@Override
	@Declare
	public void fireBukkitLoadEvent(Chunk chunk, boolean newlyGenerated) {
	}

	@Override
	@Declare
	public boolean fireBukkitUnloadEvent(Chunk chunk) {
		return true;
	}

	@Override
	protected Chunk safeLoadChunk(int x, int z) {
		if (loader == null) {
			return null;
		}
		try {
			Chunk chunk = loader.loadChunk(world, x, z);

			if (chunk != null) {
				chunk.lastSaveTime = world.getTotalWorldTime();
			}

			return chunk;
		} catch (Exception e) {
			FMLLog.log(Level.SEVERE, e, "Failed to load chunk at " + x + ',' + z);
			return null;
		}
	}

	@Override
	protected void safeSaveExtraChunkData(Chunk chunk) {
		if (loader == null) {
			return;
		}
		try {
			loader.saveExtraChunkData(world, chunk);
		} catch (Exception e) {
			FMLLog.log(Level.SEVERE, e, "Failed to save extra chunk data for " + chunk);
		}
	}

	@Deprecated
	@Override
	protected void safeSaveChunk(Chunk chunk) {
		throw new Error("Not supported with TT");
	}

	@Override
	@Declare
	public void saveChunk(Chunk chunk) {
		if (loader == null) {
			return;
		}
		try {
			chunk.lastSaveTime = world.getTotalWorldTime();
			loader.saveChunk(world, chunk);
		} catch (Exception e) {
			FMLLog.log(Level.SEVERE, e, "Failed to save chunk " + chunk);
		}
	}

	@Deprecated
	@Override
	public void populate(IChunkProvider chunkProvider, int x, int z) {
		throw new UnsupportedOperationException("Unused, inefficient parameter choice.");
	}

	@Override
	@Declare
	public void fireBukkitPopulateEvent(Chunk chunk) {
	}

	@Override
	public boolean saveChunks(boolean fullSaveRequired, IProgressUpdate progressUpdate) {
		boolean saveAll = fullSaveRequired;
		if (saveTicks++ % 512 == 0) {
			int loadedChunks = chunks.getNumHashElements();
			if (loadedChunks > 1536) {
				DeadLockDetector.tickAhead(5);
				DeadLockDetector.sendChatSafely("Fully saving world " + world.getName() + " with " + loadedChunks + " chunks, expect a short lag spike.");
			} else {
				DeadLockDetector.tickAhead(1);
			}
			saveAll = true;
		}
		int savedChunks = 0;

		long worldTime = world.getTotalWorldTime();
		List<Chunk> chunksToSave = new ArrayList<Chunk>();
		boolean overload = false;
		boolean warnableOverload = false;
		int fullChunksToSave = 0;
		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {
				if (!chunk.partiallyUnloaded && chunk.needsSaving(saveAll, worldTime)) {
					if (!overload) {
						if (++savedChunks == maxChunksToSave && !saveAll) {
							if (++overloadCount > 25) {
								warnableOverload = true;
							}
							overload = true;
						}
						chunk.isModified = false;
						chunksToSave.add(chunk);
					}
					fullChunksToSave++;
				}
			}
		}

		if (warnableOverload) {
			Log.warning("Partial save queue overloaded in " + Log.name(world) + ". You should probably decrease saveInterval to avoid lag spikes. Only saved " + (savedChunks - 1) + " out of " + fullChunksToSave);
			maxChunksToSave = (maxChunksToSave * 3) / 2;
			overloadCount -= 4;
		}

		for (Chunk chunk : chunksToSave) {
			if (chunk.partiallyUnloaded) {
				continue;
			}
			if (chunks.getValueByKey(key(chunk.xPosition, chunk.zPosition)) != chunk) {
				if (MinecraftServer.getServer().isServerRunning()) {
					Log.warning("Not saving " + chunk + ", not in correct location in chunks map.");
				}
				continue;
			}

			if (fullSaveRequired) {
				safeSaveExtraChunkData(chunk);
			}

			saveChunk(chunk);
			chunk.isModified = false; // Just in case a mod is managing to set the chunk as modified during saving, don't want pointless repeated saving.
		}

		lastChunksSaved = savedChunks;

		if (overloadCount > 0 && savedChunks != maxChunksToSave) {
			overloadCount--;
		}

		if (fullSaveRequired) {
			handleUnloadQueue(Long.MAX_VALUE, true);

			if (loader != null) {
				loader.saveExtraData();
			}
		}

		return true;
	}

	@Override
	public boolean canSave() {
		return !world.canNotSave;
	}

	@Override
	public String makeString() {
		return "Loaded " + loadedChunks.size() + " Loading " + loadingChunks.getNumHashElements() + " Unload " + unloadStage0.size() + " UnloadSave " + unloadStage1.size() + " Locks " + chunkLoadLocks.size() + " PartialSave " + lastChunksSaved + " Forced " + world.getPersistentChunks().size() + " Cached " + ((AnvilChunkLoader) loader).getCachedChunks();
	}

	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		return generator.getPossibleCreatures(creatureType, x, y, z);
	}

	@Override
	public ChunkPosition findClosestStructure(World world, String name, int x, int y, int z) {
		return generator.findClosestStructure(world, name, x, y, z);
	}

	@Override
	public int getLoadedChunkCount() {
		return loadedChunks.size();
	}

	@Override
	public void recreateStructures(int x, int z) {
	}

	@Override
	@Declare
	public net.minecraft.nbt.NBTTagCompound readChunkNBT(int x, int z) {
		NBTTagCompound nbtTagCompound = ((AnvilChunkLoader) loader).readChunkNBT(world, x, z, true);
		if (nbtTagCompound == null) {
			return null;
		}
		return nbtTagCompound.getCompoundTag("Level");
	}

	private static long key(int x, int z) {
		return (((long) z) << 32) | (x & 0xffffffffL);
	}

	public static class ChunkLoadRunnable implements Runnable {
		private final int x;
		private final int z;
		private final Runnable runnable;
		private final ChunkProviderServer provider;
		private final boolean allowGenerate;
		private final boolean regenerate;

		public ChunkLoadRunnable(int x, int z, boolean allowGenerate, boolean regenerate, Runnable runnable, ChunkProviderServer provider) {
			this.x = x;
			this.z = z;
			this.allowGenerate = allowGenerate;
			this.regenerate = regenerate;
			this.runnable = runnable;
			this.provider = provider;
		}

		@Override
		public void run() {
			try {
				WorldServer worldServer = provider.getWorld();
				if (worldServer.unloaded) {
					FMLLog.warning("Failed to load chunk at " + worldServer.getDimension() + ':' + x + ',' + z + " asynchronously. The world is no longer loaded.");
					return;
				}
				Chunk chunk = provider.getChunkAt(x, z, allowGenerate, regenerate, null);
				if (chunk == null || (allowGenerate && chunk instanceof EmptyChunk)) {
					FMLLog.warning("Failed to load chunk at " + Log.name(worldServer) + ':' + x + ',' + z + " asynchronously. Provided " + chunk);
				} else {
					ChunkProviderServer.onChunkLoad(chunk, runnable);
				}
			} catch (Throwable t) {
				FMLLog.log(Level.SEVERE, t, "Exception loading chunk asynchronously.");
			}
		}
	}

	public static class ChunkCacheRunnable implements Runnable {
		private final ChunkProviderServer chunkProviderServer;
		private final int x;
		private final int z;

		public ChunkCacheRunnable(final ChunkProviderServer chunkProviderServer, final int x, final int z) {
			this.chunkProviderServer = chunkProviderServer;
			this.x = x;
			this.z = z;
		}

		@Override
		public void run() {
			chunkProviderServer.cacheChunkInternal(x, z);
		}
	}
}
