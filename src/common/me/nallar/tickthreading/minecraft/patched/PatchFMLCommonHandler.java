package me.nallar.tickthreading.minecraft.patched;

import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Objects;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.profiler.Profiler;

public abstract class PatchFMLCommonHandler extends FMLCommonHandler {
	public Profiler theProfiler = null;

	@Override
	public void tickStart(EnumSet<TickType> ticks, Side side, Object... data) {
		if (theProfiler == null) {
			theProfiler = getMinecraftServerInstance().theProfiler;
		}
		List<IScheduledTickHandler> scheduledTicks = side.isClient() ? scheduledClientTicks : scheduledServerTicks;

		if (scheduledTicks.isEmpty()) {
			return;
		}
		for (IScheduledTickHandler ticker : scheduledTicks) {
			EnumSet<TickType> ticksToRun = EnumSet.copyOf(Objects.firstNonNull(ticker.ticks(), EnumSet.noneOf(TickType.class)));
			ticksToRun.retainAll(ticks);
			if (!ticksToRun.isEmpty()) {
				theProfiler.startSection(ticker.getClass().toString());
				ticker.tickStart(ticksToRun, data);
				theProfiler.endSection();
			}
		}
	}

	@Override
	public void tickEnd(EnumSet<TickType> ticks, Side side, Object... data) {
		List<IScheduledTickHandler> scheduledTicks = side.isClient() ? scheduledClientTicks : scheduledServerTicks;

		if (scheduledTicks.isEmpty()) {
			return;
		}
		for (IScheduledTickHandler ticker : scheduledTicks) {
			EnumSet<TickType> ticksToRun = EnumSet.copyOf(Objects.firstNonNull(ticker.ticks(), EnumSet.noneOf(TickType.class)));
			ticksToRun.retainAll(ticks);
			if (!ticksToRun.isEmpty()) {
				theProfiler.startSection(ticker.getClass().toString());
				ticker.tickEnd(ticksToRun, data);
				theProfiler.endSection();
			}
		}
	}
}
