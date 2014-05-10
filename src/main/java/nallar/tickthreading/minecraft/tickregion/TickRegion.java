package nallar.tickthreading.minecraft.tickregion;

import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.minecraft.TryRunnable;
import nallar.tickthreading.util.TableFormatter;
import net.minecraft.world.World;

import java.util.*;

public abstract class TickRegion implements TryRunnable {
	private static final boolean variableTickRate = TickThreading.instance.variableTickRate;
	private static final Random rand = new Random();
	final World world;
	final TickManager manager;
	public final int hashCode;
	public final int regionX;
	public final int regionZ;
	long averageTickTime = 1;
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
	public synchronized boolean run() {
		long averageTickTime = this.averageTickTime;
		if (shouldTick(averageTickTime)) {
			long startTime = System.nanoTime();
			doTick();
			if (isEmpty()) {
				manager.queueForRemoval(this);
			}
			this.averageTickTime = ((averageTickTime * 31) + (System.nanoTime() - startTime)) / 32;
		}
		return true;
	}

	protected static boolean shouldTick(long averageTickTime) {
		return !variableTickRate || averageTickTime < 20000000 || rand.nextFloat() < 20000000f / averageTickTime;
	}

	protected abstract void doTick();

	public float getAverageTickTime() {
		return averageTickTime / 1000000f;
	}

	public TableFormatter writeStats(TableFormatter tf) {
		return tf
				.row(this.getShortTypeName())
				.row(regionX * TickManager.regionSize)
				.row(regionZ * TickManager.regionSize)
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

	public void onRemove() {

	}

	public abstract boolean isEmpty();

	protected abstract int size();

	public abstract void die();

	public abstract void dump(final TableFormatter tf);
}
