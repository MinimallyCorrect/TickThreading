package me.nallar.tickthreading.minecraft.patched;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;
import net.minecraft.world.chunk.storage.RegionFileCache;

public class PatchAnvilChunkLoader extends AnvilChunkLoader {
	private static Cache<Long, NBTTagCompound> chunkCache = CacheBuilder.newBuilder().maximumSize(TickThreading.instance.chunkCacheSize).build();

	public PatchAnvilChunkLoader(File par1File) {
		super(par1File);
	}

	public Chunk loadChunk(World par1World, int par2, int par3) throws IOException {
		NBTTagCompound var4 = null;
		ChunkCoordIntPair var5 = new ChunkCoordIntPair(par2, par3);

		synchronized (this.syncLockObject) {
			if (this.pendingAnvilChunksCoordinates.contains(var5)) {
				for (int var7 = 0; var7 < this.chunksToRemove.size(); ++var7) {
					if (((AnvilChunkLoaderPending) this.chunksToRemove.get(var7)).chunkCoordinate.equals(var5)) {
						var4 = ((AnvilChunkLoaderPending) this.chunksToRemove.get(var7)).nbtTags;
						break;
					}
				}
			}
		}

		if (var4 == null) {
			var4 = chunkCache.getIfPresent(hash(par2, par3));
		}

		if (var4 == null) {
			DataInputStream var10 = RegionFileCache.getChunkInputStream(this.chunkSaveLocation, par2, par3);

			if (var10 == null) {
				return null;
			}

			var4 = CompressedStreamTools.read(var10);
		}

		return this.checkedReadChunkFromNBT(par1World, par2, par3, var4);
	}

	@Override
	public boolean writeNextIO() {
		AnvilChunkLoaderPending var1 = null;
		Object var2 = this.syncLockObject;

		synchronized (this.syncLockObject) {
			if (this.chunksToRemove.isEmpty()) {
				return false;
			}

			var1 = (AnvilChunkLoaderPending) this.chunksToRemove.remove(0);
			this.pendingAnvilChunksCoordinates.remove(var1.chunkCoordinate);
		}

		chunkCache.put(hash(var1.chunkCoordinate.chunkXPos, var1.chunkCoordinate.chunkZPos), var1.nbtTags);

		try {
			this.writeChunkNBTTags(var1);
		} catch (Exception var4) {
			Log.severe("Failed to save a chunk!", var4);
		}

		return true;
	}

	private static long hash(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}
}
