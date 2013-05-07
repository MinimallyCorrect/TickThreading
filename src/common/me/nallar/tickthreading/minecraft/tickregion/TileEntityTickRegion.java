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
	public static final byte lockXPlus = 1 << 1;
	public static final byte lockXMinus = 1 << 2;
	public static final byte lockZPlus = 1 << 3;
	public static final byte lockZMinus = 1 << 4;
	public static final byte lockThis = 1 << 5;
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
		byte lock = 0;
		boolean anyLock = false;
		Lock thisLock = null;
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
		// This code is performance critical.
		// Locking calls are manipulated by the patcher,
		// INVOKEVIRTUAL java.util.concurrent.locks.Lock.lock/unlock() calls are replaced with
		// MONITORENTER/MONITOREXIT instructions.
		// For this reason, although we do use interfaces which correctly implement lock/unlock depending on the TileEntity,
		// it is critical that .lock() is not called on anything other than a NativeMutex instance in this code.
		// Behaving in this manner also allows us to avoid the overhead of calling an interface method.
		final Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.iterator();
		while (tileEntitiesIterator.hasNext()) {
			if (profilingEnabled) {
				startTime = System.nanoTime();
			}
			final TileEntity tileEntity = tileEntitiesIterator.next();
			final int xPos = tileEntity.xCoord;
			final int zPos = tileEntity.zCoord;
			try {
				if (lockable) {
					if (tileEntity.lastTTX != xPos || tileEntity.lastTTY != tileEntity.yCoord || tileEntity.lastTTZ != zPos) {
						manager.lock(tileEntity);
					}
					if (anyLock = (lock = tileEntity.shouldLock) != 0) {
						if ((lock & lockXPlus) != 0) {
							xPlusLock = tileEntity.xPlusLock;
							xPlusLock.lock();
						}
						if ((lock & lockZPlus) != 0) {
							zPlusLock = tileEntity.zPlusLock;
							zPlusLock.lock();
						}
						if ((lock & lockThis) != 0) {
							thisLock = tileEntity.thisLock;
							thisLock.lock();
						}
						if ((lock & lockZMinus) != 0) {
							zMinusLock = tileEntity.zMinusLock;
							zMinusLock.lock();
						}
						if ((lock & lockXMinus) != 0) {
							xMinusLock = tileEntity.xMinusLock;
							xMinusLock.lock();
						}
					}
				}
				if (manager.getHashCode(xPos, zPos) != hashCode) {
					tileEntitiesIterator.remove();
					manager.add(tileEntity, false);
					if (hashCode != 0) {
						Log.fine("A tile entity is in the wrong TickRegion - was it moved by a player, or did something bug out?"
								+ "\n entity: " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
								+ "\n Region: " + toString()
								+ "\n World: " + Log.name(tileEntity.worldObj));
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
				Log.severe("Exception ticking TileEntity " + Log.toString(tileEntity) + " at x,y,z:" + xPos + ',' + tileEntity.yCoord + ',' + zPos
						+ "\n World: " + Log.name(tileEntity.worldObj), throwable);
			} finally {
				if (lockable && anyLock) {
					if ((lock & lockXMinus) != 0) {
						xMinusLock.unlock();
					}
					if ((lock & lockZMinus) != 0) {
						zMinusLock.unlock();
					}
					if ((lock & lockThis) != 0) {
						thisLock.unlock();
					}
					if ((lock & lockZPlus) != 0) {
						zPlusLock.unlock();
					}
					if ((lock & lockXPlus) != 0) {
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

	public boolean add(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toAdd.add(tileEntity) && !tileEntitySet.contains(tileEntity);
			} else {
				return tileEntitySet.add(tileEntity);
			}
		}
	}

	public boolean remove(TileEntity tileEntity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toRemove.add(tileEntity) && tileEntitySet.contains(tileEntity);
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
