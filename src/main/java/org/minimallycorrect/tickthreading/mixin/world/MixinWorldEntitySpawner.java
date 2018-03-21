package org.minimallycorrect.tickthreading.mixin.world;

import java.util.Collection;
import java.util.EnumMap;

import lombok.val;

import org.minimallycorrect.mixin.Add;
import org.minimallycorrect.mixin.Mixin;
import org.minimallycorrect.mixin.Overwrite;
import org.minimallycorrect.tickthreading.collection.LongList;
import org.minimallycorrect.tickthreading.collection.LongSet;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

@Mixin
public abstract class MixinWorldEntitySpawner extends WorldEntitySpawner {
	private static final int closeRange = 1;
	private static final int farRange = 5;
	private static final int spawnVariance = 6;
	private static final int triesPerCreatureType = 4;
	private static final int clumping = 7;
	private static final int mobClumping = 5;
	private static final int maxChunksPerPlayer = (farRange * farRange * 4) - (closeRange * closeRange * 4);

	@Add
	private static long hash(int x, int z) {
		return (((long) x) << 32) | (z & 0xffffffffL);
	}

	@Add
	private static Chunk getChunkFromBlockCoords(WorldServer w, int x, int z) {
		return w.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
	}

	@Add
	private static int getPseudoRandomHeightValue(int wX, int wZ, WorldServer worldServer, boolean surface, int gapChance) {
		Chunk chunk = getChunkFromBlockCoords(worldServer, wX, wZ);
		if (chunk == null) {
			return -1;
		}
		int x = wX & 15;
		int z = wZ & 15;
		int height = chunk.getHeightValue(x, z);
		if (surface)
			return height;
		int maxHeight = worldServer.provider.getActualHeight();
		if (height >= maxHeight) {
			height = -1;
		}
		boolean inGap = false;
		int lastGap = 0;
		val blockPos = new BlockPos.MutableBlockPos(wX, 0, wZ);
		for (int y = 1; y < height; y++) {
			val block = chunk.getBlockState(x, y, z);
			blockPos.setY(y);
			if (block.getBlock() == Blocks.AIR || !block.isSideSolid(worldServer, blockPos, EnumFacing.UP)) {
				if (!inGap) {
					inGap = true;
					if (gapChance++ % 3 == 0) {
						return y;
					}
					lastGap = y;
				}
			} else {
				inGap = false;
			}
		}
		return lastGap == 0 ? height : lastGap;
	}

