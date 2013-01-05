package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
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
		//Lock classLock = null;
		Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			TileEntity tileEntity = tileEntitiesIterator.next();
			try {
				//classLock = null;
				relativeXPos = (tileEntity.xCoord % regionSize) / 2;
				relativeZPos = (tileEntity.zCoord % regionSize) / 2;
				xMinusLocked = relativeXPos == 0 && this.xMinusLock != null;
				zMinusLocked = relativeZPos == 0 && this.zMinusLock != null;
				xPlusLocked = relativeXPos == maxPosition && this.xPlusLock != null;
				zPlusLocked = relativeZPos == maxPosition && this.zPlusLock != null;
				locked = xMinusLocked || zMinusLocked || xPlusLocked || zPlusLocked;
				if (locked) {
					// ORDER MATTERS!
					if (xPlusLocked) {
						this.xPlusLock.lock();
					}
					if (zPlusLocked) {
						this.zPlusLock.lock();
					}
					thisLock.lock();
					if (zMinusLocked) {
						this.zMinusLock.lock();
					}
					if (xMinusLocked) {
						this.xMinusLock.lock();
					}
				}
				if (manager.getHashCode(tileEntity) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity);
					if (hashCode != 0) {
						Log.severe("Inconsistent state, a tile entity is in the wrong TickRegion"
								+ "\n entity: " + tileEntity.getClass() + " at x,y,z:" + tileEntity.xCoord + ',' + tileEntity.yCoord + ',' + tileEntity.zCoord
								+ "\n Has hashcode: " + manager.getHashCode(tileEntity)
								+ "\n Region: " + toString());
					}
					continue;
				}
				//classLock = LockController.lock(tileEntity);
				if (!tileEntity.isInvalid() && tileEntity.func_70309_m() && world.blockExists(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
					tileEntity.updateEntity();
				}
				//Yes, this is correct. Can't be simplified to else if, as it may be invalidated during updateEntity
				if (tileEntity.isInvalid()) {
					tileEntitiesIterator.remove();
					tileEntity.onChunkUnload();
					Log.fine("Removed invalid tile: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
						}
					}
				}
			} catch (Exception exception) {
				Log.severe("Exception during tile entity tick"
						+ "\nticking: " + tileEntity.getClass() + " at x,y,z:" + tileEntity.xCoord + ',' + tileEntity.yCoord + ',' + tileEntity.zCoord
						+ "\nTick region: " + toString() + ':', exception);
			} finally {
				//if (classLock != null) {
				//	classLock.unlock();
				//}
				if (locked) {
					if (xMinusLocked) {
						this.xMinusLock.unlock();
					}
					if (zMinusLocked) {
						this.zMinusLock.unlock();
					}
					thisLock.unlock();
					if (zPlusLocked) {
						this.zPlusLock.unlock();
					}
					if (xPlusLocked) {
						this.xPlusLock.unlock();
					}
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
}
