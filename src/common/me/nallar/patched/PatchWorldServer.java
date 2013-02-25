package me.nallar.patched;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableSetMultimap;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.TreeHashSet;
import me.nallar.tickthreading.minecraft.DeadLockDetector;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.ForgeChunkManager;

public abstract class PatchWorldServer extends WorldServer implements Runnable {
	private Iterator chunkCoordIterator;
	private ThreadManager threadManager;
	private ThreadLocal<Random> randoms;
	private ArrayList<NextTickListEntry> runningTickListEntries;
	@Declare
	public ThreadLocal<Boolean> worldGenInProgress_;

	public PatchWorldServer(MinecraftServer par1MinecraftServer, ISaveHandler par2ISaveHandler, String par3Str, int par4, WorldSettings par5WorldSettings, Profiler par6Profiler) {
		super(par1MinecraftServer, par2ISaveHandler, par3Str, par4, par5WorldSettings, par6Profiler);
	}

	public void construct() {
		randoms = new ThreadLocalRandom();
		threadManager = new ThreadManager(TickThreading.instance.getThreadCount(), "Chunk Updates for " + Log.name(this));
		field_73064_N = null;
		pendingTickListEntries = new TreeHashSet();
		worldGenInProgress = new BooleanThreadLocal();
		runningTickListEntries = new ArrayList<NextTickListEntry>();
	}

	@Override
	public void flush() {
		DeadLockDetector.tick("Saving a world before unload", System.nanoTime() + 30000000000L);
		this.saveHandler.flush();
		IChunkLoader chunkLoader = theChunkProviderServer.currentChunkLoader;
		if (chunkLoader instanceof AnvilChunkLoader) {
			((AnvilChunkLoader) chunkLoader).close();
		}
	}

	@Override
	protected void initialize(WorldSettings par1WorldSettings) {
		if (this.entityIdMap == null) {
			this.entityIdMap = new net.minecraft.util.IntHashMap();
		}

		if (this.pendingTickListEntries == null) {
			this.pendingTickListEntries = new TreeHashSet();
		}

		this.createSpawnPosition(par1WorldSettings);
	}

	@Override
	public List getPendingBlockUpdates(Chunk par1Chunk, boolean par2) {
		ArrayList var3 = null;
		ChunkCoordIntPair var4 = par1Chunk.getChunkCoordIntPair();
		int var5 = var4.chunkXPos << 4;
		int var6 = var5 + 16;
		int var7 = var4.chunkZPos << 4;
		int var8 = var7 + 16;
		synchronized (pendingTickListEntries) {
			Iterator var9 = this.pendingTickListEntries.iterator();

			while (var9.hasNext()) {
				NextTickListEntry var10 = (NextTickListEntry) var9.next();

				if (var10.xCoord >= var5 && var10.xCoord < var6 && var10.zCoord >= var7 && var10.zCoord < var8) {
					if (par2) {
						var9.remove();
					}

					if (var3 == null) {
						var3 = new ArrayList();
					}

					var3.add(var10);
				}
			}
		}

		return var3;
	}

	@Override
	public void func_82740_a(int x, int y, int z, int blockID, int timeOffset, int par6) {
		NextTickListEntry var7 = new NextTickListEntry(x, y, z, blockID);
		boolean isForced = getPersistentChunks().containsKey(new ChunkCoordIntPair(var7.xCoord >> 4, var7.zCoord >> 4));
		byte range = isForced ? (byte) 0 : 8;

		if (this.scheduledUpdatesAreImmediate && blockID > 0) {
			if (Block.blocksList[blockID].func_82506_l()) {
				if (this.checkChunksExist(var7.xCoord - range, var7.yCoord - range, var7.zCoord - range, var7.xCoord + range, var7.yCoord + range, var7.zCoord + range)) {
					int realBlockID = this.getBlockIdWithoutLoad(var7.xCoord, var7.yCoord, var7.zCoord);

					if (realBlockID > 0 && realBlockID == var7.blockID) {
						Block.blocksList[realBlockID].updateTick(this, var7.xCoord, var7.yCoord, var7.zCoord, this.rand);
					}
				}

				return;
			}

			timeOffset = 1;
		}

		if (this.checkChunksExist(x - range, y - range, z - range, x + range, y + range, z + range)) {
			if (blockID > 0) {
				var7.setScheduledTime((long) timeOffset + this.worldInfo.getWorldTotalTime());
				var7.func_82753_a(par6);
			}

			this.pendingTickListEntries.add(var7);
		}
	}

