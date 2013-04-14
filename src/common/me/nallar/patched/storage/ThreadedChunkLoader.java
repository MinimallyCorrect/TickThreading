package me.nallar.patched.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import cpw.mods.fml.common.FMLLog;
import me.nallar.patched.annotation.FakeExtend;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.minecraft.storage.RegionFileCache;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.AnvilChunkLoaderPending;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IThreadedFileIO;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;

@FakeExtend
public abstract class ThreadedChunkLoader extends AnvilChunkLoader implements IThreadedFileIO, IChunkLoader {
	private final java.util.LinkedHashMap<ChunkCoordIntPair, AnvilChunkLoaderPending> pendingSaves = new java.util.LinkedHashMap<ChunkCoordIntPair, AnvilChunkLoaderPending>(); // Spigot
	private final LongHashMap inProgressSaves = new LongHashMap();
	private final Object syncLockObject = new Object();
	public final File chunkSaveLocation;
	private final Cache<Long, NBTTagCompound> chunkCache;
	public final RegionFileCache regionFileCache;

	public ThreadedChunkLoader(File par1File) {
		super(par1File);
		this.chunkSaveLocation = par1File;
		chunkCache = CacheBuilder.newBuilder().maximumSize(TickThreading.instance.chunkCacheSize).build();
		regionFileCache = new RegionFileCache(chunkSaveLocation);
	}

	public boolean chunkExists(World world, int i, int j) {
		ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i, j);

		synchronized (this.syncLockObject) {
			if (pendingSaves.containsKey(chunkcoordintpair)) {
				return true;
			}
		}

