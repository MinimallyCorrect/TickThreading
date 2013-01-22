package me.nallar.tickthreading.minecraft;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.MapMaker;

import net.minecraft.world.World;

public class LockController {
	private static final Map<World, Map<String, Object>> locks = new MapMaker().weakKeys().makeMap();

	public static Object getLock(World world, String identifier) {
		Map<String, Object> worldLocks = locks.get(world);
		if (worldLocks == null) {
			synchronized (LockController.class) {
				worldLocks = locks.get(world);
				if (worldLocks == null) {
					worldLocks = new HashMap<String, Object>();
					locks.put(world, worldLocks);
				}
			}
		}
		Object lock = worldLocks.get(identifier);
		if (lock == null) {
			synchronized (worldLocks) {
				lock = worldLocks.get(identifier);
				if (lock == null) {
					lock = new Object();
					worldLocks.put(identifier, lock);
				}
			}
		}
		return lock;
	}
}
