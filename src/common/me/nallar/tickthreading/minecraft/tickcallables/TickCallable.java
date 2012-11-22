package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.src.World;

public abstract class TickCallable<T> implements Callable<T> {
	public volatile Lock thisLock = new ReentrantLock();
	public volatile Lock xPlusLock = null;
	public volatile Lock xMinusLock = null;
	public volatile Lock zPlusLock = null;
	public volatile Lock zMinusLock = null;
	final World world;
	final TickManager manager;
	public final int hashCode;
	public final int regionX;
	public final int regionZ;

	public TickCallable(World world, TickManager manager, int regionX, int regionZ) {
		super();
		this.world = world;
		this.manager = manager;
		this.hashCode = TickManager.getHashCodeFromRegionCoords(regionX, regionZ);
		this.regionX = regionX;
		this.regionZ = regionZ;
		setupLocks();
	}

	private void setupLocks() {
		TickCallable tickCallable = getCallable(regionX + 1, regionZ);
		if (tickCallable != null) {
			tickCallable.xMinusLock = thisLock;
			this.xPlusLock = tickCallable.thisLock;
		}
		tickCallable = getCallable(regionX - 1, regionZ);
		if (tickCallable != null) {
			tickCallable.xPlusLock = thisLock;
			this.xMinusLock = tickCallable.thisLock;
		}
		tickCallable = getCallable(regionX, regionZ + 1);
		if (tickCallable != null) {
			tickCallable.zMinusLock = thisLock;
			this.zPlusLock = tickCallable.thisLock;
		}
		tickCallable = getCallable(regionX, regionZ - 1);
		if (tickCallable != null) {
			tickCallable.zPlusLock = thisLock;
			this.zMinusLock = tickCallable.thisLock;
		}
	}

	public void die() {
		thisLock = null;
		TickCallable tickCallable = getCallable(regionX + 1, regionZ);
		if (tickCallable != null) {
			tickCallable.xMinusLock = null;
		}
		tickCallable = getCallable(regionX - 1, regionZ);
		if (tickCallable != null) {
			tickCallable.xPlusLock = null;
		}
		tickCallable = getCallable(regionX, regionZ + 1);
		if (tickCallable != null) {
			tickCallable.zMinusLock = null;
		}
		tickCallable = getCallable(regionX, regionZ - 1);
		if (tickCallable != null) {
			tickCallable.zPlusLock = null;
		}
	}

	@Override
	public String toString() {
		return "rX: " + regionX + ", rZ: " + regionZ + ", hashCode: " + hashCode;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TickCallable)) {
			return false;
		}
		TickCallable otherTickCallable = (TickCallable) other;
		return otherTickCallable.hashCode == this.hashCode && this.getClass().isInstance(other);
	}

	protected abstract TickCallable getCallable(int regionX, int regionZ);

	public abstract boolean isEmpty();
}
