package me.nallar.patched.server;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import cpw.mods.fml.relauncher.Side;
import javassist.is.faulty.Timings;
import me.nallar.exception.ConcurrencyError;
import me.nallar.insecurity.InsecurityManager;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.DeadLockDetector;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.FakeServerThread;
import me.nallar.tickthreading.util.PatchUtil;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet4UpdateTime;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ReportedException;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public abstract class PatchMinecraftServer extends MinecraftServer {
	private ThreadManager threadManager;
	private static float tickTime = 0;
	private static float networkTickTime = 0;
	private AtomicInteger currentWorld;
	private Integer[] dimensionIdsToTick;
	private Runnable tickRunnable;
	@SuppressWarnings ("FieldCanBeLocal") // Used by Spigot.
	private static int currentTick;
	private static int TARGET_TPS;
	private static int TARGET_TICK_TIME;
	private static int NETWORK_TPS;
	private static int NETWORK_TICK_TIME;
	private static double currentTPS = 0;
	private static double networkTPS = 0;
	private Map<Integer, Integer> exceptionCount;
	private boolean tickNetworkInMainThread;
	private Map<String, long[]> worldTickLengths;
	@Declare
	public List<WorldServer> worlds_;
	@Declare
	public static java.util.LinkedList playersToCheckWorld_;
	@Declare
	public final java.util.concurrent.atomic.AtomicInteger currentlySaving_ = null;
	@Declare
	public static java.util.Set<net.minecraftforge.common.Configuration> toSaveConfigurationSet_;

	public void construct() {
		currentlySaving = new AtomicInteger();
		tickNetworkInMainThread = true;
		exceptionCount = new HashMap<Integer, Integer>();
		worldTickLengths = new ConcurrentHashMap<String, long[]>();
	}

	public static void staticConstruct() {
		playersToCheckWorld = new LinkedList();
		setTargetTPS(20);
		setNetworkTPS(40);
	}

	public PatchMinecraftServer(File par1File) {
		super(par1File);
	}

	@Override
	public void initiateShutdown() {
		if (!serverRunning) {
			return;
		}
		SecurityManager securityManager = System.getSecurityManager();
		if (securityManager != null) {
			securityManager.checkExit(1);
		}
		this.serverRunning = false;
	}

	@Override
	@Declare
	public long[] getTickTimes(WorldServer w) {
		return worldTickLengths.get(w.getName());
	}

	@Declare
	public static void setTargetTPS(int targetTPS) {
		assert targetTPS > 0 : "Target TPS must be greater than 0";
		TARGET_TPS = targetTPS;
		TARGET_TICK_TIME = 1000000000 / TARGET_TPS;
	}

	@Declare
	public static void setNetworkTPS(int targetTPS) {
		assert targetTPS > 0 : "Target TPS must be greater than 0";
		NETWORK_TPS = targetTPS;
		NETWORK_TICK_TIME = 1000000000 / NETWORK_TPS;
	}

	@Override
	@Declare
	public int getId(WorldServer world) {
		if (worlds == null) {
			for (int i = 0; i < worldServers.length; i++) {
				if (worldServers[i] == world) {
					return i;
				}
			}
		} else {
			for (int i = 0; i < worlds.size(); i++) {
				if (worlds.get(i) == world) {
					return i;
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	public void run() {
		//noinspection ThrowableInstanceNeverThrown
		new ConcurrencyError("Just loading some exception classes.");
		toSaveConfigurationSet = new HashSet<Configuration>();
		FMLLog.info("Loaded " + RelaunchClassLoader.patchedClasses + " patched classes, cl: " + this.getClass().getClassLoader());
		try {
			try {
				InsecurityManager.init();
				PatchUtil.writePatchRunners();
			} catch (Throwable t) {
				FMLLog.log(Level.SEVERE, t, "Failed to set up Security Manager. This is probably not a huge problem - but it could indicate classloading issues.");
			}
			if (this.startServer()) {
				FMLLog.fine("calling handleServerStarted()");
				FMLCommonHandler.instance().handleServerStarted();
				FMLCommonHandler.instance().onWorldLoadTick(worlds == null ? worldServers : worlds.toArray(new WorldServer[worlds.size()]));
				Set<Configuration> toSaveConfigurationSet = MinecraftServer.toSaveConfigurationSet;
				MinecraftServer.toSaveConfigurationSet = null;
				for (Configuration configuration : toSaveConfigurationSet) {
					configuration.save();
				}
				// This block is derived from Spigot code,
				// LGPL
				this.serverIsRunning = true;
				if (TickThreading.instance.concurrentNetworkTicks) {
					tickNetworkInMainThread = false;
					new FakeServerThread(new NetworkTickRunnable(this), "Network Tick", false).start();
				}
				long lastTick = 0L;
				double currentMaxTPS = 0;
				while (this.serverRunning) {
					long curTime = System.nanoTime();
					long wait = TARGET_TICK_TIME - (curTime - lastTick);
					if (wait > 0 && currentMaxTPS > TARGET_TPS) {
						Thread.sleep(wait / 1000000);
						continue;
					}
					lastTick = curTime;
					currentTick = tickCounter++;
					try {
						this.tick();
					} catch (Exception e) {
						Log.severe("Exception in main tick loop", e);
					}
					currentMaxTPS = TARGET_TICK_TIME * TARGET_TPS / tickTime;
					currentTPS = currentMaxTPS > TARGET_TPS ? TARGET_TPS : currentMaxTPS;
				}
				try {
					FMLCommonHandler.instance().handleServerStopping();
				} catch (Throwable t) {
					Log.severe("Exception occurred while stopping the server", t);
				}
			} else {
				FMLLog.severe("startServer() failed.");
				this.finalTick(null);
			}
		} catch (Throwable throwable) {
			try {
				if (serverRunning && serverIsRunning) {
					DeadLockDetector.sendChatSafely("The server has crashed due to an unexpected exception during the main tick loop: " + throwable.getClass().getSimpleName());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ignored) {
					}
				}
			} catch (Throwable ignored) {
			}
			FMLLog.log(Level.SEVERE, throwable, "Encountered an unexpected exception" + throwable.getClass().getSimpleName());
			CrashReport crashReport;

			if (throwable instanceof ReportedException) {
				crashReport = this.addServerInfoToCrashReport(((ReportedException) throwable).getCrashReport());
			} else {
				crashReport = this.addServerInfoToCrashReport(new CrashReport("Exception in server tick loop", throwable));
			}

			File var3 = new File(new File(this.getDataDirectory(), "crash-reports"), "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-server.txt");

			if (crashReport.saveToFile(var3)) {
				logger.severe("This crash report has been saved to: " + var3.getAbsolutePath());
			} else {
				logger.severe("We were unable to save this crash report to disk.");
			}

			if (!TickThreading.instance.exitOnDeadlock) {
				try {
					Thread.sleep(100L);
				} catch (InterruptedException ignored) {
				}
				this.saveEverything();
				this.finalTick(crashReport);
			}
		} finally {
			try {
				this.stopServer();
				this.serverStopped = true;
			} catch (Throwable throwable) {
				FMLLog.log(Level.SEVERE, throwable, "Exception while attempting to stop the server");
			} finally {
				try {
					DeadLockDetector.checkForLeakedThreadManagers();
				} finally {
					this.systemExitNow();
				}
			}
		}
	}

	@Override
	public String getServerModName() {
		return "tickthreading,mcpc,spigot,craftbukkit,forge,fml";
	}

	@Override
	public void tick() {
		long startTime = System.nanoTime();

		TickThreading.instance.recentSpawnedItems = 0;
		DeadLockDetector.tick(startTime);
		Timings.tick();

		if (this.startProfiling) {
			this.startProfiling = false;
			theProfiler.profilingEnabled = true;
			theProfiler.clearProfiling();
		}

		theProfiler.startSection("root");

		theProfiler.startSection("forgePreServerTick");
		FMLCommonHandler.instance().rescheduleTicks(Side.SERVER);
		AxisAlignedBB.getAABBPool().cleanPool();
		FMLCommonHandler.instance().onPreServerTick();
		theProfiler.endSection();

		this.updateTimeLightAndEntities();

		if (this.tickCounter % TickThreading.instance.saveInterval == 0) {
			theProfiler.startSection("save");
			this.serverConfigManager.saveAllPlayerData();
			theProfiler.endSection();
		}

		theProfiler.startSection("tallying");
		this.sentPacketCountArray[this.tickCounter % 100] = Packet.sentID - this.lastSentPacketID;
		this.lastSentPacketID = Packet.sentID;
		this.sentPacketSizeArray[this.tickCounter % 100] = Packet.sentSize - this.lastSentPacketSize;
		this.lastSentPacketSize = Packet.sentSize;
		this.receivedPacketCountArray[this.tickCounter % 100] = Packet.receivedID - this.lastReceivedID;
		this.lastReceivedID = Packet.receivedID;
		this.receivedPacketSizeArray[this.tickCounter % 100] = Packet.receivedSize - this.lastReceivedSize;
		this.lastReceivedSize = Packet.receivedSize;
		theProfiler.endStartSection("snooper");

		if (!this.usageSnooper.isSnooperRunning() && this.tickCounter > 100) {
			this.usageSnooper.startSnooper();
		}

		if (this.tickCounter % 6000 == 0) {
			this.usageSnooper.addMemoryStatsToSnooper();
		}

		theProfiler.endStartSection("forgePostServerTick");
		FMLCommonHandler.instance().onPostServerTick();
		LinkedList<EntityPlayerMP> playersToCheckWorld = MinecraftServer.playersToCheckWorld;
		if (!playersToCheckWorld.isEmpty()) {
			synchronized (playersToCheckWorld) {
				for (EntityPlayerMP entityPlayerMP : playersToCheckWorld) {
					World world = entityPlayerMP.worldObj;
					List<Entity> entityList = world.loadedEntityList;
					synchronized (entityList) {
						// No contains check, handled by TickManager, this list is an instance of LoadedEntityList.
						entityList.add(entityPlayerMP);
					}
					if (!world.playerEntities.contains(entityPlayerMP)) {
						world.playerEntities.add(entityPlayerMP);
					}
				}
				playersToCheckWorld.clear();
			}
		}
		theProfiler.endSection();
		theProfiler.endSection();
		tickTime = tickTime * 0.98f + ((this.tickTimeArray[this.tickCounter % 100] = System.nanoTime() - startTime) * 0.02f);
	}

	@Override
	public void updateTimeLightAndEntities() {
		final Profiler profiler = theProfiler;
		profiler.startSection("levels");
		int var1;

		spigotTLETick();

		Integer[] dimensionIdsToTick = this.dimensionIdsToTick = DimensionManager.getIDs();

		if (threadManager == null) {
			threadManager = new ThreadManager(8, "World Tick");
			currentWorld = new AtomicInteger(0);
			tickRunnable = new TickRunnable(this);
		}

		currentWorld.set(0);

		boolean concurrentTicking = tickCounter >= 100 && !profiler.profilingEnabled;

		if (concurrentTicking) {
			int count = threadManager.size();
			if (count < dimensionIdsToTick.length) {
				count = dimensionIdsToTick.length;
			}
			for (int i = 0; i < count; i++) {
				threadManager.run(tickRunnable);
			}
		} else {
			// Gregtech leaks the first world which is ticked. If we tick overworld first, no leak. Only applies to first tick on server start.
			Integer[] dimensionIdsToTickOrdered = new Integer[dimensionIdsToTick.length];
			int i = 0;
			for (int d : dimensionIdsToTick) {
				if (d == 0) {
					dimensionIdsToTickOrdered[i++] = 0;
					break;
				}
			}
			for (int d : dimensionIdsToTick) {
				if (d != 0) {
					dimensionIdsToTickOrdered[i++] = d;
				}
			}
			this.dimensionIdsToTick = dimensionIdsToTickOrdered;
			doWorldTick();
		}

		TickThreading.instance.waitForEntityTicks();

		profiler.endStartSection("players");
		this.serverConfigManager.sendPlayerInfoToAllPlayers();

		profiler.endStartSection("tickables");
		for (var1 = 0; var1 < this.tickables.size(); ++var1) {
			((IUpdatePlayerListBox) this.tickables.get(var1)).update();
		}

		if (concurrentTicking) {
			threadManager.waitForCompletion();
		}

		if (tickNetworkInMainThread) {
			profiler.endStartSection("connection");
			this.getNetworkThread().networkTick();
		}

		profiler.endStartSection("dim_unloading");
		DimensionManager.unloadWorlds(worldTickTimes);

		profiler.endSection();
	}

	private void spigotTLETick() {
		// Replaced in patcher
	}

	@Declare
	public static float getTickTime() {
		return tickTime;
	}

	@Declare
	public static double getTPS() {
		return currentTPS;
	}

	@Declare
	public static double getTargetTickTime() {
		return TARGET_TICK_TIME;
	}

	@Declare
	public static double getTargetTPS() {
		return TARGET_TPS;
	}

	@Declare
	public static float getNetworkTickTime() {
		return networkTickTime;
	}

	@Declare
	public static double getNetworkTPS() {
		return networkTPS;
	}

	@Declare
	public static double getNetworkTargetTickTime() {
		return NETWORK_TICK_TIME;
	}

	@Declare
	public static double getNetworkTargetTPS() {
		return NETWORK_TPS;
	}

	@Override
	@Declare
	public void doWorldTick() {
		final Profiler profiler = this.theProfiler;
		int i;
		while ((i = currentWorld.getAndIncrement()) < dimensionIdsToTick.length) {
			int id = dimensionIdsToTick[i];
			long var2 = System.nanoTime();

			WorldServer world = DimensionManager.getWorld(id);
			String name = world.getName();
			TickManager tickManager = TickThreading.instance.getManager(world);
			if (tickManager == null) {
				Log.severe("Not sent WorldEvent.Load for loaded world " + name + ", sending it now.");
				MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
			}
			if (world.getDimension() != id) {
				Log.severe("World " + world.getName() + " exists in DimensionManager with an apparently incorrect dimension ID of " + id);
				continue;
			}
			try {
				profiler.startSection(world.getWorldInfo().getWorldName());
				profiler.startSection("pools");
				world.getWorldVec3Pool().clear();
				profiler.endSection();

				if (world.tickCount % 30 == 0) {
					profiler.startSection("timeSync");
					long totalTime = world.getTotalWorldTime();
					for (EntityPlayerMP entityPlayerMP : (Iterable<EntityPlayerMP>) world.playerEntities) {
						entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(new Packet4UpdateTime(totalTime, entityPlayerMP.getPlayerTime()));
					}
					profiler.endSection();
				}

				profiler.startSection("forgeTick");
				FMLCommonHandler.instance().onPreWorldTick(world);

				profiler.endStartSection("worldTick");
				world.tick();
				profiler.endStartSection("entityTick");
				world.updateEntities();
				profiler.endStartSection("postForgeTick");
				FMLCommonHandler.instance().onPostWorldTick(world);
				profiler.endSection();
				profiler.startSection("tracker");
				world.getEntityTracker().updateTrackedEntities();
				profiler.endSection();
				if (world.tickCount % 14 == 0) {
					exceptionCount.put(id, 0);
				}
				if (world.tickCount % TickThreading.instance.saveInterval == 0) {
					theProfiler.startSection("save");
					try {
						currentlySaving.getAndIncrement();
						long st = 0;
						boolean enabled = Timings.enabled;
						if (enabled) {
							st = System.nanoTime();
						}
						if (world.saveTickCount++ % 20 == 0) {
							world.saveAllChunks(false, null);
						} else {
							if (world.theChunkProviderServer.canSave()) {
								world.theChunkProviderServer.saveChunks(false, null);
							}
						}
						if (enabled) {
							Timings.record("World/save", System.nanoTime() - st);
						}
					} catch (MinecraftException e) {
						throw UnsafeUtil.throwIgnoreChecked(e);
					} finally {
						currentlySaving.getAndDecrement();
					}
					theProfiler.endSection();
				}
				profiler.endSection();

				long[] tickTimes = worldTickLengths.get(name);
				if (tickTimes == null) {
					tickTimes = new long[100];
					worldTickLengths.put(name, tickTimes);
					worldTickTimes.put(id, tickTimes);
				}
				tickTimes[this.tickCounter % 100] = System.nanoTime() - var2;
			} catch (Throwable t) {
				Log.severe("Exception ticking world " + Log.name(world), t);
				Integer c = exceptionCount.get(id);
				if (c == null) {
					c = 0;
				}
				c++;
				if (c >= 10) {
					DeadLockDetector.sendChatSafely("The world " + Log.name(world) + " has become unstable, and the server will now stop.");
					Log.severe(Log.name(world) + " has become unstable, stopping.");
					this.initiateShutdown();
				}
				exceptionCount.put(id, c);
			}
		}
		AxisAlignedBB.getAABBPool().cleanPool();
	}

	@Override
	@Declare
	public void doNetworkTicks() {
		long lastTime = 1;
		long lastTick = 0L;
		while (serverRunning) {
			long curTime = System.nanoTime();
			long time = curTime - lastTick;
			long wait = NETWORK_TICK_TIME - time;
			if (wait > 0) {
				try {
					Thread.sleep(wait / 1000000);
				} catch (InterruptedException ignored) {
				}
				continue;
			}
			networkTickTime = (networkTickTime * 127 + lastTime) / 128;
			networkTPS = (networkTPS * 0.975) + (1E9 / time * 0.025);
			lastTick = curTime;
			lastTime = System.nanoTime();
			this.getNetworkThread().networkTick();
			lastTime = System.nanoTime() - lastTime;
		}
	}

	@Override
	@Declare
	public void saveEverything() {
		if (this.isServerRunning() && currentlySaving.get() == 0) {
			currentlySaving.getAndIncrement();
			try {
				this.serverConfigManager.saveAllPlayerData();
				this.saveAllWorlds(false);
				if (worlds == null) {
					for (WorldServer world : this.worldServers) {
						world.flush();
					}
				} else {
					for (WorldServer world : worlds) {
						world.flush();
					}
				}
			} finally {
				currentlySaving.getAndDecrement();
			}
		} else {
			Log.severe("Server is already saving or crashed while saving - not attempting to save.");
		}
	}

	public static class TickRunnable implements Runnable {
		final MinecraftServer minecraftServer;

		public TickRunnable(MinecraftServer minecraftServer) {
			this.minecraftServer = minecraftServer;
		}

		@Override
		public void run() {
			minecraftServer.doWorldTick();
		}
	}

	public static class NetworkTickRunnable implements Runnable {
		private final MinecraftServer minecraftServer;

		public NetworkTickRunnable(MinecraftServer minecraftServer) {
			this.minecraftServer = minecraftServer;
		}

		@Override
		public void run() {
			minecraftServer.doNetworkTicks();
		}
	}
}
