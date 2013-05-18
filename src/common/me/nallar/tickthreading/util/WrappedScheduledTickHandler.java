package me.nallar.tickthreading.util;

import java.util.EnumSet;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.SingleIntervalHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.modloader.BaseModTicker;
import me.nallar.tickthreading.Log;

public class WrappedScheduledTickHandler implements IScheduledTickHandler {
	public final EnumSet<TickType> ticks;
	private final IScheduledTickHandler scheduledTickHandler;

	@Override
	public int nextTickSpacing() {
		return scheduledTickHandler.nextTickSpacing();
	}

	@Override
	public void tickStart(final EnumSet<TickType> type, final Object... tickData) {
		scheduledTickHandler.tickStart(type, tickData);
	}

	@Override
	public void tickEnd(final EnumSet<TickType> type, final Object... tickData) {
		scheduledTickHandler.tickEnd(type, tickData);
	}

	@Override
	public EnumSet<TickType> ticks() {
		return ticks == null ? scheduledTickHandler.ticks() : ticks;
	}

	@Override
	public String getLabel() {
		return scheduledTickHandler.getLabel();
	}

	public WrappedScheduledTickHandler(IScheduledTickHandler scheduledTickHandler) {
		EnumSet<TickType> ticks = scheduledTickHandler.ticks();
		ITickHandler tickHandler = scheduledTickHandler;
		if (tickHandler instanceof SingleIntervalHandler) {
			tickHandler = ReflectUtil.get(scheduledTickHandler, "wrapped");
		}
		boolean baseModTicker = tickHandler instanceof BaseModTicker;
		if (ticks == null || ticks.isEmpty() || baseModTicker) {
			ticks = null;
			if (!baseModTicker) {
				Log.warning("Null ticks for tick handler " + Log.toString(tickHandler) + ':' + tickHandler.getLabel());
			}
		}
		this.ticks = ticks;
		this.scheduledTickHandler = scheduledTickHandler;
	}
}
