package me.nallar.tickthreading.minecraft.patched;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickthreading.minecraft.ThreadManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.patcher.Declare;
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
	private AtomicInteger currentWorld;
	private Integer[] dimensionIdsToTick;
	private Runnable tickRunnable;
	private static final int TARGET_TPS = 20;
	private static final int TARGET_TICK_TIME = 1000000000 / TARGET_TPS;
	private static double currentTPS = 0;

	public PatchMinecraftServer(File par1File) {
		super(par1File);
	}

	@Override
	public void run() {
		try {
			if (this.startServer()) {
				FMLCommonHandler.instance().handleServerStarted();
				FMLCommonHandler.instance().onWorldLoadTick(worldServers);
				// This block is derived from Spigot code,
				// LGPL
				for (long lastTick = 0L; this.serverRunning; this.serverIsRunning = true) {
					long curTime = System.nanoTime();
					long wait = TARGET_TICK_TIME - (curTime - lastTick);
					if (wait > 0 && (currentTPS > TARGET_TPS || !TickThreading.instance.aggressiveTicks)) {
						Thread.sleep(wait / 1000000);
						continue;
					}
					currentTPS = (currentTPS * 0.975) + (1E9 / (curTime - lastTick) * 0.025);
					lastTick = curTime;
					tickCounter++;
					this.tick();
				}
				FMLCommonHandler.instance().handleServerStopping();
			} else {
				this.finalTick(null);
			}
		} catch (Throwable throwable) {
			if (FMLCommonHandler.instance().shouldServerBeKilledQuietly()) {
				return;
			}
			throwable.printStackTrace();
			logger.log(Level.SEVERE, "Encountered an unexpected exception " + throwable.getClass().getSimpleName(), throwable);
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

			this.finalTick(crashReport);
		} finally {
			try {
				if (!FMLCommonHandler.instance().shouldServerBeKilledQuietly()) {
					this.stopServer();
					this.serverStopped = true;
				}
			} catch (Throwable throwable) {
				throwable.printStackTrace();
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
			this.theProfiler.startSection("save");
			this.serverConfigManager.saveAllPlayerData();
			this.saveAllWorlds(true);
			this.theProfiler.endSection();
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
		tickTime = (tickTime * 127 + ((this.tickTimeArray[this.tickCounter % 100] = System.nanoTime() - var1) / 1000000)) / 128;
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

		this.theProfiler.endStartSection("players");
		this.serverConfigManager.sendPlayerInfoToAllPlayers();

		this.theProfiler.endStartSection("tickables");
		for (var1 = 0; var1 < this.tickables.size(); ++var1) {
			((IUpdatePlayerListBox) this.tickables.get(var1)).update();
		}

		if (concurrentTicking) {
			threadManager.waitForCompletion();
		}

		this.theProfiler.endStartSection("connection");
		this.getNetworkThread().networkTick();

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
		return currentTPS;
	}

	@Declare
	public void doWorldTick() {
		int i;
		while ((i = currentWorld.getAndIncrement()) < dimensionIdsToTick.length) {
			int id = dimensionIdsToTick[i];
			long var2 = System.nanoTime();

			WorldServer var4 = DimensionManager.getWorld(id);
			this.theProfiler.startSection(var4.getWorldInfo().getWorldName());
			this.theProfiler.startSection("pools");
			var4.getWorldVec3Pool().clear();
			this.theProfiler.endSection();

			if (this.tickCounter % 60 == 0) {
				this.theProfiler.startSection("timeSync");
				this.serverConfigManager.sendPacketToAllPlayersInDimension(new Packet4UpdateTime(var4.getTotalWorldTime(), var4.getWorldTime()), var4.provider.dimensionId);
				this.theProfiler.endSection();
			}

			this.theProfiler.startSection("forgeTick");
			FMLCommonHandler.instance().onPreWorldTick(var4);

			this.theProfiler.endStartSection("entityTick");
			var4.updateEntities();
			this.theProfiler.endStartSection("worldTick");
			var4.tick();
			this.theProfiler.endStartSection("postForgeTick");
			FMLCommonHandler.instance().onPostWorldTick(var4);
			this.theProfiler.endSection();
			this.theProfiler.startSection("tracker");
			var4.getEntityTracker().updateTrackedEntities();
			this.theProfiler.endSection();
			this.theProfiler.endSection();

			worldTickTimes.get(id)[this.tickCounter % 100] = System.nanoTime() - var2;
		}
	}

	@Declare
	public void save() {
		this.saveAllWorlds(false);
	}

	@Override
	protected void initialWorldChunkLoad() {
		if (TickThreading.instance.shouldLoadSpawn) {
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
}
