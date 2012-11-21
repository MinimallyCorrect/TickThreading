package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickManager;
import net.minecraft.src.Entity;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;

public class EntityTickCallable<T> extends TickCallable {
	private final List<Entity> entityList = new ArrayList<Entity>();

	public EntityTickCallable(World world, String identifier, TickManager manager, int regionX, int regionZ) {
		super(world, identifier, manager, regionX, regionZ);
	}

	@Override
	public T call() {
		try {
			IChunkProvider chunkProvider = world.getChunkProvider();
			int regionSize = manager.entityRegionSize;
			for (Entity entity : entityList) {
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
					int entityY = entity.chunkCoordZ;

					if (entity.addedToChunk && chunkProvider.chunkExists(entityX, entityY)) {
						world.getChunkFromChunkCoords(entityX, entityY).removeEntity(entity);
					}

					manager.remove(entity);
				}

				if (manager.getHashCode(entity) != hashCode) {
					this.remove(entity);
					manager.add(entity);
				}
			}
		} catch (Exception exception) {
			Log.severe("Exception during entity tick at " + identifier + ":", exception);
		}
		return null;
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
