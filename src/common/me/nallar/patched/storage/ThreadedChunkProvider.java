package me.nallar.patched.storage;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.nallar.patched.annotation.FakeExtend;
import me.nallar.patched.collection.NonBlockingLongSet;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
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
	private final LongHashMap chunkLoadLocks = new LongHashMap();
	private final NonBlockingHashMapLong<Chunk> chunks = new NonBlockingHashMapLong<Chunk>();
	private final NonBlockingHashMapLong<Chunk> unloadingChunks = new NonBlockingHashMapLong<Chunk>();
	private final NonBlockingLongSet unloadStage0 = new NonBlockingLongSet();
	private final ConcurrentLinkedQueue<QueuedUnload> unloadStage1 = new ConcurrentLinkedQueue<QueuedUnload>();
	private final IChunkProvider chunkGenerator; // Mojang shouldn't use the same interface for this. :(
	private final WorldServer world;
	private int ticks = 0;
	public final Set<Long> chunksToUnloadSet = new NonBlockingLongSet();
	private Chunk lastChunk;

	public ThreadedChunkProvider(WorldServer worldServer, IChunkLoader chunkLoader, IChunkProvider chunkGenerator) {
		super(worldServer, chunkLoader, chunkGenerator); // This call will be removed by javassist.
		this.chunkGenerator = chunkGenerator;
		world = worldServer;
	}

	@Override
	public boolean unload100OldestChunks() {
		return tick();
	}

	private boolean tick() {
		boolean empty = false;
		int ticks = this.ticks++;
		// Handle unload requests
		if (world.tickCount % 3 == 0 && !world.canNotSave && !unloadStage0.isEmpty()) {
			for (ChunkCoordIntPair forced : world.getPersistentChunks().keySet()) {
				if (unloadStage0.remove(hash(forced.chunkXPos, forced.chunkZPos)) && unloadStage0.isEmpty()) {
					empty = true;
					break;
				}
			}

			if (!empty) {
				NonBlockingHashMapLong.IteratorLong i$ = unloadStage0.iteratorLong();
				while (i$.hasNext()) {
					long chunkHash = i$.next();
					i$.remove();
					Chunk chunk = chunks.get(chunkHash);
					if (chunk == null) {
						continue;
					}
					unloadingChunks.put(chunkHash, chunk);
					unloadStage1.add(new QueuedUnload(chunkHash, ticks));
					chunks.remove(chunkHash);
				}

				if (this.currentChunkLoader != null) {
					this.currentChunkLoader.chunkTick();
				}
			}
		}

		long queueThreshold = ticks - 30;
		// Handle unloading stage 1
		{
			QueuedUnload queuedUnload = unloadStage1.peek();
			while (queuedUnload != null && queuedUnload.ticks <= queueThreshold) {
				Chunk chunk = unloadingChunks.remove(queuedUnload.chunkHash);
				if (chunk == null || !unloadStage1.remove(queuedUnload)) {
					continue;
				}
				if (lastChunk == chunk) {
					lastChunk = null;
				}
				chunk.onChunkUnload();
				this.safeSaveChunk(chunk);
				this.safeSaveExtraChunkData(chunk);
				synchronized (loadedChunks) {
					this.loadedChunks.remove(chunk);
				}
				queuedUnload = unloadStage1.peek();
			}
		}

		if (ticks > 1200 && this.world.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading && loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(world).isEmpty() && (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(world.provider.dimensionId))) {
			DimensionManager.unloadWorld(world.provider.dimensionId);
		}

		return this.currentChunkProvider.unload100OldestChunks();
	}

	// Public visibility as it will be accessed from net.minecraft.whatever, not actually this class
	public static class QueuedUnload implements Comparable<QueuedUnload> {
		public final int ticks;
		public final long chunkHash;

		public QueuedUnload(long chunkHash, int ticks) {
			this.chunkHash = chunkHash;
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
		return super.chunkExists(x, z);
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int x, int z) {
		super.unloadChunksIfNotNearSpawn(x, z);
	}

	@Override
	public void unloadAllChunks() {
		super.unloadAllChunks();
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		return super.loadChunk(x, z);
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		return super.provideChunk(x, z);
	}

	@Override
	protected Chunk safeLoadChunk(int x, int z) {
		return super.safeLoadChunk(x, z);
	}

	@Override
	protected void safeSaveExtraChunkData(Chunk chunk) {
		super.safeSaveExtraChunkData(chunk);
	}

	@Override
	protected void safeSaveChunk(Chunk chunk) {
		super.safeSaveChunk(chunk);
	}

	@Override
	public void populate(IChunkProvider chunkProvider, int x, int z) {
		super.populate(chunkProvider, x, z);
	}

	@Override
	public boolean saveChunks(boolean saveAll, IProgressUpdate progressUpdate) {
		return super.saveChunks(saveAll, progressUpdate);
	}

	@Override
	public boolean canSave() {
		return super.canSave();
	}

	@Override
	public String makeString() {
		return "Loaded " + chunks.size() + " Unload0 " + unloadStage0.size() + " Unload1 " + unloadStage1.size();
	}

	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		return super.getPossibleCreatures(creatureType, x, y, z);
	}

	@Override
	public ChunkPosition findClosestStructure(World world, String name, int x, int y, int z) {
		return super.findClosestStructure(world, name, x, y, z);
	}

	@Override
	public int getLoadedChunkCount() {
		return super.getLoadedChunkCount();
	}

	@Override
	public void recreateStructures(int x, int z) {
		super.recreateStructures(x, z);
	}

	@Override
	public List<Chunk> getLoadedChunks() {
		return super.getLoadedChunks();
	}

	@Override
	@Declare
	public Chunk getChunkIfExists(int x, int z) {
		return chunks.get(hash(x, z));
	}

	private void garbageCollect() {

	}

	private static long hash(int x, int z)
	{
		return (long)x & 4294967295L | ((long)z & 4294967295L) << 32;
	}
}
