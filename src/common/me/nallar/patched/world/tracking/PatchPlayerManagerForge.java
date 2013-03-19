package me.nallar.patched.world.tracking;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManagerForge extends PlayerManager {
	private int unloadAllChunksCounter;
	@Declare
	public java.util.concurrent.locks.Lock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.Lock playersUpdateLock_;

	public PatchPlayerManagerForge(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	@Override
	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

	@Override
	public void updatePlayerInstances() {
		playersUpdateLock.lock();
		try {
			for (Object chunkWatcherWithPlayer : this.chunkWatcherWithPlayers) {
				if (chunkWatcherWithPlayer instanceof PlayerInstance) {
					try {
						((PlayerInstance) chunkWatcherWithPlayer).sendChunkUpdate();
					} catch (Exception e) {
						Log.severe("Failed to send " + chunkWatcherWithPlayer, e);
						((PlayerInstance) chunkWatcherWithPlayer).clearTileCount();
					}
				}
			}
			this.chunkWatcherWithPlayers.clear();
		} finally {
			playersUpdateLock.unlock();
		}

		if (unloadAllChunksCounter++ % 300 == 0 && this.players.isEmpty()) {
			this.theWorldServer.theChunkProviderServer.unloadAllChunks();
		}
	}
}
