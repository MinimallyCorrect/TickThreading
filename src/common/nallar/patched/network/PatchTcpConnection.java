package nallar.patched.network;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nallar.collections.ConcurrentQueueList;
import nallar.tickthreading.Log;
import nallar.tickthreading.patcher.Declare;
import nallar.tickthreading.util.ReflectUtil;
import net.minecraft.network.TcpConnection;
import net.minecraft.network.packet.IPacketHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;

public abstract class PatchTcpConnection extends TcpConnection {
	private static List<IPacketHandler> packetHandlers;

	public PatchTcpConnection(Socket par1Socket, String par2Str, NetHandler par3NetHandler) throws IOException {
		super(par1Socket, par2Str, par3NetHandler);
	}

	public void construct() {
		try {
			readPackets = new ConcurrentQueueList();
		} catch (NoSuchFieldError ignored) {
			// Already using sane collection in MCPC+.
		}
	}

	private boolean callPacketOut(Packet p) {
		for (IPacketHandler handler : getPacketHandlers()) {
			if (!handler.onOutgoingPacket(theNetHandler, p.getPacketId(), p)) {
				return false;
			}
		}

		return true;
	}

	@Override
	@Declare
	public void addReadPacket(Packet packet) {
		getReadPackets().add(packet);
	}

	private Collection<Packet> actualReadPackets;

	private Collection<Packet> getReadPackets() {
		if (actualReadPackets != null) {
			return actualReadPackets;
		}
		try {
			return actualReadPackets = readPackets;
		} catch (NoSuchFieldError e) {
			try {
				return actualReadPackets = (Collection<Packet>) ReflectUtil.getField(TcpConnection.class, "o").get(this); // TODO: Implement reflection obfuscation
			} catch (IllegalAccessException e1) {
				Log.severe("Failed to get readPackets collection", e1);
			}
		}
		throw new Error("Failed to get readPackets collection");
	}

	@Override
	@Declare
	public boolean readNetworkPacket() {
		return this.readPacket();
	}

	@Override
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
		if (e == null || e.toString() == null) {
			Log.severe("onNetworkError with bad exception " + e, new Throwable());
		}
		if (e instanceof SocketException || e instanceof SocketTimeoutException) {
			this.networkShutdown("disconnected: " + e.getMessage(), e);
		} else {
			String name = "unknown";
			try {
				name = this.theNetHandler.getPlayer().getCommandSenderName();
			} catch (Throwable ignored) {
			}
			Log.severe("Failed to handle packet from " + name, e);
			this.networkShutdown("Internal error: " + e, e);
		}
	}

	@Override
	public void wakeThreads() {
		if (this.writeThread != null) {
			this.writeThread.interrupt();
		}
	}
}
