package org.minimallycorrect.tickthreading.mixin.extended.forge;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.minimallycorrect.mixin.Add;
import org.minimallycorrect.mixin.Mixin;
import org.minimallycorrect.mixin.OverrideStatic;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;
import org.minimallycorrect.tickthreading.reporting.LeakDetector;

import java.util.*;

@Mixin
public abstract class MixinDimensionManager extends DimensionManager {
	@OverrideStatic
	public static void unloadWorlds(@SuppressWarnings({"UseOfObsoleteCollectionType", "unused"}) Hashtable<Integer, long[]> worldTickTimes) {
		if (unloadQueue.isEmpty()) {
			return;
		}
		//noinspection SynchronizationOnStaticField
		//noinspection SynchronizeOnNonFinalField
		synchronized (unloadQueue) {
			if (!Config.$.worldUnloading) {
				unloadQueue.clear();
				return;
			}
			for (int id : unloadQueue) {
				unloadWorldImmediately(worlds.get(id));
			}
			unloadQueue.clear();
			Log.checkWorlds();
			weakWorldMap.clear(); // We do our own leak checking.
		}
	}

	@Add
	protected static boolean unloadWorldImmediately(WorldServer w) {
		if (w == null || !worlds.containsValue(w) || !w.getPersistentChunks().isEmpty() || !w.playerEntities.isEmpty()) {
			return false;
		}

		try {
			w.saveAllChunks(true, null);
		} catch (net.minecraft.world.MinecraftException ex) {
			Log.error("Failed to save world " + w.getName() + " while unloading it.");
		}
		try {
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
		} catch (Throwable t) {
			Log.error("A mod failed to handle unloading the world " + w.getName(), t);
		}
		setWorld(w.getDimensionId(), null, w.getMinecraftServer());
		try {
			w.flush();
		} catch (Throwable t) {
			Log.error("Failed to flush changes when unloading world", t);
		}
		LeakDetector.scheduleLeakCheck(w, w.getName());
		return true;
	}

	@OverrideStatic
	public static void initDimension(int dim) {
		// TODO: re-implement this safely
		throw new UnsupportedOperationException();
	}
}
