package nallar.patched.storage;

import cpw.mods.fml.common.FMLLog;
import nallar.collections.SynchronizedList;
import nallar.tickthreading.Log;
import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.BlockInfo;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.command.IEntitySelector;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
public abstract class PatchChunk extends Chunk {
	@Declare
	public List pendingBlockUpdates_;
	@Declare
	public boolean queuedUnload_;
	@Declare
	public boolean partiallyUnloaded_;
	@Declare
	public boolean alreadySavedAfterUnload_;

	public PatchChunk(World par1World, int par2, int par3) {
		super(par1World, par2, par3);
	}

	private List<TileEntity> toInvalidate;

	public void construct() {
		for (int i = 0; i < entityLists.length; i++) {
			entityLists[i] = new SynchronizedList();
		}
		toInvalidate = new ArrayList<TileEntity>();
	}

	@Override
	@Declare
	public boolean isNormallyLoaded() {
		return !this.alreadySavedAfterUnload && !this.partiallyUnloaded && this.isChunkLoaded;
	}

	@Override
	@Declare
	public boolean needsSaving(boolean force, long worldTime) {
		if (lastSaveTime == worldTime) {
			return false;
		}
		if (force && (isModified || hasEntities || !chunkTileEntityMap.isEmpty() || lastSaveTime + 1000 < worldTime)) {
			return true;
		}
		long nextSaveTime;
		if (isModified) {
			nextSaveTime = 4000;
		} else if (hasEntities) {
			nextSaveTime = 6000;
		} else if (!chunkTileEntityMap.isEmpty()) {
			nextSaveTime = 9000;
		} else {
			return false;
		}
		return (nextSaveTime + lastSaveTime) < worldTime;
	}

	@Override
	public String toString() {
		return "chunk at " + xPosition + ',' + zPosition + " which is " + (partiallyUnloaded ? (alreadySavedAfterUnload ? "unloaded and saved" : "unloading") : "loaded") + " and " + (isTerrainPopulated ? "" : "un") + "populated";
	}

	@Override
	@Declare
	public <T> ArrayList<T> getEntitiesOfType(Class<T> aClass) {
		ArrayList<T> list = new ArrayList<T>();
		int min = 0;
		int max = entityLists.length;

		for (int i = min; i < max; ++i) {
			SynchronizedList<Entity> entityList = (SynchronizedList<Entity>) this.entityLists[i];
			Object[] entities = entityList.elementData();
			int length = entityList.size() - 1;
			for (; length >= 0; length--) {
				Entity entity = (Entity) entities[length];
				if (entity == null) {
					continue;
				}
				if (entity.getClass().equals(aClass)) {
					list.add((T) entity);
				}
			}
		}
		return list;
	}

	@Override
	public void getEntitiesOfTypeWithinAAAB(Class aClass, AxisAlignedBB aabb, List list, IEntitySelector selector) {
		int var5 = MathHelper.floor_double((aabb.minY - 2D) / 16.0D);
		int var6 = MathHelper.floor_double((aabb.maxY + 2D) / 16.0D);

		if (var5 < 0) {
			var5 = 0;
		} else if (var5 >= this.entityLists.length) {
			var5 = this.entityLists.length - 1;
		}

		if (var6 >= this.entityLists.length) {
			var6 = this.entityLists.length - 1;
		} else if (var6 < 0) {
			var6 = 0;
		}

		for (int var7 = var5; var7 <= var6; ++var7) {
			SynchronizedList<Entity> entityList = (SynchronizedList<Entity>) this.entityLists[var7];
			Object[] entities = entityList.elementData();
			int length = entityList.size() - 1;
			for (; length >= 0; length--) {
				Entity entity = (Entity) entities[length];
				if (entity == null) {
					continue;
				}
				if (entity.boundingBox.intersectsWith(aabb) && aClass.isAssignableFrom(entity.getClass()) && (selector == null || selector.isEntityApplicable(entity))) {
					list.add(entity);
				}
			}
		}
	}

	@Override
	public void getEntitiesWithinAABBForEntity(Entity excludedEntity, AxisAlignedBB collisionArea, List collidingAABBs, IEntitySelector entitySelector) {
		getEntitiesWithinAABBForEntity(excludedEntity, collisionArea, collidingAABBs, entitySelector, 2000);
	}

