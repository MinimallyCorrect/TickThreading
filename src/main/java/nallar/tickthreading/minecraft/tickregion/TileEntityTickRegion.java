package nallar.tickthreading.minecraft.tickregion;

import nallar.collections.LinkedHashSetTempSetNoClear;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickManager;
import nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import nallar.tickthreading.util.TableFormatter;
import nallar.tickthreading.util.concurrent.SimpleMutex;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;

public class TileEntityTickRegion extends TickRegion {
	private int checkTime = 0;
	private final LinkedHashSetTempSetNoClear<TileEntity> tileEntitySet = new LinkedHashSetTempSetNoClear<TileEntity>();
	public final SimpleMutex lock = new SimpleMutex();
	public SimpleMutex xPlusLock;
	public SimpleMutex xMinusLock;
	public SimpleMutex zPlusLock;
	public SimpleMutex zMinusLock;

	public TileEntityTickRegion(World world, TickManager manager, int regionX, int regionY) {
		super(world, manager, regionX, regionY);
		setupLocks();
	}

	public synchronized void setupLocks() {
		{
			TileEntityTickRegion xPlus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX + 1, regionZ));
			if (xPlus != null) {
				synchronized (xPlus) {
					xPlus.xMinusLock = this.lock;
					xPlusLock = xPlus.lock;
				}
			}
		}
		{
			TileEntityTickRegion xMinus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX - 1, regionZ));
			if (xMinus != null) {
				synchronized (xMinus) {
					xMinus.xPlusLock = this.lock;
					xMinusLock = xMinus.lock;
				}
			}
		}
		{
			TileEntityTickRegion zPlus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionZ + 1));
			if (zPlus != null) {
				synchronized (zPlus) {
					zPlus.zMinusLock = this.lock;
					zPlusLock = zPlus.lock;
				}
			}
		}
		{
			TileEntityTickRegion zMinus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionZ - 1));
			if (zMinus != null) {
				synchronized (zMinus) {
					zMinus.zPlusLock = this.lock;
					zMinusLock = zMinus.lock;
				}
			}
		}
	}

	public synchronized void removeLocks() {
		{
			TileEntityTickRegion xPlus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX + 1, regionZ));
			if (xPlus != null) {
				synchronized (xPlus) {
					xPlus.xMinusLock = null;
					xPlusLock = null;
				}
			}
		}
		{
			TileEntityTickRegion xMinus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX - 1, regionZ));
			if (xMinus != null) {
				synchronized (xMinus) {
					xMinus.xPlusLock = null;
					xMinusLock = null;
				}
			}
		}
		{
			TileEntityTickRegion zPlus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionZ + 1));
			if (zPlus != null) {
				synchronized (zPlus) {
					zPlus.zMinusLock = null;
					zPlusLock = null;
				}
			}
		}
		{
			TileEntityTickRegion zMinus = manager.getTileEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionZ - 1));
			if (zMinus != null) {
				synchronized (zMinus) {
					zMinus.zPlusLock = null;
					zMinusLock = null;
				}
			}
		}
	}

	@Override
	public void onRemove() {
		removeLocks();
	}

	@Override
	public synchronized boolean run() {
		SimpleMutex xPlusLock = this.xPlusLock;
		if (xPlusLock == null || xPlusLock.tryLock()) {
			try {
				SimpleMutex xMinusLock = this.xMinusLock;
				if (xMinusLock == null || xMinusLock.tryLock()) {
					try {
						SimpleMutex zPlusLock = this.zPlusLock;
						if (zPlusLock == null || zPlusLock.tryLock()) {
							try {
								SimpleMutex zMinusLock = this.zMinusLock;
								if (zMinusLock == null || zMinusLock.tryLock()) {
									try {
										return super.run();
									} finally {
										if (zMinusLock != null) {
											zMinusLock.unlock();
										}
									}
								}
							} finally {
								if (zPlusLock != null) {
									zPlusLock.unlock();
								}
							}
						}
					} finally {
						if (xMinusLock != null) {
							xMinusLock.unlock();
						}
					}
				}
			} finally {
				if (xPlusLock != null) {
					xPlusLock.unlock();
				}
			}
		}
		return false;
	}

	@Override
	public void doTick() {
		final TickManager manager = this.manager;
		final boolean check = checkTime++ % 60 == 0;
		final boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
		EntityTickProfiler entityTickProfiler = profilingEnabled ? EntityTickProfiler.ENTITY_TICK_PROFILER : null;
		long startTime = profilingEnabled ? System.nanoTime() : 0;
		final Iterator<TileEntity> tileEntitiesIterator = tileEntitySet.startIteration();
		try {
			while (tileEntitiesIterator.hasNext()) {
				final TileEntity tileEntity = tileEntitiesIterator.next();
				if (check && check(tileEntity, tileEntitiesIterator)) {
					continue;
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
			return true;
		}
		return tileEntity.lastTTX != xPos || tileEntity.lastTTY != tileEntity.yCoord || tileEntity.lastTTZ != zPos;
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
