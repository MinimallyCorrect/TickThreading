package me.nallar.patched.network;

import net.minecraft.network.TcpConnection;
import net.minecraft.server.ThreadMinecraftServer;
import net.minecraft.util.AxisAlignedBB;

public abstract class ReplaceTcpReaderThread extends ThreadMinecraftServer {
	private int count;
	private final TcpConnection tcpConnection;

	public ReplaceTcpReaderThread(TcpConnection tcpConnection, String name) {
		super(null, name);
		this.tcpConnection = tcpConnection;
	}

	@SuppressWarnings ("InfiniteLoopStatement")
	@Override
	public void run() {
		TcpConnection.field_74471_a.getAndIncrement();

		try {
			if (tcpConnection.isRunning()) {
				while (true) {
					if (!tcpConnection.readNetworkPacket()) {
						try {
							sleep(2L);
						} catch (InterruptedException ignored) {
						}
					}
					if (count++ % 5 == 0) {
						AxisAlignedBB.getAABBPool().cleanPool();
					}
				}
			}
		} finally {
			TcpConnection.field_74471_a.getAndDecrement();
		}
	}
}
