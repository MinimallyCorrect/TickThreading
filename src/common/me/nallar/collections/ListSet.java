package me.nallar.collections;

import java.util.Iterator;

import me.nallar.tickthreading.Log;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;

public class ListSet extends SynchronizedList {
	@Override
	public synchronized boolean add(final Object o) {
		if (o instanceof World) {
			World world = (World) o;
			WorldProvider provider = world.provider;
			if (provider == null) {
				Log.severe("Tried to add world " + world + " with null provider to bukkit dimensions.", new Throwable());
				return false;
			}
			Iterator<World> iterator = this.iterator();
			while (iterator.hasNext()) {
				World world_ = iterator.next();
				if (world_ == world) {
					return false;
				} else if (world_.provider == null) {
					Log.severe("World " + world_ + " with null provider still in bukkit dimensions.", new Throwable());
					iterator.remove();
				} else if (provider.dimensionId == world_.provider.dimensionId) {
					Log.severe("Attempted to add " + Log.dumpWorld(world) + " to bukkit dimensions when " + Log.dumpWorld(world_) + " is already in it.", new Throwable());
					return false;
				}
			}
			return super.add(o);
		}
		return !this.contains(o) && super.add(o);
	}
}
