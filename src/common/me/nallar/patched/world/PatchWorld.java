package me.nallar.patched.world;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSetMultimap;

import javassist.is.faulty.ThreadLocals;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.ForcedChunksRedirectMap;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayer;
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
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

@SuppressWarnings ("unchecked")
public abstract class PatchWorld extends World {
	private int forcedUpdateCount;
	@Declare
	public org.cliffc.high_scale_lib.NonBlockingHashMapLong<Integer> redstoneBurnoutMap_;
	@Declare
	public Set<Entity> unloadedEntitySet_;
	@Declare
	public Set<TileEntity> tileEntityRemovalSet_;
	@Declare
	public com.google.common.collect.ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forcedChunks_;
	@Declare
	public int tickCount_;

	public void construct() {
		tickCount = rand.nextInt(240); // So when different worlds do every N tick actions,
		// they won't all happen at the same time even if the worlds loaded at the same time
		tileEntityRemovalSet = new HashSet<TileEntity>();
		unloadedEntitySet = new HashSet<Entity>();
		redstoneBurnoutMap = new NonBlockingHashMapLong<Integer>();
	}

	public PatchWorld(ISaveHandler par1ISaveHandler, String par2Str, WorldProvider par3WorldProvider, WorldSettings par4WorldSettings, Profiler par5Profiler) {
		super(par1ISaveHandler, par2Str, par3WorldProvider, par4WorldSettings, par5Profiler);
	}

