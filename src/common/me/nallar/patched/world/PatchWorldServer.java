package me.nallar.patched.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableSetMultimap;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.collections.TreeHashSet;
import me.nallar.tickthreading.minecraft.DeadLockDetector;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.contextaccess.ContextAccess;
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
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

@SuppressWarnings ("unchecked") // Can't make the code in WorldServer checked. Yet. // TODO: Add type parameters via prepatcher?
public abstract class PatchWorldServer extends WorldServer implements Runnable {
	private Iterator chunkCoordIterator;
	private ThreadManager threadManager;
	private ThreadLocal<Random> randoms;
	private ArrayList<NextTickListEntry> runningTickListEntries;
	@Declare
	public ThreadLocal<Boolean> worldGenInProgress_;
	private HashSet<ChunkCoordIntPair> chunkTickSet;

	public PatchWorldServer(MinecraftServer par1MinecraftServer, ISaveHandler par2ISaveHandler, String par3Str, int par4, WorldSettings par5WorldSettings, Profiler par6Profiler) {
		super(par1MinecraftServer, par2ISaveHandler, par3Str, par4, par5WorldSettings, par6Profiler);
	}

	public void construct() {
		randoms = new ThreadLocalRandom();
		threadManager = new ThreadManager(TickThreading.instance.getThreadCount(), "Chunk Updates for " + Log.name(this));
		try {
			field_73064_N = null;
		} catch (NoSuchFieldError ignored) {
			//MCPC+ compatibility - they also remove this.
		}
		pendingTickListEntries = new TreeHashSet();
		runningTickListEntries = new ArrayList<NextTickListEntry>();
		try {
			chunkTickSet = (HashSet<ChunkCoordIntPair>) activeChunkSet;
		} catch (NoSuchFieldError ignored) {
			//Spigot support
			chunkTickSet = new HashSet<ChunkCoordIntPair>();
		}
	}

	@Override
	@Declare
	public Object[] getChunks() {
		List<Chunk> loadedChunks = theChunkProviderServer.getLoadedChunks();
		synchronized (loadedChunks) {
			return loadedChunks.toArray();
		}
	}

	@Override
	@Declare
	public void ttStop() {
		IChunkLoader chunkLoader = theChunkProviderServer.currentChunkLoader;
		if (chunkLoader instanceof AnvilChunkLoader) {
			try {
				((AnvilChunkLoader) chunkLoader).close();
			} catch (NoSuchMethodError ignored) {
				//MCPC+ compatibility
			}
		}
		threadManager.stop();
	}

	@Override
	public void flush() {
		DeadLockDetector.tick("Saving a world before unload", System.nanoTime() + 30000000000L);
		if (saveHandler != null) {
			this.saveHandler.flush();

			if (ContextAccess.$.getContext(1).equals(DimensionManager.class)) {
				ttStop();
			}
		}
	}

	@Override
	protected void initialize(WorldSettings par1WorldSettings) {
		if (this.entityIdMap == null) {
			this.entityIdMap = new net.minecraft.util.IntHashMap();
		}

		if (pendingTickListEntries == null) {
			pendingTickListEntries = new TreeHashSet();
		}

		this.createSpawnPosition(par1WorldSettings);
	}

	@Override
	public List getPendingBlockUpdates(Chunk chunk, boolean remove) {
		ArrayList<NextTickListEntry> var3 = null;
		int minCX = chunk.xPosition << 4;
		int maxCX = minCX + 16;
		int minCZ = chunk.zPosition << 4;
		int maxCZ = minCZ + 16;
		TreeSet<NextTickListEntry> pendingTickListEntries = this.pendingTickListEntries;
		synchronized (pendingTickListEntries) {
			Iterator<NextTickListEntry> nextTickListEntryIterator = pendingTickListEntries.iterator();

			while (nextTickListEntryIterator.hasNext()) {
				NextTickListEntry var10 = nextTickListEntryIterator.next();

				if (var10.xCoord >= minCX && var10.xCoord < maxCX && var10.zCoord >= minCZ && var10.zCoord < maxCZ) {
					if (remove) {
						nextTickListEntryIterator.remove();
					}

					if (var3 == null) {
						var3 = new ArrayList<NextTickListEntry>();
					}

					var3.add(var10);
				}
			}
		}

		return var3;
	}

