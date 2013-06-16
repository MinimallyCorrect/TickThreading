package nallar.patched.server;

import nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public abstract class PatchServerConfigurationManager extends ServerConfigurationManager {
	@Override
	@Declare
	public void disconnectAllPlayers(String reason) {
		while (!this.playerEntityList.isEmpty()) {
			((EntityPlayerMP) this.playerEntityList.get(0)).playerNetServerHandler.kickPlayerFromServer(reason);
		}
	}

	public PatchServerConfigurationManager(MinecraftServer par1MinecraftServer) {
		super(par1MinecraftServer);
	}
}