	@Override
	public void removeEntity(Entity entity) {
		if (entity == null) {
			return;
		}

		try {
			if (entity.riddenByEntity != null) {
				entity.riddenByEntity.mountEntity(null);
			}

			if (entity.ridingEntity != null) {
				entity.mountEntity(null);
			}

			entity.setDead();

			// The next instanceof, somehow, seems to throw NPEs. I don't even. :(
			// http://pastebin.com/zqDPsUjz
			if (entity instanceof EntityPlayer) {
				this.playerEntities.remove(entity);
				this.updateAllPlayersSleepingFlag();
			}
		} catch (Exception e) {
			Log.severe("Exception removing a player entity", e);
		}
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000 && y > 0 && y < 256) {
			try {
				return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockID(x & 15, y, z & 15);
			} catch (Throwable t) {
				Log.severe("Exception getting block ID in " + Log.name(this) + " at x,y,z" + x + ',' + y + ',' + z, t);
			}
		}
		return 0;
	}

	@Override
	@Declare
	public int getBlockIdWithoutLoad(int x, int y, int z) {
		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000 && y > 0 && y < 256) {
			try {
				Chunk chunk = ((ChunkProviderServer) chunkProvider).getChunkIfExists(x >> 4, z >> 4);
				return chunk == null ? -1 : chunk.getBlockID(x & 15, y, z & 15);
			} catch (Throwable t) {
				Log.severe("Exception getting block ID in " + Log.name(this) + " at x,y,z" + x + ',' + y + ',' + z, t);
			}
		}
		return 0;
	}

	@Override
	public EntityPlayer getClosestPlayer(double x, double y, double z, double maxRange) {
		double closestRange = Double.MAX_VALUE;
		EntityPlayer target = null;

		for (EntityPlayer playerEntity : (Iterable<EntityPlayer>) this.playerEntities) {
			if (!playerEntity.capabilities.disableDamage && playerEntity.isEntityAlive()) {
				double distanceSq = playerEntity.getDistanceSq(x, y, z);

				if ((maxRange < 0.0D || distanceSq < (maxRange * maxRange)) && (distanceSq < closestRange)) {
					closestRange = distanceSq;
					target = playerEntity;
				}
			}
		}

		return target;
	}

	@SuppressWarnings ("ConstantConditions")
	@Override
	public EntityPlayer getClosestVulnerablePlayer(double x, double y, double z, double maxRange) {
		double closestRange = Double.MAX_VALUE;
		EntityPlayer target = null;

		for (EntityPlayer playerEntity : (Iterable<EntityPlayer>) this.playerEntities) {
			if (!playerEntity.capabilities.disableDamage && playerEntity.isEntityAlive()) {
				double distanceSq = playerEntity.getDistanceSq(x, y, z);
				double effectiveMaxRange = maxRange;

				if (playerEntity.isSneaking()) {
					effectiveMaxRange = maxRange * 0.800000011920929D;
				}

				if (playerEntity.getHasActivePotion()) {
					float var18 = playerEntity.func_82243_bO();

					if (var18 < 0.1F) {
						var18 = 0.1F;
					}

					effectiveMaxRange *= (double) (0.7F * var18);
				}

				if ((maxRange < 0.0D || distanceSq < (effectiveMaxRange * effectiveMaxRange)) && (distanceSq < closestRange)) {
					closestRange = distanceSq;
					target = playerEntity;
				}
			}
		}

		return target;
	}

	@Override
	public EntityPlayer getPlayerEntityByName(String name) {
		for (EntityPlayer player : (Iterable<EntityPlayer>) playerEntities) {
			if (name.equals(player.username)) {
				return player;
			}
		}

		return null;
	}

	@Override
	protected void notifyBlockOfNeighborChange(int x, int y, int z, int par4) {
		if (!this.editingBlocks && !this.isRemote) {
			int var5 = this.getBlockIdWithoutLoad(x, y, z);
			Block var6 = var5 < 1 ? null : Block.blocksList[var5];

			if (var6 != null) {
				try {
					var6.onNeighborBlockChange(this, x, y, z, par4);
				} catch (Throwable t) {
					Log.severe("Exception while updating block neighbours", t);
				}
			}
		}
	}

	@Override
	public ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> getPersistentChunks() {
		return forcedChunks == null ? ForcedChunksRedirectMap.emptyMap : forcedChunks;
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
	@Declare
	public TileEntity getTEWithoutLoad(int x, int y, int z) {
		if (y >= 256) {
			return null;
		} else {
			Chunk chunk = ((ChunkProviderServer) this.chunkProvider).getChunkIfExists(x >> 4, z >> 4);
			return chunk == null ? null : chunk.getChunkBlockTileEntity(x & 15, y, z & 15);
		}
	}

	@Override
	public void updateEntityWithOptionalForce(Entity par1Entity, boolean par2) {
		int x = MathHelper.floor_double(par1Entity.posX);
		int z = MathHelper.floor_double(par1Entity.posZ);

		Boolean isForced = par1Entity.isForced;
		if (isForced == null || forcedUpdateCount++ % 7 == 0) {
			par1Entity.isForced = isForced = getPersistentChunks().containsKey(new ChunkCoordIntPair(x >> 4, z >> 4));
		}
		byte range = isForced ? (byte) 0 : 32;
		boolean canUpdate = !par2 || this.checkChunksExist(x - range, 0, z - range, x + range, 0, z + range);
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
				if (par1Entity.addedToChunk) {
					Chunk chunk = getChunkIfExists(par1Entity.chunkCoordX, par1Entity.chunkCoordZ);
					if (chunk != null) {
						chunk.removeEntityAtIndex(par1Entity, par1Entity.chunkCoordY);
					}
				}

				Chunk chunk = getChunkIfExists(var6, var8);
				if (chunk != null) {
					par1Entity.addedToChunk = true;
					chunk.addEntity(par1Entity);
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
		//noinspection RedundantCast
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
		int minX = MathHelper.floor_double(par2AxisAlignedBB.minX);
		int maxX = MathHelper.floor_double(par2AxisAlignedBB.maxX + 1.0D);
		int minY = MathHelper.floor_double(par2AxisAlignedBB.minY);
		int maxY = MathHelper.floor_double(par2AxisAlignedBB.maxY + 1.0D);
		int minZ = MathHelper.floor_double(par2AxisAlignedBB.minZ);
		int maxZ = MathHelper.floor_double(par2AxisAlignedBB.maxZ + 1.0D);

		int ystart = ((minY - 1) < 0) ? 0 : (minY - 1);
		for (int chunkx = (minX >> 4); chunkx <= ((maxX - 1) >> 4); chunkx++) {
			int cx = chunkx << 4;
			for (int chunkz = (minZ >> 4); chunkz <= ((maxZ - 1) >> 4); chunkz++) {
				Chunk chunk = this.getChunkIfExists(chunkx, chunkz);
				if (chunk == null) {
					continue;
				}
				// Compute ranges within chunk
				int cz = chunkz << 4;
				int xstart = (minX < cx) ? cx : minX;
				int xend = (maxX < (cx + 16)) ? maxX : (cx + 16);
				int zstart = (minZ < cz) ? cz : minZ;
				int zend = (maxZ < (cz + 16)) ? maxZ : (cz + 16);
				// Loop through blocks within chunk
				for (int x = xstart; x < xend; x++) {
					for (int z = zstart; z < zend; z++) {
						for (int y = ystart; y < maxY; y++) {
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
		List<Entity> var16 = this.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(var14, var14, var14), 100);

		for (Entity aVar16 : var16) {
			AxisAlignedBB var13 = aVar16.getBoundingBox();

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				return true;
			}

			var13 = par1Entity.getCollisionBox(aVar16);

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				return true;
			}
		}

		return false;
	}

	@Override
	@Declare
	public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB, int limit) {
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
				Chunk chunk = this.getChunkIfExists(chunkx, chunkz);
				if (chunk == null) {
					continue;
				}
				// Compute ranges within chunk
				int cz = chunkz << 4;
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
		List<Entity> var16 = this.getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB.expand(var14, var14, var14), limit);

		for (Entity aVar16 : var16) {
			AxisAlignedBB var13 = aVar16.getBoundingBox();

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				collidingBoundingBoxes.add(var13);
			}

			var13 = par1Entity.getCollisionBox(aVar16);

			if (var13 != null && var13.intersectsWith(par2AxisAlignedBB)) {
				collidingBoundingBoxes.add(var13);
			}
		}

		return collidingBoundingBoxes;
	}

	@Override
	public List getCollidingBoundingBoxes(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
		return getCollidingBoundingBoxes(par1Entity, par2AxisAlignedBB, 2000);
	}

	@Override
	public void addTileEntity(Collection tileEntities) {
		List dest = scanningTileEntities ? addedTileEntityList : loadedTileEntityList;
		for (TileEntity tileEntity : (Iterable<TileEntity>) tileEntities) {
			tileEntity.validate();
			if (tileEntity.canUpdate()) {
				dest.add(tileEntity);
			}
		}
	}

	@Override
	@Declare
	public List getEntitiesWithinAABBExcludingEntity(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB, int limit) {
		List entitiesWithinAABBExcludingEntity = (List) ThreadLocals.entitiesWithinAABBExcludingEntity.get();
		entitiesWithinAABBExcludingEntity.clear();
		int minX = MathHelper.floor_double((par2AxisAlignedBB.minX - MAX_ENTITY_RADIUS) / 16.0D);
		int maxX = MathHelper.floor_double((par2AxisAlignedBB.maxX + MAX_ENTITY_RADIUS) / 16.0D);
		int minZ = MathHelper.floor_double((par2AxisAlignedBB.minZ - MAX_ENTITY_RADIUS) / 16.0D);
		int maxZ = MathHelper.floor_double((par2AxisAlignedBB.maxZ + MAX_ENTITY_RADIUS) / 16.0D);

		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				Chunk chunk = getChunkIfExists(x, z);
				if (chunk != null) {
					chunk.getEntitiesWithinAABBForEntity(par1Entity, par2AxisAlignedBB, entitiesWithinAABBExcludingEntity, limit);
				}
			}
		}

		return entitiesWithinAABBExcludingEntity;
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
				Chunk chunk = getChunkIfExists(var7, var8);
				if (chunk != null) {
					chunk.getEntitiesWithinAABBForEntity(par1Entity, par2AxisAlignedBB, entitiesWithinAABBExcludingEntity);
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
		int count = 0;

		for (Entity e : (Iterable<Entity>) this.loadedEntityList) {

			if (entityType.isAssignableFrom(e.getClass())) {
				++count;
			}
		}

		return count;
	}

	@Override
	public boolean checkChunksExist(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		if (minY > 255 || maxY < 0) {
			return false;
		}

		minX >>= 4;
		minZ >>= 4;
		maxX >>= 4;
		maxZ >>= 4;

		ChunkProviderServer chunkProviderServer = (ChunkProviderServer) chunkProvider;
		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				if (!chunkProviderServer.chunkExists(x, z)) {
					return false;
				}
			}
		}

		return true;
	}

	@Override
	public void unloadEntities(List entitiesToUnload) {
		this.unloadedEntitySet.addAll(entitiesToUnload);
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
		int var3;
		int var13;
		if (this.loadedEntityList instanceof EntityList) {
			((EntityList) this.loadedEntityList).manager.batchRemoveEntities(unloadedEntitySet);
		} else {
			this.loadedEntityList.removeAll(this.unloadedEntitySet);

			for (Entity entity : unloadedEntitySet) {
				var3 = entity.chunkCoordX;
				var13 = entity.chunkCoordZ;

				if (entity.addedToChunk) {
					Chunk chunk = getChunkIfExists(var3, var13);
					if (chunk != null) {
						chunk.removeEntity(entity);
					}
				}

				releaseEntitySkin(entity);
			}
		}
		this.unloadedEntitySet.clear();
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

					if (var2.addedToChunk) {
						Chunk chunk = getChunkIfExists(var3, var13);
						if (chunk != null) {
							chunk.removeEntity(var2);
						}
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

					Chunk var11 = this.getChunkIfExists(var9.xCoord >> 4, var9.zCoord >> 4);

					if (var11 != null) {
						var11.cleanChunkBlockTileEntity(var9.xCoord & 15, var9.yCoord, var9.zCoord & 15);
					}
				}
			}
		}

		this.theProfiler.endStartSection("removingTileEntities");

		if (!this.tileEntityRemovalSet.isEmpty()) {
			if (loadedTileEntityList instanceof LoadedTileEntityList) {
				((LoadedTileEntityList) loadedTileEntityList).manager.batchRemoveTileEntities(tileEntityRemovalSet);
			} else {
				for (TileEntity tile : tileEntityRemovalSet) {
					tile.onChunkUnload();
				}
				this.loadedTileEntityList.removeAll(tileEntityRemovalSet);
			}
			tileEntityRemovalSet.clear();
		}

		this.scanningTileEntities = false;

		this.theProfiler.endStartSection("pendingTileEntities");

		if (!this.addedTileEntityList.isEmpty()) {
			for (TileEntity te : (Iterable<TileEntity>) this.addedTileEntityList) {
				if (te.isInvalid()) {
					Chunk var15 = this.getChunkIfExists(te.xCoord >> 4, te.zCoord >> 4);

					if (var15 != null) {
						var15.cleanChunkBlockTileEntity(te.xCoord & 15, te.yCoord, te.zCoord & 15);
					}
				} else {
					if (!this.loadedTileEntityList.contains(te)) {
						this.loadedTileEntityList.add(te);
					}
				}
			}

			this.addedTileEntityList.clear();
		}

		this.theProfiler.endSection();
		this.theProfiler.endSection();
	}

	@Override
	@Declare
	public Chunk getChunkIfExists(int x, int z) {
		return ((ChunkProviderServer) chunkProvider).getChunkIfExists(x, z);
	}

	@Override
	public void markTileEntityForDespawn(TileEntity tileEntity) {
		tileEntityRemovalSet.add(tileEntity);
	}
}
