package me.nallar.patched.world.tracking;

import me.nallar.collections.ConcurrentLinkedQueueList;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManager extends PlayerManager {
	public Object chunkWatcherLock;
	private ConcurrentLinkedQueueList<PlayerInstance> chunkWatcherWithPlayersQ;

	public void construct() {
		chunkWatcherLock = new Object();
		try {
			chunkWatcherWithPlayers = chunkWatcherWithPlayersQ = new ConcurrentLinkedQueueList<PlayerInstance>();
		} catch (NoSuchFieldError e) {
			// MCPC+.
		}
	}

	private int unloadAllChunksCounter;

	@Override
	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

	@Override
	public void updatePlayerInstances() {
		PlayerInstance playerInstance;
		while ((playerInstance = chunkWatcherWithPlayersQ.poll()) != null) {
			try {
				playerInstance.sendChunkUpdate();
			} catch (Exception e) {
				Log.severe("Failed to send " + playerInstance, e);
				playerInstance.clearTileCount();
			}
		}

		if (unloadAllChunksCounter++ % 300 == 0 && this.players.isEmpty()) {
			this.theWorldServer.theChunkProviderServer.unloadAllChunks();
		}
	}

	@Override
	public PlayerInstance getOrCreateChunkWatcher(int par1, int par2, boolean par3) {
		long var4 = (long) par1 + 2147483647L | (long) par2 + 2147483647L << 32;
		PlayerInstance var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);

		if (var6 == null && par3) {
			synchronized (chunkWatcherLock) {
				var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);
				if (var6 == null) {
					var6 = new PlayerInstance(this, par1, par2);
					this.playerInstances.add(var4, var6);
				}
			}
		}

		return var6;
	}

	public PatchPlayerManager(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	@Override
	@Declare
	public java.util.Queue<PlayerInstance> getChunkWatcherWithPlayers() {
		return this.chunkWatcherWithPlayersQ;
	}
}
