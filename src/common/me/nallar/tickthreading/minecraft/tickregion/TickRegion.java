package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Random;

import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.world.World;

public abstract class TickRegion implements Runnable {
	private static final boolean variableTickRate = TickThreading.instance.variableTickRate;
	private static final Random rand = new Random();
	final World world;
	final TickManager manager;
	public final int hashCode;
	private final int regionX;
	private final int regionZ;
	private float averageTickTime = 1;
	public boolean profilingEnabled = false;

	TickRegion(World world, TickManager manager, int regionX, int regionZ) {
		super();
		this.world = world;
		this.manager = manager;
		this.hashCode = TickManager.getHashCodeFromRegionCoords(regionX, regionZ);
		this.regionX = regionX;
		this.regionZ = regionZ;
	}

	@Override
	public synchronized void run() {
		float averageTickTime = this.averageTickTime;
		if (shouldTick(averageTickTime)) {
			long startTime = System.nanoTime();
			doTick();
			if (isEmpty()) {
				manager.queueForRemoval(this);
			}
			this.averageTickTime = ((averageTickTime * 127) + (System.nanoTime() - startTime)) / 128;
		}
	}

	private static boolean shouldTick(float averageTickTime) {
		return !variableTickRate || averageTickTime < 20000000 || rand.nextFloat() < 20000000f / averageTickTime;
	}

	protected abstract void doTick();

	public float getAverageTickTime() {
		return averageTickTime / 1000000f;
	}

	public TableFormatter writeStats(TableFormatter tf) {
		return tf
				.row(this.getShortTypeName())
				.row(regionX * manager.regionSize)
				.row(regionZ * manager.regionSize)
				.row(size())
				.row(getAverageTickTime());
	}

	protected abstract String getShortTypeName();

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

	public abstract boolean isEmpty();

	protected abstract int size();

	public abstract void die();
}
