package me.nallar.patched.world.tracking;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet51MapChunk;
import net.minecraft.network.packet.Packet52MultiBlockChange;
import net.minecraft.network.packet.Packet53BlockChange;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeDummyContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

public abstract class PatchPlayerInstance extends PlayerInstance {
	private ConcurrentLinkedQueue<TileEntity> tilesToUpdate;
	private static byte[] unloadSequence;
	@Declare
	public boolean loaded_;
	private boolean watched;

	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	public static void staticConstruct() {
		unloadSequence = new byte[]{0x78, (byte) 0x9C, 0x63, 0x64, 0x1C, (byte) 0xD9, 0x00, 0x00, (byte) 0x81, (byte) 0x80, 0x01, 0x01};
	}

	public void construct() {
		tilesToUpdate = new ConcurrentLinkedQueue<TileEntity>();
		myManager.getWorldServer().theChunkProviderServer.getChunkAt(chunkLocation.chunkXPos, chunkLocation.chunkZPos, new LoadRunnable(this));
	}

	@Override
	public void addPlayerToChunkWatchingList(final EntityPlayerMP entityPlayerMP) {
		if (this.playersInChunk.contains(entityPlayerMP)) {
			throw new IllegalStateException("Failed to add player. " + entityPlayerMP + " already is in chunk " + this.chunkLocation.chunkXPos + ", " + this.chunkLocation.chunkZPos);
		} else {
			this.playersInChunk.add(entityPlayerMP);
			if (loaded) {
				Collection<ChunkCoordIntPair> loadedChunks = entityPlayerMP.loadedChunks;
				synchronized (loadedChunks) {
					loadedChunks.add(chunkLocation);
				}
			} else {
				myManager.getWorldServer().theChunkProviderServer.getChunkAt(chunkLocation.chunkXPos, chunkLocation.chunkZPos, new AddToPlayerRunnable(entityPlayerMP, chunkLocation));
			}
		}
	}

	@Override
	@Declare
	public void clearTileCount() {
		this.numberOfTilesToUpdate = 0;
	}

	@Override
	public void sendThisChunkToPlayer(EntityPlayerMP entityPlayerMP) {
		if (this.playersInChunk.remove(entityPlayerMP)) {
			Packet51MapChunk packet51MapChunk = new Packet51MapChunk();
			packet51MapChunk.includeInitialize = true;
			packet51MapChunk.xCh = chunkLocation.chunkXPos;
			packet51MapChunk.zCh = chunkLocation.chunkZPos;
			packet51MapChunk.yChMax = 0;
			packet51MapChunk.yChMin = 0;
			packet51MapChunk.setData(unloadSequence);
			entityPlayerMP.playerNetServerHandler.sendPacketToPlayer(packet51MapChunk);
			Collection<ChunkCoordIntPair> loadedChunks = entityPlayerMP.loadedChunks;
			synchronized (loadedChunks) {
				loadedChunks.remove(chunkLocation);
			}

			MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(chunkLocation, entityPlayerMP));

			if (this.playersInChunk.isEmpty()) {
				long var2 = (long) this.chunkLocation.chunkXPos + 2147483647L | (long) this.chunkLocation.chunkZPos + 2147483647L << 32;
				this.myManager.getChunkWatchers().remove(var2);

				if (numberOfTilesToUpdate > 0) {
					List chunkWatcherWithPlayers = this.myManager.getChunkWatcherWithPlayers();
					this.myManager.playerUpdateLock.lock();
					try {
						chunkWatcherWithPlayers.remove(this);
					} finally {
						this.myManager.playerUpdateLock.unlock();
					}
				}

				this.myManager.getWorldServer().theChunkProviderServer.unloadChunksIfNotNearSpawn(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos);
			}
		}
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
	public synchronized void flagChunkForUpdate(int par1, int par2, int par3) {
		int tiles = markRequiresUpdate(true);

		this.field_73260_f |= 1 << (par2 >> 4);

		short var4 = (short) (par1 << 12 | par3 << 8 | par2);
		short[] locationOfBlockChange = this.locationOfBlockChange;

		for (int var5 = 0; var5 < tiles; ++var5) {
			if (locationOfBlockChange[var5] == var4) {
				return;
			}
		}

		if (tiles == locationOfBlockChange.length) {
			this.locationOfBlockChange = locationOfBlockChange = Arrays.copyOf(locationOfBlockChange, locationOfBlockChange.length << 1);
		}
		locationOfBlockChange[tiles] = var4;
	}

