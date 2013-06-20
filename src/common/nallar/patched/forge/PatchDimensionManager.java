package nallar.patched.forge;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

public abstract class PatchDimensionManager extends DimensionManager {
	public static void unloadWorlds(
			@SuppressWarnings ("UseOfObsoleteCollectionType")
			Hashtable<Integer, long[]> worldTickTimes) {
		//noinspection SynchronizationOnStaticField
		if (unloadQueue.isEmpty()) {
			return;
		}
		synchronized (unloadQueue) {
			if (!TickThreading.instance.allowWorldUnloading) {
				unloadQueue.clear();
				return;
			}
			for (int id : unloadQueue) {
				unloadWorld(worlds.get(id), true);
			}
			unloadQueue.clear();
			Log.checkWorlds();
			weakWorldMap.clear(); // We do our own leak checking.
		}
	}

	public static synchronized boolean unloadWorld(WorldServer w, boolean save) {
		if (w == null) {
			return false;
		}

		if (!worlds.containsValue(w)) {
			return false;
		}

		if (!w.getPersistentChunks().isEmpty() || !w.playerEntities.isEmpty()) {
			return false;
		}

		if (fireBukkitWorldUnload(w)) {
			return false;
		}

		if (!save) {
			Log.severe("Requested to unload a world without saving it. Ignoring this.");
		}
		try {
			w.saveAllChunks(true, null);
			w.flush();
			fireBukkitWorldSave(w);
		} catch (net.minecraft.world.MinecraftException ex) {
			FMLLog.log(Level.SEVERE, ex, "Failed to save world " + w.getName() + " while unloading it.");
		}
		removeBukkitWorld(w);
		try {
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
		} catch (Throwable t) {
			Log.severe("A mod failed to handle unloading the world " + w.getName(), t);
		}
		setWorld(w.provider.dimensionId, null);
		List<WorldServer> worlds = MinecraftServer.getServer().worlds;
		if (worlds != null) {
			worlds.removeAll(Collections.singletonList(w));
		}
		return true;
	}

	private static boolean fireBukkitWorldUnload(WorldServer w) {
		return false;
	}

	private static void fireBukkitWorldSave(WorldServer w) {

	}

	private static void removeBukkitWorld(WorldServer w) {

	}
}
