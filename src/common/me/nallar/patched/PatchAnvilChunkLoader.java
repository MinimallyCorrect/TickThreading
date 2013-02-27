package me.nallar.patched;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import me.nallar.tickthreading.minecraft.storage.RegionFileCache;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;

public abstract class PatchAnvilChunkLoader extends AnvilChunkLoader {
	private Cache<Long, NBTTagCompound> chunkCache;
	public RegionFileCache regionFileCache;

	public void construct() {
		if (TickThreading.instance.chunkCacheSize > 0) {
			chunkCache = CacheBuilder.newBuilder().maximumSize(TickThreading.instance.chunkCacheSize).build();
		}
		regionFileCache = new RegionFileCache(chunkSaveLocation);
	}

	public PatchAnvilChunkLoader(File par1File) {
		super(par1File);
	}

	@Override
	protected Chunk checkedReadChunkFromNBT(World par1World, int x, int z, NBTTagCompound par4NBTTagCompound) {
		if (!par4NBTTagCompound.hasKey("Level")) {
			FMLLog.severe("Chunk file at " + x + ',' + z + " is missing level data, skipping");
			return null;
		} else if (!par4NBTTagCompound.getCompoundTag("Level").hasKey("Sections")) {
			FMLLog.severe("Chunk file at " + x + ',' + z + " is missing block data, skipping");
			return null;
		} else {
			Chunk var5 = this.readChunkFromNBT(par1World, par4NBTTagCompound.getCompoundTag("Level"));

			if (!var5.isAtLocation(x, z)) {
				FMLLog.warning("Chunk file at " + x + ',' + z + " is in the wrong location; relocating. (Expected " + x + ", " + z + ", got " + var5.xPosition + ", " + var5.zPosition + ')');
				par4NBTTagCompound.setInteger("xPos", x);
				par4NBTTagCompound.setInteger("zPos", z);
				var5 = this.readChunkFromNBT(par1World, par4NBTTagCompound.getCompoundTag("Level"));
			}

			try {
				MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(var5, par4NBTTagCompound));
			} catch (Throwable t) {
				FMLLog.severe("A mod failed to handle a ChunkDataEvent.Load event", t);
			}

			return var5;
		}
	}

	@Override
	public Chunk loadChunk(World par1World, int x, int z) throws IOException {
		NBTTagCompound var4 = null;
		ChunkCoordIntPair var5 = new ChunkCoordIntPair(x, z);

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
			long cacheHash = hash(x, z);
			var4 = chunkCache == null ? null : chunkCache.getIfPresent(cacheHash);

			if (var4 == null) {
				DataInputStream var10 = regionFileCache.getChunkInputStream(x, z);

				if (var10 == null) {
					return null;
				}

				var4 = CompressedStreamTools.read(var10);
			} else {
				chunkCache.invalidate(cacheHash);
			}
		}

		return this.checkedReadChunkFromNBT(par1World, x, z, var4);
	}

	@Override
	protected void writeChunkNBTTags(AnvilChunkLoaderPending pendingChunk) throws IOException {
		DataOutputStream var2 = regionFileCache.getChunkOutputStream(pendingChunk.chunkCoordinate.chunkXPos, pendingChunk.chunkCoordinate.chunkZPos);
		CompressedStreamTools.write(pendingChunk.nbtTags, var2);
		var2.close();
	}

	@Override
	public void saveChunk(World par1World, Chunk par2Chunk) throws MinecraftException, IOException {
		par1World.checkSessionLock();

		try {
			NBTTagCompound var3 = new NBTTagCompound();
			NBTTagCompound var4 = new NBTTagCompound();
			var3.setTag("Level", var4);
			this.writeChunkToNBT(par2Chunk, par1World, var4);
			MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(par2Chunk, var3));
			this.func_75824_a(par2Chunk.getChunkCoordIntPair(), var3);
		} catch (Exception var5) {
			FMLLog.log(Level.WARNING, var5, "Exception attempting to save a chunk");
		}
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

		if (chunkCache != null) {
			chunkCache.put(hash(var1.chunkCoordinate.chunkXPos, var1.chunkCoordinate.chunkZPos), var1.nbtTags);
		}

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

	@Override
	@Declare
	public void close() {
		regionFileCache.close();
	}

	private static long hash(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}
}
