package me.nallar.patched;

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
	public volatile int teleported_;
	@Declare
	public long lastNotify_;

	public void construct() {
		teleported = 3;
		averageSpeed = -1000;
	}

	@Override
	@Declare
	public void setHasMoved() {
		this.hasMoved = true;
	}

	public PatchNetServerHandler(MinecraftServer par1, INetworkManager par2, EntityPlayerMP par3) {
		super(par1, par2, par3);
	}
}
