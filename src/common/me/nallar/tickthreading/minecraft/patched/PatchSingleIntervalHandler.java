package me.nallar.tickthreading.minecraft.patched;

import java.util.EnumSet;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.SingleIntervalHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;

public abstract class PatchSingleIntervalHandler extends SingleIntervalHandler {
	public static Profiler theProfiler = null;

	public PatchSingleIntervalHandler(ITickHandler handler) {
		super(handler);
	}

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
		if (theProfiler == null) {
			theProfiler = MinecraftServer.getServer().theProfiler;
		}
		theProfiler.startSection(wrapped.getClass().toString());
		wrapped.tickStart(type, tickData);
		theProfiler.endSection();
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		theProfiler.startSection(wrapped.getClass().toString());
		wrapped.tickEnd(type, tickData);
		theProfiler.endSection();
	}
}