	@Override
	@Declare
	public int getEntitiesWithinAABBForEntity(Entity excludedEntity, AxisAlignedBB collisionArea, List collidingAABBs, IEntitySelector entitySelector, int limit) {
		int var4 = MathHelper.floor_double((collisionArea.minY - 2D) / 16.0D);
		int var5 = MathHelper.floor_double((collisionArea.maxY + 2D) / 16.0D);

		if (var4 < 0) {
			var4 = 0;
		}

		if (var5 >= entityLists.length) {
			var5 = entityLists.length - 1;
		}

		for (int var6 = var4; var6 <= var5; ++var6) {
			SynchronizedList<Entity> entityList = (SynchronizedList<Entity>) entityLists[var6];

			Object[] entities = entityList.elementData();
			int length = entityList.size() - 1;
			for (; length >= 0; length--) {
				Entity entity = (Entity) entities[length];
				if (entity == null) {
					continue;
				}

				if (entity != excludedEntity && entity.boundingBox.intersectsWith(collisionArea) && (entitySelector == null || entitySelector.isEntityApplicable(entity))) {
					collidingAABBs.add(entity);
					if (--limit == 0) {
						return limit;
					}
					Entity[] var10 = entity.getParts();

					if (var10 != null) {
						for (Entity part : var10) {
							if (part != excludedEntity && part.boundingBox.intersectsWith(collisionArea) && (entitySelector == null || entitySelector.isEntityApplicable(part))) {
								collidingAABBs.add(part);
								if (--limit == 0) {
									return limit;
								}
							}
						}
					}
				}
			}
		}
		return limit;
	}

	@Override
	public void addTileEntity(TileEntity tileEntity) {
		int x = tileEntity.xCoord - xPosition * 16;
		int y = tileEntity.yCoord;
		int z = tileEntity.zCoord - zPosition * 16;

		if (isChunkLoaded) {
			setChunkBlockTileEntity(x, y, z, tileEntity);
			worldObj.addTileEntity(tileEntity);
		} else {
			ChunkPosition chunkPosition = new ChunkPosition(x, y, z);
			tileEntity.setWorldObj(worldObj);

			Block block = Block.blocksList[getBlockID(x, y, z)];
			if (block != null && block.hasTileEntity(getBlockMetadata(x, y, z))) {
				TileEntity old = (TileEntity) chunkTileEntityMap.put(chunkPosition, tileEntity);
				if (old != null) {
					toInvalidate.add(old);
				}
			}
		}
	}

	@Override
	public void onChunkUnload() {
		throw new Error("Not supported with TT");
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod") // Patcher makes worldObj final
	@Override
	@Declare
	public void onChunkUnloadTT() {
		isChunkLoaded = false;
		Set<TileEntity> removalSet = worldObj.tileEntityRemovalSet;
		for (TileEntity var2 : (Iterable<TileEntity>) chunkTileEntityMap.values()) {
			removalSet.add(var2);
		}

		for (List entityList : entityLists) {
			worldObj.unloadEntities(entityList);
		}

		synchronized (ChunkEvent.class) {
			MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(this));
		}
	}

	@Override
	public void removeEntityAtIndex(Entity e, int index) {
		if (index < 0) {
			index = 0;
		}

		if (index >= this.entityLists.length) {
			index = this.entityLists.length - 1;
		}

		this.entityLists[index].remove(e);
		e.chunk = null;
	}

	@Override
	public void onChunkLoad() {
		for (TileEntity tileEntity : toInvalidate) {
			tileEntity.invalidate();
		}
		toInvalidate.clear();
		for (Map.Entry<ChunkPosition, TileEntity> entry : ((Map<ChunkPosition, TileEntity>) chunkTileEntityMap).entrySet()) {
			TileEntity tileEntity = entry.getValue();
			if ((!tileEntity.canUpdate() && tileEntity.isInvalid()) || tileEntity.getClass() == TileEntity.class) {
				tileEntity.invalidate();
				ChunkPosition position = entry.getKey();
				chunkTileEntityMap.remove(position);
				worldObj.loadedTileEntityList.remove(tileEntity);
				int x = position.x, y = position.y, z = position.z;
				int id = getBlockID(x, y, z);
				int meta = getBlockMetadata(x, y, z);
				Log.info("Resetting invalid TileEntity " + Log.toString(tileEntity) + " for block: " + new BlockInfo(id, meta) + " at within chunk coords " + x + ',' + y + ',' + z + " in chunk " + this);
				setBlockIDWithMetadata(x, y, z, 0, 0);
				setBlockIDWithMetadata(x, y, z, id, meta);
			}
		}
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod") // Patcher makes worldObj final
	@Override
	@Declare
	public void threadUnsafeChunkLoad() {
		isChunkLoaded = true;

		worldObj.addTileEntity(chunkTileEntityMap.values());

		for (List entityList : entityLists) {
			synchronized (entityList) {
				worldObj.addLoadedEntities(entityList);
			}
		}

		synchronized (ChunkEvent.class) {
			MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(this));
		}
	}

