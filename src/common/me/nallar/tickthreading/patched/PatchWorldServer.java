package me.nallar.tickthreading.patched;

import java.util.Iterator;

import me.nallar.tickthreading.minecraft.TickManager;
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

public abstract class PatchWorldServer extends WorldServer {
	public PatchWorldServer(MinecraftServer par1MinecraftServer, ISaveHandler par2ISaveHandler, String par3Str, int par4, WorldSettings par5WorldSettings, Profiler par6Profiler) {
		super(par1MinecraftServer, par2ISaveHandler, par3Str, par4, par5WorldSettings, par6Profiler);
	}

	public long startTime = 0;

	protected void tickBlocksAndAmbiance() {
		final Iterator var3 = this.activeChunkSet.iterator();

		doneChunks.retainAll(activeChunkSet);
		if (doneChunks.size() == activeChunkSet.size()) {
			doneChunks.clear();
		}

		startTime = System.nanoTime();

		TickThreading tickThreading = TickThreading.instance();
		TickManager tickManager = tickThreading.getManager(this);
		boolean concurrentTicks = tickThreading.enableChunkTickThreading;

		while (var3.hasNext()) {
			final ChunkCoordIntPair chunkCoordIntPair = (ChunkCoordIntPair) var3.next();
			if (concurrentTicks) {
				tickManager.submitRunnable(new Runnable() {
					@Override
					public void run() {
						tickBlocks(chunkCoordIntPair);
					}
				});
			} else {
				tickBlocks(chunkCoordIntPair);
			}
		}
	}

	public void tickBlocks(ChunkCoordIntPair var4) {
		int xPos = var4.chunkXPos * 16;
		int yPos = var4.chunkZPos * 16;
		Chunk chunk = this.getChunkFromChunkCoords(var4.chunkXPos, var4.chunkZPos);
		this.moodSoundAndLightCheck(xPos, yPos, chunk);
		//Limits and evenly distributes the lighting update time
		if (System.nanoTime() - startTime <= 4000000 && doneChunks.add(var4)) {
			chunk.updateSkylight();
		}
		int var8;
		int var9;
		int var10;
		int var11;

		if (provider.canDoLightning(chunk) && this.rand.nextInt(100000) == 0 && this.isRaining() && this.isThundering()) {
			this.updateLCG = this.updateLCG * 3 + 1013904223;
			var8 = this.updateLCG >> 2;
			var9 = xPos + (var8 & 15);
			var10 = yPos + (var8 >> 8 & 15);
			var11 = this.getPrecipitationHeight(var9, var10);

			if (this.canLightningStrikeAt(var9, var11, var10)) {
				this.addWeatherEffect(new EntityLightningBolt(this, (double) var9, (double) var11, (double) var10));
			}
		}

		int var13;

		if (provider.canDoRainSnowIce(chunk) && this.rand.nextInt(16) == 0) {
			this.updateLCG = this.updateLCG * 3 + 1013904223;
			var8 = this.updateLCG >> 2;
			var9 = var8 & 15;
			var10 = var8 >> 8 & 15;
			var11 = this.getPrecipitationHeight(var9 + xPos, var10 + yPos);

			if (this.isBlockFreezableNaturally(var9 + xPos, var11 - 1, var10 + yPos)) {
				this.setBlockWithNotify(var9 + xPos, var11 - 1, var10 + yPos, Block.ice.blockID);
			}

			if (this.isRaining() && this.canSnowAt(var9 + xPos, var11, var10 + yPos)) {
				this.setBlockWithNotify(var9 + xPos, var11, var10 + yPos, Block.snow.blockID);
			}

			if (this.isRaining()) {
				BiomeGenBase var12 = this.getBiomeGenForCoords(var9 + xPos, var10 + yPos);

				if (var12.canSpawnLightningBolt()) {
					var13 = this.getBlockId(var9 + xPos, var11 - 1, var10 + yPos);

					if (var13 != 0) {
						Block.blocksList[var13].fillWithRain(this, var9 + xPos, var11 - 1, var10 + yPos);
					}
				}
			}
		}

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
						var18.updateTick(this, var14 + xPos, var16 + var21.getYLocation(), var15 + yPos, this.rand);
					}
				}
			}
		}
	}
}
