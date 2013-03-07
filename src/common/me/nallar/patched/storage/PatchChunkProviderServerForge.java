package me.nallar.patched.storage;

import java.util.HashMap;
import java.util.Iterator;
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

public abstract class PatchChunkProviderServerForge extends ChunkProviderServer {
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

	public PatchChunkProviderServerForge(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}

	@Override
	@Declare
	public Set<Long> getChunksToUnloadSet() {
		return chunksToUnload;
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
	public void unloadChunksIfNotNearSpawn(int x, int z) {
		long hash = ChunkCoordIntPair.chunkXZ2Int(x, z);
		//noinspection StatementWithEmptyBody
		if (loadedChunkHashMap.getValueByKey(hash) == null) {
		} else if (TickThreading.instance.shouldLoadSpawn && this.worldObj.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(worldObj.provider.dimensionId)) {
			ChunkCoordinates var3 = this.worldObj.getSpawnPoint();
			int bX = x * 16 + 8 - var3.posX;
			int bY = z * 16 + 8 - var3.posZ;
			short range = 128;

			if (bX < -range || bX > range || bY < -range || bY > range) {
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
		long key = ChunkCoordIntPair.chunkXZ2Int(x, z);
		synchronized (chunksToUnload) {
			this.chunksToUnload.remove(Long.valueOf(key));
		}
		Chunk chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(key);

		if (chunk != null) {
			return chunk;
		}

		final Object lock = getLock(x, z);
		boolean inLoadingMap = false;

		// Lock on the lock for this chunk - prevent multiple instances of the same chunk
		ThreadLocal<Boolean> worldGenInProgress = worldObj.worldGenInProgress;
		synchronized (lock) {
			chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(key);
			if (chunk != null) {
				return chunk;
			}
			chunk = (Chunk) loadingChunkHashMap.getValueByKey(key);
			if (chunk == null) {
				chunk = this.safeLoadChunk(x, z);
				if (chunk != null) {
					this.loadingChunkHashMap.add(key, chunk);
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
		try {
			synchronized (genLock) {
				synchronized (lock) {
					if (worldGenInProgress != null) {
						worldGenInProgress.set(Boolean.TRUE);
					}
					chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(key);
					if (chunk != null) {
						return chunk;
					}
					chunk = (Chunk) this.loadingChunkHashMap.getValueByKey(key);
					if (chunk == null) {
						if (this.currentChunkProvider == null) {
							chunk = this.defaultEmptyChunk;
						} else {
							try {
								chunk = this.currentChunkProvider.provideChunk(x, z);
							} catch (Throwable t) {
								Log.severe("Failed to generate a chunk in " + Log.name(worldObj) + " at chunk coords " + x + ',' + z);
								throw UnsafeUtil.throwIgnoreChecked(t);
							}
						}
					} else {
						if (this.currentChunkProvider != null) {
							this.currentChunkProvider.recreateStructures(x, z);
						}
					}

					if (chunk == null) {
						throw new IllegalStateException("Null chunk was provided for " + x + ',' + z);
					}

					if (!inLoadingMap) {
						this.loadingChunkHashMap.add(key, chunk);
					}

					chunk.populateChunk(this, this, x, z);

					this.loadingChunkHashMap.remove(key);
					this.loadedChunkHashMap.add(key, chunk);

					synchronized (loadedChunks) {
						this.loadedChunks.add(chunk);
					}
				}
			}
		} finally {
			if (worldGenInProgress != null) {
				worldGenInProgress.set(Boolean.FALSE);
			}
		}

		// TODO: Do initial mob spawning here - doing it while locked is stupid and can cause deadlocks with some bukkit plugins

		chunk.onChunkLoad();
		chunkLoadLocks.remove(hash(x, z));

		return chunk;
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
	public boolean saveChunks(boolean force, IProgressUpdate par2IProgressUpdate) {
		int savedChunks = 0;

		synchronized (loadedChunks) {
			for (Object loadedChunk : this.loadedChunks) {
				Chunk var5 = (Chunk) loadedChunk;

				if (force) {
					this.safeSaveExtraChunkData(var5);
				}

				if (var5.needsSaving(force)) {
					this.safeSaveChunk(var5);
					var5.isModified = false;

					if (++savedChunks == 24 && !force) {
						return false;
					}
				}
			}
		}

		if (force) {
			if (this.currentChunkLoader == null) {
				return true;
			}

			this.currentChunkLoader.saveExtraData();
		}

		return true;
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
