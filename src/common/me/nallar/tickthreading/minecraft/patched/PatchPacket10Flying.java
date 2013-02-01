package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.Log;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet10Flying;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public abstract class PatchPacket10Flying extends Packet10Flying {
	@Override
	public void processPacket(NetHandler par1NetHandler) {
		if (false && moving && yPosition != -999.0D && stance != -999.0D && par1NetHandler instanceof NetServerHandler) {
			long currentTime = System.currentTimeMillis();
			NetServerHandler nsh = (NetServerHandler) par1NetHandler;
			EntityPlayerMP entityPlayerMP = nsh.playerEntity;
			long time = Math.min(5000, currentTime - nsh.lastMovement);
			nsh.lastMovement = currentTime;
			if (time < 0) {
				time = 1000;
			}
			double dX = (xPosition - nsh.lastPX);
			double dZ = (zPosition - nsh.lastPZ);
			double speed = (Math.sqrt(dX*dX + dZ*dZ) * 1000) / time;
			double averageSpeed = nsh.averageSpeed;
			if (averageSpeed == -1) {
				speed = 0;
			}
			averageSpeed = (nsh.averageSpeed = ((nsh.averageSpeed * 2 + speed) / 3));
			ServerConfigurationManager serverConfigurationManager = MinecraftServer.getServer().getConfigurationManager();
			if (!serverConfigurationManager.areCommandsAllowed(entityPlayerMP.username) && (averageSpeed > 50 || (!entityPlayerMP.isRiding() && averageSpeed > 20))) {
				nsh.kickPlayerFromServer("Stop cheating.");
				serverConfigurationManager.sendPacketToAllPlayers(new Packet3Chat(entityPlayerMP.username + " was caught speed-hacking or has a terrible connection"));
			}
			nsh.lastPZ = this.zPosition;
			nsh.lastPX = this.xPosition;
			Log.info(String.valueOf(averageSpeed));
		}
		par1NetHandler.handleFlying(this);
	}
}
