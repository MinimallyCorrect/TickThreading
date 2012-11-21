package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;

public class TileEntityTickCallable<T> extends TickCallable {
	private final List<TileEntity> tileEntityList = new ArrayList<TileEntity>();
	private final List<TileEntity> toRemoveList = new ArrayList<TileEntity>();

	public TileEntityTickCallable(World world, String identifier, TickManager manager, int regionX, int regionY) {
		super(world, identifier, manager, regionX, regionY);
	}

	@Override
	public T call() {
		IChunkProvider chunkProvider = world.getChunkProvider();
		int regionSize = manager.tileEntityRegionSize;
		int maxPosition = regionSize - 1;
		int relativeXPos;
		int relativeZPos;
		boolean locked;
		boolean xMinusLocked = false;
		boolean xPlusLocked = false;
		boolean zMinusLocked = false;
		boolean zPlusLocked = false;
		Log.fine("In tick for r: ");
		for (TileEntity tileEntity : tileEntityList) {
			locked = false;
			try {
				relativeXPos = tileEntity.xCoord % regionSize;
				relativeZPos = tileEntity.zCoord % regionSize;
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
				if (!tileEntity.isInvalid()) {
					tileEntity.updateEntity();
				}
				if (tileEntity.isInvalid()) {
					toRemoveList.add(tileEntity);
					Log.warning("Removed invalid tile: " + tileEntity.xCoord + ", " + tileEntity.yCoord + ", " + tileEntity.zCoord + "\ttype:" + tileEntity.getClass().toString());
					if (chunkProvider.chunkExists(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4)) {
						Chunk chunk = world.getChunkFromChunkCoords(tileEntity.xCoord >> 4, tileEntity.zCoord >> 4);
						if (chunk != null) {
							chunk.cleanChunkBlockTileEntity(tileEntity.xCoord & 0xf, tileEntity.yCoord, tileEntity.zCoord & 0xf);
						}
					}
				} else if (manager.getHashCode(tileEntity) != hashCode) {
					toRemoveList.add(tileEntity);
					manager.add(tileEntity);
				}
			} catch (Exception exception) {
				Log.severe("Exception during tile entity tick ");
				Log.severe("Tick region: " + identifier + ":", exception);
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
		tileEntityList.removeAll(toRemoveList);
		toRemoveList.clear();
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
