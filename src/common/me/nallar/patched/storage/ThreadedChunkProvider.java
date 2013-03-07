package me.nallar.patched.storage;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import me.nallar.patched.annotation.FakeExtend;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
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

/**
 * This is a replacement for ChunkProviderServer
 * Instead of attempting to patch a class with many different implementations,
 * this replaces it with an implementation which is intended to be compatible
 * with both Forge and MCPC+.
 */
@FakeExtend
public abstract class ThreadedChunkProvider extends ChunkProviderServer implements IChunkProvider {
	public ThreadedChunkProvider(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider); // This call will be removed by javassist.
	}

	@Override
	public boolean unload100OldestChunks() {
		return tick();
	}

	private boolean tick() {
		if (worldObj.tickCount % 3 == 0 && !this.worldObj.canNotSave && !chunksToUnload.isEmpty()) {
			int i = 0;
			for (ChunkCoordIntPair forced : worldObj.getPersistentChunks().keySet()) {
				if (this.chunksToUnload.remove(ChunkCoordIntPair.chunkXZ2Int(forced.chunkXPos, forced.chunkZPos)) && chunksToUnload.isEmpty()) {
					i = 600;
					break;
				}
			}

			for (; i < 600; ++i) {
				long var2;
				synchronized (chunksToUnload) {
					Iterator<Long> i$ = chunksToUnload.iterator();
					if (!i$.hasNext()) {
						break;
					}
					var2 = i$.next();
					i$.remove();
				}
				Chunk var3 = (Chunk) this.loadedChunkHashMap.getValueByKey(var2);
				if (var3 != null) {
					if (lastChunk == var3) {
						lastChunk = null;
					}
					var3.onChunkUnload();
					this.safeSaveChunk(var3);
					this.safeSaveExtraChunkData(var3);
					synchronized (loadedChunks) {
						this.loadedChunks.remove(var3);
					}
				}
				this.loadedChunkHashMap.remove(var2);
			}

			if (this.currentChunkLoader != null) {
				this.currentChunkLoader.chunkTick();
			}
		}

		if (unloadTicks++ > 1200 && this.worldObj.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading && loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(worldObj).isEmpty() && (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(worldObj.provider.dimensionId))) {
			DimensionManager.unloadWorld(worldObj.provider.dimensionId);
		}

		return this.currentChunkProvider.unload100OldestChunks();
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
		return super.makeString();
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
		return super.getChunkIfExists(x, z);
	}

	private void garbageCollect() {

	}
}