		return regionFileCache.get(i, j).isChunkSaved(i & 31, j & 31);
	}

	@Override
	public Chunk loadChunk(World world, int x, int z) {
		NBTTagCompound nbttagcompound;
		ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(x, z);

		synchronized (this.syncLockObject) {
			AnvilChunkLoaderPending pendingchunktosave = pendingSaves.get(chunkcoordintpair);

			if (pendingchunktosave == null) {
				long hash = hash(x, z);
				nbttagcompound = (NBTTagCompound) inProgressSaves.getValueByKey(hash);
				if (nbttagcompound == null) {
					nbttagcompound = chunkCache.getIfPresent(hash);
				} else {
					Log.info("Loading chunk " + hash + ": " + x + ',' + z + " while saving is in progress.");
				}
				if (nbttagcompound != null) {
					chunkCache.invalidate(hash);
				}
			} else {
				nbttagcompound = pendingchunktosave.nbtTags;
			}
		}

		if (nbttagcompound == null) {
			DataInputStream dataInputStream = regionFileCache.getChunkInputStream(x, z);

			if (dataInputStream == null) {
				return null;
			}

			try {
				nbttagcompound = CompressedStreamTools.read(dataInputStream);
			} catch (Throwable t) {
				t.printStackTrace();
				return null;
			}
		}

		return this.checkedReadChunkFromNBT(world, x, z, nbttagcompound);
	}

	public Chunk loadChunk__Async_CB(World world, int x, int z) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected Chunk checkedReadChunkFromNBT(World world, int x, int z, NBTTagCompound chunkTagCompound) {
		NBTTagCompound levelTag = (NBTTagCompound) chunkTagCompound.getTag("Level");
		if (levelTag == null) {
			FMLLog.severe("Chunk file at " + x + ',' + z + " is missing level data, skipping");
			return null;
		} else if (!levelTag.hasKey("Sections")) {
			FMLLog.severe("Chunk file at " + x + ',' + z + " is missing block data, skipping");
			return null;
		} else {
			int cX = levelTag.getInteger("xPos");
			int cZ = levelTag.getInteger("zPos");
			if (cX != x || cZ != z) {
				FMLLog.warning("Chunk file at " + x + ',' + z + " is in the wrong location; relocating. (Expected " + x + ", " + z + ", got " + cX + ", " + cZ + ')');
				levelTag.setInteger("xPos", x);
				levelTag.setInteger("zPos", z);
			}
			Chunk chunk = this.readChunkFromNBT(world, levelTag);

			try {
				MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, chunkTagCompound));
			} catch (Throwable t) {
				FMLLog.severe("A mod failed to handle a ChunkDataEvent.Load event for " + x + ',' + z, t);
			}

			return chunk;
		}
	}

	protected Object[] a(World world, int x, int z, NBTTagCompound chunkTagCompound) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void saveChunk(World world, Chunk chunk) {
		try {
			world.checkSessionLock();
		} catch (MinecraftException ex) {
			// MCPC+ disable this for now.
			//ex.printStackTrace();
		}

		try {
			NBTTagCompound nbttagcompound = new NBTTagCompound();
			NBTTagCompound nbttagcompound1 = new NBTTagCompound();
			nbttagcompound.setTag("Level", nbttagcompound1);
			this.writeChunkToNBT(chunk, world, nbttagcompound1);
			try {
				MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, nbttagcompound));
			} catch (Throwable t) {
				FMLLog.severe("A mod failed to handle a ChunkDataEvent.Save event for " + chunk.xPosition + ',' + chunk.zPosition, t);
			}
			this.addToSaveQueue(chunk.getChunkCoordIntPair(), nbttagcompound, chunk.alreadySavedAfterUnload || chunk.unloading || !chunk.isChunkLoaded);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	protected void addToSaveQueue(ChunkCoordIntPair par1ChunkCoordIntPair, NBTTagCompound par2NBTTagCompound, boolean unloading) {
		synchronized (this.syncLockObject) {
			AnvilChunkLoaderPending pending = new AnvilChunkLoaderPending(par1ChunkCoordIntPair, par2NBTTagCompound);
			pending.unloading = unloading;
			if (this.pendingSaves.put(par1ChunkCoordIntPair, pending) == null) {
				ThreadedFileIOBase.threadedIOInstance.queueIO(this);
			}
		}
	}

	/**
	 * Returns a boolean stating if the write was unsuccessful.
	 */
	@Override
	public boolean writeNextIO() {
		AnvilChunkLoaderPending anvilchunkloaderpending;

		long hash;
		synchronized (this.syncLockObject) {
			if (this.pendingSaves.isEmpty()) {
				return false;
			}

			anvilchunkloaderpending = this.pendingSaves.values().iterator().next();
			this.pendingSaves.remove(anvilchunkloaderpending.chunkCoordinate);
			hash = hash(anvilchunkloaderpending.chunkCoordinate.chunkXPos, anvilchunkloaderpending.chunkCoordinate.chunkZPos);
			if (anvilchunkloaderpending.unloading) {
				chunkCache.put(hash, anvilchunkloaderpending.nbtTags);
			}
			inProgressSaves.add(hash, anvilchunkloaderpending.nbtTags);
		}

		try {
			this.writeChunkNBTTags(anvilchunkloaderpending);
		} catch (Exception exception) {
			exception.printStackTrace();
		}

		inProgressSaves.remove(hash);

		return true;
	}

	@Override
	public void writeChunkNBTTags(AnvilChunkLoaderPending par1AnvilChunkLoaderPending) throws java.io.IOException   // CraftBukkit - public -> private, added throws
	{
		DataOutputStream dataoutputstream = regionFileCache.getChunkOutputStream(par1AnvilChunkLoaderPending.chunkCoordinate.chunkXPos, par1AnvilChunkLoaderPending.chunkCoordinate.chunkZPos);
		CompressedStreamTools.write(par1AnvilChunkLoaderPending.nbtTags, dataoutputstream);
		dataoutputstream.close();
	}

	@Override
	public void saveExtraChunkData(World par1World, Chunk par2Chunk) {
	}

	@Override
	public void chunkTick() {
	}

	@Override
	public void saveExtraData() {
	}

	@Override
	protected void writeChunkToNBT(Chunk par1Chunk, World par2World, NBTTagCompound par3NBTTagCompound) {
		par3NBTTagCompound.setInteger("xPos", par1Chunk.xPosition);
		par3NBTTagCompound.setInteger("zPos", par1Chunk.zPosition);
		par3NBTTagCompound.setLong("LastUpdate", par2World.getTotalWorldTime());
		par3NBTTagCompound.setIntArray("HeightMap", par1Chunk.heightMap);
		par3NBTTagCompound.setBoolean("TerrainPopulated", par1Chunk.isTerrainPopulated);
		ExtendedBlockStorage[] aextendedblockstorage = par1Chunk.getBlockStorageArray();
		NBTTagList nbttaglist = new NBTTagList("Sections");
		boolean flag = !par2World.provider.hasNoSky;
		int i = aextendedblockstorage.length;
		NBTTagCompound nbttagcompound1;

		for (int j = 0; j < i; ++j) {
			ExtendedBlockStorage extendedblockstorage = aextendedblockstorage[j];

			if (extendedblockstorage != null) {
				nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Y", (byte) (extendedblockstorage.getYLocation() >> 4 & 255));
				nbttagcompound1.setByteArray("Blocks", extendedblockstorage.getBlockLSBArray());

				if (extendedblockstorage.getBlockMSBArray() != null) {
					nbttagcompound1.setByteArray("Add", extendedblockstorage.getBlockMSBArray().getValueArray()); // Spigot
				}

				nbttagcompound1.setByteArray("Data", extendedblockstorage.getMetadataArray().getValueArray()); // Spigot
				nbttagcompound1.setByteArray("BlockLight", extendedblockstorage.getBlocklightArray().getValueArray()); // Spigot

				if (flag) {
					nbttagcompound1.setByteArray("SkyLight", extendedblockstorage.getSkylightArray().getValueArray()); // Spigot
				} else {
					nbttagcompound1.setByteArray("SkyLight", new byte[extendedblockstorage.getBlocklightArray().getValueArray().length]); // Spigot
				}

				nbttaglist.appendTag(nbttagcompound1);
			}
		}

		par3NBTTagCompound.setTag("Sections", nbttaglist);
		par3NBTTagCompound.setByteArray("Biomes", par1Chunk.getBiomeArray());
		par1Chunk.hasEntities = false;
		NBTTagList nbttaglist1 = new NBTTagList();
		Iterator iterator;

		for (i = 0; i < par1Chunk.entityLists.length; ++i) {
			iterator = par1Chunk.entityLists[i].iterator();

			while (iterator.hasNext()) {
				Entity entity = (Entity) iterator.next();
				nbttagcompound1 = new NBTTagCompound();

				try {
					if (entity.addEntityID(nbttagcompound1)) {
						par1Chunk.hasEntities = true;
						nbttaglist1.appendTag(nbttagcompound1);
					}
				} catch (Exception e) {
					FMLLog.log(Level.SEVERE, e,
							"An Entity type %s at %s,%f,%f,%f has thrown an exception trying to write state. It will not persist. Report this to the mod author",
							entity.getClass().getName(),
							Log.name(entity.worldObj),
							entity.posX, entity.posY, entity.posZ); // MCPC+ - add location
				}
			}
		}

		par3NBTTagCompound.setTag("Entities", nbttaglist1);
		NBTTagList nbttaglist2 = new NBTTagList();
		iterator = par1Chunk.chunkTileEntityMap.values().iterator();

		while (iterator.hasNext()) {
			TileEntity tileentity = (TileEntity) iterator.next();
			nbttagcompound1 = new NBTTagCompound();
			try {
				tileentity.writeToNBT(nbttagcompound1);
				nbttaglist2.appendTag(nbttagcompound1);
			} catch (Exception e) {
				FMLLog.log(Level.SEVERE, e,
						"A TileEntity type %s at %s,%d,%d,%d has throw an exception trying to write state. It will not persist. Report this to the mod author",
						tileentity.getClass().getName(),
						Log.name(tileentity.worldObj),
						tileentity.xCoord, tileentity.yCoord, tileentity.zCoord); // MCPC+ - add location
			}
		}

		par3NBTTagCompound.setTag("TileEntities", nbttaglist2);
		List list = par1Chunk.pendingBlockUpdates;

		if (list != null) {
			long k = par2World.getTotalWorldTime();
			NBTTagList nbttaglist3 = new NBTTagList();

			for (final Object aList : list) {
				NextTickListEntry nextticklistentry = (NextTickListEntry) aList;
				NBTTagCompound nbttagcompound2 = new NBTTagCompound();
				nbttagcompound2.setInteger("i", nextticklistentry.blockID);
				nbttagcompound2.setInteger("x", nextticklistentry.xCoord);
				nbttagcompound2.setInteger("y", nextticklistentry.yCoord);
				nbttagcompound2.setInteger("z", nextticklistentry.zCoord);
				nbttagcompound2.setInteger("t", (int) (nextticklistentry.scheduledTime - k));
				nbttagcompound2.setInteger("p", nextticklistentry.field_82754_f);
				nbttaglist3.appendTag(nbttagcompound2);
			}

			par3NBTTagCompound.setTag("TileTicks", nbttaglist3);
		}
	}

	@Override
	protected Chunk readChunkFromNBT(World world, NBTTagCompound nbtTagCompound) {
		int i = nbtTagCompound.getInteger("xPos");
		int j = nbtTagCompound.getInteger("zPos");
		Chunk chunk = new Chunk(world, i, j);
		chunk.heightMap = nbtTagCompound.getIntArray("HeightMap");
		chunk.isTerrainPopulated = nbtTagCompound.getBoolean("TerrainPopulated");
		NBTTagList nbttaglist = nbtTagCompound.getTagList("Sections");
		byte b0 = 16;
		ExtendedBlockStorage[] aextendedblockstorage = new ExtendedBlockStorage[b0];
		boolean flag = !world.provider.hasNoSky;

		for (int k = 0; k < nbttaglist.tagCount(); ++k) {
			NBTTagCompound nbttagcompound1 = (NBTTagCompound) nbttaglist.tagAt(k);
			byte b1 = nbttagcompound1.getByte("Y");
			ExtendedBlockStorage extendedblockstorage = new ExtendedBlockStorage(b1 << 4, flag);
			extendedblockstorage.setBlockLSBArray(nbttagcompound1.getByteArray("Blocks"));

			if (nbttagcompound1.hasKey("Add")) {
				extendedblockstorage.setBlockMSBArray(new NibbleArray(nbttagcompound1.getByteArray("Add"), 4));
			}

			extendedblockstorage.setBlockMetadataArray(new NibbleArray(nbttagcompound1.getByteArray("Data"), 4));
			extendedblockstorage.setBlocklightArray(new NibbleArray(nbttagcompound1.getByteArray("BlockLight"), 4));

			if (flag) {
				extendedblockstorage.setSkylightArray(new NibbleArray(nbttagcompound1.getByteArray("SkyLight"), 4));
			}

			extendedblockstorage.removeInvalidBlocks();
			aextendedblockstorage[b1] = extendedblockstorage;
		}

		chunk.setStorageArrays(aextendedblockstorage);

		if (nbtTagCompound.hasKey("Biomes")) {
			chunk.setBiomeArray(nbtTagCompound.getByteArray("Biomes"));
		}

		NBTTagList nbttaglist1 = nbtTagCompound.getTagList("Entities");

		if (nbttaglist1 != null) {
			for (int l = 0; l < nbttaglist1.tagCount(); ++l) {
				NBTTagCompound nbttagcompound2 = (NBTTagCompound) nbttaglist1.tagAt(l);
				Entity entity = EntityList.createEntityFromNBT(nbttagcompound2, world);
				chunk.hasEntities = true;

				if (entity != null) {
					chunk.addEntity(entity);

					for (NBTTagCompound nbttagcompound3 = nbttagcompound2; nbttagcompound3.hasKey("Riding"); nbttagcompound3 = nbttagcompound3.getCompoundTag("Riding")) {
						Entity entity2 = EntityList.createEntityFromNBT(nbttagcompound3.getCompoundTag("Riding"), world);

						if (entity2 != null) {
							chunk.addEntity(entity2);
							entity.mountEntity(entity2);
						}
					}
				}
			}
		}

		NBTTagList nbttaglist2 = nbtTagCompound.getTagList("TileEntities");

		if (nbttaglist2 != null) {
			for (int i1 = 0; i1 < nbttaglist2.tagCount(); ++i1) {
				NBTTagCompound nbttagcompound4 = (NBTTagCompound) nbttaglist2.tagAt(i1);
				TileEntity tileentity = TileEntity.createAndLoadEntity(nbttagcompound4);

				if (tileentity != null) {
					chunk.addTileEntity(tileentity);
				}
			}
		}

		if (nbtTagCompound.hasKey("TileTicks")) {
			NBTTagList nbttaglist3 = nbtTagCompound.getTagList("TileTicks");

			if (nbttaglist3 != null) {
				for (int j1 = 0; j1 < nbttaglist3.tagCount(); ++j1) {
					NBTTagCompound nbttagcompound5 = (NBTTagCompound) nbttaglist3.tagAt(j1);
					world.func_82740_a(nbttagcompound5.getInteger("x"), nbttagcompound5.getInteger("y"), nbttagcompound5.getInteger("z"), nbttagcompound5.getInteger("i"), nbttagcompound5.getInteger("t"), nbttagcompound5.getInteger("p"));
				}
			}
		}

		return chunk;
	}

	@Override
	@Declare
	public void close() {
		regionFileCache.close();
	}

	private static long hash(int x, int z) {
		return (((long) x) << 32) | (z & 0xffffffffL);
	}
}
