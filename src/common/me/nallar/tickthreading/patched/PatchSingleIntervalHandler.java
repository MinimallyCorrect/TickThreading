package me.nallar.tickthreading.patched;

import java.util.EnumSet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.SingleIntervalHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.profiler.Profiler;

public abstract class PatchSingleIntervalHandler extends SingleIntervalHandler {
	public static Profiler theProfiler = null;

	public PatchSingleIntervalHandler(ITickHandler handler) {
		super(handler);
	}

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (theProfiler == null) {
			theProfiler = FMLCommonHandler.instance().getMinecraftServerInstance().theProfiler;
		}
		theProfiler.startSection(wrapped.getClass().getName());
		wrapped.tickStart(type, tickData);
		theProfiler.endSection();
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		theProfiler.startSection(wrapped.getClass().getName());
		wrapped.tickEnd(type, tickData);
		theProfiler.endSection();
	}
}
