package me.nallar.patched.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet10Flying;
import net.minecraft.network.packet.Packet56MapChunks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkWatchEvent;

public abstract class PatchPacket10Flying extends Packet10Flying {
	@Override
	public boolean canProcessAsync() {
		return true;
	}

	@Override
	public void processPacket(NetHandler par1NetHandler) {
		NetServerHandler nsh = (NetServerHandler) par1NetHandler;
		synchronized (nsh) {
			EntityPlayerMP entityPlayerMP = nsh.playerEntity;
			if (!(nsh.teleported-- > 0 || nsh.tpPosY > yPosition + 0.02) || (yPosition == -999.0D && stance == -999.0D)) {
				nsh.tpPosX = Double.NaN;
				nsh.setHasMoved();
				nsh.tpPosY = -256;
				par1NetHandler.handleFlying(this);
				sendChunks(entityPlayerMP);
			} else {
				nsh.lastPZ = this.zPosition;
				nsh.lastPX = this.xPosition;
				if (nsh.teleported <= 1 || (nsh.teleported < 10 && nsh.tpPosY > yPosition + 0.02)) {
					nsh.updatePositionAfterTP();
					((WorldServer) entityPlayerMP.worldObj).getPlayerManager().updateMountedMovingPlayer(entityPlayerMP);
					if (nsh.teleported == 1) {
						LinkedList<EntityPlayerMP> playersToCheckWorld = MinecraftServer.playersToCheckWorld;
						synchronized (playersToCheckWorld) {
							playersToCheckWorld.add(entityPlayerMP);
						}
					}
				}
				sendChunks(entityPlayerMP);
			}
		}
	}

	public static void sendChunks(EntityPlayerMP entityPlayerMP) {
		NetServerHandler netServerHandler = entityPlayerMP.playerNetServerHandler;
		if (!entityPlayerMP.loadedChunks.isEmpty()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			long st = 0;
			boolean timings = Timings.enabled;
			if (timings) {
				st = System.nanoTime();
			}
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
						Log.warning("Null chunk at " + chunkCoordIntPair + " when trying to send chunks to " + entityPlayerMP);
						chunk = entityPlayerMP.worldObj.getChunkProvider().loadChunk(x, z);
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
				Iterator var11 = tileEntities.iterator();

				while (var11.hasNext()) {
					Packet var5 = ((TileEntity) var11.next()).getDescriptionPacket();
					if (var5 != null) {
						netServerHandler.sendPacketToPlayer(var5);
					}
				}

				var11 = chunks.iterator();

				while (var11.hasNext()) {
					Chunk var10 = (Chunk) var11.next();
					entityPlayerMP.getServerForPlayer().getEntityTracker().func_85172_a(entityPlayerMP, var10);
					try {
						MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(var10.getChunkCoordIntPair(), entityPlayerMP));
					} catch (Throwable t) {
						Log.severe("A mod failed to handle a ChunkWatch event", t);
					}
				}
			}
			if (timings) {
				Timings.record("onMovement/chunks", System.nanoTime() - st);
			}
		}
	}
}
