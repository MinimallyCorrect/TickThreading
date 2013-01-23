package me.nallar.tickthreading.minecraft.patched;

import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManager extends PlayerManager {
	public Object chunkWatcherLock;

	public void construct() {
		chunkWatcherLock = new Object();
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
		boolean needsLoaded = false;

		if (var6 == null && par3) {
			synchronized (chunkWatcherLock) {
				var6 = (PlayerInstance) this.playerInstances.getValueByKey(var4);
				if (var6 == null) {
					var6 = new PlayerInstance(this, par1, par2);
					this.playerInstances.add(var4, var6);
					needsLoaded = true;
				}
			}
		}

		if (needsLoaded) {
			getWorldServer().theChunkProviderServer.loadChunk(par1, par2);
		}

		return var6;
	}
}
