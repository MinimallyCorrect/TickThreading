package me.nallar.patched.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.TwoWayReentrantReadWriteLock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.world.ChunkEvent;

@SuppressWarnings ("unchecked")
public abstract class PatchChunk extends Chunk {
	@Declare
	public List pendingBlockUpdates_;
	@Declare
	public boolean unloading_;
	@Declare
	public boolean alreadySavedAfterUnload_;
	public Lock entityListWriteLock;
	public Lock entityListReadLock;

	public PatchChunk(World par1World, int par2, int par3) {
		super(par1World, par2, par3);
	}

	private List<TileEntity> toInvalidate;

	public void construct() {
		toInvalidate = new ArrayList<TileEntity>();
		TwoWayReentrantReadWriteLock twoWayReentrantReadWriteLock = new TwoWayReentrantReadWriteLock();
		entityListWriteLock = twoWayReentrantReadWriteLock.writeLock();
		entityListReadLock = twoWayReentrantReadWriteLock.readLock();
	}

	@Override
	public boolean needsSaving(boolean force) {
		boolean hasTileEntities = !chunkTileEntityMap.isEmpty();
		if (isModified || (force && (hasEntities || hasTileEntities))) {
			return true;
		}
		long nextSaveTime = lastSaveTime + 4000;
		long time = worldObj.getTotalWorldTime();
		return (hasEntities && nextSaveTime <= time) || (hasTileEntities && (nextSaveTime + 3000) < time);
	}

