package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import me.nallar.tickthreading.util.concurrent.SimpleMutex;
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
		if (TickThreading.instance.lockRegionBorders) {
			TickRegion tickRegion = getCallable(regionX + 1, regionZ);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					if (xPlusLock == null) {
						this.xPlusLock = tickRegion.xMinusLock = new SimpleMutex();
					}
				}
			}
			tickRegion = getCallable(regionX - 1, regionZ);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					if (xMinusLock == null) {
						this.xMinusLock = tickRegion.xPlusLock = new SimpleMutex();
					}
				}
			}
			tickRegion = getCallable(regionX, regionZ + 1);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					if (zPlusLock == null) {
						this.zPlusLock = tickRegion.zMinusLock = new SimpleMutex();
					}
				}
			}
			tickRegion = getCallable(regionX, regionZ - 1);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					if (zMinusLock == null) {
						this.zMinusLock = tickRegion.zPlusLock = new SimpleMutex();
					}
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
		boolean lockable = TickThreading.instance.lockRegionBorders;
		boolean xMinusLocked = false;
		boolean xPlusLocked = false;
		boolean zMinusLocked = false;
		boolean zPlusLocked = false;
		boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
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
		Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			if (profilingEnabled) {
				startTime = System.nanoTime();
			}
			TileEntity tileEntity = tileEntitiesIterator.next();
			try {
				xPos = tileEntity.xCoord;
				zPos = tileEntity.zCoord;
				if (lockable) {
					relativeXPos = (xPos % regionSize) / 2;
					relativeZPos = (zPos % regionSize) / 2;
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
		synchronized (this) {
			TickRegion tickRegion = getCallable(regionX + 1, regionZ);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					tickRegion.xMinusLock = null;
				}
			}
			tickRegion = getCallable(regionX - 1, regionZ);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					tickRegion.xPlusLock = null;
				}
			}
			tickRegion = getCallable(regionX, regionZ + 1);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					tickRegion.zMinusLock = null;
				}
			}
			tickRegion = getCallable(regionX, regionZ - 1);
			if (tickRegion != null) {
				synchronized (tickRegion) {
					tickRegion.zPlusLock = null;
				}
			}
		}
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
