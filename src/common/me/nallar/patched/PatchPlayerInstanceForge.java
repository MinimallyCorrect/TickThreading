package me.nallar.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet51MapChunk;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

public abstract class PatchPlayerInstanceForge extends PlayerInstance {
	private static byte[] unloadSequence;

	public PatchPlayerInstanceForge(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	@Override
	@Declare
	public ChunkCoordIntPair getLocation() {
		return chunkLocation;
	}

	public static void staticConstruct() {
		unloadSequence = new byte[]{0x78, (byte) 0x9C, 0x63, 0x64, 0x1C, (byte) 0xD9, 0x00, 0x00, (byte) 0x81, (byte) 0x80, 0x01, 0x01};
	}

	@Override
	@Declare
	public void clearTileCount() {
		this.numberOfTilesToUpdate = 0;
	}

	@Override
	public void sendThisChunkToPlayer(EntityPlayerMP par1EntityPlayerMP) {
		if (this.playersInChunk.remove(par1EntityPlayerMP)) {
			Packet51MapChunk packet51MapChunk = new Packet51MapChunk();
			packet51MapChunk.includeInitialize = true;
			packet51MapChunk.xCh = chunkLocation.chunkXPos;
			packet51MapChunk.zCh = chunkLocation.chunkZPos;
			packet51MapChunk.yChMax = 0;
			packet51MapChunk.yChMin = 0;
			packet51MapChunk.setData(unloadSequence);
			par1EntityPlayerMP.playerNetServerHandler.sendPacketToPlayer(packet51MapChunk);
			par1EntityPlayerMP.loadedChunks.remove(this.chunkLocation);

			MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(chunkLocation, par1EntityPlayerMP));

			if (this.playersInChunk.isEmpty()) {
				long var2 = (long) this.chunkLocation.chunkXPos + 2147483647L | (long) this.chunkLocation.chunkZPos + 2147483647L << 32;
				this.myManager.getChunkWatchers().remove(var2);

				if (numberOfTilesToUpdate > 0) {
					this.myManager.playerUpdateLock.lock();
					try {
						this.myManager.getChunkWatcherWithPlayers().remove(this);
					} finally {
						this.myManager.playerUpdateLock.unlock();
					}
				}

				this.myManager.getWorldServer().theChunkProviderServer.unloadChunksIfNotNearSpawn(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos);
			}
		}
	}
}
