package me.nallar.patched.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.google.common.collect.ImmutableSetMultimap;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import me.nallar.exception.ConcurrencyError;
import me.nallar.patched.annotation.FakeExtend;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ChunkGarbageCollector;
import me.nallar.tickthreading.minecraft.DeadLockDetector;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.BooleanThreadLocal;
import me.nallar.tickthreading.util.DoNothingRunnable;
import me.nallar.tickthreading.util.ServerThreadFactory;
import me.nallar.tickthreading.util.concurrent.NativeMutex;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.EnumCreatureType;
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
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

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

	static {
		chunkLoadThreadPool = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors(), 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ServerThreadFactory("Async ChunkLoader"));
		chunkLoadThreadPool.allowCoreThreadTimeOut(true);
	}

	private static final Runnable doNothingRunnable = new DoNothingRunnable();
	private final NonBlockingHashMapLong<AtomicInteger> chunkLoadLocks = new NonBlockingHashMapLong<AtomicInteger>();
	private final LongHashMap chunks = new LongHashMap();
	private final LongHashMap loadingChunks = new LongHashMap();
	private final LongHashMap unloadingChunks = new LongHashMap();
	private final Set<Long> unloadStage0 = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
	private final ConcurrentLinkedQueue<QueuedUnload> unloadStage1 = new ConcurrentLinkedQueue<QueuedUnload>();
	private final IChunkProvider generator; // Mojang shouldn't use the same interface for  :(
	private final IChunkLoader loader;
	private final WorldServer world;
	private final Chunk defaultEmptyChunk;
	private final BooleanThreadLocal inUnload = new BooleanThreadLocal();
	private final BooleanThreadLocal worldGenInProgress;
	private boolean loadedPersistentChunks = false;
	private boolean loadChunksInProvideChunk = true;
	private int unloadTicks = 0;
	private int overloadCount = 0;
	private int saveTicks = 0;
	private int maxChunksToSave = 384;
	private int lastChunksSaved = -1;
	private Chunk lastChunk;
	// Mojang compatiblity fields.
	public final IChunkProvider currentChunkProvider;
	public final Set<Long> chunksToUnload = unloadStage0;
	public final List<Chunk> loadedChunks;
	public final IChunkLoader currentChunkLoader;
	@SuppressWarnings ("UnusedDeclaration")
	public boolean loadChunkOnProvideRequest;

	public ThreadedChunkProvider(WorldServer world, IChunkLoader loader, IChunkProvider generator) {
		super(world, loader, generator); // This call will be removed by javassist.
		currentChunkProvider = this.generator = generator;
		this.world = world;
		currentChunkLoader = this.loader = loader;
		loadedChunks = Collections.synchronizedList(new ArrayList<Chunk>());
		defaultEmptyChunk = new EmptyChunk(world, 0, 0);
		world.worldGenInProgress = worldGenInProgress = new BooleanThreadLocal();
		world.inImmediateBlockUpdate = new BooleanThreadLocal();
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
	@Declare
	public Set<Long> getChunksToUnloadSet() {
		return unloadStage0;
	}

	@Override
	public boolean unload100OldestChunks() {
		return generator.unload100OldestChunks();
	}

	@SuppressWarnings ({"ConstantConditions", "FieldRepeatedlyAccessedInMethod"})
	@Override
	@Declare
	public void tick() {
		int ticks = world.tickCount;
		// Handle unload requests
		if (!unloadStage0.isEmpty()) {
			ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = world.getPersistentChunks();
			PlayerManager playerManager = world.getPlayerManager();
			ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(0, 0);
			Iterator<Long> i$ = unloadStage0.iterator();
			int done = 0;
			while (i$.hasNext()) {
				if (done++ > 100) {
					break;
				}
				long key = i$.next();
				i$.remove();
				int x = (int) key;
				int z = (int) (key >> 32);
				chunkCoordIntPair.chunkXPos = x;
				chunkCoordIntPair.chunkZPos = z;
				Chunk chunk = (Chunk) chunks.getValueByKey(key);
				if (chunk == null || chunk.partiallyUnloaded || !chunk.queuedUnload) {
					continue;
				}
				if (persistentChunks.containsKey(chunkCoordIntPair) || unloadingChunks.containsItem(key) || playerManager.getOrCreateChunkWatcher(x, z, false) != null || !fireBukkitUnloadEvent(chunk)) {
					chunk.queuedUnload = false;
					continue;
				}
				if (lastChunk == chunk) {
					lastChunk = null;
				}
				chunk.partiallyUnloaded = true;
				chunk.onChunkUnload();
				chunk.pendingBlockUpdates = world.getPendingBlockUpdates(chunk, false);
				loadedChunks.remove(chunk);
				chunks.remove(key);
				synchronized (unloadingChunks) {
					unloadingChunks.add(key, chunk);
					unloadStage1.add(new QueuedUnload(key, ticks));
				}
			}

			if (loader != null) {
				loader.chunkTick();
			}
		}

		handleUnloadQueue(ticks - 3);

		if (this.unloadTicks++ > 1200 && !world.multiverseWorld && world.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading
				&& loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(world).isEmpty()
				&& (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(world.getDimension()))) {
			DimensionManager.unloadWorld(world.getDimension());
		}

		if (ticks % TickThreading.instance.chunkGCInterval == 0) {
			ChunkGarbageCollector.garbageCollect(world);
		}

		if (unloadTicks == 100) {
			loadChunksInProvideChunk = false;
		}

		if (!loadedPersistentChunks && unloadTicks >= 5) {
			loadedPersistentChunks = true;
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

	private void handleUnloadQueue(long queueThreshold, boolean full) {
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
			chunk = (Chunk) unloadingChunks.getValueByKey(key);
		}
		if (chunk == null) {
			return false;
		}
		try {
			if (!chunk.partiallyUnloaded) {
				return false;
			}
			synchronized (chunk) {
				if (chunk.alreadySavedAfterUnload) {
					Log.severe("Chunk save may have failed for " + key + ": " + (int) key + ',' + (int) (key >> 32));
					return false;
				}
				chunk.alreadySavedAfterUnload = true;
				boolean notInUnload = !inUnload.getAndSet(true);
				boolean notWorldGen = !worldGenInProgress.getAndSet(true);
				safeSaveChunk(chunk);
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
			}
		} finally {
			if (unloadingChunks.remove(key) != chunk) {
				Log.severe("While unloading " + chunk + " it was replaced/removed from the unloadingChunks map.");
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
		long hash = key(x, z);
		Chunk chunk = (Chunk) chunks.getValueByKey(hash);
		if (chunk == null) {
			return false;
		}
		if (world.getPersistentChunks().keySet().contains(new ChunkCoordIntPair(x, z))) {
			return false;
		}
		chunk.queuedUnload = true;
		return unloadStage0.add(hash);
	}

	@Override
	@Declare
	public void unloadChunkForce(long hash) {
		if (unloadStage0.add(hash)) {
			Chunk chunk = (Chunk) chunks.getValueByKey(hash);
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
		long key = key(x, z);
		finalizeUnload(key);
		Chunk chunk = getChunkIfExists(x, z);
		if (chunk == null || !fireBukkitUnloadEvent(chunk)) {
			return;
		}
		synchronized (chunk) {
			chunk.queuedUnload = true;
			chunk.partiallyUnloaded = true;
			chunk.onChunkUnload();
			chunk.isChunkLoaded = false;
			if (save || chunk.isModified) {
				safeSaveChunk(chunk);
				safeSaveExtraChunkData(chunk);
			}
			loadedChunks.remove(chunk);
			chunks.remove(key);
		}
	}

	@Deprecated
	public Chunk regenerateChunk(int x, int z) {
		unloadChunkImmediately(x, z, false);
		return getChunkAtInternal(x, z, true, true);
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
		chunk = (Chunk) chunks.getValueByKey(key);
		if (chunk == null && worldGenInProgress.get() == Boolean.TRUE) {
			chunk = (Chunk) loadingChunks.getValueByKey(key);
			if (chunk == null && inUnload.get() == Boolean.TRUE) {
				chunk = (Chunk) unloadingChunks.getValueByKey(key);
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
				runnable.run();
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

	@SuppressWarnings ("ConstantConditions")
	private Chunk getChunkAtInternal(final int x, final int z, boolean allowGenerate, boolean regenerate) {
		if (worldGenInProgress.get() == Boolean.TRUE || Thread.holdsLock(generateLock)) {
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
				chunk = (Chunk) chunks.getValueByKey(key);
				if (chunk != null) {
					return chunk;
				}
				chunk = (Chunk) loadingChunks.getValueByKey(key);
				if (chunk == null) {
					finalizeUnload(key);
					chunk = regenerate ? null : safeLoadChunk(x, z);
					if (chunk != null && (chunk.xPosition != x || chunk.zPosition != z)) {
						Log.severe("Chunk at " + chunk.xPosition + ',' + chunk.zPosition + " was stored at " + x + ',' + z + "\nResetting this chunk.");
						chunk = null;
					}
					if (chunk == null) {
						loadingChunks.add(key, defaultEmptyChunk);
						if (!allowGenerate || generator == null) {
							return defaultEmptyChunk;
						}
					} else {
						loadingChunks.add(key, chunk);
						inLoadingMap = true;
					}
				} else {
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

			synchronized (generateLock) {
				synchronized (lock) {
					chunk = (Chunk) chunks.getValueByKey(key);
					if (chunk != null) {
						return chunk;
					}
					worldGenInProgress.set(Boolean.TRUE);
					try {
						chunk = (Chunk) loadingChunks.getValueByKey(key);
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
							loadingChunks.add(key, chunk);
						}

						chunk.threadUnsafeChunkLoad();

						chunks.add(key, chunk);
						loadedChunks.add(chunk);
					} finally {
						worldGenInProgress.set(Boolean.FALSE);
					}
				}
				tryPopulateChunks(chunk);
			}
		} finally {
			if (lock.decrementAndGet() == 0) {
				loadingChunks.remove(key);
			}
		}

		chunk.onChunkLoad();
		fireBukkitLoadEvent(chunk, wasGenerated);
		chunkLoadLocks.remove(key);

		return chunk;
	}

	@Override
	@Declare
	public Chunk getChunkFastUnsafe(int x, int z) {
		return (Chunk) chunks.getValueByKey(key(x, z));
	}

	private void tryPopulateChunks(Chunk centerChunk) {
		int cX = centerChunk.xPosition;
		int cZ = centerChunk.zPosition;
		for (int x = cX - 1; x <= cX + 1; x++) {
			for (int z = cZ - 1; z <= cZ + 1; z++) {
				Chunk chunk = getChunkFastUnsafe(x, z);
				if (chunk != null && !chunk.partiallyUnloaded && !chunk.isTerrainPopulated && checkChunksExistLoadedNormally(x - 1, z - 1, x - 1, z + 1)) {
					populate(chunk);
				}
			}
		}
	}

	public boolean checkChunksExistLoadedNormally(int minX, int minZ, int maxX, int maxZ) {
		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				Chunk chunk = getChunkFastUnsafe(x, z);
				if (chunk == null || chunk.partiallyUnloaded) {
					return false;
				}
			}
		}

		return true;
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

	@Override
	protected void safeSaveChunk(Chunk chunk) {
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

	private void populate(Chunk chunk) {
		if (!Thread.holdsLock(generateLock)) {
			Log.severe("Attempted to populate chunk without locking generateLock", new ConcurrencyError("Caused by: incorrect external code"));
		}
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

	@Override
	@Declare
	public void fireBukkitPopulateEvent(Chunk chunk) {
	}

	@Override
	public boolean saveChunks(boolean fullSaveRequired, IProgressUpdate progressUpdate) {
		boolean saveAll = fullSaveRequired;
		if (saveTicks++ % 512 == 0) {
			int loadedChunks = chunks.getNumHashElements();
			if (loadedChunks > 4096) {
				DeadLockDetector.sendChatSafely("Saving world " + world.getName() + " with " + loadedChunks + " chunks, expect a short lag spike.");
			}
			saveAll = true;
		}
		int savedChunks = 0;

		List<Chunk> chunksToSave = new ArrayList<Chunk>();
		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {
				if (!chunk.partiallyUnloaded && chunk.needsSaving(saveAll)) {
					chunk.isModified = false;
					chunksToSave.add(chunk);
				}
			}
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

			safeSaveChunk(chunk);

			if (++savedChunks == maxChunksToSave && !saveAll) {
				if ((++overloadCount) > 50) {
					Log.warning("Partial save queue overloaded in " + Log.name(world) + ". You should decrease saveInterval. Only saved " + savedChunks + " out of " + chunksToSave.size());
					maxChunksToSave = (maxChunksToSave * 3) / 2;
					overloadCount -= 2;
				}
				lastChunksSaved = savedChunks;
				return true;
			}
		}

		lastChunksSaved = savedChunks;

		if (overloadCount > 0) {
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
		return "Loaded " + loadedChunks.size() + " Loading " + loadingChunks.getNumHashElements() + " Unload " + unloadStage0.size() + " UnloadSave " + unloadStage1.size() + " Locks " + chunkLoadLocks.size() + " PartialSave " + lastChunksSaved + " LCOPR " + loadChunksInProvideChunk;
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
					runnable.run();
				}
			} catch (Throwable t) {
				FMLLog.log(Level.SEVERE, t, "Exception loading chunk asynchronously.");
			}
		}
	}
}
