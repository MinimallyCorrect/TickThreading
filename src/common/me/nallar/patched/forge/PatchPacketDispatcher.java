package me.nallar.patched.forge;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public class PatchPacketDispatcher {
	public static void sendPacketToAllAround(double X, double Y, double Z, double range, int dimensionId, Packet packet) {
		WorldServer worldServer = MinecraftServer.getServer().worldServerForDimension(dimensionId);
		int x = MathHelper.floor_double(X);
		int z = MathHelper.floor_double(Z);
		PlayerInstance playerInstance = worldServer.getPlayerManager().getOrCreateChunkWatcher(x >> 4, z >> 4, false);
		if (playerInstance != null) {
			range *= range;
			synchronized (playerInstance) {
				for (EntityPlayerMP entityPlayerMP : (Iterable<EntityPlayerMP>) playerInstance.playersInChunk) {
					double xD = X - entityPlayerMP.posX;
					double yD = Y - entityPlayerMP.posY;
					double zD = Z - entityPlayerMP.posZ;

					if (xD * xD + yD * yD + zD * zD < range) {
						entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(packet);
					}
				}
			}
		}
	}
}
