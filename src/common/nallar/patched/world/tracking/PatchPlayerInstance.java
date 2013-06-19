package nallar.patched.world.tracking;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import nallar.patched.annotation.Public;
import nallar.tickthreading.Log;
import nallar.tickthreading.minecraft.TickThreading;
import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.ChunkLoadRunnable;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet51MapChunk;
import net.minecraft.network.packet.Packet52MultiBlockChange;
import net.minecraft.network.packet.Packet53BlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeDummyContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

@Public
public abstract class PatchPlayerInstance extends PlayerInstance {
	private ConcurrentLinkedQueue<TileEntity> tilesToUpdate;
	private static final boolean rateLimitChunkUpdates = TickThreading.instance.rateLimitChunkUpdates;
	private static final byte[] unloadSequence = {0x78, (byte) 0x9C, 0x63, 0x64, 0x1C, (byte) 0xD9, 0x00, 0x00, (byte) 0x81, (byte) 0x80, 0x01, 0x01};
	@Declare
	public boolean loaded_;
	private boolean watched;
	@Declare
	public net.minecraft.world.chunk.Chunk chunk_;
	private int startTime;
	private int sentUpdates;

	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	public void construct() {
		tilesToUpdate = new ConcurrentLinkedQueue<TileEntity>();
		thePlayerManager.getWorldServer().theChunkProviderServer.getChunkAt(chunkLocation.chunkXPos, chunkLocation.chunkZPos, new LoadRunnable(this));
		startTime = MinecraftServer.currentTick + 30;
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
				thePlayerManager.getWorldServer().theChunkProviderServer.getChunkAt(chunkLocation.chunkXPos, chunkLocation.chunkZPos, new AddToPlayerRunnable(entityPlayerMP, chunkLocation));
			}
		}
	}

	@Override
	@Declare
	public synchronized void clearTileCount() {
		this.numberOfTilesToUpdate = 0;
		this.field_73260_f = 0;
		this.watched = false;
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
				this.thePlayerManager.getChunkWatchers().remove(var2);

				if (watched) {
					this.thePlayerManager.getChunkWatcherWithPlayers().remove(this);
				}

				this.thePlayerManager.getWorldServer().theChunkProviderServer.unloadChunksIfNotNearSpawn(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos);
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
		this.sendToAllPlayersWatchingChunk(new Packet51MapChunk(thePlayerManager.getWorldServer().getChunkFromChunkCoords(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos), true, Integer.MAX_VALUE));
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
	public void flagChunkForUpdate(int par1, int par2, int par3) {
		if (noUpdateRequired()) {
			return;
		}
		markRequiresUpdate();

		synchronized (this) {
			this.field_73260_f |= 1 << (par2 >> 4);

			short mask = (short) (par1 << 12 | par3 << 8 | par2);
			short[] locationOfBlockChange = this.locationOfBlockChange;

			int tiles = numberOfTilesToUpdate;
			for (int var5 = 0; var5 < tiles; ++var5) {
				if (locationOfBlockChange[var5] == mask) {
					return;
				}
			}

			if (tiles == locationOfBlockChange.length) {
				this.locationOfBlockChange = locationOfBlockChange = Arrays.copyOf(locationOfBlockChange, locationOfBlockChange.length << 1);
			}
			locationOfBlockChange[tiles++] = mask;
			numberOfTilesToUpdate = tiles;
		}
	}

	private void markRequiresUpdate() {
		boolean requiresWatch = false;
		synchronized (this) {
			if (!watched) {
				watched = requiresWatch = true;
			}
		}
		if (requiresWatch) {
			this.thePlayerManager.getChunkWatcherWithPlayers().add(this);
		}
	}

	@Override
	@Declare
	public void updateTile(TileEntity tileEntity) {
		if (noUpdateRequired()) {
			return;
		}
		markRequiresUpdate();
		tilesToUpdate.add(tileEntity);
	}

	@Override
	public void sendTileToAllPlayersWatchingChunk(TileEntity tileEntity) {
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

	private boolean noUpdateRequired() {
		Chunk chunk = this.chunk;
		if (chunk == null || !chunk.isTerrainPopulated || playersInChunk.isEmpty()) {
			return true;
		}
		if (chunk.partiallyUnloaded) {
			Log.severe("Chunk for " + this + " has been unloaded without removing the PlayerInstance");
			this.chunk = null;
			return true;
		}
		return false;
	}

	@Override
	@Declare
	public boolean shouldPostPone(boolean squash, int currentTick) {
		if (!rateLimitChunkUpdates) {
			return false;
		}
		int runningTicks = currentTick - startTime;
		if (squash) {
			startTime = currentTick - (runningTicks /= 2);
			sentUpdates /= 2;
		}
		return (sentUpdates / (float) runningTicks) > 0.1f;
	}

	@Override
	public void sendChunkUpdate() {
		watched = false;
		if (noUpdateRequired()) {
			return;
		}
		sentUpdates++;
		sendTiles();
		synchronized (this) {
			int numberOfTilesToUpdate = this.numberOfTilesToUpdate;
			if (numberOfTilesToUpdate != 0) {
				short[] locationOfBlockChange = this.locationOfBlockChange;
				if (numberOfTilesToUpdate > locationOfBlockChange.length) {
					Log.warning("numberOfTilesToUpdate set too high. Got " + numberOfTilesToUpdate + " should be <= " + locationOfBlockChange.length);
					numberOfTilesToUpdate = locationOfBlockChange.length;
				}

				WorldServer worldServer = thePlayerManager.getWorldServer();
				Chunk chunk = this.chunk;
				if (numberOfTilesToUpdate == 1) {
					int x = chunkLocation.chunkXPos * 16 + (locationOfBlockChange[0] >> 12 & 15);
					int y = locationOfBlockChange[0] & 255;
					int z = chunkLocation.chunkZPos * 16 + (locationOfBlockChange[0] >> 8 & 15);
					sendToAllPlayersWatchingChunk(new Packet53BlockChange(x, y, z, worldServer));

					sendTileToAllPlayersWatchingChunk(chunk.getChunkBlockTileEntity(locationOfBlockChange[0] >> 12 & 15, locationOfBlockChange[0] & 255, locationOfBlockChange[0] >> 8 & 15));
				} else {
					if (numberOfTilesToUpdate >= ForgeDummyContainer.clumpingThreshold) {
						sendToAllPlayersWatchingChunk(new Packet51MapChunk(chunk, false, field_73260_f));
					} else {
						sendToAllPlayersWatchingChunk(new Packet52MultiBlockChange(chunkLocation.chunkXPos, chunkLocation.chunkZPos, locationOfBlockChange, numberOfTilesToUpdate, worldServer));
					}

					for (int i = 0; i < numberOfTilesToUpdate; ++i) {
						sendTileToAllPlayersWatchingChunk(chunk.getChunkBlockTileEntity(locationOfBlockChange[i] >> 12 & 15, locationOfBlockChange[i] & 255, locationOfBlockChange[i] >> 8 & 15));
					}
				}

				this.numberOfTilesToUpdate = 0;
				this.field_73260_f = 0;
			}
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

	public static class LoadRunnable extends ChunkLoadRunnable {
		final PlayerInstance playerInstance;

		public LoadRunnable(PlayerInstance playerInstance) {
			this.playerInstance = playerInstance;
		}

		@Override
		public void onLoad(Chunk chunk) {
			playerInstance.loaded = true;
			playerInstance.chunk = chunk;
		}
	}
}
