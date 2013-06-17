package nallar.patched.forge;

import nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public abstract class PatchPacketDispatcher {
	private static int square(int a) {
		return a * a;
	}

	@Declare
	public static void sendPacketAroundPlayer(EntityPlayerMP player, Packet packet, int squaredDistance) {
		EntityPlayerMP playerMP = (EntityPlayerMP) player;
		WorldServer worldServer = ((WorldServer) playerMP.worldObj);
		int cX = playerMP.chunkCoordX;
		int cZ = playerMP.chunkCoordZ;
		for (EntityPlayerMP entityPlayerMP : (Iterable<EntityPlayerMP>) worldServer.playerEntities) {
			if (entityPlayerMP != playerMP && (square((entityPlayerMP.chunkCoordX - cX) << 4) + square((entityPlayerMP.chunkCoordZ - cZ) << 4)) < squaredDistance) {
				entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(packet);
			}
		}
	}

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
