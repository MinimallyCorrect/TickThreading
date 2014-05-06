package nallar.patched.world;

import com.google.common.collect.ImmutableSetMultimap;
import nallar.collections.TreeHashSet;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.DeadLockDetector;
import nallar.tickthreading.minecraft.ThreadManager;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.BlockInfo;
import nallar.tickthreading.util.contextaccess.ContextAccess;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEventData;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.logging.ILogAgent;
import net.minecraft.network.packet.Packet54PlayNoteBlock;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.ServerBlockEventList;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import java.util.*;

@SuppressWarnings("unchecked")
// Can't make the code in WorldServer checked. Yet. // TODO: Add type parameters via prepatcher?
public abstract class PatchWorldServer extends WorldServer implements Runnable {
	public long ticksPerAnimalSpawns;
	public long ticksPerMonsterSpawns;
	private Iterator chunkCoordIterator;
	private ThreadManager threadManager;
	private static final ThreadLocalRandom randoms = new ThreadLocalRandom();
	private int lastTickEntries;
	@Declare
	public nallar.tickthreading.util.BooleanThreadLocalDefaultFalse worldGenInProgress_;
	@Declare
	public nallar.tickthreading.util.BooleanThreadLocalDefaultFalse inImmediateBlockUpdate_;
	@Declare
	public int saveTickCount_;
	private int chunkTickWait;
	private HashSet<ChunkCoordIntPair> chunkTickSet;
	private TreeHashSet<NextTickListEntry> pendingTickListEntries;

	public PatchWorldServer(final MinecraftServer par1MinecraftServer, final ISaveHandler par2ISaveHandler, final String par3Str, final int par4, final WorldSettings par5WorldSettings, final Profiler par6Profiler, final ILogAgent par7ILogAgent) {
		super(par1MinecraftServer, par2ISaveHandler, par3Str, par4, par5WorldSettings, par6Profiler, par7ILogAgent);
	}

	public void construct() {
		if (ticksPerAnimalSpawns == 0) {
			ticksPerAnimalSpawns = 1;
		}
		if (ticksPerMonsterSpawns == 0) {
			ticksPerMonsterSpawns = 1;
		}
		threadManager = new ThreadManager(TickThreading.instance.getThreadCount(), "Chunk Updates for " + Log.name(this));
		try {
			pendingTickListEntriesHashSet = null;
		} catch (NoSuchFieldError ignored) {
			//MCPC+ compatibility - they also remove this.
		}
		pendingTickListEntries = new TreeHashSet();
		try {
			chunkTickSet = (HashSet<ChunkCoordIntPair>) activeChunkSet;
		} catch (NoSuchFieldError ignored) {
			//Spigot support
			chunkTickSet = new HashSet<ChunkCoordIntPair>();
		}
	}

	@Override
	protected boolean onBlockEventReceived(BlockEventData blockEvent) {
		int blockId = this.getBlockIdWithoutLoad(blockEvent.getX(), blockEvent.getY(), blockEvent.getZ());

		if (blockId != -1 && blockId == blockEvent.getBlockID()) {
			Block block = Block.blocksList[blockId];
			if (block != null) {
				block.onBlockEventReceived(this, blockEvent.getX(), blockEvent.getY(), blockEvent.getZ(), blockEvent.getEventID(), blockEvent.getEventParameter());
				return true;
			}
		}
		return false;
	}

	@Override
	@Declare
	public Object[] getChunks() {
		ChunkProviderServer chunkProviderServer = theChunkProviderServer;
		if (chunkProviderServer == null) {
			Log.warning("Bukkit getChunks call for unloaded world", new Throwable());
			return new Object[0];
		}
		List<Chunk> loadedChunks = chunkProviderServer.getLoadedChunks();
		synchronized (loadedChunks) {
			return loadedChunks.toArray();
		}
	}

	@Override
	@Declare
	public List getEntities() {
		List loadedEntityList = this.loadedEntityList;
		if (loadedEntityList == null) {
			Log.warning("Bukkit getChunks call for unloaded world", new Throwable());
			return Collections.emptyList();
		}
		synchronized (loadedEntityList) {
			return Arrays.asList(loadedEntityList.toArray());
		}
	}

	@Override
	@Declare
	public List getPlayerEntities() {
		return playerEntities;
	}

	@Override
	@Declare
	public void stopChunkTickThreads() {
		threadManager.stop();
	}

	@Override
	@Declare
	public void ttStop() {
		unloaded = true;
		IChunkLoader chunkLoader = theChunkProviderServer.currentChunkLoader;
		if (chunkLoader instanceof AnvilChunkLoader) {
			try {
				((AnvilChunkLoader) chunkLoader).close();
			} catch (NoSuchMethodError ignored) {
				//MCPC+ compatibility
			}
		}
	}

