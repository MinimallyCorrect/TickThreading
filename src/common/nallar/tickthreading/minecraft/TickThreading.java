package nallar.tickthreading.minecraft;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import cpw.mods.fml.relauncher.Side;
import nallar.collections.IntSet;
import nallar.reporting.LeakDetector;
import nallar.reporting.Metrics;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.commands.Command;
import nallar.tickthreading.minecraft.commands.DumpCommand;
import nallar.tickthreading.minecraft.commands.ProfileCommand;
import nallar.tickthreading.minecraft.commands.TPSCommand;
import nallar.tickthreading.minecraft.commands.TicksCommand;
import nallar.tickthreading.minecraft.entitylist.EntityList;
import nallar.tickthreading.minecraft.entitylist.LoadedEntityList;
import nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import nallar.tickthreading.minecraft.profiling.Timings;
import nallar.tickthreading.util.LocationUtil;
import nallar.tickthreading.util.PatchUtil;
import nallar.tickthreading.util.ReflectUtil;
import nallar.tickthreading.util.TableFormatter;
import nallar.tickthreading.util.VersionUtil;
import nallar.tickthreading.util.contextaccess.ContextAccess;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.PacketCount;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;

@SuppressWarnings ("WeakerAccess")
@Mod (modid = "TickThreading", name = "TickThreading", version = "@MOD_VERSION@", acceptedMinecraftVersions = "[1.4.7]")
@NetworkMod (clientSideRequired = false, serverSideRequired = false)
public class TickThreading {
	@Mod.Instance
	public static TickThreading instance;
	private static final int loadedEntityFieldIndex = 0;
	private static final int loadedTileEntityFieldIndex = 2;
	final Map<World, TickManager> managers = new LinkedHashMap<World, TickManager>();
	private final Runtime runtime = Runtime.getRuntime();
	private final IntSet worlds = new IntSet();
	public String messageDeadlockDetected = "The server appears to have frozen and will restart soon if it does not recover. :(";
	public String messageDeadlockRecovered = "The server has recovered and will not need to restart. :)";
	public String messageDeadlockSavingExiting = "The server is saving the world and restarting - be right back!";
	private String profilingFileName = "world/computer/<computer id>/profile.txt";
	public boolean exitOnDeadlock = false;
	public boolean requireOpForTicksCommand = true;
	public boolean requireOpForProfileCommand = true;
	public boolean shouldLoadSpawn = false;
	public boolean concurrentNetworkTicks = false;
	public boolean antiCheatKick = false;
	public boolean antiCheatNotify = false;
	public boolean cleanWorlds = true;
	public boolean allowWorldUnloading = true;
	public boolean requireOpForDumpCommand = true;
	public boolean enableFastMobSpawning = true;
	public boolean enableBugWarningMessage = true;
	public boolean concurrentMovementUpdates = true;
	public boolean rateLimitChunkUpdates = true;
	public int saveInterval = 180;
	public int deadLockTime = 45;
	public int chunkCacheSize = 2000;
	public int chunkGCInterval = 1200;
	public int maxEntitiesPerPlayer = 1000;
	public float mobSpawningMultiplier = 1;
	private int tickThreads = 0;
	private int regionSize = 16;
	private int profilingInterval = 0;
	private int maxItemsPerChunk = 0;
	private boolean profilingJson = false;
	public boolean variableTickRate = true;
	private DeadLockDetector deadLockDetector;
	private HashSet<Integer> disabledFastMobSpawningDimensions = new HashSet<Integer>();
	private boolean waitForEntityTickCompletion = true;
	private int targetTPS = 20;
	private final LeakDetector leakDetector = new LeakDetector(1800);
	public static int recentSpawnedItems;
	private int lastMaxItemWarnedTime;

	static {
		new Metrics("TickThreading", VersionUtil.TTVersionNumber());
	}

