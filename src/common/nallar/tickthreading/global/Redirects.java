package nallar.tickthreading.global;

import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.minecraft.profiling.PacketProfiler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatMessageComponent;

public class Redirects {
	public static void exploitNotify(String message, EntityPlayerMP entityPlayerMP) {
		String fullMessage = entityPlayerMP + " attempted to use an exploit: " + message;
		Log.severe(fullMessage);
		sendToAdmins(fullMessage);
	}

	public static void notifyAdmins(String message) {
		if (!TickThreading.instance.antiCheatNotify) {
			return;
		}
		Log.warning("Admin notify: " + message);
		sendToAdmins(message);
	}

	private static void sendToAdmins(String message) {
		ServerConfigurationManager serverConfigurationManager = MinecraftServer.getServer().getConfigurationManager();
		serverConfigurationManager.playerUpdateLock.lock();
		try {
			for (Object aPlayerEntityList : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
				EntityPlayerMP var7 = (EntityPlayerMP) aPlayerEntityList;

				if (MinecraftServer.getServer().getConfigurationManager().isPlayerOpped(var7.username)) {
					var7.sendChatToPlayer(new ChatMessageComponent().addText(message));
				}
			}
		} finally {
			serverConfigurationManager.playerUpdateLock.unlock();
		}
	}

	public static boolean interceptPacket(Packet packet, NetServerHandler handler) {
		if (packet != null) {
			PacketProfiler.record(packet);
		}
		return false;
	}
}
