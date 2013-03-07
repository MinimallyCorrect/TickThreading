package me.nallar.patched.network;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.Packet13PlayerLookMove;
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
	@Declare
	public double tpPosX_;
	@Declare
	public double tpPosY_;
	private double tpPosZ;

	public void construct() {
		teleported = 10;
		averageSpeed = -1000;
	}

	@Override
	@Declare
	public void setHasMoved() {
		this.hasMoved = true;
	}

	@Override
	public synchronized void setPlayerLocation(double x, double y, double z, float rotationYaw, float rotationPitch) {
		teleported = 20;
		this.hasMoved = false;
		this.lastPosX = tpPosX = x;
		this.lastPosY = tpPosY = y;
		this.lastPosZ = tpPosZ = z;
		this.playerEntity.setPositionAndRotation(x, y, z, rotationYaw, rotationPitch);
		this.sendPacketToPlayer(new Packet13PlayerLookMove(x, y + 1.6200000047683716D, y, z, rotationYaw, rotationPitch, false));
	}

	@Override
	@Declare
	public void updatePositionAfterTP() {
		if (Double.isNaN(tpPosX)) {
			return;
		}
		float rotationYaw = playerEntity.rotationYaw;
		float rotationPitch = playerEntity.rotationPitch;
		double x = tpPosX;
		double y = tpPosY;
		double z = tpPosZ;
		this.lastPosX = x;
		this.lastPosY = y;
		this.lastPosZ = z;
		this.playerEntity.setPositionAndRotation(x, y, z, rotationYaw, rotationPitch);
		this.sendPacketToPlayer(new Packet13PlayerLookMove(x, y + 1.6200000047683716D, y, z, rotationYaw, rotationPitch, false));
	}

	public PatchNetServerHandler(MinecraftServer par1, INetworkManager par2, EntityPlayerMP par3) {
		super(par1, par2, par3);
	}
}
