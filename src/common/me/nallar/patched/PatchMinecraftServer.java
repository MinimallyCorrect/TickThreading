package me.nallar.patched;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ChunkGarbageCollector;
import me.nallar.tickthreading.minecraft.DeadLockDetector;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.FakeServerThread;
import net.minecraft.crash.CrashReport;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet4UpdateTime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ReportedException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public abstract class PatchMinecraftServer extends MinecraftServer {
	public ThreadManager threadManager;
	private static float tickTime = 0;
	private static float networkTickTime = 0;
	private AtomicInteger currentWorld;
	private Integer[] dimensionIdsToTick;
	private Runnable tickRunnable;
	private static int TARGET_TPS;
	private static int TARGET_TICK_TIME;
	private static int NETWORK_TPS;
	private static int NETWORK_TICK_TIME;
	private static double currentTPS = 0;
	private static double networkTPS = 0;
	private Map<Integer, Integer> exceptionCount;
	private boolean tickNetworkInMainThread;
	@Declare
	public boolean currentlySaving_;

	public void construct() {
		currentlySaving = false;
		tickNetworkInMainThread = true;
		exceptionCount = new HashMap<Integer, Integer>();
	}

	public static void staticConstruct() {
		setTargetTPS(20);
		setNetworkTPS(40);
	}

	public PatchMinecraftServer(File par1File) {
		super(par1File);
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
	public void run() {
		try {
			System.out.println("calling startServer()");
			if (this.startServer()) {
				System.out.println("calling handleServerStarted()");
				FMLCommonHandler.instance().handleServerStarted();
				FMLCommonHandler.instance().onWorldLoadTick(worldServers);
				// This block is derived from Spigot code,
				// LGPL
				this.serverIsRunning = true;
				if (TickThreading.instance.concurrentNetworkTicks) {
					tickNetworkInMainThread = false;
					new FakeServerThread(new NetworkTickRunnable(this), "Network Tick", false).start();
				}
				for (long lastTick = 0L; this.serverRunning; ) {
					long curTime = System.nanoTime();
					long wait = TARGET_TICK_TIME - (curTime - lastTick);
					if (wait > 0 && (currentTPS > TARGET_TPS || !TickThreading.instance.aggressiveTicks)) {
						Thread.sleep(wait / 1000000);
						continue;
					}
					lastTick = curTime;
					tickCounter++;
					try {
						this.tick();
					} catch (Exception e) {
						Log.severe("Exception in main tick loop", e);
					}
					currentTPS = TARGET_TICK_TIME * TARGET_TPS / tickTime;
				}
				FMLCommonHandler.instance().handleServerStopping();
			} else {
				System.out.println("startServer() failed.");
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
			} catch (Throwable t) {
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
				this.finalTick(crashReport);
			}
		} finally {
			try {
				this.stopServer();
				this.serverStopped = true;
			} catch (Throwable throwable) {
				FMLLog.log(Level.SEVERE, throwable, "Exception while attempting to stop the server");
			} finally {
				this.systemExitNow();
			}
		}
	}

	@Override
	public String getServerModName() {
		return "tickthreading-fml";
	}

	@Override
	public void tick() {
		long var1 = System.nanoTime();

		if (this.startProfiling) {
			this.startProfiling = false;
			this.theProfiler.profilingEnabled = true;
			this.theProfiler.clearProfiling();
		}

		this.theProfiler.startSection("root");

		this.theProfiler.startSection("forgePreServerTick");
		FMLCommonHandler.instance().rescheduleTicks(Side.SERVER);
		AxisAlignedBB.getAABBPool().cleanPool();
		FMLCommonHandler.instance().onPreServerTick();
		this.theProfiler.endSection();

		this.updateTimeLightAndEntities();

		if (this.tickCounter % TickThreading.instance.saveInterval == 0) {
			if (currentlySaving) {
				throw new IllegalStateException("Already saving!");
			}
			currentlySaving = true;
			this.theProfiler.startSection("save");
			this.serverConfigManager.saveAllPlayerData();
			this.saveAllWorlds(true);
			this.theProfiler.endSection();
			currentlySaving = false;
		}

		this.theProfiler.startSection("tallying");
		this.sentPacketCountArray[this.tickCounter % 100] = Packet.sentID - this.lastSentPacketID;
		this.lastSentPacketID = Packet.sentID;
		this.sentPacketSizeArray[this.tickCounter % 100] = Packet.sentSize - this.lastSentPacketSize;
		this.lastSentPacketSize = Packet.sentSize;
		this.receivedPacketCountArray[this.tickCounter % 100] = Packet.receivedID - this.lastReceivedID;
		this.lastReceivedID = Packet.receivedID;
		this.receivedPacketSizeArray[this.tickCounter % 100] = Packet.receivedSize - this.lastReceivedSize;
		this.lastReceivedSize = Packet.receivedSize;
		this.theProfiler.endStartSection("snooper");

		if (!this.usageSnooper.isSnooperRunning() && this.tickCounter > 100) {
			this.usageSnooper.startSnooper();
		}

		if (this.tickCounter % 6000 == 0) {
			this.usageSnooper.addMemoryStatsToSnooper();
		}

		this.theProfiler.endStartSection("forgePostServerTick");
		FMLCommonHandler.instance().onPostServerTick();

		this.theProfiler.endSection();
		this.theProfiler.endSection();
		tickTime = tickTime * 0.98f + ((this.tickTimeArray[this.tickCounter % 100] = System.nanoTime() - var1) * 0.02f);
	}

	@Override
	public void updateTimeLightAndEntities() {
		this.theProfiler.startSection("levels");
		int var1;

		dimensionIdsToTick = DimensionManager.getIDs();

		if (threadManager == null) {
			threadManager = new ThreadManager(8, "World Tick");
			currentWorld = new AtomicInteger(0);
			tickRunnable = new TickRunnable(this);
		}

		currentWorld.set(0);

		boolean concurrentTicking = tickCounter >= 100 && !theProfiler.profilingEnabled && TickThreading.instance.enableWorldTickThreading;

		if (concurrentTicking) {
			int count = Math.min(threadManager.size(), dimensionIdsToTick.length);
			for (int i = 0; i < count; i++) {
				threadManager.run(tickRunnable);
			}
		} else {
			doWorldTick();
		}

		TickThreading.instance.waitForEntityTicks();

		this.theProfiler.endStartSection("players");
		this.serverConfigManager.sendPlayerInfoToAllPlayers();

		this.theProfiler.endStartSection("tickables");
		for (var1 = 0; var1 < this.tickables.size(); ++var1) {
			((IUpdatePlayerListBox) this.tickables.get(var1)).update();
		}

		if (concurrentTicking) {
			threadManager.waitForCompletion();
		}

		if (tickNetworkInMainThread) {
			this.theProfiler.endStartSection("connection");
			this.getNetworkThread().networkTick();
		}

		this.theProfiler.endStartSection("dim_unloading");
		DimensionManager.unloadWorlds(worldTickTimes);

		this.theProfiler.endSection();
	}

	@Declare
	public static float getTickTime() {
		return tickTime;
	}

	@Declare
	public static double getTPS() {
		return currentTPS > TARGET_TPS ? TARGET_TPS : currentTPS;
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
		int i;
		while ((i = currentWorld.getAndIncrement()) < dimensionIdsToTick.length) {
			int id = dimensionIdsToTick[i];
			long var2 = System.nanoTime();

			WorldServer world = DimensionManager.getWorld(id);
			try {
				this.theProfiler.startSection(world.getWorldInfo().getWorldName());
				this.theProfiler.startSection("pools");
				world.getWorldVec3Pool().clear();
				this.theProfiler.endSection();

				if (this.tickCounter % 60 == 0) {
					this.theProfiler.startSection("timeSync");
					this.serverConfigManager.sendPacketToAllPlayersInDimension(new Packet4UpdateTime(world.getTotalWorldTime(), world.getWorldTime()), world.provider.dimensionId);
					this.theProfiler.endSection();
				}

				this.theProfiler.startSection("forgeTick");
				FMLCommonHandler.instance().onPreWorldTick(world);

				this.theProfiler.endStartSection("worldTick");
				world.tick();
				this.theProfiler.endStartSection("entityTick");
				world.updateEntities();
				this.theProfiler.endStartSection("postForgeTick");
				FMLCommonHandler.instance().onPostWorldTick(world);
				this.theProfiler.endSection();
				this.theProfiler.startSection("tracker");
				world.getEntityTracker().updateTrackedEntities();
				this.theProfiler.endSection();
				if (this.tickCounter % TickThreading.instance.chunkGCInterval == 0) {
					ChunkGarbageCollector.garbageCollect(world);
				}
				if (this.tickCounter % 202 == 0) {
					exceptionCount.put(id, 0);
				}
				this.theProfiler.endSection();

				if (worldTickTimes != null) {
					long[] tickTimes = worldTickTimes.get(id);
					if (tickTimes != null) {
						tickTimes[this.tickCounter % 100] = System.nanoTime() - var2;
					}
				}
			} catch (Throwable t) {
				Log.severe("Exception ticking world " + Log.name(world), t);
				Integer c = exceptionCount.get(id);
				if (c == null) {
					c = 0;
				}
				c++;
				if (TickThreading.instance.exitOnDeadlock) {
					if (c >= 199) {
						DeadLockDetector.sendChatSafely("The world " + Log.name(world) + " has become unstable, and the server will now restart.");
						this.initiateShutdown();
					}
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
		for (long lastTick = 0L; serverRunning; ) {
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
		if (this.isServerRunning() && !currentlySaving) {
			currentlySaving = true;
			this.serverConfigManager.saveAllPlayerData();
			this.saveAllWorlds(false);
			for (WorldServer world : this.worldServers) {
				world.flush();
			}
			currentlySaving = false;
		} else {
			Log.severe("Server is already saving or crashed while saving - not attempting to save.");
		}
	}

	@Override
	protected void initialWorldChunkLoad() {
		if (!TickThreading.instance.shouldLoadSpawn) {
			return;
		}
		int loadedChunks = 0;
		this.setUserMessage("menu.generatingTerrain");
		byte dimension = 0;
		long startTime = System.currentTimeMillis();
		logger.info("Preparing start region for level " + dimension);
		WorldServer worldServer = this.worldServers[dimension];
		ChunkCoordinates spawnPoint = worldServer.getSpawnPoint();

		for (int var11 = -192; var11 <= 192 && this.isServerRunning(); var11 += 16) {
			for (int var12 = -192; var12 <= 192 && this.isServerRunning(); var12 += 16) {
				long currentTime = System.currentTimeMillis();

				if (currentTime - startTime > 1000L) {
					this.outputPercentRemaining("Preparing spawn area", loadedChunks * 100 / 625);
					startTime = currentTime;
				}

				++loadedChunks;
				worldServer.theChunkProviderServer.loadChunk(spawnPoint.posX + var11 >> 4, spawnPoint.posZ + var12 >> 4);
			}
		}

		this.clearCurrentTask();
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
