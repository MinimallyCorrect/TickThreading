package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import me.nallar.tickthreading.minecraft.profiling.EntityTickProfiler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

public class EntityTickRegion extends TickRegion {
	private final Set<Entity> entitySet = new LinkedHashSet<Entity>();

	public EntityTickRegion(World world, TickManager manager, int regionX, int regionZ) {
		super(world, manager, regionX, regionZ);
	}

	@Override
	public void doTick() {
		ChunkProviderServer chunkProvider = (ChunkProviderServer) world.getChunkProvider();
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
			try {
				Entity ridingEntity = entity.ridingEntity;
				if (ridingEntity != null) {
					if (!ridingEntity.isDead && ridingEntity.riddenByEntity == entity) {
						continue;
					}

					ridingEntity.riddenByEntity = null;
					entity.ridingEntity = null;
				}

				if (!entity.isDead) {
					if (entity instanceof EntityPlayerMP) {
						synchronized (((EntityPlayerMP) entity).playerNetServerHandler) {
							world.updateEntity(entity);
						}
					} else {
						world.updateEntity(entity);
					}
				}

				if (entity.isDead) {
					int entityX = entity.chunkCoordX;
					int entityZ = entity.chunkCoordZ;

					if (entity.addedToChunk) {
						Chunk chunk = chunkProvider.getChunkIfExists(entityX, entityZ);
						if (chunk != null) {
							chunk.removeEntity(entity);
						}
					}

					entitiesIterator.remove();
					manager.removed(entity);
					world.releaseEntitySkin(entity);
				} else if (manager.getHashCode(entity) != hashCode) {
					entitiesIterator.remove();
					manager.add(entity, false);
					//Log.severe("Inconsistent state: " + entity + " is in the wrong TickRegion.");
					// Note to self for when I decide this is wrong later:
					// Entities are supposed to move, of course this will happen!
				}
			} catch (Throwable throwable) {
				Log.severe("Exception ticking entity " + entity + " in " + toString() + '/' + Log.name(entity.worldObj) + ':', throwable);
				if (entity.worldObj != world) {
					Log.severe("Seems to be caused by an entity being in a broken state, set to an impossible/incorrect world. Killing this entity.");
					entity.setDead();
				}
			}
			if (profilingEnabled) {
				entityTickProfiler.record(entity, System.nanoTime() - startTime);
			}
		}
	}

	@Override
	protected String getShortTypeName() {
		return "E";
	}

	public boolean add(Entity entity) {
		synchronized (tickStateLock) {
			if (ticking) {
				return toAdd.add(entity) && !entitySet.contains(entity);
			} else {
				return entitySet.add(entity);
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
