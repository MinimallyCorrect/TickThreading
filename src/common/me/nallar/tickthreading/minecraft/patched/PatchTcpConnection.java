package me.nallar.tickthreading.minecraft.patched;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;
import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.network.TcpConnection;
import net.minecraft.network.packet.IPacketHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;

public abstract class PatchTcpConnection extends TcpConnection {
	private static List<IPacketHandler> packetHandlers;

	public PatchTcpConnection(Socket par1Socket, String par2Str, NetHandler par3NetHandler) throws IOException {
		super(par1Socket, par2Str, par3NetHandler);
	}

	private boolean callPacketOut(Packet p) {
		for (IPacketHandler handler : getPacketHandlers()) {
			if (!handler.onOutgoingPacket(theNetHandler, p.getPacketId(), p)) {
				return false;
			}
		}

		return true;
	}

	@Declare
	public boolean readNetworkPacket() {
		return this.readPacket();
	}

	@Declare
	public boolean isRunning() {
		return isRunning && !isServerTerminating;
	}

	@Declare
	public static java.util.List<net.minecraft.network.packet.IPacketHandler> getPacketHandlers() {
		if (packetHandlers == null) {
			synchronized (IPacketHandler.class) {
				if (packetHandlers == null) {
					packetHandlers = new ArrayList<IPacketHandler>();
				}
			}
		}
		return packetHandlers;
	}

	@Override
	public void addToSendQueue(Packet par1Packet) {
		if (!this.isServerTerminating && callPacketOut(par1Packet)) {
			synchronized (this.sendQueueLock) {
				this.sendQueueByteLength += par1Packet.getPacketSize() + 1;
				this.dataPackets.add(par1Packet);
			}
		}
	}

	@Override
	protected void onNetworkError(Exception e) {
		if (e instanceof SocketException) {
			this.networkShutdown("disconnected: " + e.getMessage(), e);
		} else {
			String name = "unknown";
			try {
				name = this.theNetHandler.getPlayer().getCommandSenderName();
			} catch (Throwable ignored) {
			}
			FMLLog.log(Level.SEVERE, e, "Failed to handle packet from " + name);
			this.networkShutdown("disconnect.genericReason", "Internal exception: " + e.toString());
		}
	}
}
