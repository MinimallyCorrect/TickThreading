package me.nallar.tickthreading.minecraft.patched;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchChunkProviderServer extends ChunkProviderServer {
	public static Object genLock;
	public Object chunkLoadLock;
	public Map<Long, Object> chunkLoadLocks;

	public void construct() {
		chunkLoadLock = new Object();
		chunkLoadLocks = new HashMap<Long, Object>();
	}

	public static void staticConstruct() {
		genLock = new Object();
	}

	public PatchChunkProviderServer(WorldServer par1WorldServer, IChunkLoader par2IChunkLoader, IChunkProvider par3IChunkProvider) {
		super(par1WorldServer, par2IChunkLoader, par3IChunkProvider);
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int par1, int par2) {
		long hash = ChunkCoordIntPair.chunkXZ2Int(par1, par2);
		if (loadedChunkHashMap.getValueByKey(hash) == null) {
		} else if (TickThreading.instance.shouldLoadSpawn && this.currentServer.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(currentServer.provider.dimensionId)) {
			ChunkCoordinates var3 = this.currentServer.getSpawnPoint();
			int var4 = par1 * 16 + 8 - var3.posX;
			int var5 = par2 * 16 + 8 - var3.posZ;
			short var6 = 128;

			if (var4 < -var6 || var4 > var6 || var5 < -var6 || var5 > var6) {
				this.chunksToUnload.add(hash);
			}
		} else {
			this.chunksToUnload.add(hash);
		}
	}

	/* 100 chunks?
	 * I DO WHAT I WANT!
	 */
	@Override
	public boolean unload100OldestChunks() {
		if (!this.currentServer.canNotSave) {
			for (ChunkCoordIntPair forced : currentServer.getPersistentChunks().keySet()) {
				this.chunksToUnload.remove(ChunkCoordIntPair.chunkXZ2Int(forced.chunkXPos, forced.chunkZPos));
			}

			Iterator<Long> i$ = chunksToUnload.iterator();
			for (int i = 0; i < 200 && i$.hasNext(); ++i) {
				Long var2 = i$.next();
				Chunk var3 = (Chunk) this.loadedChunkHashMap.getValueByKey(var2);
				if (var3 != null) {
					this.safeSaveChunk(var3);
					this.safeSaveExtraChunkData(var3);
					var3.onChunkUnload();
					synchronized (loadedChunks) {
						this.loadedChunks.remove(var3);
					}
				}
				i$.remove();
				this.loadedChunkHashMap.remove(var2);
				if (loadedChunks.isEmpty() && ForgeChunkManager.getPersistentChunksFor(currentServer).isEmpty() && !DimensionManager.shouldLoadSpawn(currentServer.provider.dimensionId)) {
					DimensionManager.unloadWorld(currentServer.provider.dimensionId);
					return currentChunkProvider.unload100OldestChunks();
				}
			}

			if (this.currentChunkLoader != null) {
				this.currentChunkLoader.chunkTick();
			}
		}

		return this.currentChunkProvider.unload100OldestChunks();
	}

	@Override
	protected Chunk safeLoadChunk(int x, int z) {
		if (this.currentChunkLoader == null) {
			return null;
		} else {
			try {
				Chunk chunk = this.currentChunkLoader.loadChunk(this.currentServer, x, z);

				if (chunk != null) {
					chunk.lastSaveTime = this.currentServer.getTotalWorldTime();

					if (this.currentChunkProvider != null) {
						synchronized (genLock) {
							this.currentChunkProvider.recreateStructures(x, z);
						}
					}
				}

				return chunk;
			} catch (Exception e) {
				FMLLog.severe("Failed to load chunk", e);
				return null;
			}
		}
	}

	@Override
	public Chunk loadChunk(int x, int z) {
		long var3 = ChunkCoordIntPair.chunkXZ2Int(x, z);
		this.chunksToUnload.remove(Long.valueOf(var3));
		Chunk var5 = (Chunk) this.loadedChunkHashMap.getValueByKey(var3);

		if (var5 != null) {
			return var5;
		}

		synchronized (getLock(x, z)) {
			var5 = (Chunk) this.loadedChunkHashMap.getValueByKey(var3);
			if (var5 != null) {
				return var5;
			}
			var5 = this.safeLoadChunk(x, z);
			if (var5 == null) {
				if (this.currentChunkProvider == null) {
					var5 = this.defaultEmptyChunk;
				} else {
					try {
						synchronized (genLock) {
							var5 = this.currentChunkProvider.provideChunk(x, z);
						}
					} catch (Throwable var9) {
						CrashReport var7 = CrashReport.makeCrashReport(var9, "Exception generating new chunk");
						CrashReportCategory var8 = var7.makeCategory("Chunk to be generated");
						var8.addCrashSection("Location", String.format("%d,%d", x, z));
						var8.addCrashSection("Position hash", var3);
						var8.addCrashSection("Generator", this.currentChunkProvider.makeString());
						throw new ReportedException(var7);
					}
				}
			}

			this.loadedChunkHashMap.add(var3, var5);
			synchronized (loadedChunks) {
				this.loadedChunks.add(var5);
			}

			if (var5 == null) {
				throw new IllegalStateException("Null chunk was provided!");
			}

			var5.onChunkLoad();
			var5.populateChunk(this, this, x, z);
		}

		chunkLoadLocks.remove(hash(x, z));

		return var5;
	}

	@Override
	public void unloadAllChunks() {
		if (loadedChunks.size() == currentServer.getPersistentChunks().size()) {
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