	@SuppressWarnings("FieldRepeatedlyAccessedInMethod") // Patcher makes x/zPosition and worldObj final
	@Override
	public void addEntity(Entity par1Entity) {
		int entityChunkX = MathHelper.floor_double(par1Entity.posX / 16.0D);
		int entityChunkZ = MathHelper.floor_double(par1Entity.posZ / 16.0D);

		if (entityChunkX != xPosition || entityChunkZ != zPosition) {
			if (Log.debug) {
				FMLLog.log(Log.DEBUG, new Throwable(), "Entity %s added to the wrong chunk - expected x%d z%d, got x%d z%d", par1Entity.toString(), xPosition, zPosition, entityChunkX, entityChunkZ);
			}
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

		hasEntities = true;

		int var4 = MathHelper.floor_double(par1Entity.posY / 16.0D);

		if (var4 < 0) {
			var4 = 0;
		}

		if (var4 >= entityLists.length) {
			var4 = entityLists.length - 1;
		}
		MinecraftForge.EVENT_BUS.post(new EntityEvent.EnteringChunk(par1Entity, xPosition, zPosition, par1Entity.chunkCoordX, par1Entity.chunkCoordZ));
		par1Entity.addedToChunk = true;
		par1Entity.chunkCoordX = xPosition;
		par1Entity.chunkCoordY = var4;
		par1Entity.chunkCoordZ = zPosition;
		entityLists[var4].add(par1Entity);
		par1Entity.chunk = this;
	}

	@Override
	@Declare
	public void setChunkBlockTileEntityWithoutValidate(int x, int y, int z, TileEntity tileEntity) {
		ChunkPosition var5 = new ChunkPosition(x, y, z);
		tileEntity.worldObj = worldObj;
		tileEntity.xCoord = xPosition * 16 + x;
		tileEntity.yCoord = y;
		tileEntity.zCoord = zPosition * 16 + z;

		Block block = Block.blocksList[getBlockID(x, y, z)];
		if (block != null && block.hasTileEntity(getBlockMetadata(x, y, z))) {
			chunkTileEntityMap.put(var5, tileEntity);
		}
		isModified = true;
	}

	@Override
	@Declare
	public boolean setBlockIDWithMetadataWithoutValidate(int x, int y, int z, int id, int meta) {
		int xzIndex = z << 4 | x;

		if (y >= precipitationHeightMap[xzIndex] - 1) {
			precipitationHeightMap[xzIndex] = -999;
		}

		int var7 = heightMap[xzIndex];
		int var8 = getBlockID(x, y, z);
		int var9 = getBlockMetadata(x, y, z);

		if (var8 == id && var9 == meta) {
			return false;
		} else {
			if (y >> 4 >= storageArrays.length || y >> 4 < 0) {
				return false;
			}

			ExtendedBlockStorage var10 = storageArrays[y >> 4];
			boolean var11 = false;

			if (var10 == null) {
				if (id == 0) {
					return false;
				}

				var10 = storageArrays[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !worldObj.provider.hasNoSky);
				var11 = y >= var7;
			}

			int var12 = xPosition * 16 + x;
			int var13 = zPosition * 16 + z;

			var10.setExtBlockID(x, y & 15, z, id);

			if (var8 != 0) {
				if (Block.blocksList[var8] != null && Block.blocksList[var8].hasTileEntity(var9)) {
					TileEntity te = worldObj.getBlockTileEntity(var12, y, var13);
					if (te != null && te.shouldRefresh(var8, id, var9, meta, worldObj, var12, y, var13)) {
						worldObj.removeBlockTileEntity(var12, y, var13);
					}
				}
			}

			if (var10.getExtBlockID(x, y & 15, z) != id) {
				return false;
			} else {
				var10.setExtBlockMetadata(x, y & 15, z, meta);

				if (var11) {
					generateSkylightMap();
				} else {
					if (getBlockLightOpacity(x, y, z) > 0) {
						if (y >= var7) {
							relightBlock(x, y + 1, z);
						}
					} else if (y == var7 - 1) {
						relightBlock(x, y, z);
					}

					propagateSkylightOcclusion(x, z);
				}

				TileEntity var14;

				if (id != 0) {
					if (Block.blocksList[id] != null && Block.blocksList[id].hasTileEntity(meta)) {
						var14 = getChunkBlockTileEntity(x, y, z);

						if (var14 == null) {
							var14 = Block.blocksList[id].createTileEntity(worldObj, meta);
							worldObj.setBlockTileEntity(var12, y, var13, var14);
						}

						if (var14 != null) {
							var14.updateContainingBlockInfo();
							var14.blockMetadata = meta;
						}
					}
				}

				isModified = true;
				return true;
			}
		}
	}

	@Override
	public boolean setBlockIDWithMetadata(int x, int y, int z, int id, int meta) {
		int horizontalIndex = z << 4 | x;

		if (y >= precipitationHeightMap[horizontalIndex] - 1) {
			precipitationHeightMap[horizontalIndex] = -999;
		}

		int height = heightMap[horizontalIndex];
		int oldId = getBlockID(x, y, z);
		int oldMeta = getBlockMetadata(x, y, z);

		if (oldId == id && oldMeta == meta) {
			return false;
		} else if (id < 0 || (id != 0 && Block.blocksList[id] == null)) {
			Log.warning("Tried to set invalid block ID " + id + ':' + meta, new Throwable());
			return false;
		} else {
			if (y >> 4 >= storageArrays.length || y >> 4 < 0) {
				return false;
			}

			ExtendedBlockStorage ebs = storageArrays[y >> 4];
			boolean changedHeightMap = false;

			if (ebs == null) {
				if (id == 0) {
					return false;
				}

				ebs = storageArrays[y >> 4] = new ExtendedBlockStorage(y >> 4 << 4, !worldObj.provider.hasNoSky);
				changedHeightMap = y >= height;
			}

			int wX = xPosition * 16 + x;
			int wZ = zPosition * 16 + z;
			Block oldBlock = oldId > 0 ? Block.blocksList[oldId] : null;

			if (oldBlock != null && !worldObj.isRemote) {
				oldBlock.onBlockPreDestroy(worldObj, wX, y, wZ, oldMeta);
			}

			ebs.setExtBlockID(x, y & 15, z, id);

			if (oldBlock != null) {
				if (!worldObj.isRemote) {
					oldBlock.breakBlock(worldObj, wX, y, wZ, oldId, oldMeta);
				} else if (oldBlock.hasTileEntity(oldMeta)) {
					TileEntity te = worldObj.getBlockTileEntity(wX, y, wZ);
					if (te != null && te.shouldRefresh(oldId, id, oldMeta, meta, worldObj, wX, y, wZ)) {
						worldObj.removeBlockTileEntity(wX, y, wZ);
					}
				}
			}

			if (ebs.getExtBlockID(x, y & 15, z) != id) {
				return false;
			} else {
				ebs.setExtBlockMetadata(x, y & 15, z, meta);

				if (changedHeightMap) {
					generateSkylightMap();
				} else {
					if (getBlockLightOpacity(x, y, z) > 0) {
						if (y >= height) {
							relightBlock(x, y + 1, z);
						}
					} else if (y == height - 1) {
						relightBlock(x, y, z);
					}

					propagateSkylightOcclusion(x, z);
				}

				Block block = id > 0 ? Block.blocksList[id] : null;
				if (block != null) {
					// CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer
					if (!worldObj.isRemote && (block instanceof BlockContainer || (worldObj.inPlaceEvent == null || worldObj.inPlaceEvent.get() == Boolean.FALSE))) {
						block.onBlockAdded(worldObj, wX, y, wZ);
					}

					if (block.hasTileEntity(meta)) {
						// CraftBukkit start - don't create tile entity if placement failed
						if (getBlockID(x, y, z) != id) {
							return false;
						}
						//CraftBukkit end

						TileEntity tileEntity = getChunkBlockTileEntity(x, y, z);

						if (tileEntity == null) {
							tileEntity = block.createTileEntity(worldObj, meta);
							worldObj.setBlockTileEntity(wX, y, wZ, tileEntity);
						}

						if (tileEntity != null) {
							tileEntity.updateContainingBlockInfo();
							tileEntity.blockMetadata = meta;
						}
					}
				}

				isModified = true;
				return true;
			}
		}
	}

	@Override
	public TileEntity getChunkBlockTileEntity(int par1, int par2, int par3) {
		ChunkPosition position = new ChunkPosition(par1, par2, par3);
		TileEntity tileEntity = (TileEntity) this.chunkTileEntityMap.get(position);

		if (tileEntity != null && tileEntity.isInvalid()) {
			chunkTileEntityMap.remove(position);
			worldObj.loadedTileEntityList.remove(tileEntity);
			tileEntity = null;
		}

		if (tileEntity == null) {
			int id = this.getBlockID(par1, par2, par3);
			int meta = this.getBlockMetadata(par1, par2, par3);
			Block block;

			if (id <= 0 || (block = Block.blocksList[id]) == null || !block.hasTileEntity(meta)) {
				return null;
			}

			tileEntity = block.createTileEntity(this.worldObj, meta);
			this.worldObj.setBlockTileEntity(this.xPosition * 16 + par1, par2, this.zPosition * 16 + par3, tileEntity);

			tileEntity = (TileEntity) this.chunkTileEntityMap.get(position);
		}

		return tileEntity;
	}
}
