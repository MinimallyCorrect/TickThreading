package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.network.TcpConnection;
import net.minecraft.server.ThreadMinecraftServer;

public abstract class PatchTcpReaderThread extends ThreadMinecraftServer {
	final TcpConnection tcpConnection;

	public PatchTcpReaderThread(TcpConnection tcpConnection, String name) {
		super(null, name);
		this.tcpConnection = tcpConnection;
	}

	public void run() {
		TcpConnection.field_74471_a.getAndIncrement();

		try {
			while (tcpConnection.isRunning()) {
				while (true) {
					if (!tcpConnection.readNetworkPacket()) {
						try {
							sleep(2L);
						} catch (InterruptedException var5) {
						}
					}
				}
			}
		} finally {
			TcpConnection.field_74471_a.getAndDecrement();
		}
	}
}