	@Override
	public void scheduleBlockUpdateFromLoad(int x, int y, int z, int blockID, int timeOffset) {
		NextTickListEntry var6 = new NextTickListEntry(x, y, z, blockID);

		if (blockID > 0) {
			var6.setScheduledTime((long) timeOffset + this.worldInfo.getWorldTotalTime());
		}

		this.pendingTickListEntries.add(var6);
	}

	@Override
	public boolean tickUpdates(boolean runAll) {
		boolean result;
		final ArrayList<NextTickListEntry> runningTickListEntries = this.runningTickListEntries;
		synchronized (pendingTickListEntries) {
			int var2 = Math.min(1000, this.pendingTickListEntries.size());
			runningTickListEntries.ensureCapacity(var2);

			for (int var3 = 0; var3 < var2; ++var3) {
				NextTickListEntry nextTickListEntry = (NextTickListEntry) this.pendingTickListEntries.first();

				if (!runAll && nextTickListEntry.scheduledTime > this.worldInfo.getWorldTotalTime()) {
					break;
				}

				this.pendingTickListEntries.remove(nextTickListEntry);
				runningTickListEntries.add(nextTickListEntry);
			}

			result = !this.pendingTickListEntries.isEmpty();
		}

		ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = getPersistentChunks();
		for (NextTickListEntry var4 : runningTickListEntries) {
			boolean isForced = persistentChunks.containsKey(new ChunkCoordIntPair(var4.xCoord >> 4, var4.zCoord >> 4));
			byte range = isForced ? (byte) 0 : 8;

			if (this.checkChunksExist(var4.xCoord - range, var4.yCoord - range, var4.zCoord - range, var4.xCoord + range, var4.yCoord + range, var4.zCoord + range)) {
				int blockID = this.getBlockIdWithoutLoad(var4.xCoord, var4.yCoord, var4.zCoord);

				if (blockID == var4.blockID && blockID > 0) {
					try {
						Block.blocksList[blockID].updateTick(this, var4.xCoord, var4.yCoord, var4.zCoord, this.rand);
					} catch (Throwable var13) {
						Log.severe("Exception while ticking a block", var13);
					}
				}
			}
		}
		runningTickListEntries.clear();
		return result;
	}

	@Override
	public void tick() {
		int tickCount = this.tickCount++;
		this.updateWeather();
		if (this.difficultySetting < 3 && this.getWorldInfo().isHardcoreModeEnabled()) {
			this.difficultySetting = 3;
		}

		if (tickCount % 200 == 0) {
			this.provider.worldChunkMgr.cleanupCache();
		}

		if (this.areAllPlayersAsleep()) {
			long var2 = this.worldInfo.getWorldTime();
			this.worldInfo.setWorldTime(var2 + var2 % 24000L);
			this.wakeAllPlayers();
		}

		this.theProfiler.startSection("mobSpawner");

		if (this.getGameRules().getGameRuleBooleanValue("doMobSpawning")) {
			SpawnerAnimals.findChunksForSpawning(this, this.spawnHostileMobs, this.spawnPeacefulMobs, this.worldInfo.getWorldTotalTime() % 400L == 0L);
		}

		this.theProfiler.endStartSection("chunkSource");
		this.chunkProvider.unload100OldestChunks();
		this.skylightSubtracted = this.calculateSkylightSubtracted(1.0F);

		this.sendAndApplyBlockEvents();
		this.worldInfo.incrementTotalWorldTime(this.worldInfo.getWorldTotalTime() + 1L);
		this.worldInfo.setWorldTime(this.worldInfo.getWorldTime() + 1L);
		this.theProfiler.endStartSection("tickPending");
		this.tickUpdates(false);
		this.theProfiler.endStartSection("tickTiles");
		this.tickBlocksAndAmbiance();
		this.theProfiler.endStartSection("chunkMap");
		this.thePlayerManager.updatePlayerInstances();
		this.theProfiler.endStartSection("village");
		this.villageCollectionObj.tick();
		this.villageSiegeObj.tick();
		this.theProfiler.endStartSection("portalForcer");
		this.field_85177_Q.func_85189_a(this.getTotalWorldTime());
		for (Teleporter tele : customTeleporters) {
			tele.func_85189_a(getTotalWorldTime());
		}
		this.theProfiler.endSection();
		this.sendAndApplyBlockEvents();
	}

