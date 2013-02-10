package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public class EntityTickRegion extends TickRegion {
	private final Set<Entity> entitySet = new LinkedHashSet<Entity>();

	public EntityTickRegion(World world, TickManager manager, int regionX, int regionZ) {
		super(world, manager, regionX, regionZ);
	}

	@Override
	public void doTick() {
		try {
			IChunkProvider chunkProvider = world.getChunkProvider();
			boolean profilingEnabled = manager.profilingEnabled || this.profilingEnabled;
			EntityTickProfiler entityTickProfiler = null;
			long startTime = 0;
			if (profilingEnabled) {
				entityTickProfiler = manager.entityTickProfiler;
			}
			Iterator<Entity> entitiesIterator = entitySet.iterator();
			while (entitiesIterator.hasNext()) {
				if (profilingEnabled) {
					startTime = System.nanoTime();
				}
				Entity entity = entitiesIterator.next();
				if (entity.ridingEntity != null) {
					if (!entity.ridingEntity.isDead && entity.ridingEntity.riddenByEntity == entity) {
						continue;
					}

					entity.ridingEntity.riddenByEntity = null;
					entity.ridingEntity = null;
				}

				if (!entity.isDead) {
					world.updateEntity(entity);
				}

				if (entity.isDead) {
					int entityX = entity.chunkCoordX;
					int entityZ = entity.chunkCoordZ;

					if (entity.addedToChunk && chunkProvider.chunkExists(entityX, entityZ)) {
						world.getChunkFromChunkCoords(entityX, entityZ).removeEntity(entity);
					}

					entitiesIterator.remove();
					manager.removed(entity);
					world.releaseEntitySkin(entity);
				} else if (manager.getHashCode(entity) != hashCode) {
					entitiesIterator.remove();
					manager.add(entity);
					//Log.severe("Inconsistent state: " + entity + " is in the wrong TickRegion.");
					// Note to self for when I decide this is wrong later:
					// Entities are supposed to move, of course this will happen!
				}
				if (profilingEnabled) {
					entityTickProfiler.record(entity, System.nanoTime() - startTime);
				}
			}
		} catch (Throwable throwable) {
			Log.severe("Exception during entity tick at " + toString() + ':', throwable);
		}
	}

	@Override
	protected String getShortTypeName() {
		return "E";
	}

	public void add(Entity entity) {
		synchronized (tickStateLock) {
			if (ticking) {
				toAdd.add(entity);
			} else {
				entitySet.add(entity);
			}
		}
	}

	public boolean remove(Entity entity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toRemove.add(entity);
			} else {
				return entitySet.remove(entity);
			}
		}
	}

	@Override
	public void processChanges() {
		synchronized (tickStateLock) {
			if (ticking) {
				return;
			}
			entitySet.addAll(toAdd);
			entitySet.removeAll(toRemove);
			toAdd.clear();
			toRemove.clear();
		}
	}

	@Override
	protected TickRegion getCallable(int regionX, int regionY) {
		return manager.getEntityRegion(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
	}

	@Override
	public boolean isEmpty() {
		return entitySet.isEmpty();
	}

	@Override
	public int size() {
		return entitySet.size();
	}

	@Override
	public void die() {
		super.die();
		entitySet.clear();
	}
}
