package me.nallar.patched.server;

import cpw.mods.fml.common.registry.GameRegistry;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet41EntityEffect;
import net.minecraft.network.packet.Packet9Respawn;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

public abstract class PatchServerConfigurationManagerForge extends ServerConfigurationManager {
	@Declare
	public java.util.concurrent.locks.Lock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.Lock playersUpdateLock_;

	@Override
	@Declare
	public void disconnectAllPlayers(String reason) {
		while (!this.playerEntityList.isEmpty()) {
			((EntityPlayerMP) this.playerEntityList.get(0)).playerNetServerHandler.kickPlayerFromServer(reason);
		}
	}

	@Override
	public void transferPlayerToDimension(EntityPlayerMP entityPlayerMP, int toDimensionId, Teleporter teleporter) {
		WorldServer fromDimension;
		WorldServer toDimension;
		synchronized (entityPlayerMP.playerNetServerHandler) {
			synchronized (entityPlayerMP.loadedChunks) {
				int fromDimensionId = entityPlayerMP.dimension;
				fromDimension = this.mcServer.worldServerForDimension(fromDimensionId);
				entityPlayerMP.dimension = toDimensionId;
				toDimension = this.mcServer.worldServerForDimension(toDimensionId);
				Log.info(entityPlayerMP + " changed dimension from " + Log.name(fromDimension) + " to " + Log.name(toDimension));
				if (fromDimension == toDimension) {
					if (!toDimension.playerEntities.contains(entityPlayerMP)) {
						toDimension.spawnEntityInWorld(entityPlayerMP);
					}
					Log.severe("Can't transfer player to the dimension they are already in! " + entityPlayerMP + ", dimension: " + Log.name(toDimension));
					return;
				}
				entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(new Packet9Respawn(entityPlayerMP.dimension, (byte) entityPlayerMP.worldObj.difficultySetting, toDimension.getWorldInfo().getTerrainType(), toDimension.getHeight(), entityPlayerMP.theItemInWorldManager.getGameType()));
				fromDimension.playerEntities.remove(entityPlayerMP);
				fromDimension.loadedEntityList.remove(entityPlayerMP);
				fromDimension.releaseEntitySkin(entityPlayerMP);
				fromDimension.updateAllPlayersSleepingFlag();
				int x = entityPlayerMP.chunkCoordX;
				int z = entityPlayerMP.chunkCoordZ;
				if (entityPlayerMP.addedToChunk && fromDimension.getChunkProvider().chunkExists(x, z)) {
					fromDimension.getChunkFromChunkCoords(x, z).removeEntity(entityPlayerMP);
				}
				this.transferEntityToWorld(entityPlayerMP, fromDimensionId, fromDimension, toDimension, teleporter);
				entityPlayerMP.setWorld(toDimension);
				toDimension.theChunkProviderServer.loadChunk((int) entityPlayerMP.posX >> 4, (int) entityPlayerMP.posZ >> 4);
				if (!toDimension.playerEntities.contains(entityPlayerMP)) {
					toDimension.spawnEntityInWorld(entityPlayerMP);
				}
				entityPlayerMP.playerNetServerHandler.setPlayerLocation(entityPlayerMP.posX, entityPlayerMP.posY, entityPlayerMP.posZ, entityPlayerMP.rotationYaw, entityPlayerMP.rotationPitch);
				entityPlayerMP.theItemInWorldManager.setWorld(toDimension);
				this.updateTimeAndWeatherForPlayer(entityPlayerMP, toDimension);
				this.syncPlayerInventory(entityPlayerMP);

				for (PotionEffect potionEffect : (Iterable<PotionEffect>) entityPlayerMP.getActivePotionEffects()) {
					entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(new Packet41EntityEffect(entityPlayerMP.entityId, potionEffect));
				}

				GameRegistry.onPlayerChangedDimension(entityPlayerMP);
			}
			fromDimension.getPlayerManager().removePlayer(entityPlayerMP);
			toDimension.getPlayerManager().removePlayer(entityPlayerMP);
			toDimension.getPlayerManager().addPlayer(entityPlayerMP);
		}
	}

	public PatchServerConfigurationManagerForge(MinecraftServer par1MinecraftServer) {
		super(par1MinecraftServer);
	}
}