	@Overwrite
	public int findChunksForSpawning(WorldServer worldServer, boolean peaceful, boolean hostile, boolean animal) {
		if (worldServer.getWorldTime() % clumping != 0) {
			return 0;
		}
		float entityMultiplier = worldServer.playerEntities.size() * 1.5f;
		if (entityMultiplier == 0) {
			return 0;
		}

		val profiler = worldServer.profiler;
		profiler.startSection("creatureTypes");
		val p = worldServer.getChunkProvider();
		val hell = worldServer.provider instanceof WorldProviderHell;
		boolean dayTime = !hell && worldServer.isDaytime();
		float mobMultiplier = entityMultiplier * (dayTime ? 1 : 2);
		val requiredSpawns = new EnumMap<EnumCreatureType, Integer>(EnumCreatureType.class);
		for (EnumCreatureType creatureType : EnumCreatureType.values()) {
			if (hell && creatureType == EnumCreatureType.WATER_CREATURE)
				continue;

			int count = (int) ((creatureType.getPeacefulCreature() ? entityMultiplier : mobMultiplier) * creatureType.getMaxNumberOfCreature());
			if (creatureType.getPeacefulCreature() && !peaceful || creatureType.getAnimal() && !animal || !creatureType.getPeacefulCreature() && !hostile)
				continue;

			val current = worldServer.countEntities(creatureType, true);
			if (count > current)
				requiredSpawns.put(creatureType, count - current);
		}
		profiler.endSection();

		if (requiredSpawns.isEmpty()) {
			return 0;
		}

		profiler.startSection("spawnableChunks");
		int attemptedSpawnedMobs = 0;
		val closeChunks = new LongSet();
		Collection<EntityPlayer> entityPlayers = worldServer.playerEntities;
		LongList spawnableChunks = new LongList(entityPlayers.size() * maxChunksPerPlayer);
		for (EntityPlayer entityPlayer : entityPlayers) {
			int pX = entityPlayer.chunkCoordX;
			int pZ = entityPlayer.chunkCoordZ;
			int x = pX - closeRange;
			int maxX = pX + closeRange;
			int startZ = pZ - closeRange;
			int maxZ = pZ + closeRange;
			for (; x <= maxX; x++) {
				for (int z = startZ; z <= maxZ; z++) {
					closeChunks.add(hash(x, z));
				}
			}
		}
		for (EntityPlayer entityPlayer : entityPlayers) {
			int pX = entityPlayer.chunkCoordX;
			int pZ = entityPlayer.chunkCoordZ;
			int x = pX - farRange;
			int maxX = pX + farRange;
			int startZ = pZ - farRange;
			int maxZ = pZ + farRange;
			for (; x <= maxX; x++) {
				for (int z = startZ; z <= maxZ; z++) {
					long hash = hash(x, z);
					if (!closeChunks.contains(hash) || !p.chunkExists(x, z)) {
						spawnableChunks.add(hash);
					}
				}
			}
		}
		profiler.endSection();

		int size = spawnableChunks.size;
		if (size == 0)
			return 0;

		profiler.startSection("spawnMobs");

		int surfaceChance = 0;
		int gapChance = 0;
		SpawnLoop: for (val entry : requiredSpawns.entrySet()) {
			val creatureType = entry.getKey();
			val tries = Math.max(triesPerCreatureType, entry.getValue() / (triesPerCreatureType * mobClumping));
			for (int j = 0; j < tries; j++) {
				long hash = spawnableChunks.get(worldServer.rand.nextInt(size));
				int x = (int) (hash >> 32);
				int z = (int) hash;
				val chunk = p.getLoadedChunk(x, z);
				if (chunk == null)
					continue;
				int sX = x << 4 + worldServer.rand.nextInt(16);
				int sZ = z << 4 + worldServer.rand.nextInt(16);
				boolean surface = !hell && (creatureType.getPeacefulCreature() || (dayTime ? surfaceChance++ % 5 == 0 : surfaceChance++ % 5 != 0));
				int gap = gapChance++;
				int sY;
				if (creatureType == EnumCreatureType.WATER_CREATURE) {
					String biomeName = chunk.getBiome(new BlockPos(sX, 64, sZ), worldServer.provider.getBiomeProvider()).getBiomeName();
					if (!"Ocean".equals(biomeName) && !"River".equals(biomeName)) {
						continue;
					}
					sY = getPseudoRandomHeightValue(sX, sZ, worldServer, true, gap) - 2;
				} else {
					sY = getPseudoRandomHeightValue(sX, sZ, worldServer, surface, gap);
				}
				if (sY <= 0) {
					continue;
				}
				// TODO: Fix this check?
				if (true) {
					//if (chunk.getBlockState(sX, sY, sZ).getMaterial() == creatureType.getCreatureMaterial()) {
					IEntityLivingData unusedIEntityLivingData = null;
					for (int i = 0; i < mobClumping; i++) {
						int ssX = sX + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);
						int ssZ = sZ + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);

						if (!p.chunkExists(ssX >> 4, ssZ >> 4))
							continue;

						int ssY;
						IBlockState state = null;
						if (creatureType == EnumCreatureType.WATER_CREATURE) {
							ssY = sY;
						} else if (creatureType == EnumCreatureType.AMBIENT) {
							ssY = worldServer.rand.nextInt(63) + 1;
						} else {
							ssY = getPseudoRandomHeightValue(ssX, ssZ, worldServer, surface, gap);
							if (ssY <= 0)
								continue;
							state = chunk.getBlockState(ssX, ssY - 1, ssZ);
							if (!state.getBlock().canCreatureSpawn(state, worldServer, new BlockPos(ssX, ssY - 1, ssZ), null))
								continue;
						}

						if (creatureType != EnumCreatureType.WATER_CREATURE) {
							if (state == null)
								state = chunk.getBlockState(ssX, ssY - 1, ssZ);
							if (state.getMaterial().isLiquid())
								continue;
						}

						Biome.SpawnListEntry creatureClass = worldServer.getSpawnListEntryForTypeAt(creatureType, new BlockPos(ssX, ssY, ssZ));
						if (creatureClass == null)
							break;

						EntityLiving spawnedEntity;
						try {
							spawnedEntity = creatureClass.entityClass.getConstructor(World.class).newInstance(worldServer);
							spawnedEntity.setLocationAndAngles((double) ssX, (double) ssY, (double) ssZ, worldServer.rand.nextFloat() * 360.0F, 0.0F);

							val canSpawn = ForgeEventFactory.canEntitySpawn(spawnedEntity, worldServer, ssX, ssY, ssZ, null);
							if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && spawnedEntity.getCanSpawnHere())) {
								worldServer.spawnEntity(spawnedEntity);
								if (!ForgeEventFactory.doSpecialSpawn(spawnedEntity, worldServer, ssX, ssY, ssZ, null)) {
									unusedIEntityLivingData = spawnedEntity.onInitialSpawn(worldServer.getDifficultyForLocation(new BlockPos(spawnedEntity)), unusedIEntityLivingData);
								}
							}
							attemptedSpawnedMobs++;
						} catch (Exception e) {
							System.err.println("Failed to spawn entity " + creatureClass);
							e.printStackTrace();
							break SpawnLoop;
						}
					}
				}
				if (attemptedSpawnedMobs >= size)
					break SpawnLoop;
			}
		}
		profiler.endSection();
		return attemptedSpawnedMobs;
	}
}
