package me.nallar.tickthreading.minecraft.patched;

import java.util.Iterator;
import java.util.List;

import javassist.is.faulty.ThreadLocals;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.ISaveHandler;

@SuppressWarnings ("ForLoopReplaceableByForEach")
public abstract class PatchWorld extends World {
	public PatchWorld(ISaveHandler par1ISaveHandler, String par2Str, WorldProvider par3WorldProvider, WorldSettings par4WorldSettings, Profiler par5Profiler) {
		super(par1ISaveHandler, par2Str, par3WorldProvider, par4WorldSettings, par5Profiler);
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

		for (int var9 = var3; var9 < var4; ++var9) {
			for (int var10 = var7; var10 < var8; ++var10) {
				if (this.blockExists(var9, 64, var10)) {
					for (int var11 = var5 - 1; var11 < var6; ++var11) {
						Block var12 = Block.blocksList[this.getBlockId(var9, var11, var10)];

						if (var12 != null) {
							var12.addCollidingBlockToList(this, var9, var11, var10, par2AxisAlignedBB, collidingBoundingBoxes, par1Entity);
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

		if (!this.entityRemoval.isEmpty()) {
			for (Object tile : entityRemoval) {
				((TileEntity) tile).onChunkUnload();
			}
			this.loadedTileEntityList.removeAll(this.entityRemoval);
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
