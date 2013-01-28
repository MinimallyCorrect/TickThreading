package me.nallar.tickthreading.minecraft.patched;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.RegionFileCache;

public abstract class PatchAnvilChunkLoader extends AnvilChunkLoader {
	private Cache<Long, NBTTagCompound> chunkCache;

	public void construct() {
		chunkCache = CacheBuilder.newBuilder().maximumSize(TickThreading.instance.chunkCacheSize).build();
	}

	public PatchAnvilChunkLoader(File par1File) {
		super(par1File);
	}

	@Override
	public Chunk loadChunk(World par1World, int par2, int par3) throws IOException {
		NBTTagCompound var4 = null;
		ChunkCoordIntPair var5 = new ChunkCoordIntPair(par2, par3);

		synchronized (this.syncLockObject) {
			if (this.pendingAnvilChunksCoordinates.contains(var5)) {
				for (Object aChunksToRemove : this.chunksToRemove) {
					if (((AnvilChunkLoaderPending) aChunksToRemove).chunkCoordinate.equals(var5)) {
						var4 = ((AnvilChunkLoaderPending) aChunksToRemove).nbtTags;
						break;
					}
				}
			}
		}

		if (var4 == null) {
			long cacheHash = hash(par2, par3);
			var4 = chunkCache.getIfPresent(cacheHash);

			if (var4 == null) {
				DataInputStream var10 = RegionFileCache.getChunkInputStream(this.chunkSaveLocation, par2, par3);

				if (var10 == null) {
					return null;
				}

				var4 = CompressedStreamTools.read(var10);
			} else {
				chunkCache.invalidate(cacheHash);
			}
		}

		return this.checkedReadChunkFromNBT(par1World, par2, par3, var4);
	}

	@Override
	public boolean writeNextIO() {
		AnvilChunkLoaderPending var1;

		synchronized (this.syncLockObject) {
			if (this.chunksToRemove.isEmpty()) {
				return false;
			}

			var1 = (AnvilChunkLoaderPending) this.chunksToRemove.remove(0);
			this.pendingAnvilChunksCoordinates.remove(var1.chunkCoordinate);
		}

		try {
			this.writeChunkNBTTags(var1);
		} catch (Exception var4) {
			Log.severe("Failed to save a chunk, trying again.", var4);
			try {
				this.writeChunkNBTTags(var1);
			} catch (Exception e) {
				Log.severe("Completely failed to save a chunk! :(", e);
			}
		}

		chunkCache.put(hash(var1.chunkCoordinate.chunkXPos, var1.chunkCoordinate.chunkZPos), var1.nbtTags);

		return true;
	}

	@Override
	protected void writeChunkToNBT(Chunk par1Chunk, World par2World, NBTTagCompound par3NBTTagCompound) {
		par3NBTTagCompound.setInteger("xPos", par1Chunk.xPosition);
		par3NBTTagCompound.setInteger("zPos", par1Chunk.zPosition);
		par3NBTTagCompound.setLong("LastUpdate", par2World.getTotalWorldTime());
		par3NBTTagCompound.setIntArray("HeightMap", par1Chunk.heightMap);
		par3NBTTagCompound.setBoolean("TerrainPopulated", par1Chunk.isTerrainPopulated);
		ExtendedBlockStorage[] var4 = par1Chunk.getBlockStorageArray();
		NBTTagList var5 = new NBTTagList("Sections");
		boolean var6 = !par2World.provider.hasNoSky;
		int var8 = var4.length;
		NBTTagCompound var11;

		for (int var9 = 0; var9 < var8; ++var9) {
			ExtendedBlockStorage var10 = var4[var9];

			if (var10 != null) {
				var11 = new NBTTagCompound();
				var11.setByte("Y", (byte) (var10.getYLocation() >> 4 & 255));
				var11.setByteArray("Blocks", var10.getBlockLSBArray());

				if (var10.getBlockMSBArray() != null) {
					var11.setByteArray("Add", var10.getBlockMSBArray().data);
				}

				var11.setByteArray("Data", var10.getMetadataArray().data);
				var11.setByteArray("BlockLight", var10.getBlocklightArray().data);

				if (var6) {
					var11.setByteArray("SkyLight", var10.getSkylightArray().data);
				} else {
					var11.setByteArray("SkyLight", new byte[var10.getBlocklightArray().data.length]);
				}

				var5.appendTag(var11);
			}
		}

		par3NBTTagCompound.setTag("Sections", var5);
		par3NBTTagCompound.setByteArray("Biomes", par1Chunk.getBiomeArray());
		par1Chunk.hasEntities = false;
		NBTTagList var16 = new NBTTagList();
		Iterator var18;

		for (var8 = 0; var8 < par1Chunk.entityLists.length; ++var8) {
			var18 = par1Chunk.entityLists[var8].iterator();

			while (var18.hasNext()) {
				Entity var21 = (Entity) var18.next();
				par1Chunk.hasEntities = true;
				var11 = new NBTTagCompound();

				try {
					if (var21.addEntityID(var11)) {
						var16.appendTag(var11);
					}
				} catch (Exception e) {
					FMLLog.log(Level.SEVERE, e,
							"An Entity type %s has thrown an exception trying to write state. It will not persist. Report this to the mod author",
							var21.toString());
				}
			}
		}

		par3NBTTagCompound.setTag("Entities", var16);
		NBTTagList var17 = new NBTTagList();
		var18 = par1Chunk.chunkTileEntityMap.values().iterator();

		while (var18.hasNext()) {
			TileEntity var22 = (TileEntity) var18.next();
			var11 = new NBTTagCompound();
			try {
				var22.writeToNBT(var11);
				var17.appendTag(var11);
			} catch (Exception e) {
				FMLLog.log(Level.SEVERE, e,
						"A TileEntity type %s has throw an exception trying to write state. It will not persist. Report this to the mod author",
						var22.toString());
			}
		}

		par3NBTTagCompound.setTag("TileEntities", var17);
		List var20 = par2World.getPendingBlockUpdates(par1Chunk, false);

		if (var20 != null) {
			long var19 = par2World.getTotalWorldTime();
			NBTTagList var12 = new NBTTagList();

			for (Object aVar20 : var20) {
				NextTickListEntry var14 = (NextTickListEntry) aVar20;
				NBTTagCompound var15 = new NBTTagCompound();
				var15.setInteger("i", var14.blockID);
				var15.setInteger("x", var14.xCoord);
				var15.setInteger("y", var14.yCoord);
				var15.setInteger("z", var14.zCoord);
				var15.setInteger("t", (int) (var14.scheduledTime - var19));
				var12.appendTag(var15);
			}

			par3NBTTagCompound.setTag("TileTicks", var12);
		}
	}

	private static long hash(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}
}
