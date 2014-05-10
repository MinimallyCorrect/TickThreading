package nallar.patched;

import nallar.tickthreading.Log;
import nallar.tickthreading.patcher.Declare;
import net.minecraft.block.Block;
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
	public volatile nallar.tickthreading.minecraft.tickregion.TileEntityTickRegion tickRegion_;

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
