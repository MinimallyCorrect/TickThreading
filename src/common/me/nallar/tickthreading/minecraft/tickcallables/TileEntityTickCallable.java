package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class TileEntityTickCallable<T> extends TickCallable {
	private final List<TileEntity> tileEntityList = new ArrayList<TileEntity>();

	public TileEntityTickCallable(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
	}

	@Override
	public T call() {
		IChunkProvider chunkProvider = world.getChunkProvider();
		int regionSize = manager.tileEntityRegionSize;
		int maxPosition = (regionSize / 2) - 1;
		int relativeXPos;
		int relativeZPos;
		boolean locked = false;
		boolean xMinusLocked = false;
		boolean xPlusLocked = false;
		boolean zMinusLocked = false;
		boolean zPlusLocked = false;
		Iterator<TileEntity> tileEntitiesIterator = tileEntityList.iterator();
		while (tileEntitiesIterator.hasNext()) {
			TileEntity tileEntity = tileEntitiesIterator.next();
			try {
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
				if (!tileEntity.isInvalid() && tileEntity.func_70309_m() && world.blockExists(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord)) {
					tileEntity.updateEntity();
				}
				//Yes, this is correct. Can't be simplified to else if, as it may be invalidated during updateEntity
				if (tileEntity.isInvalid()) {
					tileEntitiesIterator.remove();
					Log.warning("Removed invalid tile: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
						}
					}
				} else if (manager.getHashCode(tileEntity) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity);
					Log.severe("Inconsistent state: " + tileEntity + " is in the wrong TickCallable.");
				}
			} catch (Exception exception) {
				Log.severe("Exception during tile entity tick ");
				Log.severe("Tick region: " + toString() + ":", exception);
			} finally {
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
		return null;
	}

	public void add(TileEntity tileEntity) {
		if (!tileEntityList.contains(tileEntity)) {
			tileEntityList.add(tileEntity);
		}
	}

	public boolean remove(TileEntity tileEntity) {
		return tileEntityList.remove(tileEntity);
	}

	@Override
	protected TickCallable getCallable(int regionX, int regionY) {
		return manager.getTileEntityCallable(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
	}

	@Override
	public boolean isEmpty() {
		return tileEntityList.isEmpty();
	}
}