	public TickThreading() {
		Log.LOGGER.getLevel(); // Force log class to load
		try {
			PatchUtil.writePatchRunners();
		} catch (IOException e) {
			Log.severe("Failed to write patch runners", e);
		}
		if (PatchUtil.shouldPatch(LocationUtil.getJarLocations())) {
			Log.severe("TickThreading is disabled, because your server has not been patched" +
					" or the patches are out of date" +
					"\nTo patch your server, simply run the PATCHME.bat/sh file in your server directory" +
					"\n\nAlso, make a full backup of your server if you haven't already!");
			MinecraftServer.getServer().initiateShutdown();
			Runtime.getRuntime().exit(1);
		}
	}

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		initPeriodicProfiling();
		if (!enableBugWarningMessage) {
			return;
		}
		GameRegistry.registerPlayerTracker(new LoginWarningHandler());
	}

	private void initPeriodicProfiling() {
		final int profilingInterval = this.profilingInterval;
		if (profilingInterval == 0) {
			return;
		}
		TickRegistry.registerScheduledTickHandler(new ProfilingScheduledTickHandler(profilingInterval, MinecraftServer.getServer().getFile(profilingFileName), profilingJson), Side.SERVER);
	}

	@SuppressWarnings ("FieldRepeatedlyAccessedInMethod")
	@Mod.PreInit
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		String GENERAL = Configuration.CATEGORY_GENERAL;

		TicksCommand.name = config.get(GENERAL, "ticksCommandName", TicksCommand.name, "Name of the command to be used for performance stats. Defaults to ticks.").value;
		TPSCommand.name = config.get(GENERAL, "tpsCommandName", TPSCommand.name, "Name of the command to be used for TPS reports.").value;
		ProfileCommand.name = config.get(GENERAL, "profileCommandName", ProfileCommand.name, "Name of the command to be used for profiling reports.").value;
		DumpCommand.name = config.get(GENERAL, "dumpCommandName", DumpCommand.name, "Name of the command to be used for profiling reports.").value;
		messageDeadlockDetected = config.get(GENERAL, "messageDeadlockDetected", messageDeadlockDetected, "The message to be displayed if a deadlock is detected. (Only sent if exitOnDeadlock is on)").value;
		messageDeadlockRecovered = config.get(GENERAL, "messageDeadlockRecovered", messageDeadlockRecovered, "The message to be displayed if the server recovers from an apparent deadlock. (Only sent if exitOnDeadlock is on)").value;
		messageDeadlockSavingExiting = config.get(GENERAL, "messageDeadlockSavingExiting", messageDeadlockSavingExiting, "The message to be displayed when the server attempts to save and stop after a deadlock. (Only sent if exitOnDeadlock is on)").value;
		tickThreads = config.get(GENERAL, "tickThreads", tickThreads, "number of threads to use to tick. 0 = automatic").getInt(tickThreads);
		regionSize = config.get(GENERAL, "regionSize", regionSize, "width/length of tick regions, specified in blocks.").getInt(regionSize);
		saveInterval = config.get(GENERAL, "saveInterval", saveInterval, "Time between auto-saves, in ticks.").getInt(saveInterval);
		deadLockTime = config.get(GENERAL, "deadLockTime", deadLockTime, "The time(seconds) of being frozen which will trigger the DeadLockDetector. Set to 1 to instead detect lag spikes.").getInt(deadLockTime);
		chunkCacheSize = Math.max(100, config.get(GENERAL, "chunkCacheSize", chunkCacheSize, "Number of unloaded chunks to keep cached. Replacement for Forge's dormant chunk cache, which tends to break. Minimum size of 100").getInt(chunkCacheSize));
		chunkGCInterval = config.get(GENERAL, "chunkGCInterval", chunkGCInterval, "Interval between chunk garbage collections in ticks").getInt(chunkGCInterval);
		targetTPS = config.get(GENERAL, "targetTPS", targetTPS, "TPS the server should try to run at.").getInt(targetTPS);
		maxItemsPerChunk = config.get(GENERAL, "maxItemsPerChunk", maxItemsPerChunk, "Maximum number of entity items allowed per chunk. 0 = no limit.").getInt(maxItemsPerChunk);
		maxEntitiesPerPlayer = config.get(GENERAL, "maxEntitiesPerPlayer", maxEntitiesPerPlayer, "If more entities than this are loaded per player in a world, mob spawning will be disabled in that world.").getInt(maxEntitiesPerPlayer);
		mobSpawningMultiplier = (float) config.get(GENERAL, "mobSpawningMultiplier", mobSpawningMultiplier, "Mob spawning multiplier. Default is 1, can be a decimal.").getDouble(mobSpawningMultiplier);
		variableTickRate = config.get(GENERAL, "variableRegionTickRate", variableTickRate, "Allows tick rate to vary per region so that each region uses at most 50ms on average per tick.").getBoolean(variableTickRate);
		exitOnDeadlock = config.get(GENERAL, "exitOnDeadlock", exitOnDeadlock, "If the server should shut down when a deadlock is detected").getBoolean(exitOnDeadlock);
		enableFastMobSpawning = config.get(GENERAL, "enableFastMobSpawning", enableFastMobSpawning, "If enabled, TT's alternative mob spawning implementation will be used.").getBoolean(enableFastMobSpawning);
		requireOpForTicksCommand = config.get(GENERAL, "requireOpsForTicksCommand", requireOpForTicksCommand, "If a player must be opped to use /ticks").getBoolean(requireOpForTicksCommand);
		requireOpForProfileCommand = config.get(GENERAL, "requireOpsForProfileCommand", requireOpForProfileCommand, "If a player must be opped to use /profile").getBoolean(requireOpForProfileCommand);
		requireOpForDumpCommand = config.get(GENERAL, "requireOpsForDumpCommand", requireOpForDumpCommand, "If a player must be opped to use /dump").getBoolean(requireOpForDumpCommand);
		shouldLoadSpawn = config.get(GENERAL, "shouldLoadSpawn", shouldLoadSpawn, "Whether chunks within 200 blocks of world spawn points should always be loaded.").getBoolean(shouldLoadSpawn);
		waitForEntityTickCompletion = config.get(GENERAL, "waitForEntityTickCompletion", waitForEntityTickCompletion, "Whether we should wait until all Tile/Entity tick threads are finished before moving on with world tick. False = experimental, but may improve performance.").getBoolean(waitForEntityTickCompletion);
		concurrentNetworkTicks = config.get(GENERAL, "concurrentNetworkTicks", concurrentNetworkTicks, "Whether network ticks should be ran in a separate thread from the main minecraft thread. This is likely to be very buggy, especially with mods doing custom networking such as IC2!").getBoolean(concurrentNetworkTicks);
		concurrentMovementUpdates = config.get(GENERAL, "concurrentMovementUpdates", concurrentMovementUpdates, "Whether movement updates should be processed asynchronously. Improves performance, but may cause spontaneous fall damage in some (still not sure what) situations.").getBoolean(concurrentMovementUpdates);
		antiCheatKick = config.get(GENERAL, "antiCheatKick", antiCheatKick, "Whether to kick players for detected cheating").getBoolean(antiCheatKick);
		antiCheatNotify = config.get(GENERAL, "antiCheatNotify", antiCheatNotify, "Whether to notify admins if TT anti-cheat detects cheating").getBoolean(antiCheatNotify);
		cleanWorlds = config.get(GENERAL, "cleanWorlds", cleanWorlds, "Whether to clean worlds on unload - this should fix some memory leaks due to mods holding on to world objects").getBoolean(cleanWorlds);
		allowWorldUnloading = config.get(GENERAL, "allowWorldUnloading", allowWorldUnloading, "Whether worlds should be allowed to unload.").getBoolean(allowWorldUnloading);
		enableBugWarningMessage = config.get(GENERAL, "enableBugWarningMessage", enableBugWarningMessage, "Whether to enable warning if there are severe known compatibility issues with the current TT build you are using and your installed mods. Highly recommend leaving this enabled, if you disable it chances are you'll get users experiencing these issues annoying mod authors, which I really don't want to happen.").getBoolean(enableBugWarningMessage);
		profilingInterval = config.get(GENERAL, "profilingInterval", profilingInterval, "Interval, in minutes, to record profiling information to disk. 0 = never. Recommended >= 2.").getInt();
		profilingFileName = config.get(GENERAL, "profilingFileName", profilingFileName, "Location to store profiling information to, relative to the server folder. For example, why not store it in a computercraft computer's folder?").value;
		profilingJson = config.get(GENERAL, "profilingJson", profilingJson, "Whether to write periodic profiling in JSON format").getBoolean(profilingJson);
		rateLimitChunkUpdates = config.get(GENERAL, "rateLiitChunkUpdates", rateLimitChunkUpdates, "Whether to prevent repeated chunk updates which can cause rendering issues and disconnections for slow clients/connections.").getBoolean(rateLimitChunkUpdates);
		config.save();
		int[] disabledDimensions = config.get(GENERAL, "disableFastMobSpawningDimensions", new int[]{-1}, "List of dimensions not to enable fast spawning in.").getIntList();
		disabledFastMobSpawningDimensions = new HashSet<Integer>(disabledDimensions.length);
		for (int disabledDimension : disabledDimensions) {
			disabledFastMobSpawningDimensions.add(disabledDimension);
		}
		PacketCount.allowCounting = false;
	}

	@Mod.ServerStarting
	public void serverStarting(FMLServerStartingEvent event) {
		Log.severe(VersionUtil.versionString() + " is installed on this server!"
				+ "\nIf anything breaks, check if it is still broken without TickThreading"
				+ "\nWe don't want to annoy mod devs with issue reports caused by TickThreading."
				+ "\nSeriously, please don't."
				+ "\nIf it's only broken with TickThreading, report it at http://github.com/nallar/TickThreading"
				+ "\n\nAlso, you really should be making regular backups. (You should be doing that even when not using TT.)");
		if (Log.debug) {
			Log.severe("TickThreading is running in debug mode.");
		}
		ServerCommandManager serverCommandManager = (ServerCommandManager) event.getServer().getCommandManager();
		serverCommandManager.registerCommand(new TicksCommand());
		serverCommandManager.registerCommand(new TPSCommand());
		serverCommandManager.registerCommand(new ProfileCommand());
		serverCommandManager.registerCommand(new DumpCommand());
		MinecraftServer.setTargetTPS(targetTPS);
		FMLLog.info("Loaded " + RelaunchClassLoader.patchedClasses + " patched classes" +
				"\nUsed " + RelaunchClassLoader.usedPatchedClasses + '.');
		Command.checkForPermissions();
		String javaVersion = System.getProperty("java.runtime.version");
		if (javaVersion.startsWith("1.6.0_")) {
			int extrasIndex = javaVersion.indexOf('-');
			if (extrasIndex != -1) {
				javaVersion = javaVersion.substring(0, extrasIndex);
			}
			try {
				boolean old = Integer.parseInt(javaVersion.substring(6)) < 34;
				String warning = "It is recommended to use a Java 7 JRE. " + (old ? ", or if that is not possible, at least use the latest Java 6 JRE. " : "") + "Current version: " + javaVersion;
				if (old) {
					Log.severe(warning);
				} else {
					Log.info(warning);
				}
			} catch (NumberFormatException e) {
				Log.warning("Unknown JRE version format, " + System.getProperty("java.runtime.version") + " -> " + javaVersion, e);
			}
		}
	}

	@ForgeSubscribe (
			priority = EventPriority.HIGHEST
	)
	public synchronized void onWorldLoad(WorldEvent.Load event) {
		World world = event.world;
		if (world.isRemote) {
			Log.severe("World " + Log.name(world) + " seems to be a client world", new Throwable());
			return;
		}
		if (DimensionManager.getWorld(world.getDimension()) != world) {
			Log.severe("World " + world.getName() + " was loaded with an incorrect dimension ID!", new Throwable());
			return;
		}
		if (managers.containsKey(world)) {
			Log.severe("World " + world.getName() + "'s world load event was fired twice.", new Throwable());
			return;
		}
		if (!worlds.add(world.provider.dimensionId)) {
			Log.severe("World " + world.getName() + " has a duplicate provider dimension ID.\n" + Log.dumpWorlds());
		}
		world.loadEventFired = true;
		TickManager manager = new TickManager((WorldServer) world, regionSize, getThreadCount(), waitForEntityTickCompletion);
		try {
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			new LoadedTileEntityList(world, loadedTileEntityField, manager);
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			new LoadedEntityList(world, loadedEntityField, manager);
			Log.info("Threading initialised for world " + Log.name(world));
			if (managers.put(world, manager) != null) {
				Log.severe("World load fired twice for world " + world.getName());
			}
		} catch (Exception e) {
			Log.severe("Failed to initialise threading for world " + Log.name(world), e);
		}
		if (deadLockDetector == null) {
			deadLockDetector = new DeadLockDetector();
		}
	}

	@ForgeSubscribe
	public synchronized void onWorldUnload(WorldEvent.Unload event) {
		if (MinecraftServer.getServer().isServerRunning() && !ContextAccess.$.runningUnder(DimensionManager.class)) {
			Log.severe("World unload event fired from unexpected location", new Throwable());
		}
		World world = event.world;
		try {
			TickManager tickManager = managers.remove(world);
			if (tickManager == null) {
				Log.severe("World unload fired twice for world " + world.getName(), new Throwable());
				return;
			}
			tickManager.unload();
			Field loadedTileEntityField = ReflectUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
			Object loadedTileEntityList = loadedTileEntityField.get(world);
			if (!(loadedTileEntityList instanceof EntityList)) {
				Log.severe("Looks like another mod broke TT's replacement tile entity list in world: " + Log.name(world));
			}
			Field loadedEntityField = ReflectUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
			Object loadedEntityList = loadedEntityField.get(world);
			if (!(loadedEntityList instanceof EntityList)) {
				Log.severe("Looks like another mod broke TT's replacement entity list in world: " + Log.name(world));
			}
		} catch (Exception e) {
			Log.severe("Probable memory leak, failed to unload threading for world " + Log.name(world), e);
		}
		if (!worlds.remove(world.provider.dimensionId)) {
			Log.severe("When removing " + world.getName() + ", its provider dimension ID was not already in the world dimension ID set.\n" + Log.dumpWorlds());
		}
		if (world instanceof WorldServer) {
			((WorldServer) world).stopChunkTickThreads();
			leakDetector.scheduleLeakCheck(world, world.getName(), cleanWorlds);
		}
	}

	@ForgeSubscribe
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
			EntityPlayer entityPlayer = event.entityPlayer;
			ItemStack usedItem = entityPlayer.getCurrentEquippedItem();
			if (usedItem != null) {
				Item usedItemType = usedItem.getItem();
				if (usedItemType == Item.pocketSundial && (!requireOpForDumpCommand || entityPlayer.canCommandSenderUseCommand(4, "dump"))) {
					Command.sendChat(entityPlayer, DumpCommand.dump(new TableFormatter(entityPlayer), entityPlayer.worldObj, event.x, event.y, event.z, 35).toString());
					event.setCanceled(true);
				}
			}
		}
	}

	@ForgeSubscribe
	public void onPlayerFall(LivingFallEvent event) {
		EntityLiving livingEntity = event.entityLiving;
		if (livingEntity.fallDistance > 2 && Log.debug && livingEntity instanceof EntityPlayerMP) {
			EntityPlayerMP entityPlayerMP = (EntityPlayerMP) livingEntity;
			Log.debug(entityPlayerMP.username + " fell " + entityPlayerMP.fallDistance + " blocks. Teleported counter: " + entityPlayerMP.playerNetServerHandler.teleported, new Throwable());
		}
	}

	public TickManager getManager(World world) {
		return managers.get(world);
	}

	public List<TickManager> getManagers() {
		return new ArrayList<TickManager>(managers.values());
	}

	public boolean shouldFastSpawn(World world) {
		return this.enableFastMobSpawning && !disabledFastMobSpawningDimensions.contains(world.getDimension());
	}

	public int getThreadCount() {
		return tickThreads == 0 ? runtime.availableProcessors() + 1 : tickThreads;
	}

	public void waitForEntityTicks() {
		if (!waitForEntityTickCompletion) {
			long sT = 0;
			boolean profiling = Timings.enabled;
			if (profiling) {
				sT = System.nanoTime();
			}
			for (TickManager manager : managers.values()) {
				manager.tickEnd();
			}
			if (profiling) {
				Timings.record("server/EntityTickWait", System.nanoTime() - sT);
			}
		}
	}

	public boolean removeIfOverMaxItems(final EntityItem e, final Chunk chunk) {
		if (maxItemsPerChunk == 0) {
			return false;
		}
		ArrayList<EntityItem> entityItems = chunk.getEntitiesOfType(EntityItem.class);
		if (entityItems.size() > maxItemsPerChunk) {
			int remaining = 0;
			for (EntityItem entityItem : entityItems) {
				if (!entityItem.isDead) {
					remaining++;
					entityItem.combineList(entityItems);
				}
			}
			if (remaining > maxItemsPerChunk) {
				e.setDead();
			}
			if (lastMaxItemWarnedTime < MinecraftServer.currentTick - 10) {
				lastMaxItemWarnedTime = MinecraftServer.currentTick;
				Log.warning("Entity items in chunk " + chunk + " exceeded limit of " + maxItemsPerChunk);
			}
			return e.isDead;
		}
		return false;
	}

	private class LoginWarningHandler implements IPlayerTracker {
		LoginWarningHandler() {
		}

		@Override
		public void onPlayerLogin(final EntityPlayer player) {
			if (player instanceof EntityPlayerMP) {
				NetServerHandler netServerHandler = ((EntityPlayerMP) player).playerNetServerHandler;
				// Warnings for severe issues go here.
			}
		}

		@Override
		public void onPlayerLogout(final EntityPlayer player) {
		}

		@Override
		public void onPlayerChangedDimension(final EntityPlayer player) {
		}

		@Override
		public void onPlayerRespawn(final EntityPlayer player) {
		}
	}

	private static class ProfilingScheduledTickHandler implements IScheduledTickHandler {
		private static final EnumSet<TickType> TICKS = EnumSet.of(TickType.SERVER);
		private final int profilingInterval;
		private final File profilingFile;
		private final boolean json;

		public ProfilingScheduledTickHandler(final int profilingInterval, final File profilingFile, final boolean json) {
			this.profilingInterval = profilingInterval;
			this.profilingFile = profilingFile;
			this.json = json;
		}

		@Override
		public int nextTickSpacing() {
			return profilingInterval * 60 * 20;
		}

		@Override
		public void tickStart(final EnumSet<TickType> type, final Object... tickData) {
			final EntityTickProfiler entityTickProfiler = EntityTickProfiler.ENTITY_TICK_PROFILER;
			entityTickProfiler.startProfiling(new Runnable() {
				@Override
				public void run() {
					try {
						TableFormatter tf = new TableFormatter(MinecraftServer.getServer());
						tf.tableSeparator = "\n";
						if (json) {
							entityTickProfiler.writeJSONData(profilingFile);
						} else {
							Files.write(entityTickProfiler.writeStringData(tf, 6).toString(), profilingFile, Charsets.UTF_8);
						}
					} catch (Throwable t) {
						Log.severe("Failed to save periodic profiling data to " + profilingFile, t);
					}
				}
			}, ProfileCommand.ProfilingState.GLOBAL, 10, Arrays.<World>asList(DimensionManager.getWorlds()));
		}

		@Override
		public void tickEnd(final EnumSet<TickType> type, final Object... tickData) {
		}

		@Override
		public EnumSet<TickType> ticks() {
			return TICKS;
		}

		@Override
		public String getLabel() {
			return "TickThreading scheduled profiling handler";
		}
	}
}
