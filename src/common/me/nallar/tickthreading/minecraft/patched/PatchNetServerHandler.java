package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetServerHandler;
import net.minecraft.server.MinecraftServer;

public abstract class PatchNetServerHandler extends NetServerHandler {
	@Declare
	public double averageSpeed_;
	@Declare
	public long lastMovement_;
	@Declare
	public double lastPX_;
	@Declare
	public double lastPZ_;
	@Declare
	public boolean teleported_;

	public void construct() {
		teleported = true;
	}

	public PatchNetServerHandler(MinecraftServer par1, INetworkManager par2, EntityPlayerMP par3) {
		super(par1, par2, par3);
	}
}
