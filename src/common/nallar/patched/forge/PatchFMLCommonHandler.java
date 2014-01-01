package nallar.patched.forge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

import java.util.*;
import java.util.concurrent.locks.*;

public abstract class PatchFMLCommonHandler extends FMLCommonHandler {
	private Lock tickReadLock;
	private Lock tickWriteLock;

	public void construct() {
		ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
		tickReadLock = readWriteLock.readLock();
		tickWriteLock = readWriteLock.writeLock();
	}

	@Override
	public Side getEffectiveSide() {
		return Side.SERVER;
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
				}
				ticksToRun = EnumSet.copyOf(ticksToRun);
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					ticker.tickStart(ticksToRun, data);
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
				}
				ticksToRun = EnumSet.copyOf(ticksToRun);
				ticksToRun.retainAll(ticks);
				if (!ticksToRun.isEmpty()) {
					ticker.tickEnd(ticksToRun, data);
				}
			}
		} finally {
			tickReadLock.unlock();
		}
	}
}
