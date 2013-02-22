package me.nallar.patched;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchChunkProviderServer extends ChunkProviderServer {
	public Object genLock;
	public Object chunkLoadLock;
	public Map<Long, Object> chunkLoadLocks;
	private Chunk lastChunk;
	private net.minecraft.util.LongHashMap loadingChunkHashMap;
	private int unloadTicks;

	public void construct() {
		chunkLoadLock = new Object();
		chunkLoadLocks = new HashMap<Long, Object>();
		genLock = new Object();
		loadingChunkHashMap = new net.minecraft.util.LongHashMap();
	}

	public PatchChunkProviderServer(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int par1, int par2) {
		long hash = ChunkCoordIntPair.chunkXZ2Int(par1, par2);
		if (loadedChunkHashMap.getValueByKey(hash) == null) {
		} else if (TickThreading.instance.shouldLoadSpawn && this.worldObj.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(worldObj.provider.dimensionId)) {
			ChunkCoordinates var3 = this.worldObj.getSpawnPoint();
			int var4 = par1 * 16 + 8 - var3.posX;
			int var5 = par2 * 16 + 8 - var3.posZ;
			short var6 = 128;

			if (var4 < -var6 || var4 > var6 || var5 < -var6 || var5 > var6) {
				synchronized (chunksToUnload) {
					this.chunksToUnload.add(hash);
				}
			}
		} else {
			synchronized (chunksToUnload) {
				this.chunksToUnload.add(hash);
			}
		}
	}

	/* 100 chunks?
	 * I DO WHAT I WANT!
	 */
	@Override
	public boolean unload100OldestChunks() {
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

		if (unloadTicks++ > 200 && this.worldObj.provider.dimensionId != 0 && TickThreading.instance.allowWorldUnloading && loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(worldObj).isEmpty() && (!TickThreading.instance.shouldLoadSpawn || !DimensionManager.shouldLoadSpawn(worldObj.provider.dimensionId))) {
			DimensionManager.unloadWorld(worldObj.provider.dimensionId);
		}

		return this.currentChunkProvider.unload100OldestChunks();
	}

	@Override
	@Declare
	public Chunk getChunkIfExists(int x, int z) {
		Chunk chunk = lastChunk;
		if (chunk == null || chunk.xPosition != x || chunk.zPosition != z) {
			chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
			if (chunk != null) {
				lastChunk = chunk;
			}
		}
		return chunk;
	}

	@Override
	public Chunk provideChunk(int x, int z) {
		Chunk chunk = lastChunk;
		if (chunk == null || chunk.xPosition != x || chunk.zPosition != z) {
			chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
			chunk = (chunk == null ? (!this.loadChunkOnProvideRequest && !this.worldObj.findingSpawnPoint ? this.defaultEmptyChunk : this.loadChunk(x, z)) : chunk);
			lastChunk = chunk;
		}
		return chunk;
	}

	@Override
	protected Chunk safeLoadChunk(int x, int z) {
		if (this.currentChunkLoader == null) {
			return null;
		} else {
			try {
				Chunk chunk = this.currentChunkLoader.loadChunk(this.worldObj, x, z);

				if (chunk != null) {
					chunk.lastSaveTime = this.worldObj.getTotalWorldTime();
				}

				return chunk;
			} catch (Exception e) {
				FMLLog.log(Level.SEVERE, e, "Failed to load chunk");
				return null;
			}
		}
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		// TODO: replace most of CPS chunk-loading and related logic with Spigot-compatible variants
		long var3 = ChunkCoordIntPair.chunkXZ2Int(x, z);
		synchronized (chunksToUnload) {
			this.chunksToUnload.remove(Long.valueOf(var3));
		}
		Chunk var5 = (Chunk) this.loadedChunkHashMap.getValueByKey(var3);

		if (var5 != null) {
			return var5;
		}

		final Object lock = getLock(x, z);

		// Lock on the lock for this chunk - prevent multiple instances of the same chunk
		synchronized (lock) {
			var5 = (Chunk) this.loadedChunkHashMap.getValueByKey(var3);
			if (var5 != null) {
				return var5;
			}
			var5 = (Chunk) loadingChunkHashMap.getValueByKey(var3);
			if (var5 == null) {
				var5 = this.safeLoadChunk(x, z);
				if (var5 != null) {
					this.loadingChunkHashMap.add(var3, var5);
				}
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
		try {
			synchronized (genLock) {
				synchronized (lock) {
					if (worldObj.worldGenInProgress != null) {
						worldObj.worldGenInProgress.set(true);
					}
					var5 = (Chunk) this.loadedChunkHashMap.getValueByKey(var3);
					if (var5 != null) {
						return var5;
					}
					var5 = (Chunk) this.loadingChunkHashMap.getValueByKey(var3);
					if (var5 == null) {
						if (this.currentChunkProvider == null) {
							var5 = this.defaultEmptyChunk;
						} else {
							try {
								var5 = this.currentChunkProvider.provideChunk(x, z);
							} catch (Throwable t) {
								Log.severe("Failed to generate a chunk in " + Log.name(worldObj) + " at chunk coords " + x + ',' + z);
								UnsafeUtil.throwIgnoreChecked(t);
							}
						}
					} else {
						if (this.currentChunkProvider != null) {
							this.currentChunkProvider.recreateStructures(x, z);
						}
					}

					if (var5 == null) {
						throw new IllegalStateException("Null chunk was provided!");
					}

					this.loadingChunkHashMap.remove(var3);
					this.loadedChunkHashMap.add(var3, var5);
					synchronized (loadedChunks) {
						this.loadedChunks.add(var5);
					}

					var5.populateChunk(this, this, x, z);
				}
			}
		} finally {
			if (worldObj.worldGenInProgress != null) {
				worldObj.worldGenInProgress.set(false);
			}
		}

		// TODO: Do initial mob spawning here - doing it while locked is stupid and can cause deadlocks with some bukkit plugins

		var5.onChunkLoad();
		chunkLoadLocks.remove(hash(x, z));

		return var5;
	}

	@Override
	public void unloadAllChunks() {
		if (loadedChunks.size() == worldObj.getPersistentChunks().size()) {
			return;
		}
		synchronized (loadedChunks) {
			for (Object loadedChunk : this.loadedChunks) {
				Chunk var2 = (Chunk) loadedChunk;
				this.unloadChunksIfNotNearSpawn(var2.xPosition, var2.zPosition);
			}
		}
	}

	@Override
	public boolean saveChunks(boolean par1, IProgressUpdate par2IProgressUpdate) {
		int var3 = 0;

		synchronized (loadedChunks) {
			for (Object loadedChunk : this.loadedChunks) {
				Chunk var5 = (Chunk) loadedChunk;

				if (par1) {
					this.safeSaveExtraChunkData(var5);
				}

				if (var5.needsSaving(par1)) {
					this.safeSaveChunk(var5);
					var5.isModified = false;
					++var3;

					if (var3 == 24 && !par1) {
						return false;
					}
				}
			}
		}

		if (par1) {
			if (this.currentChunkLoader == null) {
				return true;
			}

			this.currentChunkLoader.saveExtraData();
		}

		return true;
	}

	@Override
	@Declare
	public List<Chunk> getLoadedChunks() {
		return loadedChunks;
	}

	@Override
	@Declare
	public Set<Long> getChunksToUnloadSet() {
		return chunksToUnload;
	}

	public Object getLock(int x, int z) {
		long hash = hash(x, z);
		Object lock = chunkLoadLocks.get(hash);
		if (lock == null) {
			synchronized (chunkLoadLock) {
				lock = chunkLoadLocks.get(hash);
				if (lock == null) {
					lock = new Object();
					chunkLoadLocks.put(hash, lock);
				}
			}
		}
		return lock;
	}

	private static long hash(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}
}