	@Override
	public String toString() {
		return "chunk at " + xPosition + ',' + zPosition + " which is " + (unloading ? (alreadySavedAfterUnload ? "unloaded" : "unloading") : "loaded") + " and " + (isTerrainPopulated ? "" : "un") + "populated";
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod") // Patcher makes entityLists final
	@Override
	@Declare
	public void getEntitiesWithinAABBForEntity(Entity excludedEntity, AxisAlignedBB collisionArea, List collidingAABBs, int limit) {
		entityListReadLock.lock();
		try {
			int var4 = MathHelper.floor_double((collisionArea.minY - World.MAX_ENTITY_RADIUS) / 16.0D);
			int var5 = MathHelper.floor_double((collisionArea.maxY + World.MAX_ENTITY_RADIUS) / 16.0D);

			if (var4 < 0) {
				var4 = 0;
			}

			if (var5 >= this.entityLists.length) {
				var5 = this.entityLists.length - 1;
			}

			for (int var6 = var4; var6 <= var5; ++var6) {
				List var7 = this.entityLists[var6];

				for (Object aVar7 : var7) {
					Entity var9 = (Entity) aVar7;

					if (var9 != excludedEntity && var9.boundingBox.intersectsWith(collisionArea)) {
						collidingAABBs.add(var9);
						if (--limit == 0) {
							return;
						}
						Entity[] var10 = var9.getParts();

						if (var10 != null) {
							for (Entity aVar10 : var10) {
								var9 = aVar10;

								if (var9 != excludedEntity && var9.boundingBox.intersectsWith(collisionArea)) {
									collidingAABBs.add(var9);
									if (--limit == 0) {
										return;
									}
								}
							}
						}
					}
				}
			}
		} finally {
			entityListReadLock.unlock();
		}
	}

	@Override
	public void addTileEntity(TileEntity tileEntity) {
		int x = tileEntity.xCoord - this.xPosition * 16;
		int y = tileEntity.yCoord;
		int z = tileEntity.zCoord - this.zPosition * 16;

		if (this.isChunkLoaded) {
			this.setChunkBlockTileEntity(x, y, z, tileEntity);
			this.worldObj.addTileEntity(tileEntity);
		} else {
			ChunkPosition chunkPosition = new ChunkPosition(x, y, z);
			tileEntity.setWorldObj(this.worldObj);

			Block block = Block.blocksList[getBlockID(x, y, z)];
			if (block != null && block.hasTileEntity(getBlockMetadata(x, y, z))) {
				TileEntity old = (TileEntity) chunkTileEntityMap.put(chunkPosition, tileEntity);
				if (old != null) {
					toInvalidate.add(old);
				}
			}
		}
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod") // Patcher makes worldObj final
	@Override
	public void onChunkUnload() {
		isChunkLoaded = false;
		Set<TileEntity> removalSet = worldObj.tileEntityRemovalSet;
		for (TileEntity var2 : (Iterable<TileEntity>) this.chunkTileEntityMap.values()) {
			removalSet.add(var2);
		}

		for (List entityList : this.entityLists) {
			this.worldObj.unloadEntities(entityList);
		}

		synchronized (ChunkEvent.class) {
			MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(this));
		}
	}

	@Override
	public void onChunkLoad() {
		for (TileEntity tileEntity : toInvalidate) {
			tileEntity.invalidate();
		}
		toInvalidate.clear();
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod") // Patcher makes worldObj final
	@Override
	@Declare
	public void threadUnsafeChunkLoad() {
		this.isChunkLoaded = true;

		worldObj.addTileEntity(this.chunkTileEntityMap.values());

		for (List entityList : this.entityLists) {
			worldObj.addLoadedEntities(entityList);
		}

		synchronized (ChunkEvent.class) {
			MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(this));
		}
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod") // Patcher makes x/zPosition and worldObj final
	@Override
	public void addEntity(Entity par1Entity) {
		int entityChunkX = MathHelper.floor_double(par1Entity.posX / 16.0D);
		int entityChunkZ = MathHelper.floor_double(par1Entity.posZ / 16.0D);

		if (entityChunkX != this.xPosition || entityChunkZ != this.zPosition) {
			FMLLog.log(Level.FINE, new Throwable(), "Entity %s added to the wrong chunk - expected x%d z%d, got x%d z%d", par1Entity.toString(), this.xPosition, this.zPosition, entityChunkX, entityChunkZ);
			if (worldObj instanceof WorldServer) {
				Chunk correctChunk = ((WorldServer) worldObj).theChunkProviderServer.getChunkIfExists(entityChunkX, entityChunkZ);
				if (correctChunk == this) {
					Log.severe("What?! This chunk isn't at the position it says it's at...: " + this + " was added at " + entityChunkX + ", " + entityChunkZ);
				} else if (correctChunk != null) {
					correctChunk.addEntity(par1Entity);
					return;
				}
			}
		}

		this.hasEntities = true;

		int var4 = MathHelper.floor_double(par1Entity.posY / 16.0D);

		if (var4 < 0) {
			var4 = 0;
		}

		if (var4 >= this.entityLists.length) {
			var4 = this.entityLists.length - 1;
		}
		MinecraftForge.EVENT_BUS.post(new EntityEvent.EnteringChunk(par1Entity, this.xPosition, this.zPosition, par1Entity.chunkCoordX, par1Entity.chunkCoordZ));
		par1Entity.addedToChunk = true;
		par1Entity.chunkCoordX = this.xPosition;
		par1Entity.chunkCoordY = var4;
		par1Entity.chunkCoordZ = this.zPosition;
		this.entityLists[var4].add(par1Entity);
	}

	@Override
	@Declare
	public void setChunkBlockTileEntityWithoutValidate(int x, int y, int z, TileEntity tileEntity) {
		ChunkPosition var5 = new ChunkPosition(x, y, z);
		tileEntity.worldObj = worldObj;
		tileEntity.xCoord = this.xPosition * 16 + x;
		tileEntity.yCoord = y;
		tileEntity.zCoord = this.zPosition * 16 + z;

		Block block = Block.blocksList[getBlockID(x, y, z)];
		if (block != null && block.hasTileEntity(getBlockMetadata(x, y, z))) {
			this.chunkTileEntityMap.put(var5, tileEntity);
		}
	}

	@Override
	@Declare
	public boolean setBlockIDWithMetadataWithoutValidate(int x, int y, int z, int id, int meta) {
		int xzIndex = z << 4 | x;

		if (y >= this.precipitationHeightMap[xzIndex] - 1) {
			this.precipitationHeightMap[xzIndex] = -999;
		}

		int var7 = this.heightMap[xzIndex];
		int var8 = this.getBlockID(x, y, z);
		int var9 = this.getBlockMetadata(x, y, z);

		if (var8 == id && var9 == meta) {
			return false;
		} else {
			if (y >> 4 >= storageArrays.length || y >> 4 < 0) {
				return false;
			}

			ExtendedBlockStorage var10 = this.storageArrays[y >> 4];
			boolean var11 = false;

			if (var10 == null) {
				if (id == 0) {
					return false;
				}

				var10 = this.storageArrays[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !this.worldObj.provider.hasNoSky);
				var11 = y >= var7;
			}

			int var12 = this.xPosition * 16 + x;
			int var13 = this.zPosition * 16 + z;

			var10.setExtBlockID(x, y & 15, z, id);

			if (var8 != 0) {
				if (Block.blocksList[var8] != null && Block.blocksList[var8].hasTileEntity(var9)) {
					TileEntity te = worldObj.getBlockTileEntity(var12, y, var13);
					if (te != null && te.shouldRefresh(var8, id, var9, meta, worldObj, var12, y, var13)) {
						this.worldObj.removeBlockTileEntity(var12, y, var13);
					}
				}
			}

			if (var10.getExtBlockID(x, y & 15, z) != id) {
				return false;
			} else {
				var10.setExtBlockMetadata(x, y & 15, z, meta);

				if (var11) {
					this.generateSkylightMap();
				} else {
					if (getBlockLightOpacity(x, y, z) > 0) {
						if (y >= var7) {
							this.relightBlock(x, y + 1, z);
						}
					} else if (y == var7 - 1) {
						this.relightBlock(x, y, z);
					}

					this.propagateSkylightOcclusion(x, z);
				}

				TileEntity var14;

				if (id != 0) {
					if (Block.blocksList[id] != null && Block.blocksList[id].hasTileEntity(meta)) {
						var14 = this.getChunkBlockTileEntity(x, y, z);

						if (var14 == null) {
							var14 = Block.blocksList[id].createTileEntity(this.worldObj, meta);
							this.worldObj.setBlockTileEntity(var12, y, var13, var14);
						}

						if (var14 != null) {
							var14.updateContainingBlockInfo();
							var14.blockMetadata = meta;
						}
					}
				}

				this.isModified = true;
				return true;
			}
		}
	}

	@Override
	public boolean setBlockIDWithMetadata(int x, int y, int z, int id, int meta) {
		int horizontalIndex = z << 4 | x;

		if (y >= this.precipitationHeightMap[horizontalIndex] - 1) {
			this.precipitationHeightMap[horizontalIndex] = -999;
		}

		int height = this.heightMap[horizontalIndex];
		int oldId = this.getBlockID(x, y, z);
		int oldMeta = this.getBlockMetadata(x, y, z);

		if (oldId == id && oldMeta == meta) {
			return false;
		} else if (id < 0 || (id != 0 && Block.blocksList[id] == null)) {
			Log.warning("Tried to set invalid block ID " + id + ':' + meta, new Throwable());
			return false;
		} else {
			if (y >> 4 >= storageArrays.length || y >> 4 < 0) {
				return false;
			}

			ExtendedBlockStorage ebs = this.storageArrays[y >> 4];
			boolean changedHeightMap = false;

			if (ebs == null) {
				if (id == 0) {
					return false;
				}

				ebs = this.storageArrays[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !this.worldObj.provider.hasNoSky);
				changedHeightMap = y >= height;
			}

			int wX = this.xPosition * 16 + x;
			int wZ = this.zPosition * 16 + z;
			Block oldBlock = oldId > 0 ? Block.blocksList[oldId] : null;

			if (oldBlock != null && !this.worldObj.isRemote) {
				oldBlock.onSetBlockIDWithMetaData(this.worldObj, wX, y, wZ, oldMeta);
			}

			ebs.setExtBlockID(x, y & 15, z, id);

			if (oldBlock != null) {
				if (!this.worldObj.isRemote) {
					oldBlock.breakBlock(this.worldObj, wX, y, wZ, oldId, oldMeta);
				} else if (oldBlock.hasTileEntity(oldMeta)) {
					TileEntity te = worldObj.getBlockTileEntity(wX, y, wZ);
					if (te != null && te.shouldRefresh(oldId, id, oldMeta, meta, worldObj, wX, y, wZ)) {
						this.worldObj.removeBlockTileEntity(wX, y, wZ);
					}
				}
			}

			if (ebs.getExtBlockID(x, y & 15, z) != id) {
				return false;
			} else {
				ebs.setExtBlockMetadata(x, y & 15, z, meta);

				if (changedHeightMap) {
					this.generateSkylightMap();
				} else {
					if (getBlockLightOpacity(x, y, z) > 0) {
						if (y >= height) {
							this.relightBlock(x, y + 1, z);
						}
					} else if (y == height - 1) {
						this.relightBlock(x, y, z);
					}

					this.propagateSkylightOcclusion(x, z);
				}

				Block block = id > 0 ? Block.blocksList[id] : null;
				if (block != null) {
					// CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer
					if (!this.worldObj.isRemote && (block instanceof BlockContainer || (worldObj.inPlaceEvent == null || worldObj.inPlaceEvent.get() == Boolean.FALSE))) {
						block.onBlockAdded(this.worldObj, wX, y, wZ);
					}

					if (block.hasTileEntity(meta)) {
						// CraftBukkit start - don't create tile entity if placement failed
						if (getBlockID(x, y, z) != id) {
							return false;
						}
						//CraftBukkit end

						TileEntity tileEntity = this.getChunkBlockTileEntity(x, y, z);

						if (tileEntity == null) {
							tileEntity = block.createTileEntity(this.worldObj, meta);
							this.worldObj.setBlockTileEntity(wX, y, wZ, tileEntity);
						}

						if (tileEntity != null) {
							tileEntity.updateContainingBlockInfo();
							tileEntity.blockMetadata = meta;
						}
					}
				}

				this.isModified = true;
				return true;
			}
		}
	}
}
