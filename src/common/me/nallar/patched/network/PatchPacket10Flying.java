package me.nallar.patched.network;

import java.util.ArrayList;
import java.util.Iterator;

import javassist.is.faulty.Timings;
import me.nallar.tickthreading.Log;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.TcpConnection;
import net.minecraft.network.TcpReaderThread;
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
		EntityPlayerMP entityPlayerMP = nsh.playerEntity;
		boolean mainThreadProcess = false;
		if (entityPlayerMP.ridingEntity != null) {
			if (Thread.currentThread() instanceof TcpReaderThread) {
				TcpConnection tcpConnection = (TcpConnection) nsh.netManager;
				tcpConnection.addReadPacket(this);
				return;
			} else {
				mainThreadProcess = true;
			}
		}
		synchronized (nsh) {
			int teleported = nsh.teleported--;
			boolean finishedTeleporting = teleported < 0;
			if (finishedTeleporting && mainThreadProcess) {
				finishedTeleporting = false;
				nsh.teleported = -21;
			}
			if ((yPosition == -999.0D && stance == -999.0D) || (finishedTeleporting && (teleported < -20 || (nsh.tpPosY < yPosition + 0.02)))) {
				nsh.tpPosX = Double.NaN;
				nsh.setHasMoved();
				nsh.tpPosY = -256;
				par1NetHandler.handleFlying(this);
			} else if (nsh.teleported <= 1) {
				nsh.updatePositionAfterTP(yaw, pitch);
				((WorldServer) entityPlayerMP.worldObj).getPlayerManager().updateMountedMovingPlayer(entityPlayerMP);
				if (nsh.teleported == 1) {
					MinecraftServer.addPlayerToCheckWorld(entityPlayerMP);
				}
			}
			sendChunks(entityPlayerMP);
		}
	}

	private static void sendChunks(EntityPlayerMP entityPlayerMP) {
		NetServerHandler netServerHandler = entityPlayerMP.playerNetServerHandler;
		if (!entityPlayerMP.loadedChunks.isEmpty()) {
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
			if (timings) {
				Timings.record("onMovement/chunks", System.nanoTime() - st);
			}
		}
	}
}