	@Override
	protected void tickBlocksAndAmbiance() {
		boolean concurrentTicks = TickThreading.instance.enableChunkTickThreading && !mcServer.theProfiler.profilingEnabled;

		if (concurrentTicks) {
			threadManager.waitForCompletion();
		}

		if (tickCount % 5 == 0) {
			this.theProfiler.startSection("buildList");

			this.activeChunkSet.clear();
			this.activeChunkSet.addAll(getPersistentChunks().keySet());
			for (EntityPlayer entityPlayer : (Iterable<EntityPlayer>) this.playerEntities) {
				int x = (int) (entityPlayer.posX / 16.0D);
				int z = (int) (entityPlayer.posZ / 16.0D);
				byte var5 = 6;

				for (int var6 = -var5; var6 <= var5; ++var6) {
					for (int var7 = -var5; var7 <= var5; ++var7) {
						this.activeChunkSet.add(new ChunkCoordIntPair(var6 + x, var7 + z));
					}
				}
			}

			this.theProfiler.endSection();

			if (this.ambientTickCountdown > 0) {
				--this.ambientTickCountdown;
			}

			this.theProfiler.startSection("playerCheckLight");

			if (!this.playerEntities.isEmpty()) {
				EntityPlayer entityPlayer = (EntityPlayer) this.playerEntities.get(this.rand.nextInt(this.playerEntities.size()));
				int x = ((int) entityPlayer.posX) + this.rand.nextInt(11) - 5;
				int y = ((int) entityPlayer.posY) + this.rand.nextInt(11) - 5;
				int z = ((int) entityPlayer.posZ) + this.rand.nextInt(11) - 5;
				this.updateAllLightTypes(x, y, z);
			}

			this.theProfiler.endSection();
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
		final Iterator<ChunkCoordIntPair> chunkCoordIterator = this.chunkCoordIterator;
		final ChunkProviderServer chunkProviderServer = this.theChunkProviderServer;
		final boolean isRaining = this.isRaining();
		final boolean isThundering = this.isThundering();
		int updateLCG = this.updateLCG;
		// We use a random per thread - randoms are threadsafe, however synchronization is involved.
		// This reduces contention -> slightly increased performance, woo! :P
		while (true) {
			ChunkCoordIntPair var4;
			synchronized (chunkCoordIterator) {
				if (!chunkCoordIterator.hasNext()) {
					break;
				}
				try {
					var4 = chunkCoordIterator.next();
				} catch (ConcurrentModificationException e) {
					break;
				}
			}

			int cX = var4.chunkXPos;
			int cZ = var4.chunkZPos;
			if ((tpsFactor < 1 && rand.nextFloat() > tpsFactor) || chunkProviderServer.getChunksToUnloadSet().contains(ChunkCoordIntPair.chunkXZ2Int(cX, cZ))) {
				continue;
			}

			int xPos = cX * 16;
			int zPos = cZ * 16;
			Chunk chunk = chunkProviderServer.getChunkIfExists(cX, cZ);
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
			if (isRaining && isThundering && provider.canDoLightning(chunk) && rand.nextInt(100000) == 0) {
				updateLCG = updateLCG * 1664525 + 1013904223;
				var8 = updateLCG >> 2;
				var9 = xPos + (var8 & 15);
				var10 = zPos + (var8 >> 8 & 15);
				var11 = this.getPrecipitationHeight(var9, var10);

				if (this.canLightningStrikeAt(var9, var11, var10)) {
					this.addWeatherEffect(new EntityLightningBolt(this, (double) var9, (double) var11, (double) var10));
				}
			}

			int blockID;

			theProfiler.endStartSection("precipitation");
			if (provider.canDoRainSnowIce(chunk) && rand.nextInt(16) == 0) {
				updateLCG = updateLCG * 1664525 + 1013904223;
				var8 = updateLCG >> 2;
				var9 = var8 & 15;
				var10 = var8 >> 8 & 15;
				var11 = this.getPrecipitationHeight(var9 + xPos, var10 + zPos);

				if (this.isBlockFreezableNaturally(var9 + xPos, var11 - 1, var10 + zPos)) {
					this.setBlockWithNotify(var9 + xPos, var11 - 1, var10 + zPos, Block.ice.blockID);
				}

				if (isRaining) {
					if (this.canSnowAt(var9 + xPos, var11, var10 + zPos)) {
						this.setBlockWithNotify(var9 + xPos, var11, var10 + zPos, Block.snow.blockID);
					}

					BiomeGenBase var12 = this.getBiomeGenForCoords(var9 + xPos, var10 + zPos);

					if (var12.canSpawnLightningBolt()) {
						blockID = this.getBlockIdWithoutLoad(var9 + xPos, var11 - 1, var10 + zPos);

						if (blockID > 0) {
							Block.blocksList[blockID].fillWithRain(this, var9 + xPos, var11 - 1, var10 + zPos);
						}
					}
				}
			}

			theProfiler.endStartSection("blockTick");
			ExtendedBlockStorage[] var19 = chunk.getBlockStorageArray();
			var9 = var19.length;

			for (var10 = 0; var10 < var9; ++var10) {
				ExtendedBlockStorage ebs = var19[var10];

				if (ebs != null && ebs.getNeedsRandomTick()) {
					for (int i = 0; i < 3; ++i) {
						updateLCG = updateLCG * 1664525 + 1013904223;
						blockID = updateLCG >> 2;
						int x = blockID & 15;
						int y = blockID >> 8 & 15;
						int z = blockID >> 16 & 15;
						Block var18 = Block.blocksList[ebs.getExtBlockID(x, z, y)];

						if (var18 != null && var18.getTickRandomly()) {
							try {
								var18.updateTick(this, x + xPos, z + ebs.getYLocation(), y + zPos, rand);
							} catch (Exception e) {
								Log.severe("Exception ticking block " + var18 + " at x" + x + xPos + 'y' + z + ebs.getYLocation() + 'z' + y + zPos, e);
							}
						}
					}
				}
			}
			theProfiler.endSection();
			theProfiler.endStartSection("iterate");
		}
		this.updateLCG += updateLCG * 1664525;
	}

	public static class ThreadLocalRandom extends ThreadLocal<Random> {
		@Override
		public Random initialValue() {
			return new Random();
		}
	}

	@Override
	public void markBlockForUpdate(int x, int y, int z) {
		if (worldGenInProgress == null || !worldGenInProgress.get()) {
			final List<IWorldAccess> worldAccesses = this.worldAccesses;
			for (int i = 0, size = worldAccesses.size(); i < size; ++i) {
				worldAccesses.get(i).markBlockForUpdate(x, y, z);
			}
		}
	}

	public static class BooleanThreadLocal extends ThreadLocal<Boolean> {
		@Override
		public Boolean initialValue() {
			return false;
		}
	}
}
