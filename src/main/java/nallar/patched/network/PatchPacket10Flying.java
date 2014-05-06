package nallar.patched.network;

import nallar.tickthreading.Log;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet10Flying;
import net.minecraft.network.packet.Packet56MapChunks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

import java.util.*;

public abstract class PatchPacket10Flying extends Packet10Flying {
	@Override
	public void processPacket(NetHandler netHandler) {
		if (!(netHandler instanceof NetServerHandler)) {
			Log.warning(netHandler + " sent a movement update before properly connecting. It will be ignored.");
			return;
		}
		NetServerHandler nsh = (NetServerHandler) netHandler;
		EntityPlayerMP entityPlayerMP = nsh.playerEntity;
		sendChunks(entityPlayerMP);
		nsh.handleFlying(this);
	}

	private static void sendChunks(EntityPlayerMP entityPlayerMP) {
		NetServerHandler netServerHandler = entityPlayerMP.playerNetServerHandler;
		if (!entityPlayerMP.loadedChunks.isEmpty()) {
			ArrayList<ChunkCoordIntPair> unpopulatedChunks = new ArrayList<ChunkCoordIntPair>();
			ArrayList<Chunk> chunks = new ArrayList<Chunk>(5);
			ArrayList<TileEntity> tileEntities = new ArrayList<TileEntity>();
			synchronized (entityPlayerMP.loadedChunks) {
				ChunkCoordIntPair chunkCoordIntPair;

				while (chunks.size() < 5 && (chunkCoordIntPair = (ChunkCoordIntPair) entityPlayerMP.loadedChunks.remove(0)) != null) {
					int x = chunkCoordIntPair.chunkXPos;
					int z = chunkCoordIntPair.chunkZPos;

					Chunk chunk = entityPlayerMP.worldObj.getChunkIfExists(x, z);
					if (chunk == null) {
						continue;
					}
					synchronized (chunk) {
						if (!chunk.isTerrainPopulated) {
							unpopulatedChunks.add(chunkCoordIntPair);
							continue;
						}
					}
					chunks.add(chunk);
					tileEntities.addAll(chunk.chunkTileEntityMap.values());
				}
			}
			entityPlayerMP.loadedChunks.addAll(unpopulatedChunks);

			if (!chunks.isEmpty()) {
				netServerHandler.sendPacketToPlayer(new Packet56MapChunks(chunks));
				Iterator iterator = tileEntities.iterator();

				while (iterator.hasNext()) {
					Packet descriptionPacket;
					try {
						descriptionPacket = ((TileEntity) iterator.next()).getDescriptionPacket();
					} catch (Throwable t) {
						Log.warning("A TileEntity failed to provide a description packet", t);
						continue;
					}
					if (descriptionPacket != null) {
						netServerHandler.sendPacketToPlayer(descriptionPacket);
					}
				}

				iterator = chunks.iterator();

				while (iterator.hasNext()) {
					Chunk var10 = (Chunk) iterator.next();
					entityPlayerMP.getServerForPlayer().getEntityTracker().func_85172_a(entityPlayerMP, var10);
					try {
						MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(var10.getChunkCoordIntPair(), entityPlayerMP));
					} catch (Throwable t) {
						Log.severe("A mod failed to handle a ChunkWatch event", t);
					}
				}
			}
		}
	}
}
