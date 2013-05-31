package me.nallar.patched;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.NativeMutex;
import net.minecraft.block.Block;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInvBasic;
import net.minecraft.inventory.IInventory;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.liquids.ILiquidTank;
import net.minecraftforge.liquids.ITankContainer;

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
	public final java.util.concurrent.locks.Lock lockManagementLock_ = null;
	@Declare
	public final java.util.concurrent.locks.Lock thisLock_ = null;
	@Declare
	public volatile java.util.concurrent.locks.Lock xMinusLock_;
	@Declare
	public volatile java.util.concurrent.locks.Lock zMinusLock_;
	@Declare
	public volatile java.util.concurrent.locks.Lock xPlusLock_;
	@Declare
	public volatile java.util.concurrent.locks.Lock zPlusLock_;
	@Declare
	public byte usedLocks_;

	public void construct() {
		lockManagementLock = new NativeMutex();
		if (this instanceof IInventory || this instanceof ILiquidTank || this instanceof ICrafting || this instanceof IInvBasic || this instanceof ITankContainer) {
			thisLock = new NativeMutex();
		} else {
			thisLock = null;
		}
	}

	@Override
	@Declare
	public void sendTileWithNotify() {
		WorldServer worldServer = ((WorldServer) worldObj);
		if (worldServer == null) {
			return;
		}
		Block blockType = getBlockType();
		worldServer.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, blockType == null ? worldServer.getBlockId(xCoord, yCoord, zCoord) : blockType.blockID);
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

	@Override
	public String toString() {
		return Log.classString(this) + '@' + System.identityHashCode(this) + " in " + Log.name(worldObj) + " at x, y, z: " + xCoord + ", " + yCoord + ", " + zCoord;
	}
}
