package me.nallar.tickthreading.minecraft.tickregion;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.world.World;

public abstract class TickRegion implements Runnable {
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

	TickRegion(World world, TickManager manager, int regionX, int regionZ) {
		super();
		this.world = world;
		this.manager = manager;
		this.hashCode = TickManager.getHashCodeFromRegionCoords(regionX, regionZ);
		this.regionX = regionX;
		this.regionZ = regionZ;
		setupLocks();
	}

	private void setupLocks() {
		TickRegion tickRegion = getCallable(regionX + 1, regionZ);
		if (tickRegion != null) {
			tickRegion.xMinusLock = thisLock;
			this.xPlusLock = tickRegion.thisLock;
		}
		tickRegion = getCallable(regionX - 1, regionZ);
		if (tickRegion != null) {
			tickRegion.xPlusLock = thisLock;
			this.xMinusLock = tickRegion.thisLock;
		}
		tickRegion = getCallable(regionX, regionZ + 1);
		if (tickRegion != null) {
			tickRegion.zMinusLock = thisLock;
			this.zPlusLock = tickRegion.thisLock;
		}
		tickRegion = getCallable(regionX, regionZ - 1);
		if (tickRegion != null) {
			tickRegion.zPlusLock = thisLock;
			this.zMinusLock = tickRegion.thisLock;
		}
	}

	public void die() {
		thisLock = null;
		TickRegion tickRegion = getCallable(regionX + 1, regionZ);
		if (tickRegion != null) {
			tickRegion.xMinusLock = null;
		}
		tickRegion = getCallable(regionX - 1, regionZ);
		if (tickRegion != null) {
			tickRegion.xPlusLock = null;
		}
		tickRegion = getCallable(regionX, regionZ + 1);
		if (tickRegion != null) {
			tickRegion.zMinusLock = null;
		}
		tickRegion = getCallable(regionX, regionZ - 1);
		if (tickRegion != null) {
			tickRegion.zPlusLock = null;
		}
	}

	@Override
	public void run() {
		if (shouldTick()) {
			long startTime = System.currentTimeMillis();
			doTick();
			averageTickTime = ((averageTickTime * 511) + (System.currentTimeMillis() - startTime)) / 512;
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
		if (!(other instanceof TickRegion)) {
			return false;
		}
		TickRegion otherTickRegion = (TickRegion) other;
		return otherTickRegion.hashCode == this.hashCode && this.getClass().isInstance(other);
	}

	protected abstract TickRegion getCallable(int regionX, int regionZ);

	public abstract boolean isEmpty();
}