	@Override
	public void saveAllChunks(boolean par1, IProgressUpdate par2IProgressUpdate) throws MinecraftException {
		if (this.chunkProvider.canSave()) {
			synchronized (WorldEvent.Save.class) {
				this.saveLevel();
			}

			this.chunkProvider.saveChunks(par1, par2IProgressUpdate);

			synchronized (WorldEvent.Save.class) {
				MinecraftForge.EVENT_BUS.post(new WorldEvent.Save(this));
			}
		}
	}

	@Override
	public void flush() {
		DeadLockDetector.tickAhead(60);
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
	@Declare
	public nallar.tickthreading.util.TableFormatter writePendingBlockUpdatesStats(nallar.tickthreading.util.TableFormatter tf) {
		tf.sb.append(pendingTickListEntries.size()).append(" pending block updates\n");
		TreeHashSet<NextTickListEntry> pendingTickListEntries = this.pendingTickListEntries;
		Iterator<NextTickListEntry> nextTickListEntryIterator = pendingTickListEntries.concurrentIterator();
		int[] blockFrequencies = new int[4096];

		while (nextTickListEntryIterator.hasNext()) {
			NextTickListEntry tickListEntry = nextTickListEntryIterator.next();

			int blockId = getBlockIdWithoutLoad(tickListEntry.xCoord, tickListEntry.yCoord, tickListEntry.zCoord);
			blockFrequencies[blockId == -1 ? 0 : blockId]++;
		}

		tf
				.heading("Block")
				.heading("Count");
		for (int i = 0; i < 10; i++) {
			int longest = 0, longestIndex = -1;
			for (int j = 0; j < blockFrequencies.length; j++) {
				int f = blockFrequencies[j];
				if (f > longest) {
					longestIndex = j;
					longest = f;
				}
			}
			if (longestIndex == -1) {
				break;
			}
			blockFrequencies[longestIndex] = 0;
			BlockInfo blockInfo = new BlockInfo(longestIndex, 0);
			tf
					.row(blockInfo.id + ':' + blockInfo.name)
					.row(longest);
		}
		tf.finishTable();
		return tf;
	}

	@Override
	public List getPendingBlockUpdates(Chunk chunk, boolean remove) {
		if (remove) {
			throw new UnsupportedOperationException();
		}
		ArrayList<NextTickListEntry> var3 = null;
		int minCX = chunk.xPosition << 4;
		int maxCX = minCX + 16;
		int minCZ = chunk.zPosition << 4;
		int maxCZ = minCZ + 16;
		TreeHashSet<NextTickListEntry> pendingTickListEntries = this.pendingTickListEntries;
		Iterator<NextTickListEntry> nextTickListEntryIterator = pendingTickListEntries.concurrentIterator();

		while (nextTickListEntryIterator.hasNext()) {
			NextTickListEntry var10 = nextTickListEntryIterator.next();

			if (var10.xCoord >= minCX && var10.xCoord < maxCX && var10.zCoord >= minCZ && var10.zCoord < maxCZ) {
				if (var3 == null) {
					var3 = new ArrayList<NextTickListEntry>();
				}

				var3.add(var10);
			}
		}

		return var3;
	}

	@Override
	public void scheduleBlockUpdateWithPriority(int x, int y, int z, int blockID, int timeOffset, int par6) {
		NextTickListEntry nextTickListEntry = new NextTickListEntry(x, y, z, blockID);
		//boolean isForced = getPersistentChunks().containsKey(new ChunkCoordIntPair(nextTickListEntry.xCoord >> 4, nextTickListEntry.zCoord >> 4));
		//byte range = isForced ? (byte) 0 : 8;
		// Removed in Forge for now.
		// byte range = 0;

		if (blockID > 0 && timeOffset <= 20 && worldGenInProgress.get() == Boolean.TRUE && inImmediateBlockUpdate.get() == Boolean.FALSE) {
			if (Block.blocksList[blockID].func_82506_l()) {
				// Not needed currently due to forge change removal.
				// if (this.checkChunksExist(x - range, y - range, z - range, x + range, y + range, z + range)) {
				if (this.chunkExists(x >> 4, z >> 4)) {
					int realBlockID = this.getBlockIdWithoutLoad(nextTickListEntry.xCoord, nextTickListEntry.yCoord, nextTickListEntry.zCoord);

					if (realBlockID > 0 && realBlockID == nextTickListEntry.blockID) {
						inImmediateBlockUpdate.set(true);
						try {
							Block.blocksList[realBlockID].updateTick(this, nextTickListEntry.xCoord, nextTickListEntry.yCoord, nextTickListEntry.zCoord, this.rand);
						} finally {
							inImmediateBlockUpdate.set(false);
						}
					}
				}

				return;
			}

			timeOffset = 1;
		}

		// if (this.checkChunksExist(x - range, y - range, z - range, x + range, y + range, z + range)) {
		if (this.chunkExists(x >> 4, z >> 4)) {
			if (blockID > 0) {
				nextTickListEntry.setScheduledTime((long) timeOffset + worldInfo.getWorldTotalTime());
				nextTickListEntry.setPriority(par6);
			}

			pendingTickListEntries.add(nextTickListEntry);
		}
	}

	@Override
	public void scheduleBlockUpdateFromLoad(int x, int y, int z, int blockID, int timeOffset, int par6) {
		NextTickListEntry nextTickListEntry = new NextTickListEntry(x, y, z, blockID);
		nextTickListEntry.setPriority(par6);

		if (blockID > 0) {
			nextTickListEntry.setScheduledTime((long) timeOffset + worldInfo.getWorldTotalTime());
		}

		pendingTickListEntries.add(nextTickListEntry);
	}

	@Override
	public boolean tickUpdates(boolean runAll) {
		final ArrayList<NextTickListEntry> runningTickListEntries;
		final TreeSet pendingTickListEntries = this.pendingTickListEntries;
		final Random rand = this.rand;
		final WorldInfo worldInfo = this.worldInfo;
		synchronized (pendingTickListEntries) {
			int max = pendingTickListEntries.size();

			if (max == 0) {
				return false;
			}

			runningTickListEntries = new ArrayList<NextTickListEntry>(Math.min(lastTickEntries, max));

			final long worldTime = worldInfo.getWorldTotalTime();

			for (int i = 0; i < max; ++i) {
				NextTickListEntry nextTickListEntry = (NextTickListEntry) pendingTickListEntries.first();

				if (!runAll && nextTickListEntry.scheduledTime > worldTime) {
					break;
				}

				pendingTickListEntries.remove(nextTickListEntry);
				runningTickListEntries.add(nextTickListEntry);
			}
		}

		ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> persistentChunks = getPersistentChunks();
		for (NextTickListEntry entry : runningTickListEntries) {
			if (this.chunkExists(entry.xCoord >> 4, entry.zCoord >> 4)) {
				int blockID = this.getBlockIdWithoutLoad(entry.xCoord, entry.yCoord, entry.zCoord);

				if (blockID == entry.blockID && blockID > 0) {
					try {
						Block.blocksList[blockID].updateTick(this, entry.xCoord, entry.yCoord, entry.zCoord, rand);
					} catch (Throwable var13) {
						Log.severe("Exception while ticking a block", var13);
					}
				}
			}
		}
		lastTickEntries = runningTickListEntries.size();
		return true;
	}

	@Override
	public void tick() {
		final Profiler profiler = this.theProfiler;
		final WorldInfo worldInfo = this.worldInfo;
		final int tickCount = ++this.tickCount;
		final boolean hasPlayers = !playerEntities.isEmpty();
		this.updateWeather();
		if (!forcedChunksInited) {
			ForgeChunkManager.loadForcedChunks(this);
		}
		updateEntityTick = 0;
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

		if (hasPlayers && (loadedEntityList.size() / TickThreading.instance.maxEntitiesPerPlayer) < playerEntities.size() && this.getGameRules().getGameRuleBooleanValue("doMobSpawning")) {
			if (TickThreading.instance.shouldFastSpawn(this)) {
				SpawnerAnimals.spawnMobsQuickly(this, spawnHostileMobs && (ticksPerMonsterSpawns != 0 && tickCount % ticksPerMonsterSpawns == 0L), spawnPeacefulMobs && (ticksPerAnimalSpawns != 0 && tickCount % ticksPerAnimalSpawns == 0L), worldInfo.getWorldTotalTime() % 400L == 0L);
			} else {
				animalSpawner.findChunksForSpawning(this, spawnHostileMobs && (ticksPerMonsterSpawns != 0 && tickCount % ticksPerMonsterSpawns == 0L), spawnPeacefulMobs && (ticksPerAnimalSpawns != 0 && tickCount % ticksPerAnimalSpawns == 0L), worldInfo.getWorldTotalTime() % 400L == 0L);
			}
		}

		profiler.endStartSection("chunkSource");
		theChunkProviderServer.unloadQueuedChunks();
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
		this.worldTeleporter.removeStalePortalLocations(this.getTotalWorldTime());
		for (Teleporter tele : customTeleporters) {
			tele.removeStalePortalLocations(getTotalWorldTime());
		}
		profiler.endSection();
		this.sendAndApplyBlockEvents();
	}

	@Override
	protected void tickBlocksAndAmbiance() {
		boolean concurrentTicks = !mcServer.theProfiler.profilingEnabled;

		if (concurrentTicks) {
			if (threadManager.isWaiting()) {
				if (++chunkTickWait <= 3) {
					return;
				}
			}
			chunkTickWait = 0;
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
		final WorldProvider provider = this.provider;
		int updateLCG = this.updateLCG;
		// We use a random per thread - randoms are threadsafe, however it can result in some contention. See Random.nextInt - compareAndSet.
		// This reduces contention -> slightly increased performance, woo! :P
		while (true) {
			ChunkCoordIntPair chunkCoordIntPair;
			synchronized (chunkCoordIterator) {
				if (!chunkCoordIterator.hasNext()) {
					break;
				}
				try {
					chunkCoordIntPair = chunkCoordIterator.next();
				} catch (ConcurrentModificationException e) {
					break;
				}
			}

			if (tpsFactor < 1 && rand.nextFloat() > tpsFactor) {
				continue;
			}

			int cX = chunkCoordIntPair.chunkXPos;
			int cZ = chunkCoordIntPair.chunkZPos;

			Chunk chunk = chunkProviderServer.getChunkFastUnsafe(cX, cZ);
			if (chunk == null || !chunk.isTerrainPopulated || chunk.partiallyUnloaded || chunk.queuedUnload) {
				continue;
			}

			int xPos = cX * 16;
			int zPos = cZ * 16;
			this.moodSoundAndLightCheck(xPos, zPos, chunk);
			chunk.updateSkylight();
			int var8;
			int var9;
			int var10;
			int var11;

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

			if (provider.canDoRainSnowIce(chunk) && rand.nextInt(16) == 0) {
				updateLCG = updateLCG * 1664525 + 1013904223;
				var8 = updateLCG >> 2;
				var9 = var8 & 15;
				var10 = var8 >> 8 & 15;
				var11 = this.getPrecipitationHeight(var9 + xPos, var10 + zPos);

				if (this.isBlockFreezableNaturally(var9 + xPos, var11 - 1, var10 + zPos)) {
					this.setBlock(var9 + xPos, var11 - 1, var10 + zPos, Block.ice.blockID);
				}

				if (isRaining) {
					if (this.canSnowAt(var9 + xPos, var11, var10 + zPos)) {
						this.setBlock(var9 + xPos, var11, var10 + zPos, Block.snow.blockID);
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

	@Override
	@Declare
	public synchronized List getNoteBlockEvents() {
		ArrayList serverBlockEventList = blockEventCache[blockEventCacheIndex];
		if (serverBlockEventList.isEmpty()) {
			return Collections.EMPTY_LIST;
		}
		ArrayList<BlockEventData> noteBlockEvents = new ArrayList<BlockEventData>();
		for (BlockEventData blockEventData : (Iterable<BlockEventData>) serverBlockEventList) {
			if (blockEventData.getBlockID() == Block.music.blockID) {
				noteBlockEvents.add(blockEventData);
			}
		}
		return noteBlockEvents;
	}

	@Override
	public Chunk getChunkIfExists(int x, int z) {
		return theChunkProviderServer.getChunkIfExists(x, z);
	}

	public boolean safeToGenerate() {
		return theChunkProviderServer.safeToGenerate();
	}

	@Override
	public synchronized void addBlockEvent(int par1, int par2, int par3, int par4, int par5, int par6) {
		BlockEventData blockEventData1 = new BlockEventData(par1, par2, par3, par4, par5, par6);
		ArrayList<BlockEventData> blockEventCache = this.blockEventCache[this.blockEventCacheIndex];
		for (BlockEventData blockEventData2 : blockEventCache) {
			if (blockEventData1.equals(blockEventData2)) {
				return;
			}
		}
		blockEventCache.add(blockEventData1);
	}

	@Override
	protected void sendAndApplyBlockEvents() {
		while (true) {
			ArrayList<BlockEventData> blockEventCache;
			synchronized (this) {
				blockEventCache = this.blockEventCache[blockEventCacheIndex];
				if (blockEventCache.isEmpty()) {
					return;
				}
				this.blockEventCache[blockEventCacheIndex] = new ServerBlockEventList();
			}

			for (BlockEventData blockEventData : blockEventCache) {
				if (this.onBlockEventReceived(blockEventData)) {
					this.mcServer.getConfigurationManager().sendToAllNear((double) blockEventData.getX(), (double) blockEventData.getY(), (double) blockEventData.getZ(), 64.0D, this.provider.dimensionId, new Packet54PlayNoteBlock(blockEventData.getX(), blockEventData.getY(), blockEventData.getZ(), blockEventData.getBlockID(), blockEventData.getEventID(), blockEventData.getEventParameter()));
				}
			}

			blockEventCache.clear();
		}
	}
}
