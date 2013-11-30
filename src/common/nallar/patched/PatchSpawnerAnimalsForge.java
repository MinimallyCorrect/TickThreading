package nallar.patched;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingData;
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
import net.minecraftforge.event.Event;
import net.minecraftforge.event.ForgeEventFactory;

public abstract class PatchSpawnerAnimalsForge extends SpawnerAnimals {
	@Override
	public int findChunksForSpawning(WorldServer par0WorldServer, boolean par1, boolean par2, boolean par3) {
		if (!par1 && !par2) {
			return 0;
		}
		double tpsFactor = Math.max(1, Math.min(0.1d, MinecraftServer.getTPS() / 20d));
		HashMap<ChunkCoordIntPair, Boolean> eligibleChunksForSpawning = new HashMap<ChunkCoordIntPair, Boolean>();
		int var4;
		int var7;

		for (EntityPlayer var5 : (Iterable<EntityPlayer>) par0WorldServer.playerEntities) {
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

			if ((tpsFactor >= 1 || Math.random() < tpsFactor) && ((!var35.getPeacefulCreature() || par2) && (var35.getPeacefulCreature() || par1) && (!var35.getAnimal() || par3) && par0WorldServer.countEntities(var35.getCreatureClass()) <= var35.getMaxNumberOfCreature() * eligibleChunksForSpawning.size() / 256)) {
				ArrayList<ChunkCoordIntPair> tmp = new ArrayList<ChunkCoordIntPair>(eligibleChunksForSpawning.keySet());
				Collections.shuffle(tmp);
				Iterator<ChunkCoordIntPair> var37 = tmp.iterator();
				label110:

				while (var37.hasNext()) {
					ChunkCoordIntPair var36 = var37.next();

					if (par0WorldServer.theChunkProviderServer.chunkExists(var36.chunkXPos, var36.chunkZPos) && !eligibleChunksForSpawning.get(var36)) {
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
								EntityLivingData unusedEntityLivingData = null;
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

															var39.setLocationAndAngles((double) var24, (double) var25, (double) var26, par0WorldServer.rand.nextFloat() * 360.0F, 0.0F);

															Event.Result canSpawn = ForgeEventFactory.canEntitySpawn(var39, par0WorldServer, var24, var25, var26);
															if (canSpawn == Event.Result.ALLOW || (canSpawn == Event.Result.DEFAULT && var39.getCanSpawnHere())) {
																++var16;
																par0WorldServer.spawnEntityInWorld(var39);
																if (!ForgeEventFactory.doSpecialSpawn(var39, par0WorldServer, var24, var25, var26))
																{
																	unusedEntityLivingData = var39.onSpawnWithEgg(unusedEntityLivingData);
																}

																if (var16 >= var39.getMaxSpawnedInChunk()) {
																	continue label110;
																}
															}

															var4 += var16;
														} catch (Exception var31) {
															FMLLog.log(Level.WARNING, var31, "Exception spawning an entity");
															return var4;
														}
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
