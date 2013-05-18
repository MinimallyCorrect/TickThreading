package me.nallar.patched.forge;

import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.SingleIntervalHandler;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.util.WrappedScheduledTickHandler;

public abstract class PatchTickRegistry extends TickRegistry {
	public static void registerScheduledTickHandler(IScheduledTickHandler handler, Side side) {
		if (handler.getClass().getName().toLowerCase().contains("version")) {
			Log.info("Skipping version tick handler " + Log.toString(handler));
			return;
		}
		getQueue(side).add(new TickQueueElement(new WrappedScheduledTickHandler(handler), getCounter(side).get()));
	}

	public static void registerTickHandler(ITickHandler handler, Side side) {
		if (handler.getClass().getName().toLowerCase().contains("version")) {
			Log.info("Skipping version tick handler " + Log.toString(handler));
			return;
		}
		registerScheduledTickHandler(new SingleIntervalHandler(handler), side);
	}
}
