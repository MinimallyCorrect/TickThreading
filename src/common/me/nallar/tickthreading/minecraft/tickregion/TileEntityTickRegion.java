package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public class TileEntityTickRegion extends TickRegion {
	private final Set<TileEntity> tileEntitySet = new LinkedHashSet<TileEntity>();

	public TileEntityTickRegion(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
	}

	@Override
	protected synchronized void setupLocks() {
		TickRegion tickRegion = getCallable(regionX + 1, regionZ);
		if (tickRegion != null) {
			synchronized (tickRegion) {
				if (xPlusLock == null) {
					this.xPlusLock = tickRegion.xMinusLock = new ReentrantLock();
				}
			}
		}
		tickRegion = getCallable(regionX - 1, regionZ);
		if (tickRegion != null) {
			synchronized (tickRegion) {
				if (xMinusLock == null) {
					this.xMinusLock = tickRegion.xPlusLock = new ReentrantLock();
				}
			}
		}
		tickRegion = getCallable(regionX, regionZ + 1);
		if (tickRegion != null) {
			synchronized (tickRegion) {
				if (zPlusLock == null) {
					this.zPlusLock = tickRegion.zMinusLock = new ReentrantLock();
				}
			}
		}
		tickRegion = getCallable(regionX, regionZ - 1);
		if (tickRegion != null) {
			synchronized (tickRegion) {
				if (zMinusLock == null) {
					this.zMinusLock = tickRegion.zPlusLock = new ReentrantLock();
				}
			}
		}
	}

	@Override
	public void doTick() {
		IChunkProvider chunkProvider = world.getChunkProvider();
		int regionSize = manager.regionSize;
		int maxPosition = (regionSize / 2) - 1;
		int relativeXPos;
		int relativeZPos;
		boolean locked = false;
		boolean xMinusLocked = false;
		boolean xPlusLocked = false;
		boolean zMinusLocked = false;
		boolean zPlusLocked = false;
		boolean profilingEnabled = manager.profilingEnabled;
		EntityTickProfiler entityTickProfiler = null;
		long startTime = 0;
		if (profilingEnabled) {
			entityTickProfiler = manager.entityTickProfiler;
		}
		//Lock classLock = null;
		Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			if (profilingEnabled) {
				startTime = System.nanoTime();
			}
			TileEntity tileEntity = tileEntitiesIterator.next();
			try {
				//classLock = null;
				relativeXPos = (tileEntity.xCoord % regionSize) / 2;
				relativeZPos = (tileEntity.zCoord % regionSize) / 2;
				xMinusLocked = relativeXPos == 0 && this.xMinusLock != null;
				zMinusLocked = relativeZPos == 0 && this.zMinusLock != null;
				xPlusLocked = relativeXPos == maxPosition && this.xPlusLock != null;
				zPlusLocked = relativeZPos == maxPosition && this.zPlusLock != null;
				if (xPlusLocked) {
					this.xPlusLock.lock();
				}
				if (zPlusLocked) {
					this.zPlusLock.lock();
				}
				if (zMinusLocked) {
					this.zMinusLock.lock();
				}
				if (xMinusLocked) {
					this.xMinusLock.lock();
				}
				if (manager.getHashCode(tileEntity) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity);
					if (hashCode != 0) {
						Log.severe("Inconsistent state, a tile entity is in the wrong TickRegion"
								+ "\n entity: " + Log.toString(tileEntity) + " at x,y,z:" + tileEntity.xCoord + ',' + tileEntity.yCoord + ',' + tileEntity.zCoord
								+ "\n Has hashcode: " + manager.getHashCode(tileEntity)
								+ "\n Region: " + toString());
					}
					continue;
				}
				if (!tileEntity.isInvalid() && tileEntity.func_70309_m() && world.blockExists(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
					tileEntity.updateEntity();
				}
				//Yes, this is correct. Can't be simplified to else if, as it may be invalidated during updateEntity
				if (tileEntity.isInvalid()) {
					tileEntitiesIterator.remove();
					manager.removed(tileEntity);
					tileEntity.onChunkUnload();
					//Log.fine("Removed tile entity: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
						}
					}
				}
			} catch (Throwable throwable) {
				Log.severe("Exception during tile entity tick"
						+ "\nticking: " + Log.toString(tileEntity) + " at x,y,z:" + tileEntity.xCoord + ',' + tileEntity.yCoord + ',' + tileEntity.zCoord
						+ "\nTick region: " + toString() + ':', throwable);
			} finally {
				if (xMinusLocked) {
					this.xMinusLock.unlock();
				}
				if (zMinusLocked) {
					this.zMinusLock.unlock();
				}
				if (zPlusLocked) {
					this.zPlusLock.unlock();
				}
				if (xPlusLocked) {
					this.xPlusLock.unlock();
				}
				if (profilingEnabled) {
					entityTickProfiler.record(tileEntity.getClass(), System.nanoTime() - startTime);
				}
			}
		}
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
		return manager.getTileEntityCallable(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
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
