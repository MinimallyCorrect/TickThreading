package javassist.is.faulty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.unsafe.UnsafeUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public class Redirects {
	private static ThreadLocal<MessageDigest> messageDigestThreadLocal = new ThreadLocal<MessageDigest>() {
		@Override
		protected MessageDigest initialValue() {
			try {
				return MessageDigest.getInstance("MD-5");
			} catch (NoSuchAlgorithmException e) {
				throw UnsafeUtil.throwIgnoreChecked(e);
			}
		}
	};

	public static String md5Hash(String toHash) {
		byte[] md5 = messageDigestThreadLocal.get().digest(toHash.getBytes());
		StringBuilder hash = new StringBuilder();
		for (byte b : md5) {
			if (b >= 0) {
				hash.append(b);
			} else {
				hash.append(-b);
			}
		}
		return hash.toString();
	}

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
}
