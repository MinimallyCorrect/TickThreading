package me.nallar.patched.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.TwoWayReentrantReadWriteLock;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.world.ChunkEvent;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

@SuppressWarnings ("unchecked")
public abstract class PatchChunk extends Chunk {
	public Lock entityListWriteLock;
	public Lock entityListReadLock;

	public PatchChunk(World par1World, int par2, int par3) {
		super(par1World, par2, par3);
	}

	private List<TileEntity> toInvalidate;

	public void construct() {
		chunkTileEntityMap = new NonBlockingHashMap();
		toInvalidate = new ArrayList<TileEntity>();
		TwoWayReentrantReadWriteLock twoWayReentrantReadWriteLock = new TwoWayReentrantReadWriteLock();
		entityListWriteLock = twoWayReentrantReadWriteLock.writeLock();
		entityListReadLock = twoWayReentrantReadWriteLock.readLock();
	}

	@Override
	public String toString() {
		return "chunk at " + xPosition + ',' + zPosition;
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

		MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(this));
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

		MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(this));
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod") // Patcher makes x/zPosition and worldObj final
	@Override
	public void addEntity(Entity par1Entity) {
		int var2 = MathHelper.floor_double(par1Entity.posX / 16.0D);
		int var3 = MathHelper.floor_double(par1Entity.posZ / 16.0D);

		if (var2 != this.xPosition || var3 != this.zPosition) {
			FMLLog.log(Level.FINE, new Throwable(), "Entity %s added to the wrong chunk - expected x%d z%d, got x%d z%d", par1Entity.toString(), this.xPosition, this.zPosition, var2, var3);
			if (worldObj instanceof WorldServer) {
				Chunk correctChunk = ((WorldServer) worldObj).theChunkProviderServer.getChunkIfExists(xPosition, zPosition);
				if (correctChunk != null) {
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
}
