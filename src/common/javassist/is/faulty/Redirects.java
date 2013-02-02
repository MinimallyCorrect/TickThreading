package javassist.is.faulty;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class Redirects {
	public static void notifyAdmins(String message) {

		for (Object aPlayerEntityList : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
			EntityPlayerMP var7 = (EntityPlayerMP) aPlayerEntityList;

			if (MinecraftServer.getServer().getConfigurationManager().areCommandsAllowed(var7.username)) {
				var7.sendChatToPlayer(message);
			}
		}
	}
}
