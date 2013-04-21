package me.nallar.patched.forge;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
	private ConcurrentHashMap<TickType, ConcurrentHashMap<IScheduledTickHandler, Object>> perTickTypeLocks;

	public void construct() {
		ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
		tickReadLock = readWriteLock.readLock();
		tickWriteLock = readWriteLock.writeLock();
		perTickTypeLocks = new ConcurrentHashMap<TickType, ConcurrentHashMap<IScheduledTickHandler, Object>>();
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
					continue;
				} else {
					ticksToRun = EnumSet.copyOf(ticksToRun);
				}
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					synchronized (getLock(ticker, ticksToRun.iterator().next())) {
						ticker.tickStart(ticksToRun, data);
					}
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
					continue;
				} else {
					ticksToRun = EnumSet.copyOf(ticksToRun);
				}
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					synchronized (getLock(ticker, ticksToRun.iterator().next())) {
						ticker.tickEnd(ticksToRun, data);
					}
				}
			}
		} finally {
			tickReadLock.unlock();
		}
	}

	private Object getLock(IScheduledTickHandler tickHandler, TickType tickType) {
		ConcurrentHashMap<IScheduledTickHandler, Object> tickHandlerLockMap = perTickTypeLocks.get(tickType);
		if (tickHandlerLockMap == null) {
			ConcurrentHashMap<IScheduledTickHandler, Object> newMap = new ConcurrentHashMap<IScheduledTickHandler, Object>();
			tickHandlerLockMap = perTickTypeLocks.putIfAbsent(tickType, newMap);
			if (tickHandlerLockMap == null) {
				tickHandlerLockMap = newMap;
			}
		}
		Object lock = tickHandlerLockMap.get(tickHandler);
		if (lock == null) {
			Object newLock = new Object();
			lock = tickHandlerLockMap.putIfAbsent(tickHandler, newLock);
			if (lock == null) {
				lock = newLock;
			}
		}
		return lock;
	}
}