	@Override
	public void func_82740_a(int x, int y, int z, int blockID, int timeOffset, int par6) {
		NextTickListEntry nextTickListEntry = new NextTickListEntry(x, y, z, blockID);
		boolean isForced = getPersistentChunks().containsKey(new ChunkCoordIntPair(nextTickListEntry.xCoord >> 4, nextTickListEntry.zCoord >> 4));
		byte range = isForced ? (byte) 1 : 8;

		if (this.scheduledUpdatesAreImmediate && blockID > 0) {
			if (Block.blocksList[blockID].func_82506_l()) {
				if (this.checkChunksExist(nextTickListEntry.xCoord - range, nextTickListEntry.yCoord - range, nextTickListEntry.zCoord - range, nextTickListEntry.xCoord + range, nextTickListEntry.yCoord + range, nextTickListEntry.zCoord + range)) {
					int realBlockID = this.getBlockIdWithoutLoad(nextTickListEntry.xCoord, nextTickListEntry.yCoord, nextTickListEntry.zCoord);

					if (realBlockID > 0 && realBlockID == nextTickListEntry.blockID) {
						Block.blocksList[realBlockID].updateTick(this, nextTickListEntry.xCoord, nextTickListEntry.yCoord, nextTickListEntry.zCoord, this.rand);
					}
				}

				return;
			}

			timeOffset = 1;
		}

		if (this.checkChunksExist(x - range, y - range, z - range, x + range, y + range, z + range)) {
			if (blockID > 0) {
				nextTickListEntry.setScheduledTime((long) timeOffset + worldInfo.getWorldTotalTime());
				nextTickListEntry.func_82753_a(par6);
			}

			pendingTickListEntries.add(nextTickListEntry);
		}
	}

	@Override
	public void scheduleBlockUpdateFromLoad(int x, int y, int z, int blockID, int timeOffset) {
		NextTickListEntry nextTickListEntry = new NextTickListEntry(x, y, z, blockID);

		if (blockID > 0) {
			nextTickListEntry.setScheduledTime((long) timeOffset + worldInfo.getWorldTotalTime());
		}

		pendingTickListEntries.add(nextTickListEntry);
	}

