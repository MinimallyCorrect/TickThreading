package me.nallar.tickthreading.minecraft.tickthread;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.Entity;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.World;

public class EntityTickThread extends TickThread {
	private final List<Entity> entityList = new ArrayList<Entity>();

	public EntityTickThread(World world, String identifier, ThreadManager manager, int hashCode) {
		super(world, identifier, manager, hashCode);
	}

	@Override
	public void run() {
		try {
			Log.fine("Started entity tick thread");
			while (manager.waitForEntityTick()) {
				IChunkProvider chunkProvider = world.getChunkProvider();
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
						//Entity skin not removed - this mod only runs on the server.
					}

					if (manager.getHashCode(entity) != hashCode) {
						manager.remove(entity);
						manager.add(entity);
					}
				}
				manager.endEntityTick();
			}
		} catch (Exception exception) {
			Log.severe("Exception in entity tick thread " + identifier + ":", exception);
		}
		entityList.clear();
	}

	public void add(Entity entity) {
		if (!entityList.contains(entity)) {
			entityList.add(entity);
		}
	}

	public boolean remove(Entity entity) {
		return entityList.remove(entity);
	}
}
