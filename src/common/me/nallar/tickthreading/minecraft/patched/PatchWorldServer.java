package me.nallar.tickthreading.minecraft.patched;

import java.util.Iterator;
import java.util.Random;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.ISaveHandler;

public abstract class PatchWorldServer extends WorldServer implements Runnable {
	private Iterator chunkCoordIterator;
	private ThreadManager threadManager;
	private ThreadLocal<Random> randoms;

	public PatchWorldServer(MinecraftServer par1MinecraftServer, ISaveHandler par2ISaveHandler, String par3Str, int par4, WorldSettings par5WorldSettings, Profiler par6Profiler) {
		super(par1MinecraftServer, par2ISaveHandler, par3Str, par4, par5WorldSettings, par6Profiler);
	}

	public void construct() {
		randoms = new ThreadLocalRandom();
		threadManager = new ThreadManager(TickThreading.instance.getThreadCount(), "Chunk Updates for " + Log.name(this));
	}

	public long startTime = 0;

	@Override
	protected void tickBlocksAndAmbiance() {
		TickThreading tickThreading = TickThreading.instance;
		boolean concurrentTicks = tickThreading.enableChunkTickThreading && !mcServer.theProfiler.profilingEnabled;

		if (concurrentTicks) {
			threadManager.waitForCompletion();
		}

		chunkCoordIterator = this.activeChunkSet.iterator();

		if (concurrentTicks) {
			for (int i = 0; i < threadManager.size(); i++) {
				threadManager.run(this);
			}
		} else {
			run();
		}
	}

	@Override
	public void run() {
		double tpsFactor = MinecraftServer.getTPS() / MinecraftServer.getTargetTPS();
		final Random rand = randoms.get();
		// We use a random per thread - randoms are threadsafe, however synchronization is involved.
		// This reduces contention -> slightly increased performance, woo! :P
		while (true) {
			ChunkCoordIntPair var4;
			synchronized (chunkCoordIterator) {
				if (!chunkCoordIterator.hasNext()) {
					break;
				}
				var4 = (ChunkCoordIntPair) chunkCoordIterator.next();
			}

			int cX = var4.chunkXPos;
			int cZ = var4.chunkZPos;
			if ((tpsFactor < 1 && rand.nextFloat() > tpsFactor) || this.theChunkProviderServer.getChunksToUnloadSet().contains(ChunkCoordIntPair.chunkXZ2Int(cX, cZ))) {
				continue;
			}

			int xPos = cX * 16;
			int zPos = cZ * 16;
			Chunk chunk = this.theChunkProviderServer.getChunkIfExists(cX, cZ);
			if (chunk == null) {
				continue;
			}
			this.moodSoundAndLightCheck(xPos, zPos, chunk);
			theProfiler.endStartSection("chunkTick"); // endStart as moodSoundAndLightCheck starts a section.
			chunk.updateSkylight();
			int var8;
			int var9;
			int var10;
			int var11;

			theProfiler.startSection("lightning");
			if (provider.canDoLightning(chunk) && rand.nextInt(100000) == 0 && this.isRaining() && this.isThundering()) {
				this.updateLCG = this.updateLCG * 3 + 1013904223;
				var8 = this.updateLCG >> 2;
				var9 = xPos + (var8 & 15);
				var10 = zPos + (var8 >> 8 & 15);
				var11 = this.getPrecipitationHeight(var9, var10);

				if (this.canLightningStrikeAt(var9, var11, var10)) {
					this.addWeatherEffect(new EntityLightningBolt(this, (double) var9, (double) var11, (double) var10));
				}
			}

			int var13;

			theProfiler.endStartSection("precipitation");
			if (provider.canDoRainSnowIce(chunk) && rand.nextInt(16) == 0) {
				this.updateLCG = this.updateLCG * 3 + 1013904223;
				var8 = this.updateLCG >> 2;
				var9 = var8 & 15;
				var10 = var8 >> 8 & 15;
				var11 = this.getPrecipitationHeight(var9 + xPos, var10 + zPos);

				if (this.isBlockFreezableNaturally(var9 + xPos, var11 - 1, var10 + zPos)) {
					this.setBlockWithNotify(var9 + xPos, var11 - 1, var10 + zPos, Block.ice.blockID);
				}

				if (this.isRaining() && this.canSnowAt(var9 + xPos, var11, var10 + zPos)) {
					this.setBlockWithNotify(var9 + xPos, var11, var10 + zPos, Block.snow.blockID);
				}

				if (this.isRaining()) {
					BiomeGenBase var12 = this.getBiomeGenForCoords(var9 + xPos, var10 + zPos);

					if (var12.canSpawnLightningBolt()) {
						var13 = this.getBlockId(var9 + xPos, var11 - 1, var10 + zPos);

						if (var13 != 0) {
							Block.blocksList[var13].fillWithRain(this, var9 + xPos, var11 - 1, var10 + zPos);
						}
					}
				}
			}

			theProfiler.endStartSection("blockTick");
			ExtendedBlockStorage[] var19 = chunk.getBlockStorageArray();
			var9 = var19.length;

			for (var10 = 0; var10 < var9; ++var10) {
				ExtendedBlockStorage var21 = var19[var10];

				if (var21 != null && var21.getNeedsRandomTick()) {
					for (int var20 = 0; var20 < 3; ++var20) {
						this.updateLCG = this.updateLCG * 3 + 1013904223;
						var13 = this.updateLCG >> 2;
						int var14 = var13 & 15;
						int var15 = var13 >> 8 & 15;
						int var16 = var13 >> 16 & 15;
						int var17 = var21.getExtBlockID(var14, var16, var15);
						Block var18 = Block.blocksList[var17];

						if (var18 != null && var18.getTickRandomly()) {
							var18.updateTick(this, var14 + xPos, var16 + var21.getYLocation(), var15 + zPos, rand);
						}
					}
				}
			}
			theProfiler.endSection();
			theProfiler.endStartSection("iterate");
		}
	}

	public static class ThreadLocalRandom extends ThreadLocal<Random> {
		@Override
		public Random initialValue() {
			return new Random();
		}
	}
}
