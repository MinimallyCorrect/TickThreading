package me.nallar.tickthreading.minecraft.patched;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;

public abstract class PatchPlayerManager extends PlayerManager {
	public Object chunkWatcherLock;
	public Object chunkUpdateLock;
	@Declare
	public java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock playersUpdateLock_;

	public void construct() {
		chunkWatcherLock = new Object();
		chunkUpdateLock = new Object();
		ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
		playersUpdateLock = reentrantReadWriteLock.writeLock();
		playerUpdateLock = reentrantReadWriteLock.readLock();
	}

	public PatchPlayerManager(WorldServer par1WorldServer, int par2) {
		super(par1WorldServer, par2);
	}

	@Declare
	public net.minecraft.util.LongHashMap getChunkWatchers() {
		return this.playerInstances;
	}

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

	@Override
	public void filterChunkLoadQueue(EntityPlayerMP par1EntityPlayerMP) {
		synchronized (par1EntityPlayerMP.loadedChunks) {
			ArrayList var2 = new ArrayList(par1EntityPlayerMP.loadedChunks);
			int var3 = 0;
			int var4 = this.playerViewRadius;
			int var5 = (int) par1EntityPlayerMP.posX >> 4;
			int var6 = (int) par1EntityPlayerMP.posZ >> 4;
			int var7 = 0;
			int var8 = 0;
			PlayerInstance playerInstance = this.getOrCreateChunkWatcher(var5, var6, true);
			ChunkCoordIntPair var9 = playerInstance.getLocation();
			par1EntityPlayerMP.loadedChunks.clear();

			if (var2.contains(var9)) {
				par1EntityPlayerMP.loadedChunks.add(var9);
			}

			int var10;

			for (var10 = 1; var10 <= var4 * 2; ++var10) {
				for (int var11 = 0; var11 < 2; ++var11) {
					int[] var12 = this.xzDirectionsConst[var3++ % 4];

					for (int var13 = 0; var13 < var10; ++var13) {
						var7 += var12[0];
						var8 += var12[1];
						var9 = this.getOrCreateChunkWatcher(var5 + var7, var6 + var8, true).getLocation();

						if (var2.contains(var9)) {
							par1EntityPlayerMP.loadedChunks.add(var9);
						}
					}
				}
			}

			var3 %= 4;

			for (var10 = 0; var10 < var4 * 2; ++var10) {
				var7 += this.xzDirectionsConst[var3][0];
				var8 += this.xzDirectionsConst[var3][1];
				var9 = this.getOrCreateChunkWatcher(var5 + var7, var6 + var8, true).getLocation();

				if (var2.contains(var9)) {
					par1EntityPlayerMP.loadedChunks.add(var9);
				}
			}
		}
	}

	@Override
	public void addPlayer(EntityPlayerMP par1EntityPlayerMP) {
		synchronized (par1EntityPlayerMP.loadedChunks) {
			int var2 = (int) par1EntityPlayerMP.posX >> 4;
			int var3 = (int) par1EntityPlayerMP.posZ >> 4;
			par1EntityPlayerMP.managedPosX = par1EntityPlayerMP.posX;
			par1EntityPlayerMP.managedPosZ = par1EntityPlayerMP.posZ;

			for (int var4 = var2 - this.playerViewRadius; var4 <= var2 + this.playerViewRadius; ++var4) {
				for (int var5 = var3 - this.playerViewRadius; var5 <= var3 + this.playerViewRadius; ++var5) {
					this.getOrCreateChunkWatcher(var4, var5, true).addPlayerToChunkWatchingList(par1EntityPlayerMP);
				}
			}
		}
		this.filterChunkLoadQueue(par1EntityPlayerMP);
		synchronized (this.players) {
			this.players.add(par1EntityPlayerMP);
		}
	}

	@Override
	public void removePlayer(EntityPlayerMP par1EntityPlayerMP) {
		synchronized (par1EntityPlayerMP.loadedChunks) {
			int var2 = (int) par1EntityPlayerMP.managedPosX >> 4;
			int var3 = (int) par1EntityPlayerMP.managedPosZ >> 4;

			for (int var4 = var2 - this.playerViewRadius; var4 <= var2 + this.playerViewRadius; ++var4) {
				for (int var5 = var3 - this.playerViewRadius; var5 <= var3 + this.playerViewRadius; ++var5) {
					PlayerInstance var6 = this.getOrCreateChunkWatcher(var4, var5, false);

					if (var6 != null) {
						var6.sendThisChunkToPlayer(par1EntityPlayerMP);
					}
				}
			}
		}

		synchronized (this.players) {
			this.players.remove(par1EntityPlayerMP);
		}
	}

	@Override
	public void updatePlayerInstances() {
		playersUpdateLock.lock();
		try {
			for (Object chunkWatcherWithPlayer : this.chunkWatcherWithPlayers) {
				if (chunkWatcherWithPlayer instanceof PlayerInstance) {
					((PlayerInstance) chunkWatcherWithPlayer).sendChunkUpdate();
				}
			}
		} catch (Exception e) {
			Log.severe("Failed to send some chunk updates", e);
		} finally {
			playersUpdateLock.unlock();
		}

		this.chunkWatcherWithPlayers.clear();

		if (this.players.isEmpty()) {
			this.theWorldServer.theChunkProviderServer.unloadAllChunks();
		}
	}
}
