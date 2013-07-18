package nallar.patched.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableSetMultimap;

import nallar.collections.SynchronizedSet;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.minecraft.entitylist.EntityList;
import nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.command.IEntitySelector;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

@SuppressWarnings ("unchecked")
public abstract class PatchWorld extends World {
	private static final double COLLISION_RANGE = 2D;
	private int forcedUpdateCount;
	@Declare
	public ThreadLocal<Boolean> inPlaceEvent_;
	@Declare
	public org.cliffc.high_scale_lib.NonBlockingHashMapLong<Integer> redstoneBurnoutMap_;
	@Declare
	public nallar.collections.SynchronizedSet<Entity> unloadedEntitySet_;
	@Declare
	public nallar.collections.SynchronizedSet<TileEntity> tileEntityRemovalSet_;
	@Declare
	public com.google.common.collect.ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forcedChunks_;
	@Declare
	public int tickCount_;
	private boolean warnedWrongList;
	@Declare
	public int dimensionId_;
	private String cachedName;
	@Declare
	public boolean multiverseWorld_;
	@Declare
	public int originalDimension_;
	@Declare
	public boolean unloaded_;
	@Declare
	public Chunk emptyChunk_;
	@Declare
	public boolean loadEventFired_;
	@Declare
	public boolean forcedChunksInited_;
	public Object auraLock;

	public void construct() {
		auraLock = new Object();
		tickCount = rand.nextInt(240); // So when different worlds do every N tick actions,
		// they won't all happen at the same time even if the worlds loaded at the same time
		tileEntityRemovalSet = new SynchronizedSet<TileEntity>();
		unloadedEntitySet = new SynchronizedSet<Entity>();
		redstoneBurnoutMap = new NonBlockingHashMapLong<Integer>();
		if (dimensionId == 0) {
			dimensionId = provider.dimensionId;
		}
	}

	public PatchWorld(ISaveHandler par1ISaveHandler, String par2Str, WorldProvider par3WorldProvider, WorldSettings par4WorldSettings, Profiler par5Profiler) {
		super(par1ISaveHandler, par2Str, par3WorldProvider, par4WorldSettings, par5Profiler);
	}

	@Override
	@Declare
	public boolean onClient() {
		return isRemote;
	}

	@Override
	@Declare
	public void setDimension(int dimensionId) {
		WorldProvider provider = this.provider;
		this.dimensionId = dimensionId;
		if (provider.dimensionId != dimensionId) {
			try {
				DimensionManager.registerDimension(dimensionId, provider.dimensionId);
			} catch (Throwable t) {
				Log.warning("Failed to register corrected dimension ID with DimensionManager", t);
			}
			originalDimension = provider.dimensionId;
			multiverseWorld = true;
			provider.dimensionId = dimensionId;
		}
		cachedName = null;
		Log.fine("Set dimension ID for " + this.getName());
		if (TickThreading.instance.getManager(this) != null) {
			Log.severe("Set corrected dimension too late!");
		}
	}

	@Override
	@Declare
	public int getDimension() {
		return dimensionId;
	}

	@Override
	@Declare
	public String getName() {
		String name = cachedName;
		if (name != null) {
			return name;
		}
		int dimensionId = getDimension();
		name = worldInfo.getWorldName();
		if (name.equals("DIM" + dimensionId) || "world".equals(name)) {
			name = provider.getDimensionName();
		}
		if (name.startsWith("world_") && name.length() != 6) {
			name = name.substring(6);
		}
		cachedName = name = (name + '/' + dimensionId + (isRemote ? "-r" : ""));
		return name;
	}

