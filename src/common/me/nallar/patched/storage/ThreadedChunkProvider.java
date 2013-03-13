package me.nallar.patched.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.google.common.collect.ImmutableSetMultimap;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameRegistry;
import me.nallar.patched.annotation.FakeExtend;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.NonBlockingLongSet;
import me.nallar.tickthreading.minecraft.ChunkGarbageCollector;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.FakeServerThread;
import me.nallar.tickthreading.util.concurrent.NativeMutex;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
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
		int p = Runtime.getRuntime().availableProcessors();
		chunkLoadThreadPool = new ThreadPoolExecutor(1, p, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(p * 10), new ServerThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
		chunkLoadThreadPool.allowCoreThreadTimeOut(true);
	}

	private final NonBlockingHashMapLong<Object> chunkLoadLocks = new NonBlockingHashMapLong<Object>();
	private final LongHashMap chunks = new LongHashMap();
	private final LongHashMap loadingChunks = new LongHashMap();
	private final LongHashMap unloadingChunks = new LongHashMap();
	private final NonBlockingLongSet unloadStage0 = new NonBlockingLongSet();
	private final ConcurrentLinkedQueue<QueuedUnload> unloadStage1 = new ConcurrentLinkedQueue<QueuedUnload>();
	private final IChunkProvider generator; // Mojang shouldn't use the same interface for  :(
	private final IChunkLoader loader;
	private final WorldServer world;
	private int ticks = 0;
	private Chunk lastChunk;
	// Mojang compatiblity fields.
	public final Set<Long> chunksToUnload = unloadStage0;
	public final List<Chunk> loadedChunks;
	public final IChunkLoader currentChunkLoader;
	@SuppressWarnings ("UnusedDeclaration")
	public boolean loadChunkOnProvideRequest = true;

	public ThreadedChunkProvider(WorldServer world, IChunkLoader loader, IChunkProvider generator) {
		super(world, loader, generator); // This call will be removed by javassist.
		this.generator = generator;
		this.world = world;
		currentChunkLoader = this.loader = loader;
		loadedChunks = Collections.synchronizedList(new ArrayList<Chunk>());
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
		tick();

		return generator.unload100OldestChunks();
	}

	@SuppressWarnings ({"ConstantConditions", "FieldRepeatedlyAccessedInMethod"})
	private void tick() {
		int ticks = this.ticks++;
		// Handle unload requests
		if (world.tickCount % 3 == 0 && !world.canNotSave && !unloadStage0.isEmpty()) {
			ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = world.getPersistentChunks();
			ChunkCoordIntPair chunkCoordIntPair = new ChunkCoordIntPair(0, 0);
			NonBlockingHashMapLong.IteratorLong i$ = unloadStage0.iteratorLong();
			while (i$.hasNext()) {
				long key = i$.next();
				i$.remove();
				int x = (int) key;
				int z = (int) (key >> 32);
				chunkCoordIntPair.chunkXPos = x;
				chunkCoordIntPair.chunkZPos = z;
				if (persistentChunks.containsKey(chunkCoordIntPair) || unloadingChunks.containsItem(key)) {
					continue;
				}
				Chunk chunk = (Chunk) chunks.getValueByKey(key);
				if (chunk == null || chunk.unloaded) {
					continue;
				}
				if (lastChunk == chunk) {
					lastChunk = null;
				}
				chunk.onChunkUnload();
				chunk.unloading = true;
				synchronized (unloadingChunks) {
					unloadingChunks.add(key, chunk);
					unloadStage1.add(new QueuedUnload(chunk, key, ticks));
				}
			}

			if (loader != null) {
				loader.chunkTick();
			}
		}

		long queueThreshold = ticks - 15;
		// Handle unloading stage 1
		{
			QueuedUnload queuedUnload = unloadStage1.peek();
			while (queuedUnload != null && queuedUnload.ticks <= queueThreshold) {
				Chunk chunk = queuedUnload.chunk;
				long chunkHash = queuedUnload.key;
				synchronized (unloadingChunks) {
					if (!unloadStage1.remove(queuedUnload) || unloadingChunks.remove(chunkHash) != chunk) {
						queuedUnload = unloadStage1.peek();
						continue;
					}
					chunk.unloaded = true;
				}
				finalizeUnload(chunk, chunkHash);
				queuedUnload = unloadStage1.peek();
			}
		}

		if (ticks > 1200 && world.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading
				&& loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(world).isEmpty()
				&& (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(world.provider.dimensionId))) {
			DimensionManager.unloadWorld(world.provider.dimensionId);
		}

		if (ticks % TickThreading.instance.chunkGCInterval == 0) {
			ChunkGarbageCollector.garbageCollect(world);
		}
	}

	private void finalizeUnload(Chunk chunk, long chunkHash) {
		if (!chunk.unloading) {
			throw new IllegalArgumentException("Chunk " + chunk + " is not unloading.");
		}
		if (chunk.alreadySavedAfterUnload) {
			return;
		}
		chunk.alreadySavedAfterUnload = true;
		safeSaveChunk(chunk);
		safeSaveExtraChunkData(chunk);
		loadedChunks.remove(chunk);
		chunks.remove(chunkHash);
	}

	// Public visibility as it will be accessed from net.minecraft.whatever, not actually this class
	// (Inner classes are not remapped in patching)
	public static class QueuedUnload implements Comparable<QueuedUnload> {
		public final int ticks;
		public final long key;
		public final Chunk chunk;

		public QueuedUnload(Chunk chunk, long key, int ticks) {
			this.chunk = chunk;
			this.key = key;
			this.ticks = ticks;
		}

		@Override
		public int compareTo(QueuedUnload o) {
			long t1 = o.ticks;
			long t2 = ticks;
			return t1 == t2 ? 0 : (t1 < t2 ? -1 : 1);
		}
	}

	@Override
	public boolean chunkExists(int x, int z) {
		return chunks.containsItem(key(x, z));
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int x, int z) {
		unloadStage0.add(key(x, z));
	}

	@Override
	public void unloadAllChunks() {
		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {
				unloadStage0.add(key(chunk.xPosition, chunk.zPosition));
			}
		}
	}

	public Object getLock(int x, int z) {
		long hash = key(x, z);
		Object lock = chunkLoadLocks.get(hash);
		if (lock != null) {
			return lock;
		}
		Object newLock = new Object();
		lock = chunkLoadLocks.putIfAbsent(hash, newLock);
		if (lock != null) {
			return lock;
		}
		return newLock;
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		return getChunkAt(x, z, null);
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		return getChunkAt(x, z, null);
	}

	@Override
	@SuppressWarnings ("ConstantConditions")
	@Declare
	public Chunk getChunkAt(final int x, final int z, final Runnable runnable) {
		Chunk chunk = getChunkIfExists(x, z);

		if (chunk != null) {
			if (runnable != null) {
				runnable.run();
			}
			return chunk;
		}

		if (runnable != null) {
			chunkLoadThreadPool.execute(new ChunkLoadRunnable(x, z, runnable, this));
			return null;
		}

		long key = key(x, z);

		final Object lock = getLock(x, z);
		boolean inLoadingMap = false;

		// Lock on the lock for this chunk - prevent multiple instances of the same chunk
		ThreadLocal<Boolean> worldGenInProgress = world.worldGenInProgress;
		synchronized (lock) {
			synchronized (unloadingChunks) {
				chunk = (Chunk) unloadingChunks.remove(key);
				if (chunk != null) {
					chunk.unloaded = true;
				}
			}
			if (chunk != null) {
				finalizeUnload(chunk, key);
			}
			chunk = (Chunk) chunks.getValueByKey(key);
			if (chunk != null) {
				return chunk;
			}
			chunk = (Chunk) loadingChunks.getValueByKey(key);
			if (chunk == null) {
				chunk = safeLoadChunk(x, z);
				if (chunk != null) {
					loadingChunks.add(key, chunk);
					inLoadingMap = true;
				}
			} else if (worldGenInProgress != null && worldGenInProgress.get() == Boolean.TRUE) {
				return chunk;
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
		boolean innerGenerate = false;
		try {
			synchronized (generateLock) {
				synchronized (lock) {
					if (worldGenInProgress != null) {
						if (!(innerGenerate = (worldGenInProgress.get() == Boolean.TRUE))) {
							worldGenInProgress.set(Boolean.TRUE);
						}
					}
					chunk = (Chunk) chunks.getValueByKey(key);
					if (chunk != null) {
						return chunk;
					}
					chunk = (Chunk) loadingChunks.getValueByKey(key);
					if (chunk == null) {
						if (generator == null) {
							chunk = defaultEmptyChunk;
						} else {
							try {
								chunk = generator.provideChunk(x, z);
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

					chunk.populateChunk(this, this, x, z);

					loadingChunks.remove(key);
					chunks.add(key, chunk);
					loadedChunks.add(chunk);
				}
			}
		} finally {
			if (!innerGenerate && worldGenInProgress != null) {
				worldGenInProgress.set(Boolean.FALSE);
			}
		}

		// TODO: Do initial mob spawning here - doing it while locked is stupid and can cause deadlocks with some bukkit plugins

		chunk.onChunkLoad();
		chunkLoadLocks.remove(key);

		return chunk;
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

				if (generator != null) {
					generator.recreateStructures(x, z);
				}
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
		synchronized (generateLock) {
			Chunk var4 = provideChunk(x, z);

			if (!var4.isTerrainPopulated) {
				var4.isTerrainPopulated = true;

				if (generator != null) {
					generator.populate(chunkProvider, x, z);
					GameRegistry.generateWorld(x, z, world, generator, chunkProvider);
					var4.setChunkModified();
				}
			}
		}
	}

	@Override
	public boolean saveChunks(boolean saveAll, IProgressUpdate progressUpdate) {
		int savedChunks = 0;

		synchronized (loadedChunks) {
			for (Chunk chunk : loadedChunks) {

				if (chunk.unloading) {
					if (saveAll) {
						chunk.alreadySavedAfterUnload = true;
					} else {
						continue;
					}
				}

				if (saveAll) {
					safeSaveExtraChunkData(chunk);
				}

				if (chunk.needsSaving(saveAll)) {
					safeSaveChunk(chunk);
					chunk.isModified = false;

					if (++savedChunks == 24 && !saveAll) {
						return false;
					}
				}
			}
		}

		if (saveAll && loader != null) {
			loader.saveExtraData();
		}

		return true;
	}

	@Override
	public boolean canSave() {
		return !world.canNotSave;
	}

	@Override
	public String makeString() {
		return "Loaded " + loadedChunks.size() + " Unload0 " + unloadStage0.size() + " Unload1 " + unloadStage1.size();
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
	public Chunk getChunkIfExists(int x, int z) {
		Chunk chunk = lastChunk;
		if (chunk != null && chunk.xPosition == x && chunk.zPosition == z) {
			return chunk;
		}
		chunk = (Chunk) chunks.getValueByKey(key(x, z));
		if (chunk == null) {
			return null;
		}
		lastChunk = chunk;
		return chunk;
	}

	private static long key(int x, int z) {
		return (((long) z) << 32) | (x & 0xffffffffL);
	}

	public static class ChunkLoadRunnable implements Runnable {
		private final int x;
		private final int z;
		private final Runnable runnable;
		private final ChunkProviderServer provider;

		public ChunkLoadRunnable(int x, int z, Runnable runnable, ChunkProviderServer provider) {
			this.x = x;
			this.z = z;
			this.runnable = runnable;
			this.provider = provider;
		}

		@Override
		public void run() {
			try {
				Chunk ch = provider.getChunkAt(x, z, null);
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

	public static class ServerThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable r) {
			return new FakeServerThread(r, "Async ChunkLoader", true);
		}
	}
}
