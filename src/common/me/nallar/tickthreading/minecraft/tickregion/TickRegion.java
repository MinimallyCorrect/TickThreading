package me.nallar.tickthreading.minecraft.tickregion;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.world.World;

public abstract class TickRegion implements Runnable {
	protected final Object tickStateLock = new Object();
	protected volatile boolean ticking = false;
	protected final Set toRemove = new LinkedHashSet();
	protected final Set toAdd = new LinkedHashSet();
	volatile Lock xPlusLock = null;
	volatile Lock xMinusLock = null;
	volatile Lock zPlusLock = null;
	volatile Lock zMinusLock = null;
	final World world;
	final TickManager manager;
	public final int hashCode;
	protected final int regionX;
	protected final int regionZ;
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

	protected void setupLocks() {
	}

	public void die() {
		TickRegion tickRegion = getCallable(regionX + 1, regionZ);
		if (tickRegion != null) {
			synchronized (tickRegion) {
				tickRegion.xMinusLock = null;
			}
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
			synchronized (tickStateLock) {
				ticking = true;
			}
			doTick();
			synchronized (tickStateLock) {
				ticking = false;
			}
			averageTickTime = ((averageTickTime * 127) + (System.currentTimeMillis() - startTime)) / 128;
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
		return this.getClass().getSimpleName() + " - X: " + regionX * manager.regionSize + ", Z: " + regionZ * manager.regionSize + ", time: " + getAverageTickTime() + "ms"
				+ "\n size: " + size();
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

	public abstract int size();

	public abstract void processChanges();
}
