package me.nallar.patched;

import java.util.Hashtable;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public class PatchDimensionManager extends DimensionManager {
	public static void unloadWorlds(Hashtable<Integer, long[]> worldTickTimes) {
		for (int id : unloadQueue) {
			WorldServer w = worlds.get(id);
			try {
				if (w != null) {
					w.saveAllChunks(true, null);
				} else {
					FMLLog.warning("Unexpected world unload - world %d is already unloaded", id);
				}
			} catch (Exception e) {
				FMLLog.log(Level.SEVERE, e, "Exception saving chunks when unloading world " + w);
			} finally {
				if (w != null) {
					MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
					w.flush();
					setWorld(id, null);
					if (TickThreading.instance.cleanWorlds) {
						UnsafeUtil.clean(w);
					}
				}
			}
		}
		unloadQueue.clear();
	}
}
