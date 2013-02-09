package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

public class TileEntityTickRegion extends TickRegion {
	private final Set<TileEntity> tileEntitySet = new LinkedHashSet<TileEntity>();

	public TileEntityTickRegion(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
	}

	@Override
	public void doTick() {
		final ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
		final World world = this.world;
		final boolean lockable = TickThreading.instance.lockRegionBorders;
		final boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
		final int regionSize = manager.regionSize;
		final int maxPosition = (regionSize / 2) - 1;
		int relativeXPos;
		int relativeZPos;
		boolean xMinusLocked = false;
		boolean xPlusLocked = false;
		boolean zMinusLocked = false;
		boolean zPlusLocked = false;
		Lock xPlusLock = null;
		Lock xMinusLock = null;
		Lock zPlusLock = null;
		Lock zMinusLock = null;
		EntityTickProfiler entityTickProfiler = null;
		long startTime = 0;
		if (profilingEnabled) {
			entityTickProfiler = manager.entityTickProfiler;
			if (this.profilingEnabled) {
				entityTickProfiler.tick();
			}
		}
		//Lock classLock = null;
		int xPos = 0;
		int zPos = 0;
		final Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			if (profilingEnabled) {
				startTime = System.nanoTime();
			}
			TileEntity tileEntity = tileEntitiesIterator.next();
			try {
				xPos = tileEntity.xCoord;
				zPos = tileEntity.zCoord;
				if (lockable) {
					if (tileEntity.lastTTX != xPos || tileEntity.lastTTY != tileEntity.yCoord || tileEntity.lastTTZ != zPos) {
						manager.lock(tileEntity);
					}
					xPlusLock = tileEntity.xPlusLock;
					zPlusLock = tileEntity.zPlusLock;
					zMinusLock = tileEntity.zMinusLock;
					xMinusLock = tileEntity.xMinusLock;
					if (xPlusLock != null) {
						xPlusLock.lock();
					}
					if (zPlusLock != null) {
						zPlusLock.lock();
					}
					if (zMinusLock != null) {
						zMinusLock.lock();
					}
					if (xMinusLock != null) {
						xMinusLock.lock();
					}
				}
				if (manager.getHashCode(xPos, zPos) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity);
					if (hashCode != 0) {
						Log.severe("Inconsistent state, a tile entity is in the wrong TickRegion"
								+ "\n entity: " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
								+ "\n Has hashcode: " + manager.getHashCode(tileEntity)
								+ "\n Region: " + toString());
					}
				} else if (tileEntity.isInvalid()) {
					tileEntitiesIterator.remove();
					manager.removed(tileEntity);
					//Log.fine("Removed tile entity: " + xPos + ", " + tileEntity.yCoord + ", " + zPos + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(xPos >> 4, zPos >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(xPos >> 4, zPos >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(xPos, tileEntity.yCoord, zPos);
						}
					}
				} else if (tileEntity.worldObj != null && chunkProvider.chunkExists(xPos >> 4, zPos >> 4)) {
					tileEntity.updateEntity();
				}
			} catch (Throwable throwable) {
				Log.severe("Exception during tile entity tick"
						+ "\nticking: " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
						+ "\nTick region: " + toString() + ':', throwable);
			} finally {
				if (lockable) {
					if (xMinusLock != null) {
						xMinusLock.unlock();
					}
					if (zMinusLock != null) {
						zMinusLock.unlock();
					}
					if (zPlusLock != null) {
						zPlusLock.unlock();
					}
					if (xPlusLock != null) {
						xPlusLock.unlock();
					}
				}
				if (profilingEnabled) {
					entityTickProfiler.record(tileEntity, System.nanoTime() - startTime);
				}
			}
		}
	}

	@Override
	protected String getShortTypeName() {
		return "T";
	}

	public void add(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				toAdd.add(tileEntity);
			} else {
				tileEntitySet.add(tileEntity);
			}
		}
	}

	public boolean remove(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toRemove.add(tileEntity);
			} else {
				return tileEntitySet.remove(tileEntity);
			}
		}
	}

	@Override
	public void processChanges() {
		synchronized (tickStateLock) {
			if (ticking) {
				return;
			}
			tileEntitySet.addAll(toAdd);
			tileEntitySet.removeAll(toRemove);
			toAdd.clear();
			toRemove.clear();
		}
	}

	@Override
	public void die() {
		super.die();
		tileEntitySet.clear();
	}

	@Override
	protected TickRegion getCallable(int regionX, int regionY) {
		return manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
	}

	@Override
	public boolean isEmpty() {
		return tileEntitySet.isEmpty();
	}

	@Override
	public int size() {
		return tileEntitySet.size();
	}
}
