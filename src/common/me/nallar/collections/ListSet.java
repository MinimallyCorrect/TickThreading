package me.nallar.collections;

import me.nallar.tickthreading.Log;
import net.minecraft.world.World;

public class ListSet extends SynchronizedList {
	@Override
	public synchronized boolean add(final Object o) {
		if (o instanceof World) {
			World world = (World) o;
			for (World world_ : (Iterable<World>) this) {
				if (world_ == world) {
					return false;
				} else if (world.provider.dimensionId == world_.provider.dimensionId) {
					Log.severe("Attempted to add " + Log.dumpWorld(world) + " to bukkit dimensions when " + Log.dumpWorld(world_) + " is already in it.", new Throwable());
					return false;
				}
			}
			return super.add(o);
		}
		return !this.contains(o) && super.add(o);
	}
}
