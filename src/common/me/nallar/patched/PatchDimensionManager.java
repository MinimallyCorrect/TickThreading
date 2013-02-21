package me.nallar.patched;

import java.util.Hashtable;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public abstract class PatchDimensionManager extends DimensionManager {
	public static void unloadWorlds(Hashtable<Integer, long[]> worldTickTimes) {
		for (int id : unloadQueue) {
			WorldServer w = worlds.get(id);
			if (w == null) {
				FMLLog.warning("Unexpected world unload - world %d is already unloaded", id);
			} else {
				try {
					w.saveAllChunks(true, null);
				} catch (Exception e) {
					FMLLog.log(Level.SEVERE, e, "Exception saving chunks when unloading world " + w);
				} finally {
					try {
						MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
					} catch (Throwable t) {
						Log.severe("A mod failed to handle a world unload", t);
					}
					w.flush();
					setWorld(id, null);
					if (TickThreading.instance.cleanWorlds) {
						UnsafeUtil.clean(w);
					}
				}
			}
			unloadQueue.clear();
		}
	}
}