	private int markRequiresUpdate(boolean increment) {
		boolean requiresWatch = false;
		int tiles = 0;
		synchronized (this) {
			if (!watched) {
				watched = requiresWatch = true;
			}
			if (increment) {
				tiles = numberOfTilesToUpdate++;
			}
		}
		if (requiresWatch) {
			List chunkWatcherWithPlayers = this.myManager.getChunkWatcherWithPlayers();
			this.myManager.playerUpdateLock.lock();
			try {
				chunkWatcherWithPlayers.add(this);
			} finally {
				this.myManager.playerUpdateLock.unlock();
			}
			numberOfTilesToUpdate++;
		}
		return tiles;
	}

	@Override
	@Declare
	public void updateTile(TileEntity tileEntity) {
		markRequiresUpdate(false);
		tilesToUpdate.add(tileEntity);
	}

	@Override
	protected void sendTileToAllPlayersWatchingChunk(TileEntity tileEntity) {
		if (tileEntity != null) {
			Packet descriptionPacket;
			try {
				descriptionPacket = tileEntity.getDescriptionPacket();
			} catch (Throwable t) {
				Log.severe("Failed to send TileEntity description for " + Log.toString(tileEntity) + " at chunk coords " + chunkLocation, t);
				return;
			}

			if (descriptionPacket != null) {
				this.sendToAllPlayersWatchingChunk(descriptionPacket);
			}
		}
	}

	@Override
	public void sendChunkUpdate() {
		sendTiles();
		synchronized (this) {
			if (this.numberOfTilesToUpdate != 0) {
				int var1;
				int var2;
				int var3;
				short[] locationOfBlockChange = this.locationOfBlockChange;

				WorldServer worldServer = this.myManager.getWorldServer();
				if (this.numberOfTilesToUpdate == 1) {
					var1 = this.chunkLocation.chunkXPos * 16 + (locationOfBlockChange[0] >> 12 & 15);
					var2 = locationOfBlockChange[0] & 255;
					var3 = this.chunkLocation.chunkZPos * 16 + (locationOfBlockChange[0] >> 8 & 15);
					this.sendToAllPlayersWatchingChunk(new Packet53BlockChange(var1, var2, var3, worldServer));

					if (worldServer.blockHasTileEntity(var1, var2, var3)) {
						this.sendTileToAllPlayersWatchingChunk(worldServer.getBlockTileEntity(var1, var2, var3));
					}
				} else {
					int var4;

					if (this.numberOfTilesToUpdate >= ForgeDummyContainer.clumpingThreshold) {
						this.sendToAllPlayersWatchingChunk(new Packet51MapChunk(worldServer.getChunkFromChunkCoords(this.chunkLocation.chunkXPos, chunkLocation.chunkZPos), false, this.field_73260_f));
					} else {
						this.sendToAllPlayersWatchingChunk(new Packet52MultiBlockChange(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos, locationOfBlockChange, this.numberOfTilesToUpdate, worldServer));
					}

					for (var1 = 0; var1 < this.numberOfTilesToUpdate; ++var1) {
						var2 = this.chunkLocation.chunkXPos * 16 + (locationOfBlockChange[var1] >> 12 & 15);
						var3 = locationOfBlockChange[var1] & 255;
						var4 = this.chunkLocation.chunkZPos * 16 + (locationOfBlockChange[var1] >> 8 & 15);

						if (worldServer.blockHasTileEntity(var2, var3, var4)) {
							this.sendTileToAllPlayersWatchingChunk(worldServer.getBlockTileEntity(var2, var3, var4));
						}
					}
				}

				this.numberOfTilesToUpdate = 0;
				this.field_73260_f = 0;
			}
			watched = false;
		}
	}

	public static class AddToPlayerRunnable implements Runnable {
		private final EntityPlayerMP entityPlayerMP;
		private final ChunkCoordIntPair chunkLocation;

		public AddToPlayerRunnable(EntityPlayerMP entityPlayerMP, ChunkCoordIntPair chunkLocation) {
			this.entityPlayerMP = entityPlayerMP;
			this.chunkLocation = chunkLocation;
		}

		@Override
		public void run() {
			Collection<ChunkCoordIntPair> loadedChunks = entityPlayerMP.loadedChunks;
			synchronized (loadedChunks) {
				loadedChunks.add(chunkLocation);
			}
		}
	}

	public static class LoadRunnable implements Runnable {
		final PlayerInstance playerInstance;

		public LoadRunnable(PlayerInstance playerInstance) {
			this.playerInstance = playerInstance;
		}

		@Override
		public void run() {
			playerInstance.loaded = true;
		}
	}
}
