package me.nallar.tickthreading.minecraft.patched;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.nallar.tickthreading.Log;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.SpawnListEntry;

public abstract class PatchSpawnerAnimals extends SpawnerAnimals {
	private static long hash(long x, long y) {
		return (x << 32) & y;
	}

	private static final int closeRange = 1;
	private static final int farRange = 4;
	private static final int spawnVariance = 6;

	public static int spawnMobsQuickly(WorldServer worldServer, boolean peaceful, boolean hostile, boolean animal) {
		int mobMultiplier = worldServer.playerEntities.size();
		Map<EnumCreatureType, Integer> requiredSpawns = new HashMap<EnumCreatureType, Integer>();
		for (EnumCreatureType creatureType : EnumCreatureType.values()) {
			boolean isPeaceful = creatureType.getPeacefulCreature();
			if (((!isPeaceful || hostile) && (isPeaceful || peaceful) && (!creatureType.getAnimal() || animal))
					&& (mobMultiplier * creatureType.getMaxNumberOfCreature() < worldServer.countEntities(creatureType.getCreatureClass()))) {
				requiredSpawns.put(creatureType, mobMultiplier * creatureType.getMaxNumberOfCreature());
			}
		}

		if (requiredSpawns.isEmpty()) {
			return 0;
		}

		int spawnedMobs = 0;
		Set<Long> closeChunks = new HashSet<Long>();
		List<Long> spawnableChunks = new ArrayList<Long>();
		for (Object entityPlayer_ : worldServer.playerEntities) {
			EntityPlayer entityPlayer = (EntityPlayer) entityPlayer_;
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
			x = pX - farRange;
			maxX = pX + farRange;
			startZ = pZ - farRange;
			maxZ = pZ + farRange;
			for (; x <= maxX; x++) {
				for (int z = startZ; z <= maxZ; z++) {
					if (closeChunks.add(hash(x, z))) {
						spawnableChunks.add(hash(x, z));
					}
				}
			}
		}
		for (Map.Entry<EnumCreatureType, Integer> entry : requiredSpawns.entrySet()) {
			EnumCreatureType creatureType = entry.getKey();
			long hash = spawnableChunks.get(worldServer.rand.nextInt(spawnableChunks.size()));
			int x = (int) (hash >> 32);
			int z = (int) hash;
			ChunkPosition spawningPoint = getRandomSpawningPointInChunk(worldServer, x, z);
			int sX = spawningPoint.x;
			int sY = spawningPoint.y;
			int sZ = spawningPoint.z;
			if (!worldServer.isBlockNormalCube(sX, sY, sZ) && worldServer.getBlockMaterial(sX, sY, sZ) == creatureType.getCreatureMaterial()) {
				for (int i = 0; i < 4; i++) {
					int ssX = sX + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);
					int ssY = sY + (worldServer.rand.nextInt(2) - 1);
					int ssZ = sZ + (worldServer.rand.nextInt(spawnVariance) - spawnVariance / 2);

					if (canCreatureTypeSpawnAtLocation(creatureType, worldServer, ssX, ssY, ssZ)) {
						SpawnListEntry creatureClass = worldServer.spawnRandomCreature(creatureType, ssX, ssY, ssZ);
						if (creatureClass == null) {
							break;
						}

						EntityLiving spawnedEntity;
						try {
							spawnedEntity = (EntityLiving) creatureClass.entityClass.getConstructor(World.class).newInstance(worldServer);
						} catch (Exception e) {
							Log.severe("Failed to spawn entity " + creatureClass, e);
							return spawnedMobs;
						}

						spawnedEntity.setLocationAndAngles((double) ssX, (double) ssY, (double) ssZ, worldServer.rand.nextFloat() * 360.0F, 0.0F);

						if (spawnedEntity.getCanSpawnHere()) {
							worldServer.spawnEntityInWorld(spawnedEntity);
							creatureSpecificInit(spawnedEntity, worldServer, ssX, ssY, ssZ);
							spawnedMobs++;
						}
					}
				}
			}
			if (spawnedMobs >= 32) {
				return spawnedMobs;
			}
		}
		return spawnedMobs;
	}

	public static int a(WorldServer par0WorldServer, boolean par1, boolean par2, boolean par3) {
		if (!par1 && !par2) {
			return 0;
		}
		if (true) {
			return spawnMobsQuickly(par0WorldServer, par1, par2, par3);
		}
		double tpsFactor = MinecraftServer.getTPS() / 20;
		HashMap eligibleChunksForSpawning = new HashMap();
		int var4;
		int var7;

		for (var4 = 0; var4 < par0WorldServer.playerEntities.size(); ++var4) {
			EntityPlayer var5 = (EntityPlayer) par0WorldServer.playerEntities.get(var4);
			int var6 = MathHelper.floor_double(var5.posX / 16.0D);
			var7 = MathHelper.floor_double(var5.posZ / 16.0D);
			byte var8 = 8;

			for (int var9 = -var8; var9 <= var8; ++var9) {
				for (int var10 = -var8; var10 <= var8; ++var10) {
					boolean var11 = var9 == -var8 || var9 == var8 || var10 == -var8 || var10 == var8;
					ChunkCoordIntPair var12 = new ChunkCoordIntPair(var9 + var6, var10 + var7);

					if (!var11) {
						eligibleChunksForSpawning.put(var12, false);
					} else if (!eligibleChunksForSpawning.containsKey(var12)) {
						eligibleChunksForSpawning.put(var12, true);
					}
				}
			}
		}

		var4 = 0;
		ChunkCoordinates var32 = par0WorldServer.getSpawnPoint();
		EnumCreatureType[] var33 = EnumCreatureType.values();
		var7 = var33.length;

		for (int var34 = 0; var34 < var7; ++var34) {
			EnumCreatureType var35 = var33[var34];

			if ((Math.random() < tpsFactor) && ((!var35.getPeacefulCreature() || par2) && (var35.getPeacefulCreature() || par1) && (!var35.getAnimal() || par3) && par0WorldServer.countEntities(var35.getCreatureClass()) <= var35.getMaxNumberOfCreature() * eligibleChunksForSpawning.size() / 256)) {
				ArrayList<ChunkCoordIntPair> tmp = new ArrayList(eligibleChunksForSpawning.keySet());
				Collections.shuffle(tmp);
				Iterator var37 = tmp.iterator();
				label110:

				while (var37.hasNext()) {
					ChunkCoordIntPair var36 = (ChunkCoordIntPair) var37.next();

					if (!(Boolean) eligibleChunksForSpawning.get(var36)) {
						ChunkPosition var38 = getRandomSpawningPointInChunk(par0WorldServer, var36.chunkXPos, var36.chunkZPos);
						int var13 = var38.x;
						int var14 = var38.y;
						int var15 = var38.z;

						if (!par0WorldServer.isBlockNormalCube(var13, var14, var15) && par0WorldServer.getBlockMaterial(var13, var14, var15) == var35.getCreatureMaterial()) {
							int var16 = 0;
							int var17 = 0;

							while (var17 < 3) {
								int var18 = var13;
								int var19 = var14;
								int var20 = var15;
								byte var21 = 6;
								SpawnListEntry var22 = null;
								int var23 = 0;

								while (true) {
									if (var23 < 4) {
										label103:
										{
											var18 += par0WorldServer.rand.nextInt(var21) - par0WorldServer.rand.nextInt(var21);
											var19 += par0WorldServer.rand.nextInt(1) - par0WorldServer.rand.nextInt(1);
											var20 += par0WorldServer.rand.nextInt(var21) - par0WorldServer.rand.nextInt(var21);

											if (canCreatureTypeSpawnAtLocation(var35, par0WorldServer, var18, var19, var20)) {
												float var24 = (float) var18 + 0.5F;
												float var25 = (float) var19;
												float var26 = (float) var20 + 0.5F;

												if (par0WorldServer.getClosestPlayer((double) var24, (double) var25, (double) var26, 24.0D) == null) {
													float var27 = var24 - (float) var32.posX;
													float var28 = var25 - (float) var32.posY;
													float var29 = var26 - (float) var32.posZ;
													float var30 = var27 * var27 + var28 * var28 + var29 * var29;

													if (var30 >= 576.0F) {
														if (var22 == null) {
															var22 = par0WorldServer.spawnRandomCreature(var35, var18, var19, var20);

															if (var22 == null) {
																break label103;
															}
														}

														EntityLiving var39;

														try {
															var39 = (EntityLiving) var22.entityClass.getConstructor(World.class).newInstance(par0WorldServer);
														} catch (Exception var31) {
															var31.printStackTrace();
															return var4;
														}

														var39.setLocationAndAngles((double) var24, (double) var25, (double) var26, par0WorldServer.rand.nextFloat() * 360.0F, 0.0F);

														if (var39.getCanSpawnHere()) {
															++var16;
															par0WorldServer.spawnEntityInWorld(var39);
															creatureSpecificInit(var39, par0WorldServer, var24, var25, var26);

															if (var16 >= var39.getMaxSpawnedInChunk()) {
																continue label110;
															}
														}

														var4 += var16;
													}
												}
											}

											++var23;
											continue;
										}
									}

									++var17;
									break;
								}
							}
						}
					}
				}
			}
		}

		return var4;
	}
}
