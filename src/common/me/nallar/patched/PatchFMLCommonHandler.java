package me.nallar.patched;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.profiler.Profiler;

public abstract class PatchFMLCommonHandler extends FMLCommonHandler {
	public Profiler theProfiler = null;
	private Lock tickReadLock;
	private Lock tickWriteLock;

	public void construct() {
		ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
		tickReadLock = readWriteLock.readLock();
		tickWriteLock = readWriteLock.writeLock();
	}

	@Override
	public void rescheduleTicks(Side side) {
		List<IScheduledTickHandler> scheduledTicks = side.isClient() ? scheduledClientTicks : scheduledServerTicks;
		try {
			tickWriteLock.lock();
			TickRegistry.updateTickQueue(scheduledTicks, side);
		} finally {
			tickWriteLock.unlock();
		}
	}

	@Override
	public void tickStart(EnumSet<TickType> ticks, Side side, Object... data) {
		if (theProfiler == null) {
			theProfiler = getMinecraftServerInstance().theProfiler;
		}
		final List<IScheduledTickHandler> scheduledTicks = side.isClient() ? scheduledClientTicks : scheduledServerTicks;

		try {
			tickReadLock.lock();
			if (scheduledTicks.isEmpty()) {
				return;
			}
			for (IScheduledTickHandler ticker : scheduledTicks) {
				EnumSet<TickType> ticksToRun = ticker.ticks();
				if (ticksToRun == null) {
					ticksToRun = EnumSet.noneOf(TickType.class);
				} else {
					ticksToRun = EnumSet.copyOf(ticksToRun);
				}
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					theProfiler.startSection(ticker.getClass().toString());
					synchronized (ticker) {
						ticker.tickStart(ticksToRun, data);
					}
					theProfiler.endSection();
				}
			}
		} finally {
			tickReadLock.unlock();
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> ticks, Side side, Object... data) {
		final List<IScheduledTickHandler> scheduledTicks = side.isClient() ? scheduledClientTicks : scheduledServerTicks;

		try {
			tickReadLock.lock();
			if (scheduledTicks.isEmpty()) {
				return;
			}
			for (IScheduledTickHandler ticker : scheduledTicks) {
				EnumSet<TickType> ticksToRun = ticker.ticks();
				if (ticksToRun == null) {
					ticksToRun = EnumSet.noneOf(TickType.class);
				} else {
					ticksToRun = EnumSet.copyOf(ticksToRun);
				}
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					theProfiler.startSection(ticker.getClass().toString());
					synchronized (ticker) {
						ticker.tickEnd(ticksToRun, data);
					}
					theProfiler.endSection();
				}
			}
		} finally {
			tickReadLock.unlock();
		}
	}
}
