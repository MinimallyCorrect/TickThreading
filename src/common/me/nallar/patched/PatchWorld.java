package me.nallar.patched;

import java.util.Iterator;
import java.util.List;

import javassist.is.faulty.ThreadLocals;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

@SuppressWarnings ("ForLoopReplaceableByForEach")
public abstract class PatchWorld extends World {
	private int forcedUpdateCount;
	@Declare
	public int tickCount_;

	public void construct() {
		tickCount = rand.nextInt(5);
	}

	public PatchWorld(ISaveHandler par1ISaveHandler, String par2Str, WorldProvider par3WorldProvider, WorldSettings par4WorldSettings, Profiler par5Profiler) {
		super(par1ISaveHandler, par2Str, par3WorldProvider, par4WorldSettings, par5Profiler);
	}

	@Override
	public TileEntity getBlockTileEntity(int x, int y, int z) {
		if (y >= 256) {
			return null;
		} else {
			Chunk chunk = this.getChunkFromChunkCoords(x >> 4, z >> 4);
			return chunk == null ? null : chunk.getChunkBlockTileEntity(x & 15, y, z & 15);
		}
	}

	@Override
	public void updateEntityWithOptionalForce(Entity par1Entity, boolean par2) {
		int var3 = MathHelper.floor_double(par1Entity.posX);
		int var4 = MathHelper.floor_double(par1Entity.posZ);

		Boolean isForced = par1Entity.isForced;
		if (isForced == null || forcedUpdateCount++ % 7 == 0) {
			par1Entity.isForced = isForced = getPersistentChunks().containsKey(new ChunkCoordIntPair(var3 >> 4, var4 >> 4));
		}
		byte var5 = isForced ? (byte) 0 : 32;
		boolean canUpdate = !par2 || this.checkChunksExist(var3 - var5, 0, var4 - var5, var3 + var5, 0, var4 + var5);
		if (canUpdate) {
			par1Entity.lastTickPosX = par1Entity.posX;
			par1Entity.lastTickPosY = par1Entity.posY;
			par1Entity.lastTickPosZ = par1Entity.posZ;
			par1Entity.prevRotationYaw = par1Entity.rotationYaw;
			par1Entity.prevRotationPitch = par1Entity.rotationPitch;

			if (par2 && par1Entity.addedToChunk) {
				if (par1Entity.ridingEntity != null) {
					par1Entity.updateRidden();
				} else {
					++par1Entity.ticksExisted;
					par1Entity.onUpdate();
				}
			}

			this.theProfiler.startSection("chunkCheck");

			if (Double.isNaN(par1Entity.posX) || Double.isInfinite(par1Entity.posX)) {
				par1Entity.posX = par1Entity.lastTickPosX;
			}

			if (Double.isNaN(par1Entity.posY) || Double.isInfinite(par1Entity.posY)) {
				par1Entity.posY = par1Entity.lastTickPosY;
			}

			if (Double.isNaN(par1Entity.posZ) || Double.isInfinite(par1Entity.posZ)) {
				par1Entity.posZ = par1Entity.lastTickPosZ;
			}

			if (Double.isNaN((double) par1Entity.rotationPitch) || Double.isInfinite((double) par1Entity.rotationPitch)) {
				par1Entity.rotationPitch = par1Entity.prevRotationPitch;
			}

			if (Double.isNaN((double) par1Entity.rotationYaw) || Double.isInfinite((double) par1Entity.rotationYaw)) {
				par1Entity.rotationYaw = par1Entity.prevRotationYaw;
			}

			int var6 = MathHelper.floor_double(par1Entity.posX / 16.0D);
			int var7 = MathHelper.floor_double(par1Entity.posY / 16.0D);
			int var8 = MathHelper.floor_double(par1Entity.posZ / 16.0D);

			if (!par1Entity.addedToChunk || par1Entity.chunkCoordX != var6 || par1Entity.chunkCoordY != var7 || par1Entity.chunkCoordZ != var8) {
				if (par1Entity.addedToChunk && this.chunkExists(par1Entity.chunkCoordX, par1Entity.chunkCoordZ)) {
					this.getChunkFromChunkCoords(par1Entity.chunkCoordX, par1Entity.chunkCoordZ).removeEntityAtIndex(par1Entity, par1Entity.chunkCoordY);
				}

				if (this.chunkExists(var6, var8)) {
					par1Entity.addedToChunk = true;
					this.getChunkFromChunkCoords(var6, var8).addEntity(par1Entity);
				} else {
					par1Entity.addedToChunk = false;
				}
			}

			this.theProfiler.endSection();

			if (par2 && par1Entity.addedToChunk && par1Entity.riddenByEntity != null) {
				if (!par1Entity.riddenByEntity.isDead && par1Entity.riddenByEntity.ridingEntity == par1Entity) {
					this.updateEntity(par1Entity.riddenByEntity);
				} else {
					par1Entity.riddenByEntity.ridingEntity = null;
					par1Entity.riddenByEntity = null;
				}
			}
		}
	}

