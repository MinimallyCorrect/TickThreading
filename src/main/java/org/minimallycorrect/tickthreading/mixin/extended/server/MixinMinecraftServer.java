package org.minimallycorrect.tickthreading.mixin.extended.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import lombok.SneakyThrows;
import lombok.val;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.mojang.authlib.GameProfile;

import org.minimallycorrect.mixin.*;
import org.minimallycorrect.modpatcher.api.UsedByPatch;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;

import net.minecraft.network.ServerStatusResponse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.FMLCommonHandler;

@Mixin
public abstract class MixinMinecraftServer extends MinecraftServer {
	@Add
	private static final Object worldWait_ = new Object();

	@Injectable
	private static void mainLoopCaller() {

	}

	@Overwrite
	public <V> ListenableFuture<V> callFromMainThread(Callable<V> callable) {
		if (Config.$.separatePerWorldTickLoops)
			throw new UnsupportedOperationException("Missing required patch. TickThreading should patch this call to use per-world task");

		if (!this.isCallingFromMinecraftThread() && !this.isServerStopped()) {
			val futureTask = ListenableFutureTask.create(callable);
			synchronized (this.futureTaskQueue) {
				this.futureTaskQueue.add(futureTask);
				return futureTask;
			}
		}
		try {
			return Futures.immediateFuture(callable.call());
		} catch (Exception exception) {
			return Futures.immediateFailedCheckedFuture(exception);
		}
	}

	@Overwrite
	public ListenableFuture<Object> addScheduledTask(Runnable runnableToSchedule) {
		return callFromMainThread(Executors.callable(runnableToSchedule));
	}

	@Add
	public void waitForWorldTick() {
		val worldWait = MinecraftServer.worldWait;
		synchronized (worldWait) {
			worldWait.notify();
		}
	}

	@Matcher
	private void matchApplyServerIconToResponseMatcher() {
		//noinspection ConstantConditions
		this.applyServerIconToResponse(null);
	}

	@Override
	@Inject(injectable = "mainLoopCaller", position = Position.AFTER, type = Type.METHOD_CALL, match = "matchApplyServerIconToResponseMatcher")
	public abstract void run();

	@SneakyThrows
	@UsedByPatch("minecraft-extended.xml")
	@Add
	protected void mainLoop() {
		long TARGET_TPS = Config.$.targetTps;
		long TARGET_TICK_TIME = 1000000000 / TARGET_TPS;
		long startTime = System.nanoTime();
		float tickTime = 1;
		this.serverIsRunning = true;
		while (this.serverRunning) {
			++tickCounter;
			try {
				this.tick(startTime);
			} catch (Exception e) {
				Log.error("Exception in main tick loop", e);
			}
			long endTime = System.nanoTime();
			this.tickTimeArray[this.tickCounter % 100] = endTime - startTime;

			currentTime = endTime;
			tickTime = tickTime * 0.98f + ((endTime - startTime) * 0.02f);

			long wait = (TARGET_TICK_TIME - (endTime - startTime)) / 1000000;
			if (wait > 0 && TARGET_TICK_TIME * TARGET_TPS / tickTime > TARGET_TPS) {
				Thread.sleep(wait);
				startTime = System.currentTimeMillis();
			} else {
				startTime = endTime;
			}
		}
	}

	@Overwrite
	public void tick() {
		throw new UnsupportedOperationException();
	}

	@Add
	public void tick(long startTime) {
		FMLCommonHandler.instance().onPreServerTick();

		val separateTicks = Config.$.separatePerWorldTickLoops;
		if (!separateTicks) {
			synchronized (this.futureTaskQueue) {
				FutureTask<?> c;
				while ((c = this.futureTaskQueue.poll()) != null)
					Util.runTask(c, LOGGER);
			}
		}

		net.minecraftforge.common.chunkio.ChunkIOExecutor.tick();

		if (!separateTicks) {
			synchronized (worldWait) {
				worldWait.notifyAll();
			}
		}

		net.minecraftforge.common.DimensionManager.unloadWorlds(worldTickTimes);
		this.getNetworkSystem().networkTick();
		this.playerList.onTick();
		this.getFunctionManager().update();
		for (val tickable : this.tickables)
			tickable.update();

		if (startTime - this.nanoTimeSinceStatusRefresh >= 5000000000L) {
			this.nanoTimeSinceStatusRefresh = startTime;
			this.statusResponse.setPlayers(new ServerStatusResponse.Players(this.getMaxPlayers(), this.getCurrentPlayerCount()));
			GameProfile[] agameprofile = new GameProfile[Math.min(this.getCurrentPlayerCount(), 12)];
			int j = MathHelper.getInt(this.random, 0, this.getCurrentPlayerCount() - agameprofile.length);

			for (int k = 0; k < agameprofile.length; ++k) {
				agameprofile[k] = this.playerList.getPlayers().get(j + k).getGameProfile();
			}

			Collections.shuffle(Arrays.asList(agameprofile));
			this.statusResponse.getPlayers().setPlayers(agameprofile);
			this.statusResponse.invalidateJson();
		}

		if (this.tickCounter % 900 == 0) {
			this.playerList.saveAllPlayerData();
			// TODO: iirc this ends up forcibly saving all dirty chunks immediately every 900 secs -> lag spike?
			// definitely needs to go from here due to per-world threading
			// but probably not having it here is a problem
			this.saveAllWorlds(true);
		}

		if (!this.usageSnooper.isSnooperRunning() && this.tickCounter > 100) {
			this.usageSnooper.startSnooper();
		}

		if (this.tickCounter % 6000 == 0) {
			this.usageSnooper.addMemoryStatsToSnooper();
		}

		FMLCommonHandler.instance().onPostServerTick();
	}
}
