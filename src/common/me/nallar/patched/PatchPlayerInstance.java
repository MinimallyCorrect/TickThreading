package me.nallar.patched;

import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;

public abstract class PatchPlayerInstance extends PlayerInstance {
	private List<TileEntity> tilesToUpdate;
	private static java.lang.reflect.Method getChunkWatcherWithPlayers;

	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	public void construct() {
		tilesToUpdate = new ArrayList<TileEntity>();
	}

	public void sendTiles() {
		for (TileEntity tileEntity : tilesToUpdate) {
			sendTileToAllPlayersWatchingChunk(tileEntity);
		}
		tilesToUpdate.clear();
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
		if (!tilesToUpdate.contains(tileEntity)) {
			tilesToUpdate.add(tileEntity);
		}
	}
}
