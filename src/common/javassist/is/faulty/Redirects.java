package javassist.is.faulty;

import com.google.common.hash.Hashing;

import cpw.mods.fml.common.network.Player;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.network.packet.Packet9Respawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class Redirects {
	private static final int FML_HASH = Hashing.murmur3_32().hashString("FML").asInt();

	public static void notifyAdmins(String message) {
		if (!TickThreading.instance.antiCheatNotify) {
			return;
		}
		Log.warning("Admin notify: " + message);
		ServerConfigurationManager serverConfigurationManager = MinecraftServer.getServer().getConfigurationManager();
		serverConfigurationManager.playerUpdateLock.lock();
		try {
			for (Object aPlayerEntityList : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
				EntityPlayerMP var7 = (EntityPlayerMP) aPlayerEntityList;

				if (MinecraftServer.getServer().getConfigurationManager().areCommandsAllowed(var7.username)) {
					var7.sendChatToPlayer(message);
				}
			}
		} finally {
			serverConfigurationManager.playerUpdateLock.unlock();
		}
	}

	public static boolean interceptPacket(Packet packet, NetServerHandler handler) {
		if (packet instanceof Packet9Respawn) {
			Packet9Respawn packet9Respawn = (Packet9Respawn) packet;
			int dimension = packet9Respawn.respawnDimension;
			World world = DimensionManager.getWorld(dimension);
			if (world.multiverseWorld) {
				handler.clientDimension = packet9Respawn.respawnDimension = world.originalDimension;
			} else {
				handler.clientDimension = packet9Respawn.respawnDimension;
			}
		} else if (packet instanceof Packet1Login) {
			Packet1Login packet1Login = (Packet1Login) packet;
			if (packet1Login.clientEntityId != FML_HASH) {
				int dimension = packet1Login.dimension;
				World world = DimensionManager.getWorld(dimension);
				if (world.multiverseWorld) {
					handler.clientDimension = packet1Login.dimension = world.originalDimension;
				} else {
					handler.clientDimension = packet1Login.dimension;
				}
			}
		}
		return false;
	}

	// announceBlockUpdate,initiateTileEntityEvent,initiateExplosionEffect
	public static void interceptIC2Packet(Packet packet, Player player) {
		if (player instanceof EntityPlayerMP) {
			NetServerHandler netServerHandler = ((EntityPlayerMP) player).playerNetServerHandler;
			Packet250CustomPayload packet250CustomPayload = (Packet250CustomPayload) packet;
			byte[] data = packet250CustomPayload.data;
			byte type = data[0];
			switch (type) {
				case 1:
				case 3:
				case 5:
					setInt(data, 1, netServerHandler.clientDimension);
					break;
			}
			netServerHandler.sendPacketToPlayer(packet);
		}
	}

	public static void interceptIC2PacketIn(INetworkManager manager, Packet250CustomPayload packet, Player player) {
		if (!(player instanceof EntityPlayerMP)) {
			return;
		}
		int dimension = ((EntityPlayerMP) player).playerNetServerHandler.clientDimension;
		byte[] data = packet.data;
		byte type = data[0];
		if (type == 0 || type == 3) {
			setInt(data, 1, dimension);
		}
	}

	private static void setInt(byte[] array, int index, int value) {
		array[index++] = (byte) ((value >>> 24) & 0xFF);
		array[index++] = (byte) ((value >>> 16) & 0xFF);
		array[index++] = (byte) ((value >>> 8) & 0xFF);
		array[index] = (byte) ((value) & 0xFF);
	}
}
