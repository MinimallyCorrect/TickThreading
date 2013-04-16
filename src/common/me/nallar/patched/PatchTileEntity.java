package me.nallar.patched;

import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.NativeMutex;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

public abstract class PatchTileEntity extends TileEntity {
	@Declare
	public int lastTTX_;
	@Declare
	public int lastTTY_;
	@Declare
	public int lastTTZ_;
	@Declare
	public me.nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion tickRegion_;
	@Declare
	public java.util.concurrent.locks.Lock thisLock_;
	@Declare
	public java.util.concurrent.locks.Lock xMinusLock_;
	@Declare
	public java.util.concurrent.locks.Lock zMinusLock_;
	@Declare
	public java.util.concurrent.locks.Lock xPlusLock_;
	@Declare
	public java.util.concurrent.locks.Lock zPlusLock_;

	public void construct() {
		thisLock = new NativeMutex();
	}

	@Override
	@Declare
	public void sendTileWithNotify() {
		WorldServer worldServer = ((WorldServer) worldObj);
		worldServer.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, getBlockType().blockID);
		PlayerInstance p = worldServer.getPlayerManager().getOrCreateChunkWatcher(xCoord >> 4, zCoord >> 4, false);
		if (p != null) {
			p.updateTile(this);
		}
	}

	@Override
	@Declare
	public void sendTile() {
		PlayerInstance p = ((WorldServer) worldObj).getPlayerManager().getOrCreateChunkWatcher(xCoord >> 4, zCoord >> 4, false);
		if (p != null) {
			p.updateTile(this);
		}
	}
}
