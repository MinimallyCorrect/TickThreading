package me.nallar.patched;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import javassist.is.faulty.Redirects;
import javassist.is.faulty.Timings;
import me.nallar.tickthreading.minecraft.TickThreading;
import me.nallar.tickthreading.util.TableFormatter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet10Flying;
import net.minecraft.network.packet.Packet56MapChunks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
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
				if (false && TickThreading.instance.antiCheatNotify && moving && yPosition != -999.0D && stance != -999.0D) {
					long currentTime = System.currentTimeMillis();
					long time = Math.min(5000, currentTime - nsh.lastMovement);
					double dX = (xPosition - nsh.lastPX);
					double dZ = (zPosition - nsh.lastPZ);
					if (time == 0) {
						nsh.lastPZ += dZ;
						nsh.lastPX += dX;
					} else {
						nsh.lastMovement = currentTime;
						if (time < 1) {
							time = 1;
						}
						double speed = (Math.sqrt(dX * dX + dZ * dZ) * 1000) / time;
						//Log.info(speed + "\t" + dX + '\t' + dZ + '\t' + time + '\t' + moving + '\t' + yPosition + '\t' + stance);
						if (Double.isInfinite(speed) || Double.isNaN(speed)) {
							speed = 1;
						}
						double averageSpeed = (nsh.averageSpeed = ((nsh.averageSpeed * 100 + speed) / 101));
						ServerConfigurationManager serverConfigurationManager = MinecraftServer.getServer().getConfigurationManager();
						speed /= allowedSpeedMultiplier(entityPlayerMP);
						if (currentTime > nsh.lastNotify && !serverConfigurationManager.areCommandsAllowed(entityPlayerMP.username) && (averageSpeed > 50 || (!entityPlayerMP.isRiding() && averageSpeed > 20))) {
							if (TickThreading.instance.antiCheatKick) {
								nsh.kickPlayerFromServer("You moved too quickly. " + TableFormatter.formatDoubleWithPrecision(averageSpeed, 3) + "m/s");
							} else {
								entityPlayerMP.sendChatToPlayer("You moved too quickly. " + TableFormatter.formatDoubleWithPrecision(averageSpeed, 3) + "m/s");
							}
							Redirects.notifyAdmins(entityPlayerMP.username + " was travelling too fast: " + TableFormatter.formatDoubleWithPrecision(averageSpeed, 3) + "m/s");
							nsh.lastNotify = currentTime + 30000;
						}
						nsh.lastPZ = this.zPosition;
						nsh.lastPX = this.xPosition;
					}
				}
				synchronized (entityPlayerMP.loadedChunks) {
					par1NetHandler.handleFlying(this);
					sendChunks(entityPlayerMP);
				}
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
				synchronized (entityPlayerMP.loadedChunks) {
					sendChunks(entityPlayerMP);
				}
			}
		}
	}

	private static double allowedSpeedMultiplier(EntityPlayerMP entityPlayerMP) {
		for (int i = 0; i < 4; i++) {
			if (entityPlayerMP.inventory.armorItemInSlot(i) != null) {
				return 2;
			}
		}
		return 1;
	}

	public static void sendChunks(EntityPlayerMP entityPlayerMP) {
		NetServerHandler netServerHandler = entityPlayerMP.playerNetServerHandler;
		if (!entityPlayerMP.loadedChunks.isEmpty()) {
			long st = 0;
			boolean timings = Timings.enabled;
			if (timings) {
				st = System.nanoTime();
			}
			ArrayList var6 = new ArrayList();
			Iterator var7 = entityPlayerMP.loadedChunks.iterator();
			ArrayList var8 = new ArrayList();

			while (var7.hasNext() && var6.size() < 5) {
				ChunkCoordIntPair var9 = (ChunkCoordIntPair) var7.next();
				int x = var9.chunkXPos;
				int z = var9.chunkZPos;
				var7.remove();

				Chunk chunk = entityPlayerMP.worldObj.getChunkFromChunkCoords(x, z);
				var6.add(chunk);
				var8.addAll(chunk.chunkTileEntityMap.values());
			}

			if (!var6.isEmpty()) {
				netServerHandler.sendPacketToPlayer(new Packet56MapChunks(var6));
				Iterator var11 = var8.iterator();

				while (var11.hasNext()) {
					Packet var5 = ((TileEntity) var11.next()).getDescriptionPacket();
					if (var5 != null) {
						netServerHandler.sendPacketToPlayer(var5);
					}
				}

				var11 = var6.iterator();

				while (var11.hasNext()) {
					Chunk var10 = (Chunk) var11.next();
					entityPlayerMP.getServerForPlayer().getEntityTracker().func_85172_a(entityPlayerMP, var10);
					MinecraftForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(var10.getChunkCoordIntPair(), entityPlayerMP));
				}
			}
			if (timings) {
				Timings.record("onMovement/chunks", System.nanoTime() - st);
			}
		}
	}
}
