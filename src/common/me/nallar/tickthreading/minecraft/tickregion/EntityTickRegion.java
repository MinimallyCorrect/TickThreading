package me.nallar.tickthreading.minecraft.tickregion;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.entity.Entity;
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
			Iterator<Entity> entitiesIterator = entitySet.iterator();
			while (entitiesIterator.hasNext()) {
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
				} else if (manager.getHashCode(entity) != hashCode) {
					entitiesIterator.remove();
					manager.add(entity);
					//Log.severe("Inconsistent state: " + entity + " is in the wrong TickRegion.");
					// Note to self for when I decide this is wrong later:
					// Entities are supposed to move, of course this will happen!
				}
			}
		} catch (Exception exception) {
			Log.severe("Exception during entity tick at " + toString() + ":", exception);
		}
	}

	public void add(Entity entity) {
		entitySet.add(entity);
	}

	public boolean remove(Entity entity) {
		return entitySet.remove(entity);
	}

	@Override
	protected TickRegion getCallable(int regionX, int regionY) {
		return manager.getEntityCallable(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
	}

	@Override
	public boolean isEmpty() {
		return entitySet.isEmpty();
	}

	@Override
	public void die() {
		super.die();
		entitySet.clear();
	}
}