	@Override
	public boolean isBlockNormalCube(int x, int y, int z) {
		int id = getBlockId(x, y, z);
		Block block = Block.blocksList[id];
		return block != null && block.isBlockNormalCube(this, x, y, z);
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

			if (entity instanceof EntityPlayer) {
				if (playerEntities == null) {
					// The world has been unloaded and cleaned already, so we can't remove the player entity.
					return;
				}
				this.playerEntities.remove(entity);
				this.updateAllPlayersSleepingFlag();
			}
		} catch (Exception e) {
			Log.severe("Exception removing an entity", e);
		}
	}

	@Override
	public int getBlockId(int x, int y, int z) {
		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000 && y >= 0 && y < 256) {
			try {
				return getChunkFromChunkCoords(x >> 4, z >> 4).getBlockID(x & 15, y, z & 15);
			} catch (Throwable t) {
				Log.severe("Exception getting block ID in " + Log.pos(this, x, y, z), t);
			}
		}
		return 0;
	}

	@Override
	@Declare
	public int getBlockIdWithoutLoad(int x, int y, int z) {
		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000 && y >= 0 && y < 256) {
			try {
				Chunk chunk = getChunkIfExists(x >> 4, z >> 4);
				return chunk == null ? -1 : chunk.getBlockID(x & 15, y, z & 15);
			} catch (Throwable t) {
				Log.severe("Exception getting block ID in " + Log.pos(this, x, y, z), t);
			}
		}
		return 0;
	}

	@Override
	public Chunk getChunkFromChunkCoords(int x, int z) {
		return chunkProvider.provideChunk(x, z);
	}

	@Override
	@Declare
	public Chunk getChunkFromChunkCoordsWithLoad(int x, int z) {
		return chunkProvider.loadChunk(x, z);
	}

	@Override
	@Declare
	public int getBlockIdWithLoad(int x, int y, int z) {
		if (y >= 256) {
			return 0;
		} else {
			return chunkProvider.loadChunk(x >> 4, z >> 4).getBlockID(x & 15, y, z & 15);
		}
	}

	@Override
	public EntityPlayer getClosestPlayer(double x, double y, double z, double maxRange) {
		double closestRange = Double.MAX_VALUE;
		EntityPlayer target = null;

		for (EntityPlayer playerEntity : (Iterable<EntityPlayer>) this.playerEntities) {
			double distanceSq = playerEntity.getDistanceSq(x, y, z);

			if ((maxRange < 0.0D || distanceSq < (maxRange * maxRange)) && (distanceSq < closestRange)) {
				closestRange = distanceSq;
				target = playerEntity;
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
					Log.severe("Exception while updating block neighbours " + Log.pos(this, x, y, z), t);
				}
			}
		}
	}

	@Override
	public ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> getPersistentChunks() {
		ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forcedChunks = this.forcedChunks;
		return forcedChunks == null ? ImmutableSetMultimap.<ChunkCoordIntPair, ForgeChunkManager.Ticket>of() : forcedChunks;
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
	@Declare
	public TileEntity getTEWithLoad(int x, int y, int z) {
		if (y >= 256 || y < 0) {
			return null;
		} else {
			return chunkProvider.loadChunk(x >> 4, z >> 4).getChunkBlockTileEntity(x & 15, y, z & 15);
		}
	}

	@Override
	public void updateEntityWithOptionalForce(Entity entity, boolean notForced) {
		Chunk chunk = entity.chunk;
		if (notForced && chunk != null) {
			if (chunk.partiallyUnloaded) {
				chunk.removeEntity(entity);
				entity.addedToChunk = false;
				return;
			} else if (chunk.queuedUnload) {
				return;
			}
		}
		int x = MathHelper.floor_double(entity.posX);
		int z = MathHelper.floor_double(entity.posZ);
		boolean periodicUpdate = forcedUpdateCount++ % 32 == 0;
		Boolean isForced_ = entity.isForced;
		if (isForced_ == null || periodicUpdate) {
			entity.isForced = isForced_ = getPersistentChunks().containsKey(new ChunkCoordIntPair(x >> 4, z >> 4));
		}
		boolean isForced = isForced_;
		Boolean canUpdate_ = entity.canUpdate;
		if (canUpdate_ == null || periodicUpdate) {
			byte range = isForced ? (byte) 0 : 48;
			entity.canUpdate = canUpdate_ = !notForced || this.checkChunksExist(x - range, 0, z - range, x + range, 0, z + range);
		} else if (canUpdate_) {
			entity.canUpdate = canUpdate_ = !notForced || chunk != null || chunkExists(x >> 4, z >> 4);
		}
		boolean canUpdate = canUpdate_;
		if (canUpdate) {
			entity.lastTickPosX = entity.posX;
			entity.lastTickPosY = entity.posY;
			entity.lastTickPosZ = entity.posZ;
			entity.prevRotationYaw = entity.rotationYaw;
			entity.prevRotationPitch = entity.rotationPitch;

			if (notForced && entity.addedToChunk) {
				if (entity.ridingEntity != null) {
					entity.updateRidden();
				} else {
					++entity.ticksExisted;
					entity.onUpdate();
				}
			}

			this.theProfiler.startSection("chunkCheck");

			if (Double.isNaN(entity.posX) || Double.isInfinite(entity.posX)) {
				entity.posX = entity.lastTickPosX;
			}

			if (Double.isNaN(entity.posY) || Double.isInfinite(entity.posY)) {
				entity.posY = entity.lastTickPosY;
			}

			if (Double.isNaN(entity.posZ) || Double.isInfinite(entity.posZ)) {
				entity.posZ = entity.lastTickPosZ;
			}

			if (Double.isNaN((double) entity.rotationPitch) || Double.isInfinite((double) entity.rotationPitch)) {
				entity.rotationPitch = entity.prevRotationPitch;
			}

			if (Double.isNaN((double) entity.rotationYaw) || Double.isInfinite((double) entity.rotationYaw)) {
				entity.rotationYaw = entity.prevRotationYaw;
			}

			int cX = MathHelper.floor_double(entity.posX) >> 4;
			int cY = MathHelper.floor_double(entity.posY) >> 4;
			int cZ = MathHelper.floor_double(entity.posZ) >> 4;

			if (!entity.addedToChunk || entity.chunkCoordX != cX || entity.chunkCoordY != cY || entity.chunkCoordZ != cZ) {
				synchronized (entity) {
					if (entity.addedToChunk) {
						if (chunk == null) {
							chunk = getChunkIfExists(entity.chunkCoordX, entity.chunkCoordZ);
						}
						if (chunk != null) {
							chunk.removeEntityAtIndex(entity, entity.chunkCoordY);
						}
					}

					chunk = getChunkIfExists(cX, cZ);
					if (chunk != null) {
						entity.addedToChunk = true;
						chunk.addEntity(entity);
					} else {
						entity.addedToChunk = false;
					}
					entity.chunk = chunk;
				}
			}

			this.theProfiler.endSection();

			if (notForced && entity.addedToChunk && entity.riddenByEntity != null) {
				Entity riddenByEntity = entity.riddenByEntity;
				if (!riddenByEntity.isDead && riddenByEntity.ridingEntity == entity) {
					this.updateEntity(riddenByEntity);
				} else {
					riddenByEntity.ridingEntity = null;
					entity.riddenByEntity = null;
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
			if (entity == null) {
				Log.warning("Null entity in chunk during world load", new Throwable());
				par1List.remove(var2--);
			} else if (MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, this))) {
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
		ArrayList collidingBoundingBoxes = new ArrayList();
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
		ArrayList collidingBoundingBoxes = new ArrayList();
		int minX = MathHelper.floor_double(par2AxisAlignedBB.minX);
		int maxX = MathHelper.floor_double(par2AxisAlignedBB.maxX) + 1;
		int minY = MathHelper.floor_double(par2AxisAlignedBB.minY);
		int maxY = MathHelper.floor_double(par2AxisAlignedBB.maxY) + 1;
		int minZ = MathHelper.floor_double(par2AxisAlignedBB.minZ);
		int maxZ = MathHelper.floor_double(par2AxisAlignedBB.maxZ) + 1;

		if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
			return collidingBoundingBoxes;
		}

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
				// Compute ranges within chunk
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
							}
						}
					}
				}
				if (collidingBoundingBoxes.size() >= limit) {
					return collidingBoundingBoxes;
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
			try {
				tileEntity.validate();
			} catch (Throwable t) {
				Log.severe("Tile Entity " + tileEntity + " failed to validate, it will be ignored.", t);
			}
			if (tileEntity.canUpdate()) {
				dest.add(tileEntity);
			}
		}
	}

	@Override
	@Declare
	public List getEntitiesWithinAABBExcludingEntity(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB, int limit) {
		ArrayList entitiesWithinAABBExcludingEntity = new ArrayList();
		int minX = MathHelper.floor_double((par2AxisAlignedBB.minX - COLLISION_RANGE) / 16.0D);
		int maxX = MathHelper.floor_double((par2AxisAlignedBB.maxX + COLLISION_RANGE) / 16.0D);
		int minZ = MathHelper.floor_double((par2AxisAlignedBB.minZ - COLLISION_RANGE) / 16.0D);
		int maxZ = MathHelper.floor_double((par2AxisAlignedBB.maxZ + COLLISION_RANGE) / 16.0D);

		for (int x = minX; x <= maxX; ++x) {
			for (int z = minZ; z <= maxZ; ++z) {
				Chunk chunk = getChunkIfExists(x, z);
				if (chunk != null) {
					limit = chunk.getEntitiesWithinAABBForEntity(par1Entity, par2AxisAlignedBB, entitiesWithinAABBExcludingEntity, limit);
				}
			}
		}

		return entitiesWithinAABBExcludingEntity;
	}

	@Override
	public List getEntitiesWithinAABBExcludingEntity(Entity par1Entity, AxisAlignedBB par2AxisAlignedBB) {
		return getEntitiesWithinAABBExcludingEntity(par1Entity, par2AxisAlignedBB, 1000);
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
		return minY <= 255 && maxY >= 0 && ((ChunkProviderServer) chunkProvider).chunksExist(minX >> 4, minZ >> 4, maxX >> 4, maxZ >> 4);
	}

	@Override
	public void unloadEntities(List entitiesToUnload) {
		for (Entity entity : (Iterable<Entity>) entitiesToUnload) {
			if (entity == null) {
				Log.warning("Tried to unload null entity", new Throwable());
			}
			if (!(entity instanceof EntityPlayerMP)) {
				unloadedEntitySet.add(entity);
			}
		}
	}

	@Override
	public void updateEntities() {
		final Profiler theProfiler = this.theProfiler;
		theProfiler.startSection("updateEntities");
		int var1;
		Entity weatherEffect;
		CrashReport var4;
		CrashReportCategory var5;

		theProfiler.startSection("global");
		final List<Entity> weatherEffects = this.weatherEffects;
		synchronized (weatherEffects) {
			Iterator<Entity> iterator = weatherEffects.iterator();
			while (iterator.hasNext()) {
				weatherEffect = iterator.next();

				if (weatherEffect == null) {
					iterator.remove();
					continue;
				}

				try {
					++weatherEffect.ticksExisted;
					weatherEffect.onUpdate();
				} catch (Throwable t) {
					Log.severe("Failed to tick weather " + Log.toString(weatherEffect), t);
				}

				if (weatherEffect.isDead) {
					iterator.remove();
				}
			}
		}

		theProfiler.endStartSection("remove");
		int var3;
		int var13;
		final List loadedEntityList = this.loadedEntityList;
		boolean tickTT = loadedEntityList instanceof EntityList;
		if (tickTT) {
			((EntityList) loadedEntityList).manager.batchRemoveEntities(unloadedEntitySet);
			((EntityList) loadedEntityList).manager.doTick();
		} else {
			loadedEntityList.removeAll(unloadedEntitySet);

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
			unloadedEntitySet.clear();
			theProfiler.endStartSection("entities");
			for (var1 = 0; var1 < loadedEntityList.size(); ++var1) {
				weatherEffect = (Entity) loadedEntityList.get(var1);

				if (weatherEffect.ridingEntity != null) {
					if (!weatherEffect.ridingEntity.isDead && weatherEffect.ridingEntity.riddenByEntity == weatherEffect) {
						continue;
					}

					weatherEffect.ridingEntity.riddenByEntity = null;
					weatherEffect.ridingEntity = null;
				}

				theProfiler.startSection("tick");

				if (!weatherEffect.isDead) {
					try {
						updateEntity(weatherEffect);
					} catch (Throwable var7) {
						var4 = CrashReport.makeCrashReport(var7, "Ticking entity");
						var5 = var4.makeCategory("Entity being ticked");
						weatherEffect.func_85029_a(var5);

						throw new ReportedException(var4);
					}
				}

				theProfiler.endSection();
				theProfiler.startSection("remove");

				if (weatherEffect.isDead) {
					var3 = weatherEffect.chunkCoordX;
					var13 = weatherEffect.chunkCoordZ;

					if (weatherEffect.addedToChunk) {
						Chunk chunk = getChunkIfExists(var3, var13);
						if (chunk != null) {
							chunk.removeEntity(weatherEffect);
						}
					}

					loadedEntityList.remove(var1--);
					releaseEntitySkin(weatherEffect);
				}

				theProfiler.endSection();
			}
			theProfiler.endStartSection("tileEntities");
			scanningTileEntities = true;

			Iterator var14 = loadedTileEntityList.iterator();

			while (var14.hasNext()) {
				TileEntity var9 = (TileEntity) var14.next();

				if (!var9.isInvalid() && var9.func_70309_m() && blockExists(var9.xCoord, var9.yCoord, var9.zCoord)) {
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

					Chunk var11 = getChunkIfExists(var9.xCoord >> 4, var9.zCoord >> 4);

					if (var11 != null) {
						var11.cleanChunkBlockTileEntity(var9.xCoord & 15, var9.yCoord, var9.zCoord & 15);
					}
				}
			}
		}

		theProfiler.endStartSection("removingTileEntities");

		final List loadedTileEntityList = this.loadedTileEntityList;
		if (loadedTileEntityList instanceof LoadedTileEntityList) {
			((LoadedTileEntityList) loadedTileEntityList).manager.batchRemoveTileEntities(tileEntityRemovalSet);
		} else {
			if (!warnedWrongList && loadedTileEntityList.getClass() != ArrayList.class) {
				Log.severe("TickThreading's replacement loaded tile entity list has been replaced by one from another mod!\n" +
						"Class: " + loadedTileEntityList.getClass() + ", toString(): " + loadedTileEntityList);
				warnedWrongList = true;
			}
			for (TileEntity tile : tileEntityRemovalSet) {
				tile.onChunkUnload();
			}
			loadedTileEntityList.removeAll(tileEntityRemovalSet);
			tileEntityRemovalSet.clear();
		}

		scanningTileEntities = false;

		theProfiler.endStartSection("pendingTileEntities");

		if (!addedTileEntityList.isEmpty()) {
			for (TileEntity te : (Iterable<TileEntity>) addedTileEntityList) {
				if (te.isInvalid()) {
					Chunk var15 = getChunkIfExists(te.xCoord >> 4, te.zCoord >> 4);

					if (var15 != null) {
						var15.cleanChunkBlockTileEntity(te.xCoord & 15, te.yCoord, te.zCoord & 15);
					}
				} else {
					if (!loadedTileEntityList.contains(te)) {
						loadedTileEntityList.add(te);
					}
				}
			}

			addedTileEntityList.clear();
		}

		theProfiler.endSection();
		theProfiler.endSection();
	}

	@Override
	public boolean isBlockProvidingPowerTo(int par1, int par2, int par3, int par4) {
		int id = getBlockIdWithoutLoad(par1, par2, par3);
		return id > 0 && Block.blocksList[id].isProvidingStrongPower(this, par1, par2, par3, par4);
	}

	@Override
	public boolean isBlockIndirectlyProvidingPowerTo(int x, int y, int z, int direction) {
		int id = getBlockIdWithoutLoad(x, y, z);
		if (id < 1) {
			return false;
		}
		Block block = Block.blocksList[id];
		return block != null && ((block.isBlockNormalCube(this, x, y, z) && isBlockGettingPowered(x, y, z)) || block.isProvidingWeakPower(this, x, y, z, direction));
	}

	@Override
	@Declare
	public Chunk getChunkIfExists(int x, int z) {
		Chunk chunk = chunkProvider.provideChunk(x, z);
		return chunk == emptyChunk ? null : chunk;
	}

	@Override
	@Declare
	public Chunk getChunkFromBlockCoordsIfExists(int x, int z) {
		return getChunkIfExists(x >> 4, z >> 4);
	}

	@Override
	public void markTileEntityForDespawn(TileEntity tileEntity) {
		tileEntityRemovalSet.add(tileEntity);
	}

	@Override
	public void setBlockTileEntity(int x, int y, int z, TileEntity tileEntity) {
		if (tileEntity == null || tileEntity.isInvalid()) {
			return;
		}

		Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
		if (chunk != null) {
			chunk.setChunkBlockTileEntity(x & 15, y, z & 15, tileEntity);
		}

		if (tileEntity.canUpdate()) {
			loadedTileEntityList.add(tileEntity);
		}
	}

	@Override
	@Declare
	public void setBlockTileEntityWithoutValidate(int x, int y, int z, TileEntity tileEntity) {
		if (tileEntity == null || tileEntity.isInvalid()) {
			return;
		}

		if (tileEntity.canUpdate()) {
			(scanningTileEntities ? addedTileEntityList : loadedTileEntityList).add(tileEntity);
		}

		Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
		if (chunk != null) {
			chunk.setChunkBlockTileEntityWithoutValidate(x & 15, y, z & 15, tileEntity);
		}
	}

	@Override
	@Declare
	public boolean setBlockAndMetadataWithUpdateWithoutValidate(int x, int y, int z, int id, int meta, boolean update) {
		if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000 && y >= 0 && y < 256) {
			Chunk chunk = getChunkFromChunkCoords(x >> 4, z >> 4);
			if (!chunk.setBlockIDWithMetadataWithoutValidate(x & 15, y, z & 15, id, meta)) {
				return false;
			}
			theProfiler.startSection("checkLight");
			updateAllLightTypes(x, y, z);
			theProfiler.endSection();

			if (update) {
				markBlockForUpdate(x, y, z);
			}

			return true;
		}
		return false;
	}

	@Override
	@Declare
	public boolean isChunkSavedPopulated(int x, int z) {
		return ((AnvilChunkLoader) ((WorldServer) (Object) this).theChunkProviderServer.currentChunkLoader).isChunkSavedPopulated(x, z);
	}

	@Override
	public List selectEntitiesWithinAABB(Class par1Class, AxisAlignedBB par2AxisAlignedBB, IEntitySelector par3IEntitySelector) {
		int var4 = MathHelper.floor_double((par2AxisAlignedBB.minX - COLLISION_RANGE) / 16.0D);
		int var5 = MathHelper.floor_double((par2AxisAlignedBB.maxX + COLLISION_RANGE) / 16.0D);
		int var6 = MathHelper.floor_double((par2AxisAlignedBB.minZ - COLLISION_RANGE) / 16.0D);
		int var7 = MathHelper.floor_double((par2AxisAlignedBB.maxZ + COLLISION_RANGE) / 16.0D);
		ArrayList entities = new ArrayList();

		if (var5 < var4 || var7 < var6) {
			return entities;
		}

		for (int cX = var4; cX <= var5; ++cX) {
			for (int cZ = var6; cZ <= var7; ++cZ) {
				Chunk chunk = getChunkIfExists(cX, cZ);
				if (chunk != null) {
					chunk.getEntitiesOfTypeWithinAAAB(par1Class, par2AxisAlignedBB, entities, par3IEntitySelector);
				}
			}
		}

		return entities;
	}

	@Override
	@Declare
	public List selectEntitiesWithinAABB(Class par1Class, AxisAlignedBB par2AxisAlignedBB, IEntitySelector par3IEntitySelector, double COLLISION_RANGE) {
		int var4 = MathHelper.floor_double((par2AxisAlignedBB.minX - COLLISION_RANGE) / 16.0D);
		int var5 = MathHelper.floor_double((par2AxisAlignedBB.maxX + COLLISION_RANGE) / 16.0D);
		int var6 = MathHelper.floor_double((par2AxisAlignedBB.minZ - COLLISION_RANGE) / 16.0D);
		int var7 = MathHelper.floor_double((par2AxisAlignedBB.maxZ + COLLISION_RANGE) / 16.0D);
		ArrayList entities = new ArrayList();

		if (var5 < var4 || var7 < var6) {
			return entities;
		}

		for (int cX = var4; cX <= var5; ++cX) {
			for (int cZ = var6; cZ <= var7; ++cZ) {
				Chunk chunk = getChunkIfExists(cX, cZ);
				if (chunk != null) {
					chunk.getEntitiesOfTypeWithinAAAB(par1Class, par2AxisAlignedBB, entities, par3IEntitySelector);
				}
			}
		}

		return entities;
	}

	private static double center(double a, double b) {
		return ((a - b) / 2) + b;
	}

	private static boolean isBroken(double a) {
		return Double.isNaN(a) || Double.isInfinite(a);
	}

	@Override
	public float getBlockDensity(Vec3 start, AxisAlignedBB target) {
		if (isBroken(target.minX) || isBroken(target.maxX) ||
				isBroken(target.minY) || isBroken(target.maxY) ||
				isBroken(target.minZ) || isBroken(target.maxZ) ||
				isBroken(start.xCoord) || isBroken(start.yCoord) || isBroken(start.zCoord) ||
				target.getAverageEdgeLength() > 10) {
			return 0.5f;
		}
		Vec3 center = getWorldVec3Pool().getVecFromPool(center(target.maxX, target.minX), center(target.maxY, target.minY), center(target.maxZ, target.minZ));
		if (start.squareDistanceTo(center) > 1000 || !chunkExists(((int) center.xCoord) >> 4, ((int) center.zCoord) >> 4)) {
			return 0.5f;
		}
		double dX = 1.0D / ((target.maxX - target.minX) * 2.0D + 1.0D);
		double dY = 1.0D / ((target.maxY - target.minY) * 2.0D + 1.0D);
		double dZ = 1.0D / ((target.maxZ - target.minZ) * 2.0D + 1.0D);
		dX = dX > 0.02 ? dX : 0.02;
		dY = dY > 0.02 ? dY : 0.02;
		dZ = dZ > 0.02 ? dZ : 0.02;
		int hit = 0;
		int noHit = 0;

		for (float var11 = 0.0F; var11 <= 1.0F; var11 = (float) ((double) var11 + dX)) {
			for (float var12 = 0.0F; var12 <= 1.0F; var12 = (float) ((double) var12 + dY)) {
				for (float var13 = 0.0F; var13 <= 1.0F; var13 = (float) ((double) var13 + dZ)) {
					double x = target.minX + (target.maxX - target.minX) * (double) var11;
					double y = target.minY + (target.maxY - target.minY) * (double) var12;
					double z = target.minZ + (target.maxZ - target.minZ) * (double) var13;

					if (rayTraceBlocks(center.setComponents(x, y, z), start) == null) {
						++hit;
					}

					++noHit;
				}
			}
		}

		return (float) hit / (float) noHit;
	}

	@Override
	@Declare
	public boolean preHandleSpawn(Entity e) {
		if (e == null) {
			return true;
		}
		if (e instanceof EntityPlayer) {
			return false;
		}
		if (e.isDead) {
			return true;
		}
		Chunk chunk = getChunkIfExists(((int) e.posX) >> 4, ((int) e.posZ) >> 4);
		if (chunk == null) {
			e.setDead();
			return true;
		}
		if (e instanceof EntityItem) {
			int recentSpawnedItems = TickThreading.recentSpawnedItems++;
			if (recentSpawnedItems > 100000) {
				e.setDead();
				return true;
			}
			if (!TickThreading.instance.removeIfOverMaxItems((EntityItem) e, chunk) && recentSpawnedItems > 200) {
				if (((EntityItem) e).aggressiveCombine()) {
					return true;
				}
			}
		} else if (e instanceof EntityXPOrb) {
			EntityXPOrb thisXP = (EntityXPOrb) e;
			final double mergeRadius = 1d;
			for (EntityXPOrb otherXP : (Iterable<EntityXPOrb>) selectEntitiesWithinAABB(EntityXPOrb.class, thisXP.boundingBox.expand(mergeRadius, mergeRadius, mergeRadius), null, 0.3D)) {
				if (!otherXP.isDead) {
					otherXP.addXPFrom(thisXP);
				}
				if (thisXP.isDead) {
					break;
				}
			}
		}
		return e.isDead;
	}

	@Declare
	public Entity getEntity(int id) {
		return ((WorldServer) (Object) this).getEntityTracker().getEntity(id);
	}
}