	@Override
	public void addLoadedEntities(List par1List) {
		EntityTracker entityTracker = null;
		if (((Object) this instanceof WorldServer)) {
			entityTracker = ((WorldServer) (Object) this).getEntityTracker();
		}
		for (int var2 = 0; var2 < par1List.size(); ++var2) {
			Entity entity = (Entity) par1List.get(var2);
			if (MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, this))) {
				par1List.remove(var2--);
			} else if (entityTracker == null || !entityTracker.isTracking(entity.entityId)) {
				loadedEntityList.add(entity);
				this.obtainEntitySkin(entity);
			}
		}
	}

	@Override
	@Declare
	public boolean hasCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
		List collidingBoundingBoxes = (List) ThreadLocals.collidingBoundingBoxes.get();
		collidingBoundingBoxes.clear();
		int var3 = MathHelper.floor_double(par2AxisAlignedBB.minX);
		int var4 = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0D);
		int var5 = MathHelper.floor_double(par2AxisAlignedBB.minY);
		int var6 = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0D);
		int var7 = MathHelper.floor_double(par2AxisAlignedBB.minZ);
		int var8 = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0D);

		int ystart = ((var5 - 1) < 0) ? 0 : (var5 - 1);
		for (int chunkx = (var3 >> 4); chunkx <= ((var4 - 1) >> 4); chunkx++) {
			int cx = chunkx << 4;
			for (int chunkz = (var7 >> 4); chunkz <= ((var8 - 1) >> 4); chunkz++) {
				if (!this.chunkExists(chunkx, chunkz)) {
					continue;
				}
				int cz = chunkz << 4;
				Chunk chunk = this.getChunkFromChunkCoords(chunkx, chunkz);
				// Compute ranges within chunk
				int xstart = (var3 < cx) ? cx : var3;
				int xend = (var4 < (cx + 16)) ? var4 : (cx + 16);
				int zstart = (var7 < cz) ? cz : var7;
				int zend = (var8 < (cz + 16)) ? var8 : (cz + 16);
				// Loop through blocks within chunk
				for (int x = xstart; x < xend; x++) {
					for (int z = zstart; z < zend; z++) {
						for (int y = ystart; y < var6; y++) {
							int blkid = chunk.getBlockID(x - cx, y, z - cz);
							if (blkid > 0) {
								Block block = Block.blocksList[blkid];
								if (block != null) {
									block.addCollidingBlockToList(this, x, y, z, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
								}
								if (!collidingBoundingBoxes.isEmpty()) {
									return true;
								}
							}
						}
					}
				}
			}
		}

		double var14 = 0.25D;
		List var16 = this.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(var14, var14, var14));

		for (int var15 = 0; var15 < var16.size(); ++var15) {
			AxisAlignedBB var13 = ((Entity) var16.get(var15)).getBoundingBox();

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				return true;
			}

			var13 = par1Entity.getCollisionBox((Entity) var16.get(var15));

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
		List collidingBoundingBoxes = (List) ThreadLocals.collidingBoundingBoxes.get();
		collidingBoundingBoxes.clear();
		int var3 = MathHelper.floor_double(par2AxisAlignedBB.minX);
		int var4 = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0D);
		int var5 = MathHelper.floor_double(par2AxisAlignedBB.minY);
		int var6 = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0D);
		int var7 = MathHelper.floor_double(par2AxisAlignedBB.minZ);
		int var8 = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0D);

		int ystart = ((var5 - 1) < 0) ? 0 : (var5 - 1);
		for (int chunkx = (var3 >> 4); chunkx <= ((var4 - 1) >> 4); chunkx++) {
			int cx = chunkx << 4;
			for (int chunkz = (var7 >> 4); chunkz <= ((var8 - 1) >> 4); chunkz++) {
				if (!this.chunkExists(chunkx, chunkz)) {
					continue;
				}
				int cz = chunkz << 4;
				Chunk chunk = this.getChunkFromChunkCoords(chunkx, chunkz);
				// Compute ranges within chunk
				int xstart = (var3 < cx) ? cx : var3;
				int xend = (var4 < (cx + 16)) ? var4 : (cx + 16);
				int zstart = (var7 < cz) ? cz : var7;
				int zend = (var8 < (cz + 16)) ? var8 : (cz + 16);
				// Loop through blocks within chunk
				for (int x = xstart; x < xend; x++) {
					for (int z = zstart; z < zend; z++) {
						for (int y = ystart; y < var6; y++) {
							int blkid = chunk.getBlockID(x - cx, y, z - cz);
							if (blkid > 0) {
								Block block = Block.blocksList[blkid];
								if (block != null) {
									block.addCollidingBlockToList(this, x, y, z, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
								}
							}
						}
					}
				}
			}
		}

		double var14 = 0.25D;
		List var16 = this.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(var14, var14, var14));

		for (int var15 = 0; var15 < var16.size(); ++var15) {
			AxisAlignedBB var13 = ((Entity) var16.get(var15)).getBoundingBox();

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				collidingBoundingBoxes.add(var13);
			}

			var13 = par1Entity.getCollisionBox((Entity) var16.get(var15));

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				collidingBoundingBoxes.add(var13);
			}
		}

		return collidingBoundingBoxes;
	}

	@Override
	public List getEntitiesWithinAABBExcludingEntity(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
		List entitiesWithinAABBExcludingEntity = (List) ThreadLocals.entitiesWithinAABBExcludingEntity.get();
		entitiesWithinAABBExcludingEntity.clear();
		int var3 = MathHelper.floor_double((par2AxisAlignedBB.minX - MAX_ENTITY_RADIUS) / 16.0D);
		int var4 = MathHelper.floor_double((par2AxisAlignedBB.maxX + MAX_ENTITY_RADIUS) / 16.0D);
		int var5 = MathHelper.floor_double((par2AxisAlignedBB.minZ - MAX_ENTITY_RADIUS) / 16.0D);
		int var6 = MathHelper.floor_double((par2AxisAlignedBB.maxZ + MAX_ENTITY_RADIUS) / 16.0D);

		for (int var7 = var3; var7 <= var4; ++var7) {
			for (int var8 = var5; var8 <= var6; ++var8) {
				if (this.chunkExists(var7, var8)) {
					this.getChunkFromChunkCoords(var7, var8).getEntitiesWithinAABBForEntity(par1Entity, par2AxisAlignedBB, entitiesWithinAABBExcludingEntity);
				}
			}
		}

		return entitiesWithinAABBExcludingEntity;
	}

	@Override
	public int countEntities(Class entityType) {
		if (loadedEntityList instanceof EntityList) {
			return ((EntityList) this.loadedEntityList).manager.getEntityCount(entityType);
		}
		int var2 = 0;

		for (int var3 = 0; var3 < this.loadedEntityList.size(); ++var3) {
			Entity var4 = (Entity) this.loadedEntityList.get(var3);

			if (entityType.isAssignableFrom(var4.getClass())) {
				++var2;
			}
		}

		return var2;
	}

	@Override
	public void unloadEntities(List entitiesToUnload) {
		if (loadedEntityList instanceof EntityList) {
			for (Entity entity : (List<? extends Entity>) entitiesToUnload) {
				this.loadedEntityList.remove(entity);
				this.releaseEntitySkin(entity);
			}
		} else {
			this.unloadedEntityList.addAll(entitiesToUnload);
		}
	}

	@Override
	public void updateEntities() {
		this.theProfiler.startSection("entities");
		this.theProfiler.startSection("global");
		int var1;
		Entity var2;
		CrashReport var4;
		CrashReportCategory var5;

		for (var1 = 0; var1 < this.weatherEffects.size(); ++var1) {
			var2 = (Entity) this.weatherEffects.get(var1);

			try {
				++var2.ticksExisted;
				var2.onUpdate();
			} catch (Throwable var6) {
				var4 = CrashReport.makeCrashReport(var6, "Ticking entity");
				var5 = var4.makeCategory("Entity being ticked");
				var2.func_85029_a(var5);

				throw new ReportedException(var4);
			}

			if (var2.isDead) {
				this.weatherEffects.remove(var1--);
			}
		}

		this.theProfiler.endStartSection("remove");
		this.loadedEntityList.removeAll(this.unloadedEntityList);
		int var3;
		int var13;

		for (var1 = 0; var1 < this.unloadedEntityList.size(); ++var1) {
			var2 = (Entity) this.unloadedEntityList.get(var1);
			var3 = var2.chunkCoordX;
			var13 = var2.chunkCoordZ;

			if (var2.addedToChunk && this.chunkExists(var3, var13)) {
				this.getChunkFromChunkCoords(var3, var13).removeEntity(var2);
			}
		}

		for (var1 = 0; var1 < this.unloadedEntityList.size(); ++var1) {
			this.releaseEntitySkin((Entity) this.unloadedEntityList.get(var1));
		}

		this.unloadedEntityList.clear();
		this.theProfiler.endStartSection("regular");

		boolean shouldTickThreadingTick = true;

		if (this.loadedEntityList instanceof EntityList) {
			((EntityList) this.loadedEntityList).manager.doTick();
			shouldTickThreadingTick = false;
		} else {
			for (var1 = 0; var1 < this.loadedEntityList.size(); ++var1) {
				var2 = (Entity) this.loadedEntityList.get(var1);

				if (var2.ridingEntity != null) {
					if (!var2.ridingEntity.isDead && var2.ridingEntity.riddenByEntity == var2) {
						continue;
					}

					var2.ridingEntity.riddenByEntity = null;
					var2.ridingEntity = null;
				}

				this.theProfiler.startSection("tick");

				if (!var2.isDead) {
					try {
						this.updateEntity(var2);
					} catch (Throwable var7) {
						var4 = CrashReport.makeCrashReport(var7, "Ticking entity");
						var5 = var4.makeCategory("Entity being ticked");
						var2.func_85029_a(var5);

						throw new ReportedException(var4);
					}
				}

				this.theProfiler.endSection();
				this.theProfiler.startSection("remove");

				if (var2.isDead) {
					var3 = var2.chunkCoordX;
					var13 = var2.chunkCoordZ;

					if (var2.addedToChunk && this.chunkExists(var3, var13)) {
						this.getChunkFromChunkCoords(var3, var13).removeEntity(var2);
					}

					this.loadedEntityList.remove(var1--);
					this.releaseEntitySkin(var2);
				}

				this.theProfiler.endSection();
			}
		}

		this.theProfiler.endStartSection("tileEntities");

		if (this.loadedEntityList instanceof EntityList) {
			if (shouldTickThreadingTick) {
				((EntityList) this.loadedEntityList).manager.doTick();
			}
		} else {
			this.scanningTileEntities = true;

			Iterator var14 = this.loadedTileEntityList.iterator();

			while (var14.hasNext()) {
				TileEntity var9 = (TileEntity) var14.next();

				if (!var9.isInvalid() && var9.func_70309_m() && this.blockExists(var9.xCoord, var9.yCoord, var9.zCoord)) {
					try {
						var9.updateEntity();
					} catch (Throwable var8) {
						var4 = CrashReport.makeCrashReport(var8, "Ticking tile entity");
						var5 = var4.makeCategory("Tile entity being ticked");
						var9.func_85027_a(var5);

						throw new ReportedException(var4);
					}
				}

				if (var9.isInvalid()) {
					var14.remove();

					if (this.chunkExists(var9.xCoord >> 4, var9.zCoord >> 4)) {
						Chunk var11 = this.getChunkFromChunkCoords(var9.xCoord >> 4, var9.zCoord >> 4);

						if (var11 != null) {
							var11.cleanChunkBlockTileEntity(var9.xCoord & 15, var9.yCoord, var9.zCoord & 15);
						}
					}
				}
			}
		}

		this.theProfiler.endStartSection("removingTileEntities");

		if (!this.entityRemoval.isEmpty()) {
			if (loadedTileEntityList instanceof LoadedTileEntityList) {
				((LoadedTileEntityList) loadedTileEntityList).manager.batchRemove(entityRemoval);
			} else {
				for (Object tile : entityRemoval) {
					((TileEntity) tile).onChunkUnload();
				}
				this.loadedTileEntityList.removeAll(this.entityRemoval);
			}
			this.entityRemoval.clear();
		}

		this.scanningTileEntities = false;

		this.theProfiler.endStartSection("pendingTileEntities");

		if (!this.addedTileEntityList.isEmpty()) {
			for (int var10 = 0; var10 < this.addedTileEntityList.size(); ++var10) {
				TileEntity var12 = (TileEntity) this.addedTileEntityList.get(var10);

				if (!var12.isInvalid()) {
					if (!this.loadedTileEntityList.contains(var12)) {
						this.loadedTileEntityList.add(var12);
					}
				} else {
					if (this.chunkExists(var12.xCoord >> 4, var12.zCoord >> 4)) {
						Chunk var15 = this.getChunkFromChunkCoords(var12.xCoord >> 4, var12.zCoord >> 4);

						if (var15 != null) {
							var15.cleanChunkBlockTileEntity(var12.xCoord & 15, var12.yCoord, var12.zCoord & 15);
						}
					}
				}
			}

			this.addedTileEntityList.clear();
		}

		this.theProfiler.endSection();
		this.theProfiler.endSection();
	}
}
