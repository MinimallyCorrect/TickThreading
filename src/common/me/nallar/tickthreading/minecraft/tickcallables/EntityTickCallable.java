package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

public class EntityTickCallable<T> extends TickCallable {
	private final List<Entity> entityList = new ArrayList<Entity>();

	public EntityTickCallable(World world, TickManager manager, int regionX, int regionZ) {
		super(world, manager, regionX, regionZ);
	}

	@Override
	public void doTick() {
		try {
			IChunkProvider chunkProvider = world.getChunkProvider();
			Iterator<Entity> entitiesIterator = entityList.iterator();
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
					//Log.severe("Inconsistent state: " + entity + " is in the wrong TickCallable.");
				}
			}
		} catch (Exception exception) {
			Log.severe("Exception during entity tick at " + toString() + ":", exception);
		}
	}

	public void add(Entity entity) {
		if (!entityList.contains(entity)) {
			entityList.add(entity);
		}
	}

	public boolean remove(Entity entity) {
		return entityList.remove(entity);
	}

	@Override
	protected TickCallable getCallable(int regionX, int regionY) {
		return manager.getEntityCallable(TickManager.getHashCodeFromRegionCoords(regionX, regionY));
	}

	@Override
	public boolean isEmpty() {
		return entityList.isEmpty();
	}
}
