package me.nallar.patched.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
import me.nallar.tickthreading.collections.NonBlockingLongSet;
import me.nallar.tickthreading.minecraft.ChunkGarbageCollector;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
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
	private final NonBlockingLongSet unloadStage0 = new NonBlockingLongSet();
	private final ConcurrentLinkedQueue<QueuedUnload> unloadStage1 = new ConcurrentLinkedQueue<QueuedUnload>();
	private final IChunkProvider generator; // Mojang shouldn't use the same interface for  :(
	private final IChunkLoader loader;
	private final WorldServer world;
	private final Chunk defaultEmptyChunk;
	private final ThreadLocal<Boolean> inUnload = new BooleanThreadLocal();
	private final boolean loadChunkIfNotFound;
	private final boolean generateChunkIfNotFound;
	private final ThreadLocal<Boolean> worldGenInProgress;
	private boolean loadedPersistentChunks = false;
	private int unloadTicks = 0;
	private int overloadCount = 0;
	private int saveTicks = 0;
	private Chunk lastChunk;
	// Mojang compatiblity fields.
	public final IChunkProvider currentChunkProvider;
	public final Set<Long> chunksToUnload = unloadStage0;
	public final List<Chunk> loadedChunks;
	public final IChunkLoader currentChunkLoader;
	public boolean loadChunkOnProvideRequest;

	public ThreadedChunkProvider(WorldServer world, IChunkLoader loader, IChunkProvider generator) {
		super(world, loader, generator); // This call will be removed by javassist.
		currentChunkProvider = this.generator = generator;
		this.world = world;
		currentChunkLoader = this.loader = loader;
		loadedChunks = Collections.synchronizedList(new ArrayList<Chunk>());
		defaultEmptyChunk = new EmptyChunk(world, 0, 0);
		loadChunkIfNotFound = TickThreading.instance.loadChunkOnProvideRequest;
		generateChunkIfNotFound = TickThreading.instance.generateChunkOnProvideRequest;
		worldGenInProgress = world.worldGenInProgress = new BooleanThreadLocal();
		world.inImmediateBlockUpdate = new BooleanThreadLocal();
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
		if (ticks % 3 == 0 && !unloadStage0.isEmpty()) {
			ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = world.getPersistentChunks();
			PlayerManager playerManager = world.getPlayerManager();
			ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(0, 0);
			NonBlockingHashMapLong.IteratorLong i$ = unloadStage0.iteratorLong();
			while (i$.hasNext()) {
				long key = i$.nextLong();
				i$.remove();
				if (key == 0) {
					// Iterator can't remove 0 key for some reason.
					unloadStage0.remove(0L);
				}
				int x = (int) key;
				int z = (int) (key >> 32);
				chunkCoordIntPair.chunkXPos = x;
				chunkCoordIntPair.chunkZPos = z;
				if (persistentChunks.containsKey(chunkCoordIntPair) || unloadingChunks.containsItem(key) || playerManager.getOrCreateChunkWatcher(x, z, false) != null) {
					continue;
				}
				Chunk chunk = (Chunk) chunks.getValueByKey(key);
				if (chunk == null || chunk.unloading || !fireBukkitUnloadEvent(chunk)) {
					continue;
				}
				if (lastChunk == chunk) {
					lastChunk = null;
				}
				chunk.onChunkUnload();
				chunk.pendingBlockUpdates = world.getPendingBlockUpdates(chunk, false);
				loadedChunks.remove(chunk);
				chunks.remove(key);
				chunk.unloading = true;
				synchronized (unloadingChunks) {
					unloadingChunks.add(key, chunk);
					unloadStage1.add(new QueuedUnload(key, ticks));
				}
			}

			if (loader != null) {
				loader.chunkTick();
			}
		}

		//handleUnloadQueue(ticks, true);
		handleUnloadQueue(ticks - 3);

		if (this.unloadTicks++ > 1200 && world.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading
				&& loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(world).isEmpty()
				&& (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(world.provider.dimensionId))) {
			DimensionManager.unloadWorld(world.provider.dimensionId);
		}

		if (ticks % TickThreading.instance.chunkGCInterval == 0) {
			ChunkGarbageCollector.garbageCollect(world);
		}

		if (!loadChunkIfNotFound && !loadedPersistentChunks && unloadTicks >= 5) {
			loadedPersistentChunks = true;
			int loaded = 0;
			int possible = world.getPersistentChunks().size();
			for (ChunkCoordIntPair chunkCoordIntPair : world.getPersistentChunks().keySet()) {
				if (getChunkAt(chunkCoordIntPair.chunkXPos, chunkCoordIntPair.chunkZPos, doNothingRunnable) == null) {
					loaded++;
				}
				possible++;
			}
			if (possible > 0) {
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
			if (!chunk.unloading) {
				return false;
			}
			synchronized (chunk) {
				if (chunk.alreadySavedAfterUnload) {
					Log.severe("Chunk save may have failed for " + key + ": " + (int) key + ',' + (int) (key >> 32));
					return false;
				}
				chunk.alreadySavedAfterUnload = true;
				boolean notInUnload = !inUnload.get();
				if (notInUnload) {
					inUnload.set(true);
				}
				boolean notWorldGen = !worldGenInProgress.get();
				if (notWorldGen) {
					worldGenInProgress.set(true);
				}
				chunk.isChunkLoaded = false;
				safeSaveChunk(chunk);
				safeSaveExtraChunkData(chunk);
				if (notWorldGen) {
					worldGenInProgress.set(false);
				}
				if (notInUnload) {
					inUnload.set(false);
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
	public boolean unloadChunk(int x, int z) {
		long hash = key(x, z);
		return chunks.containsItem(hash) && unloadStage0.add(hash);
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
		chunk.unloading = true;
		chunk.onChunkUnload();
		chunk.isChunkLoaded = false;
		if (save || chunk.isModified) {
			safeSaveChunk(chunk);
			safeSaveExtraChunkData(chunk);
		}
		loadedChunks.remove(chunk);
		chunks.remove(key);
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

		if (loadChunkIfNotFound || loadChunkOnProvideRequest || worldGenInProgress.get() == Boolean.TRUE) {
			return getChunkAtInternal(x, z, generateChunkIfNotFound || loadChunkOnProvideRequest, false);
		}

		return defaultEmptyChunk;
	}

	@Override
	public final Chunk loadChunk(int x, int z) {
		return getChunkAt(x, z, true, false, null);
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
			chunkLoadThreadPool.execute(new ChunkLoadRunnable(x, z, allowGenerate, regenerate, runnable, this));
			return null;
		}
		return getChunkAtInternal(x, z, allowGenerate, regenerate);
	}

	@SuppressWarnings ("ConstantConditions")
	private Chunk getChunkAtInternal(final int x, final int z, boolean allowGenerate, boolean regenerate) {
		long key = key(x, z);
		Chunk chunk;
		if (worldGenInProgress.get() == Boolean.TRUE) {
			chunk = (Chunk) loadingChunks.getValueByKey(key);
			if (chunk != null) {
				return chunk;
			}
			if (inUnload.get() == Boolean.TRUE) {
				chunk = (Chunk) unloadingChunks.getValueByKey(key);
				if (chunk == null) {
					Log.severe("Failed to get currently unloading chunk at " + x + ',' + z + " from unloading chunks map.", new IllegalStateException());
					return null;
				} else {
					return chunk;
				}
			}
			return defaultEmptyChunk;
		}

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
						if (!allowGenerate) {
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
			// TODO: Possibly make ChunkProviderGenerate threadlocal? Would need many changes to
			// structure code to get it to work properly.

			synchronized (generateLock) {
				synchronized (lock) {
					chunk = (Chunk) chunks.getValueByKey(key);
					if (chunk != null) {
						return chunk;
					}
					try {
						worldGenInProgress.set(Boolean.TRUE);
						chunk = (Chunk) loadingChunks.getValueByKey(key);
						if (chunk == null || chunk == defaultEmptyChunk) {
							if (generator == null) {
								chunk = defaultEmptyChunk;
							} else {
								try {
									chunk = generator.provideChunk(x, z);
									wasGenerated = true;
								} catch (Throwable t) {
									Log.severe("Failed to generate a chunk in " + Log.name(world) + " at chunk coords " + x + ',' + z);
									throw UnsafeUtil.throwIgnoreChecked(t);
								}
							}
						} else {
							if (generator != null) {
								generator.recreateStructures(x, z);
							}
						}

						if (chunk == null) {
							throw new IllegalStateException("Null chunk was provided for " + x + ',' + z);
						}

						if (!inLoadingMap) {
							loadingChunks.add(key, chunk);
						}

						chunk.threadUnsafeChunkLoad();

						chunk.populateChunk(this, this, x, z);

						chunks.add(key, chunk);
						loadedChunks.add(chunk);
					} finally {
						worldGenInProgress.set(Boolean.FALSE);
					}
				}
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

	@Override
	public void populate(IChunkProvider chunkProvider, int x, int z) {
		if (!Thread.holdsLock(generateLock)) {
			Log.severe("Attempted to populate chunk without locking generateLock", new ConcurrencyError("Caused by: incorrect external code"));
		}
		Chunk chunk = getChunkIfExists(x, z);
		if (chunk == null) {
			return;
		}
		synchronized (chunk) {
			if (chunk.isTerrainPopulated) {
				Log.warning("Attempted to populate chunk " + x + ',' + z + " which is already populated.");
				return;
			}
			if (generator != null) {
				generator.populate(chunkProvider, x, z);
				GameRegistry.generateWorld(x, z, world, generator, chunkProvider);
				chunk.setChunkModified();
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
		boolean saveAll = fullSaveRequired || saveTicks++ % 512 == 0;
		int savedChunks = 0;

		List<Chunk> chunksToSave = new ArrayList<Chunk>();
		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {
				if (!chunk.unloading && chunk.needsSaving(saveAll)) {
					chunk.isModified = false;
					chunksToSave.add(chunk);
				}
			}
		}

		for (Chunk chunk : chunksToSave) {
			if (chunk.unloading) {
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

			if (++savedChunks == 256 && !saveAll) {
				if ((++overloadCount) > 50) {
					Log.warning("Partial save queue overloaded in " + Log.name(world) + ". You should decrease saveInterval. Only saved " + savedChunks + " out of " + chunksToSave.size());
				}
				return true;
			}
		}

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
		return "Loaded " + loadedChunks.size() + " Loading " + loadingChunks.getNumHashElements() + " Unload " + unloadStage0.size() + " UnloadSave " + unloadStage1.size() + " Locks " + chunkLoadLocks.size();
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
				Chunk ch = provider.getChunkAt(x, z, allowGenerate, regenerate, null);
				if (ch == null) {
					FMLLog.warning("Failed to load chunk at " + x + ',' + z + " asynchronously.");
				} else {
					runnable.run();
				}
			} catch (Throwable t) {
				FMLLog.log(Level.SEVERE, t, "Exception loading chunk asynchronously.");
			}
		}
	}

	public static class BooleanThreadLocal extends ThreadLocal<Boolean> {
		@Override
		public Boolean initialValue() {
			return false;
		}
	}
}
