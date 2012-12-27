package me.nallar.tickthreading.patched;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveHandler;

@SuppressWarnings ("ForLoopReplaceableByForEach")
public abstract class PatchWorld extends World {
	public PatchWorld(ISaveHandler par1ISaveHandler, String par2Str, WorldProvider par3WorldProvider, WorldSettings par4WorldSettings, Profiler par5Profiler) {
		super(par1ISaveHandler, par2Str, par3WorldProvider, par4WorldSettings, par5Profiler);
	}

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
}
