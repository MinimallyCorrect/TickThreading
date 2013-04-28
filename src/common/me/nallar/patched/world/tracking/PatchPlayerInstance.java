package me.nallar.patched.world.tracking;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.network.packet.Packet51MapChunk;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;

public abstract class PatchPlayerInstance extends PlayerInstance {
	private ConcurrentLinkedQueue<TileEntity> tilesToUpdate;

	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	@Override
	public String toString() {
		return chunkLocation + " watched by " + Arrays.toString(playersInChunk.toArray());
	}

	@Override
	@Declare
	public void forceUpdate() {
		this.sendToAllPlayersWatchingChunk(new Packet51MapChunk(myManager.getWorldServer().getChunkFromChunkCoords(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos), true, Integer.MAX_VALUE));
	}

	public void construct() {
		tilesToUpdate = new ConcurrentLinkedQueue<TileEntity>();
	}

	public void sendTiles() {
		HashSet<TileEntity> tileEntities = new HashSet<TileEntity>();
		for (TileEntity tileEntity = tilesToUpdate.poll(); tileEntity != null; tileEntity = tilesToUpdate.poll()) {
			tileEntities.add(tileEntity);
		}
		for (TileEntity tileEntity : tileEntities) {
			this.sendTileToAllPlayersWatchingChunk(tileEntity);
		}
		tileEntities.clear();
	}

	@Override
	@Declare
	public void updateTile(TileEntity tileEntity) {
		if (numberOfTilesToUpdate == 0) {
			this.myManager.playerUpdateLock.lock();
			try {
				this.myManager.getChunkWatcherWithPlayers().add(this);
			} finally {
				this.myManager.playerUpdateLock.unlock();
			}
			numberOfTilesToUpdate++;
		}
		tilesToUpdate.add(tileEntity);
	}
}
