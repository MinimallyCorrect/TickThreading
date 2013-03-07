package me.nallar.patched.world.tracking;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManagerForge extends PlayerManager {
	public Object chunkWatcherLock;
	private net.minecraft.util.LongHashMap loadingPlayerInstances;
	@Declare
	public java.util.concurrent.locks.Lock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.Lock playersUpdateLock_;

	public PatchPlayerManagerForge(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	public void construct() {
		loadingPlayerInstances = new net.minecraft.util.LongHashMap();
	}

	@Override
	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

	@Override
	public PlayerInstance getOrCreateChunkWatcher(int par1, int par2, boolean par3) {
		long var4 = (long) par1 + 2147483647L | (long) par2 + 2147483647L << 32;
		PlayerInstance var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);

		if (var6 == null && (par3 || loadingPlayerInstances.containsItem(var4))) {
			synchronized (chunkWatcherLock) {
				var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);
				if (var6 == null) {
					var6 = (PlayerInstance) loadingPlayerInstances.getValueByKey(var4);
				} else {
					return var6;
				}
				if (var6 == null) {
					var6 = new PlayerInstance(this, par1, par2);
					this.loadingPlayerInstances.add(var4, var6);
				}
			}
			getWorldServer().theChunkProviderServer.loadChunk(par1, par2);
			synchronized (chunkWatcherLock) {
				if (this.loadingPlayerInstances.remove(var4) != null) {
					this.playerInstances.add(var4, var6);
				}
			}
		}

		return var6;
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

		if (this.players.isEmpty()) {
			this.theWorldServer.theChunkProviderServer.unloadAllChunks();
		}
	}
}
