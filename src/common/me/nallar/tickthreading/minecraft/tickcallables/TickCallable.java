package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.world.World;

public abstract class TickCallable implements Runnable {
	volatile Lock thisLock = new ReentrantLock();
	volatile Lock xPlusLock = null;
	volatile Lock xMinusLock = null;
	volatile Lock zPlusLock = null;
	volatile Lock zMinusLock = null;
	final World world;
	final TickManager manager;
	public final int hashCode;
	private final int regionX;
	private final int regionZ;
	private float averageTickTime = 1;

	TickCallable(World world, TickManager manager, int regionX, int regionZ) {
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

	public void run() {
		if (shouldTick()) {
			long startTime = System.currentTimeMillis();
			doTick();
			averageTickTime = ((averageTickTime * 100) + (System.currentTimeMillis() - startTime)) / 101;
		}
	}

	boolean shouldTick() {
		return !manager.variableTickRate || averageTickTime < 55 || Math.random() < ((float) 55) / averageTickTime;
	}

	protected abstract void doTick();

	public float getAverageTickTime() {
		return averageTickTime;
	}

	public String getStats() {
		return "X: " + regionX * manager.regionSize + ", Z: " + regionZ * manager.regionSize + ", time: " + getAverageTickTime() + "ms";
	}

	@Override
	public String toString() {
		return "rX: " + regionX + ", rZ: " + regionZ + ", hashCode: " + hashCode;
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
