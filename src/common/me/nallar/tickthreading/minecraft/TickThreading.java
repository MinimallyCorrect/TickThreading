package me.nallar.tickthreading.minecraft;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.commands.DumpCommand;
import me.nallar.tickthreading.minecraft.commands.ProfileCommand;
import me.nallar.tickthreading.minecraft.commands.TPSCommand;
import me.nallar.tickthreading.minecraft.commands.TicksCommand;
import me.nallar.tickthreading.minecraft.entitylist.EntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedEntityList;
import me.nallar.tickthreading.minecraft.entitylist.LoadedTileEntityList;
import me.nallar.tickthreading.util.FieldUtil;
import me.nallar.tickthreading.util.LocationUtil;
import me.nallar.tickthreading.util.PatchUtil;
import me.nallar.tickthreading.util.VersionUtil;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.network.packet.PacketCount;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.world.WorldEvent;

@SuppressWarnings ("WeakerAccess")
@Mod (modid = "TickThreading", name = "TickThreading", version = "@MOD_VERSION@")
@NetworkMod (clientSideRequired = false, serverSideRequired = false)
public class TickThreading {
	private final Runtime runtime = Runtime.getRuntime();
	private static final int loadedEntityFieldIndex = 0;
	private static final int loadedTileEntityFieldIndex = 2;
	private int tickThreads = 0;
	private boolean enableEntityTickThreading = true;
	private boolean enableTileEntityTickThreading = true;
	private int regionSize = 16;
	private boolean variableTickRate = true;
	public boolean exitOnDeadlock = false;
	final Map<World, TickManager> managers = new WeakHashMap<World, TickManager>();
	private DeadLockDetector deadLockDetector = null;
	public static TickThreading instance;
	public boolean enableChunkTickThreading = true;
	public boolean enableWorldTickThreading = true;
	public boolean requireOpForTicksCommand = true;
	public boolean requireOpForProfileCommand = true;
	public boolean shouldLoadSpawn = false;
	public int saveInterval = 1800;
	public int deadLockTime = 45;
	public boolean aggressiveTicks = true;
	public boolean enableFastMobSpawning = false;
	private HashSet<Integer> disabledFastMobSpawningDimensions = new HashSet<Integer>();
	private boolean waitForEntityTickCompletion = true;
	public int chunkCacheSize = 2000;
	public int chunkGCInterval = 600;
	private int targetTPS = 20;
	public boolean concurrentNetworkTicks = false;
	public boolean antiCheatKick = false;
	public boolean antiCheatNotify = true;
	public boolean cleanWorlds = true;
	public boolean lockRegionBorders = true;
	public boolean allowWorldUnloading = true;
	public boolean requireOpForDumpCommand = true;
	public boolean loadChunkOnProvideRequest = true;
	public boolean generateChunkOnProvideRequest = false;

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
					"\nTo patch your server, simply run the PATCHME.bat/sh file in your server directory");
			MinecraftServer.getServer().initiateShutdown();
			Runtime.getRuntime().exit(1);
		}
		instance = this;
	}

	@Mod.Init
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Mod.PreInit
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		Property tickThreadsProperty = config.get(Configuration.CATEGORY_GENERAL, "tickThreads", tickThreads);
		tickThreadsProperty.comment = "number of threads to use to tick. 0 = automatic";
		Property enableEntityTickThreadingProperty = config.get(Configuration.CATEGORY_GENERAL, "enableEntityTickThreading", enableEntityTickThreading);
		enableEntityTickThreadingProperty.comment = "Whether entity ticks should be threaded";
		Property enableTileEntityTickThreadingProperty = config.get(Configuration.CATEGORY_GENERAL, "enableTileEntityTickThreading", enableTileEntityTickThreading);
		enableTileEntityTickThreadingProperty.comment = "Whether tile entity ticks should be threaded";
		Property enableChunkTickThreadingProperty = config.get(Configuration.CATEGORY_GENERAL, "enableChunkTickThreading", enableChunkTickThreading);
		enableChunkTickThreadingProperty.comment = "Whether chunk ticks should be threaded";
		Property enableWorldTickThreadingProperty = config.get(Configuration.CATEGORY_GENERAL, "enableWorldTickThreading", enableWorldTickThreading);
		enableWorldTickThreadingProperty.comment = "Whether world ticks should be threaded";
		Property regionSizeProperty = config.get(Configuration.CATEGORY_GENERAL, "regionSize", regionSize);
		regionSizeProperty.comment = "width/length of tick regions, specified in blocks.";
		Property variableTickRateProperty = config.get(Configuration.CATEGORY_GENERAL, "variableRegionTickRate", variableTickRate);
		variableTickRateProperty.comment = "Allows tick rate to vary per region so that each region uses at most 50ms on average per tick.";
		Property ticksCommandName = config.get(Configuration.CATEGORY_GENERAL, "ticksCommandName", TicksCommand.name);
		ticksCommandName.comment = "Name of the command to be used for performance stats. Defaults to ticks.";
		Property tpsCommandName = config.get(Configuration.CATEGORY_GENERAL, "tpsCommandName", TPSCommand.name);
		tpsCommandName.comment = "Name of the command to be used for TPS reports.";
		Property profileCommandName = config.get(Configuration.CATEGORY_GENERAL, "profileCommandName", ProfileCommand.name);
		profileCommandName.comment = "Name of the command to be used for profiling reports.";
		Property dumpCommandName = config.get(Configuration.CATEGORY_GENERAL, "dumpCommandName", DumpCommand.name);
		dumpCommandName.comment = "Name of the command to be used for profiling reports.";
		Property exitOnDeadlockProperty = config.get(Configuration.CATEGORY_GENERAL, "exitOnDeadlock", exitOnDeadlock);
		exitOnDeadlockProperty.comment = "If the server should shut down when a deadlock is detected";
		Property requireOpForTicksCommandProperty = config.get(Configuration.CATEGORY_GENERAL, "requireOpsForTicksCommand", requireOpForTicksCommand);
		requireOpForTicksCommandProperty.comment = "If a player must be opped to use /ticks";
		Property requireOpForProfileCommandProperty = config.get(Configuration.CATEGORY_GENERAL, "requireOpsForProfileCommand", requireOpForProfileCommand);
		requireOpForProfileCommandProperty.comment = "If a player must be opped to use /profile";
		Property requireOpForDumpCommandProperty = config.get(Configuration.CATEGORY_GENERAL, "requireOpsForDumpCommand", requireOpForDumpCommand);
		requireOpForDumpCommandProperty.comment = "If a player must be opped to use /dump";
		Property saveIntervalProperty = config.get(Configuration.CATEGORY_GENERAL, "saveInterval", saveInterval);
		saveIntervalProperty.comment = "Time between auto-saves, in ticks.";
		Property deadLockTimeProperty = config.get(Configuration.CATEGORY_GENERAL, "deadLockTime", deadLockTime);
		deadLockTimeProperty.comment = "The time(seconds) of being frozen which will trigger the DeadLockDetector.";
		Property aggressiveTicksProperty = config.get(Configuration.CATEGORY_GENERAL, "aggressiveTicks", aggressiveTicks);
		aggressiveTicksProperty.comment = "If false, will use Spigot tick time algorithm which may lead to lower idle load, but worse TPS if ticks are spiking.";
		Property shouldLoadSpawnProperty = config.get(Configuration.CATEGORY_GENERAL, "shouldLoadSpawn", shouldLoadSpawn);
		shouldLoadSpawnProperty.comment = "Whether chunks within 200 blocks of world spawn points should always be loaded.";
		Property enableFastMobSpawningProperty = config.get(Configuration.CATEGORY_GENERAL, "enableFastMobSpawning", enableFastMobSpawning);
		enableFastMobSpawningProperty.comment = "If enabled, TT's alternative mob spawning implementation will be used. This is experimental!";
		Property disabledFastMobSpawningDimensionsProperty = config.get(Configuration.CATEGORY_GENERAL, "disableFastMobSpawningDimensions", new int[]{-1});
		disabledFastMobSpawningDimensionsProperty.comment = "List of dimensions not to enable fast spawning in.";
		Property waitForEntityTickProperty = config.get(Configuration.CATEGORY_GENERAL, "waitForEntityTickCompletion", waitForEntityTickCompletion);
		waitForEntityTickProperty.comment = "Whether we should wait until all Tile/Entity tick threads are finished before moving on with world tick. False = experimental, but may improve performance.";
		Property chunkCacheSizeProperty = config.get(Configuration.CATEGORY_GENERAL, "chunkCacheSize", chunkCacheSize);
		chunkCacheSizeProperty.comment = "Number of unloaded chunks to keep cached. Replacement for Forge's dormant chunk cache, which tends to break.";
		Property chunkGCIntervalProperty = config.get(Configuration.CATEGORY_GENERAL, "chunkGCInterval", chunkGCInterval);
		chunkGCIntervalProperty.comment = "Interval between chunk garbage collections in ticks";
		Property targetTPSProperty = config.get(Configuration.CATEGORY_GENERAL, "targetTPS", targetTPS);
		targetTPSProperty.comment = "TPS the server should try to run at.";
		Property concurrentNetworkTicksProperty = config.get(Configuration.CATEGORY_GENERAL, "concurrentNetworkTicks", concurrentNetworkTicks);
		concurrentNetworkTicksProperty.comment = "Whether network ticks should be ran in a separate thread from the main minecraft thread. This is likely to be very buggy, especially with mods doing custom networking such as IC2!";
		Property antiCheatKickProperty = config.get(Configuration.CATEGORY_GENERAL, "antiCheatKick", antiCheatKick);
		antiCheatKickProperty.comment = "Whether to kick players for detected cheating";
		Property antiCheatNotifyProperty = config.get(Configuration.CATEGORY_GENERAL, "antiCheatNotify", antiCheatNotify);
		antiCheatNotifyProperty.comment = "Whether to notify admins if TT anti-cheat detects cheating";
		Property cleanWorldsProperty = config.get(Configuration.CATEGORY_GENERAL, "cleanWorlds", cleanWorlds);
		cleanWorldsProperty.comment = "Whether to clean worlds on unload - this should fix some memory leaks due to mods holding on to world objects";
		Property lockRegionBordersProperty = config.get(Configuration.CATEGORY_GENERAL, "lockRegionBorders", lockRegionBorders);
		lockRegionBordersProperty.comment = "Whether to prevent blocks next to each other on region borders from ticking concurrently. false = faster but experimental";
		Property allowWorldUnloadingProperty = config.get(Configuration.CATEGORY_GENERAL, "allowWorldUnloading", allowWorldUnloading);
		allowWorldUnloadingProperty.comment = "Whether worlds should be allowed to unload.";
		config.save();

		TicksCommand.name = ticksCommandName.value;
		TPSCommand.name = tpsCommandName.value;
		ProfileCommand.name = profileCommandName.value;
		DumpCommand.name = dumpCommandName.value;
		tickThreads = tickThreadsProperty.getInt(tickThreads);
		regionSize = regionSizeProperty.getInt(regionSize);
		saveInterval = saveIntervalProperty.getInt(saveInterval);
		deadLockTime = deadLockTimeProperty.getInt(deadLockTime);
		chunkCacheSize = chunkCacheSizeProperty.getInt(chunkCacheSize);
		chunkGCInterval = chunkGCIntervalProperty.getInt(chunkGCInterval);
		targetTPS = targetTPSProperty.getInt(targetTPS);
		enableEntityTickThreading = enableEntityTickThreadingProperty.getBoolean(enableEntityTickThreading);
		enableTileEntityTickThreading = enableTileEntityTickThreadingProperty.getBoolean(enableTileEntityTickThreading);
		variableTickRate = variableTickRateProperty.getBoolean(variableTickRate);
		exitOnDeadlock = exitOnDeadlockProperty.getBoolean(exitOnDeadlock);
		enableChunkTickThreading = enableChunkTickThreadingProperty.getBoolean(enableChunkTickThreading);
		enableWorldTickThreading = enableWorldTickThreadingProperty.getBoolean(enableWorldTickThreading);
		enableFastMobSpawning = enableFastMobSpawningProperty.getBoolean(enableFastMobSpawning);
		requireOpForTicksCommand = requireOpForTicksCommandProperty.getBoolean(requireOpForTicksCommand);
		requireOpForProfileCommand = requireOpForProfileCommandProperty.getBoolean(requireOpForProfileCommand);
		requireOpForDumpCommand = requireOpForDumpCommandProperty.getBoolean(requireOpForDumpCommand);
		aggressiveTicks = aggressiveTicksProperty.getBoolean(aggressiveTicks);
		shouldLoadSpawn = shouldLoadSpawnProperty.getBoolean(shouldLoadSpawn);
		waitForEntityTickCompletion = waitForEntityTickProperty.getBoolean(waitForEntityTickCompletion);
		concurrentNetworkTicks = concurrentNetworkTicksProperty.getBoolean(concurrentNetworkTicks);
		antiCheatKick = antiCheatKickProperty.getBoolean(antiCheatKick);
		antiCheatNotify = antiCheatNotifyProperty.getBoolean(antiCheatNotify);
		cleanWorlds = cleanWorldsProperty.getBoolean(cleanWorlds);
		allowWorldUnloading = allowWorldUnloadingProperty.getBoolean(allowWorldUnloading);
		loadChunkOnProvideRequest = config.get(Configuration.CATEGORY_GENERAL, "loadChunkOnProvideRequest", loadChunkOnProvideRequest, "Whether to load chunks in ChunkProviderServer.provideChunk").getBoolean(loadChunkOnProvideRequest);
		generateChunkOnProvideRequest = config.get(Configuration.CATEGORY_GENERAL, "generateChunkOnProvideRequest", generateChunkOnProvideRequest, "Whether to generate chunks in ChunkProviderServer.provideChunk").getBoolean(generateChunkOnProvideRequest);
		int[] disabledDimensions = disabledFastMobSpawningDimensionsProperty.getIntList();
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
				+ "\nIf it's only broken with TickThreading, report it at http://github.com/nallar/TickThreading");
		ServerCommandManager serverCommandManager = (ServerCommandManager) event.getServer().getCommandManager();
		serverCommandManager.registerCommand(new TicksCommand());
		serverCommandManager.registerCommand(new TPSCommand());
		serverCommandManager.registerCommand(new ProfileCommand());
		serverCommandManager.registerCommand(new DumpCommand());
		MinecraftServer.setTargetTPS(targetTPS);
		FMLLog.info("Loaded " + RelaunchClassLoader.patchedClasses + " patched classes" +
				"\nUsed " + RelaunchClassLoader.usedPatchedClasses + '.');
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		TickManager manager = new TickManager(event.world, regionSize, getThreadCount(), waitForEntityTickCompletion);
		manager.setVariableTickRate(variableTickRate);
		try {
			if (enableTileEntityTickThreading) {
				Field loadedTileEntityField = FieldUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
				new LoadedTileEntityList<TileEntity>(event.world, loadedTileEntityField, manager);
			}
			if (enableEntityTickThreading) {
				Field loadedEntityField = FieldUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
				new LoadedEntityList<TileEntity>(event.world, loadedEntityField, manager);
			}
			Log.fine("Threading initialised for world " + Log.name(event.world));
			managers.put(event.world, manager);
		} catch (Exception e) {
			Log.severe("Failed to initialise threading for world " + Log.name(event.world), e);
		}
		if (deadLockDetector == null) {
			deadLockDetector = new DeadLockDetector();
		}
	}

	@ForgeSubscribe
	public void onWorldUnload(WorldEvent.Unload event) {
		try {
			TickManager tickManager = managers.remove(event.world);
			if (tickManager != null) {
				tickManager.unload();
			}
			if (enableTileEntityTickThreading) {
				Field loadedTileEntityField = FieldUtil.getFields(World.class, List.class)[loadedTileEntityFieldIndex];
				Object loadedTileEntityList = loadedTileEntityField.get(event.world);
				if (!(loadedTileEntityList instanceof EntityList)) {
					Log.severe("Looks like another mod broke TT's replacement tile entity list in world: " + Log.name(event.world));
				}
			}
			if (enableEntityTickThreading) {
				Field loadedEntityField = FieldUtil.getFields(World.class, List.class)[loadedEntityFieldIndex];
				Object loadedEntityList = loadedEntityField.get(event.world);
				if (!(loadedEntityList instanceof EntityList)) {
					Log.severe("Looks like another mod broke TT's replacement entity list in world: " + Log.name(event.world));
				}
			}
		} catch (Exception e) {
			Log.severe("Probable memory leak, failed to unload threading for world " + Log.name(event.world), e);
		}
	}

	public TickManager getManager(World world) {
		return managers.get(world);
	}

	public List<TickManager> getManagers() {
		return new ArrayList<TickManager>(managers.values());
	}

	public boolean shouldFastSpawn(World world) {
		return this.enableFastMobSpawning && !disabledFastMobSpawningDimensions.contains(world.provider.dimensionId);
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
}
