package javassist.is.faulty;

import me.nallar.tickthreading.Log;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public class Redirects {
	public static void notifyAdmins(String message) {
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
}
