package nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import nallar.collections.LinkedHashSetTempSetNoClear;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import nallar.tickthreading.util.TableFormatter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class TileEntityTickRegion extends TickRegion {
	private int checkTime = 0;
	private final LinkedHashSetTempSetNoClear<TileEntity> tileEntitySet = new LinkedHashSetTempSetNoClear<TileEntity>();

	public TileEntityTickRegion(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
	}

	@Override
	public void doTick() {
		final TickManager manager = this.manager;
		final boolean check = checkTime++ % 60 == 0;
		final boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
		EntityTickProfiler entityTickProfiler = profilingEnabled ? EntityTickProfiler.ENTITY_TICK_PROFILER : null;
		Lock thisLock;
		Lock xPlusLock;
		Lock xMinusLock;
		Lock zPlusLock;
		Lock zMinusLock;
		long startTime = profilingEnabled ? System.nanoTime() : 0;
		// Locking calls are manipulated by the patcher,
		// INVOKEVIRTUAL java.util.concurrent.locks.Lock.lock/unlock() calls are replaced with
		// MONITORENTER/MONITOREXIT instructions.
		// For this reason, although we do use interfaces which correctly implement lock/unlock depending on the TileEntity,
		// it is critical that .lock() is not called on anything other than a NativeMutex instance in this code.
		// Behaving in this manner also allows us to avoid the overhead of calling an interface method.
		// Volatile reads are, if used only when necessary, fast. JVM will not optimize out repeat volatile reads.
		// Just don't read fields repeatedly unnecessary. Best not to do this with non-volatile fields anyway.
		final Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.startIteration();
		try {
			while (tileEntitiesIterator.hasNext()) {
				final TileEntity tileEntity = tileEntitiesIterator.next();
				if (check) {
					if (check(tileEntity, tileEntitiesIterator)) {
						continue;
					}
				}
				if (tileEntity.noLock()) {
					try {
						if (tileEntity.isInvalid()) {
							tileEntitiesIterator.remove();
							invalidate(tileEntity);
						} else if (tileEntity.worldObj != null) {
							tileEntity.updateEntity();
						}
					} catch (Throwable throwable) {
						Log.severe("Exception ticking TileEntity " + Log.toString(tileEntity), throwable);
					}
					continue;
				}
				xPlusLock = tileEntity.xPlusLock;
				zPlusLock = tileEntity.zPlusLock;
				thisLock = tileEntity.thisLock;
				xMinusLock = tileEntity.xMinusLock;
				zMinusLock = tileEntity.zMinusLock;
				if (xPlusLock != null) {
					xPlusLock.lock();
				}
				if (zPlusLock != null) {
					zPlusLock.lock();
				}
				if (thisLock != null) {
					thisLock.lock();
				}
				if (zMinusLock != null) {
					zMinusLock.lock();
				}
				if (xMinusLock != null) {
					xMinusLock.lock();
				}
				try {
					if (tileEntity.isInvalid()) {
						tileEntitiesIterator.remove();
						invalidate(tileEntity);
					} else if (tileEntity.worldObj != null) {
						tileEntity.updateEntity();
					}
				} catch (Throwable throwable) {
					Log.severe("Exception ticking TileEntity " + Log.toString(tileEntity), throwable);
				} finally {
					if (xMinusLock != null) {
						xMinusLock.unlock();
					}
					if (zMinusLock != null) {
						zMinusLock.unlock();
					}
					if (thisLock != null) {
						thisLock.unlock();
					}
					if (zPlusLock != null) {
						zPlusLock.unlock();
					}
					if (xPlusLock != null) {
						xPlusLock.unlock();
					}

					if (profilingEnabled) {
						long oldStartTime = startTime;
						entityTickProfiler.record(tileEntity, (startTime = System.nanoTime()) - oldStartTime);
					}
				}
			}
		} finally {
			tileEntitySet.done();
		}
	}

	private boolean check(final TileEntity tileEntity, final Iterator tileEntitiesIterator) {
		final int xPos = tileEntity.xCoord;
		final int zPos = tileEntity.zCoord;
		if (TickManager.getHashCode(xPos, zPos) != hashCode) {
			tileEntitiesIterator.remove();
			if (tileEntity.isInvalid() || !world.getChunkProvider().chunkExists(xPos >> 4, zPos >> 4)) {
				if (Log.debug) {
					Log.debug("A tile entity is invalid or unloaded."
							+ "\n entity: " + Log.toString(tileEntity)
							+ "\n In " + hashCode + "\t.tickRegion: " + tileEntity.tickRegion.hashCode + "\texpected: " + TickManager.getHashCode(xPos, zPos));
				}
				invalidate(tileEntity);
				return true;
			}
			if (Log.debug) {
				Log.debug("A tile entity is in the wrong TickRegion - was it moved by a player, or did something break?"
						+ "\n entity: " + Log.toString(tileEntity)
						+ "\n In " + hashCode + "\t.tickRegion: " + tileEntity.tickRegion.hashCode + "\texpected: " + TickManager.getHashCode(xPos, zPos));
			}
			manager.add(tileEntity, false);
			manager.lock(tileEntity);
			return true;
		}
		if (tileEntity.lastTTX != xPos || tileEntity.lastTTY != tileEntity.yCoord || tileEntity.lastTTZ != zPos) {
			manager.lock(tileEntity);
			return true;
		}
		return false;
	}

	private void invalidate(TileEntity tileEntity) {
		int xPos = tileEntity.xCoord;
		int zPos = tileEntity.zCoord;
		manager.removed(tileEntity);
		Chunk chunk = world.getChunkIfExists(xPos >> 4, zPos >> 4);
		if (chunk != null) {
			chunk.cleanChunkBlockTileEntity(xPos, tileEntity.yCoord, zPos);
		}
	}

	@Override
	protected String getShortTypeName() {
		return "T";
	}

	public boolean add(TileEntity tileEntity) {
		return tileEntitySet.add(tileEntity);
	}

	public boolean remove(TileEntity tileEntity) {
		return tileEntitySet.remove(tileEntity);
	}

	@Override
	public boolean isEmpty() {
		return tileEntitySet.isEmpty();
	}

	@Override
	public int size() {
		return tileEntitySet.size();
	}

	@Override
	public void die() {
		tileEntitySet.clear();
	}

	@Override
	public void dump(final TableFormatter tf) {
		synchronized (tileEntitySet) {
			for (TileEntity e : tileEntitySet) {
				//DumpCommand.dump(tf, e, tf.stringFiller == StringFiller.CHAT ? 35 : 70);
				tf.sb.append("TileEntity ").append(Log.toString(e)).append(" in ").append(hashCode).append(", new ").append(TickManager.getHashCode(e)).append('\n');
			}
		}
	}
}
