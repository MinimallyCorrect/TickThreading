package me.nallar.patched;

import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManager extends PlayerManager {
	public Object chunkWatcherLock;
	private net.minecraft.util.LongHashMap loadingPlayerInstances;

	public void construct() {
		chunkWatcherLock = new Object();
		loadingPlayerInstances = new net.minecraft.util.LongHashMap();
	}

	public PatchPlayerManager(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	@Override
	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

	@Override
	@Declare
	public List getChunkWatcherWithPlayers() {
		return this.chunkWatcherWithPlayers;
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
}