	@Override
	public boolean tickUpdates(boolean runAll) {
		boolean result;
		final ArrayList<NextTickListEntry> runningTickListEntries = this.runningTickListEntries;
		final TreeSet pendingTickListEntries = this.pendingTickListEntries;
		final Random rand = this.rand;
		final WorldInfo worldInfo = this.worldInfo;
		synchronized (pendingTickListEntries) {
			int var2 = Math.min(1000, pendingTickListEntries.size());
			runningTickListEntries.ensureCapacity(var2);

			for (int var3 = 0; var3 < var2; ++var3) {
				NextTickListEntry nextTickListEntry = (NextTickListEntry) pendingTickListEntries.first();

				if (!runAll && nextTickListEntry.scheduledTime > worldInfo.getWorldTotalTime()) {
					break;
				}

				pendingTickListEntries.remove(nextTickListEntry);
				runningTickListEntries.add(nextTickListEntry);
			}

			result = !pendingTickListEntries.isEmpty();
		}

		ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = getPersistentChunks();
		for (NextTickListEntry var4 : runningTickListEntries) {
			boolean isForced = persistentChunks.containsKey(new ChunkCoordIntPair(var4.xCoord >> 4, var4.zCoord >> 4));
			byte range = isForced ? (byte) 1 : 8;

			if (this.checkChunksExist(var4.xCoord - range, var4.yCoord - range, var4.zCoord - range, var4.xCoord + range, var4.yCoord + range, var4.zCoord + range)) {
				int blockID = this.getBlockIdWithoutLoad(var4.xCoord, var4.yCoord, var4.zCoord);

				if (blockID == var4.blockID && blockID > 0) {
					try {
						Block.blocksList[blockID].updateTick(this, var4.xCoord, var4.yCoord, var4.zCoord, rand);
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
		final Profiler profiler = this.theProfiler;
		final WorldInfo worldInfo = this.worldInfo;
		final int tickCount = ++this.tickCount;
		final boolean hasPlayers = !playerEntities.isEmpty();
		this.updateWeather();
		if (this.difficultySetting < 3 && this.getWorldInfo().isHardcoreModeEnabled()) {
			this.difficultySetting = 3;
		}

		if (tickCount % 120 == 0) {
			redstoneBurnoutMap.clear();
		}

		if (tickCount % 200 == 0) {
			this.provider.worldChunkMgr.cleanupCache();
		}

		if (hasPlayers && this.areAllPlayersAsleep()) {
			long var2 = worldInfo.getWorldTime();
			worldInfo.setWorldTime(var2 + 24000L - (var2 % 24000L));
			this.wakeAllPlayers();
		}

		profiler.startSection("mobSpawner");

		if (hasPlayers && this.getGameRules().getGameRuleBooleanValue("doMobSpawning")) {
			if (TickThreading.instance.shouldFastSpawn(this)) {
				SpawnerAnimals.spawnMobsQuickly(this, this.spawnHostileMobs, this.spawnPeacefulMobs, worldInfo.getWorldTotalTime() % 400L == 0L);
			} else {
				SpawnerAnimals.findChunksForSpawning(this, this.spawnHostileMobs, this.spawnPeacefulMobs, worldInfo.getWorldTotalTime() % 400L == 0L);
			}
		}

		profiler.endStartSection("chunkSource");
		theChunkProviderServer.unload100OldestChunks();
		theChunkProviderServer.tick();
		this.skylightSubtracted = this.calculateSkylightSubtracted(1.0F);

		this.sendAndApplyBlockEvents();
		worldInfo.incrementTotalWorldTime(worldInfo.getWorldTotalTime() + 1L);
		worldInfo.setWorldTime(worldInfo.getWorldTime() + 1L);
		profiler.endStartSection("tickPending");
		this.tickUpdates(false);
		profiler.endStartSection("tickTiles");
		this.tickBlocksAndAmbiance();
		profiler.endStartSection("chunkMap");
		this.thePlayerManager.updatePlayerInstances();
		profiler.endStartSection("village");
		this.villageCollectionObj.tick();
		this.villageSiegeObj.tick();
		profiler.endStartSection("portalForcer");
		this.field_85177_Q.func_85189_a(this.getTotalWorldTime());
		for (Teleporter tele : customTeleporters) {
			tele.func_85189_a(getTotalWorldTime());
		}
		profiler.endSection();
		this.sendAndApplyBlockEvents();
	}

	@Override
	protected void tickBlocksAndAmbiance() {
		boolean concurrentTicks = TickThreading.instance.enableChunkTickThreading && !mcServer.theProfiler.profilingEnabled;

		if (concurrentTicks) {
			threadManager.waitForCompletion();
		}

		HashSet<ChunkCoordIntPair> activeChunkSet = chunkTickSet;
		if (tickCount % 7 == 0) {
			Profiler profiler = this.theProfiler;
			profiler.startSection("buildList");

			activeChunkSet.clear();
			activeChunkSet.addAll(getPersistentChunks().keySet());
			List<EntityPlayer> playerEntities = this.playerEntities;
			for (EntityPlayer entityPlayer : playerEntities) {
				int x = (int) (entityPlayer.posX / 16.0D);
				int z = (int) (entityPlayer.posZ / 16.0D);
				byte var5 = 6;

				for (int var6 = -var5; var6 <= var5; ++var6) {
					for (int var7 = -var5; var7 <= var5; ++var7) {
						activeChunkSet.add(new ChunkCoordIntPair(var6 + x, var7 + z));
					}
				}
			}

			profiler.endSection();

			if (this.ambientTickCountdown > 0) {
				--this.ambientTickCountdown;
			}

			profiler.startSection("playerCheckLight");

			Random rand = this.rand;
			if (!playerEntities.isEmpty()) {
				EntityPlayer entityPlayer = playerEntities.get(rand.nextInt(playerEntities.size()));
				if (entityPlayer != null) {
					int x = ((int) entityPlayer.posX) + rand.nextInt(11) - 5;
					int y = ((int) entityPlayer.posY) + rand.nextInt(11) - 5;
					int z = ((int) entityPlayer.posZ) + rand.nextInt(11) - 5;
					this.updateAllLightTypes(x, y, z);
				}
			}

			profiler.endSection();
		}

		chunkCoordIterator = activeChunkSet.iterator();

		if (concurrentTicks) {
			threadManager.runAll(this);
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
		final Profiler profiler = this.theProfiler;
		final WorldProvider provider = this.provider;
		int updateLCG = this.updateLCG;
		Set<Long> chunksToUnloadSet;
		try {
			chunksToUnloadSet = chunkProviderServer.getChunksToUnloadSet();
		} catch (NoSuchMethodError ignored) {
			chunksToUnloadSet = Collections.emptySet();
		}
		// We use a random per thread - randoms are threadsafe, however it can result in some contention. See Random.nextInt - compareAndSet.
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
			if ((tpsFactor < 1 && rand.nextFloat() > tpsFactor) || chunksToUnloadSet.contains(ChunkCoordIntPair.chunkXZ2Int(cX, cZ))) {
				continue;
			}

			int xPos = cX * 16;
			int zPos = cZ * 16;
			Chunk chunk = chunkProviderServer.getChunkIfExists(cX, cZ);
			if (chunk == null) {
				continue;
			}
			this.moodSoundAndLightCheck(xPos, zPos, chunk);
			profiler.endStartSection("chunkTick"); // endStart as moodSoundAndLightCheck starts a section.
			chunk.updateSkylight();
			int var8;
			int var9;
			int var10;
			int var11;

			profiler.startSection("lightning");
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

			profiler.endStartSection("precipitation");
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

			profiler.endStartSection("blockTick");
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
			profiler.endSection();
			profiler.endStartSection("iterate");
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
		if (worldGenInProgress == null || worldGenInProgress.get() == Boolean.FALSE) {
			final List<IWorldAccess> worldAccesses = this.worldAccesses;
			for (IWorldAccess worldAccess : worldAccesses) {
				worldAccess.markBlockForUpdate(x, y, z);
			}
		}
	}
}
