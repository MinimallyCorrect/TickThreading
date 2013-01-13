package me.nallar.tickthreading.minecraft.patched;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.network.TcpConnection;
import net.minecraft.network.packet.IPacketHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet252SharedKey;

public abstract class PatchTcpConnection extends TcpConnection {
	private static List<IPacketHandler> packetHandlers;

	public PatchTcpConnection(Socket par1Socket, String par2Str, NetHandler par3NetHandler) throws IOException {
		super(par1Socket, par2Str, par3NetHandler);
	}

	private boolean callPacketOut(Packet p) {
		for (IPacketHandler handler : packetHandlers) {
			if (!handler.onOutgoingPacket(theNetHandler, p.getPacketId(), p)) {
				return false;
			}
		}

		return true;
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
	protected boolean sendPacket() {
		boolean var1 = false;

		try {
			Packet var2;
			int var10001;
			int[] var10000;

			if (this.field_74468_e == 0 || !this.dataPackets.isEmpty() && System.currentTimeMillis() - ((Packet) this.dataPackets.get(0)).creationTimeMillis >= (long) this.field_74468_e) {
				var2 = this.func_74460_a(false);

				if (var2 != null && callPacketOut(var2)) {
					Packet.writePacket(var2, this.socketOutputStream);

					if (var2 instanceof Packet252SharedKey && !this.isOutputEncrypted) {
						if (!this.theNetHandler.isServerHandler()) {
							this.sharedKeyForEncryption = ((Packet252SharedKey) var2).func_73304_d();
						}

						this.encryptOuputStream();
					}

					var10000 = field_74467_d;
					var10001 = var2.getPacketId();
					var10000[var10001] += var2.getPacketSize() + 1;
					var1 = true;
				}
			}

			if (this.field_74464_B-- <= 0 && (this.field_74468_e == 0 || !this.chunkDataPackets.isEmpty() && System.currentTimeMillis() - ((Packet) this.chunkDataPackets.get(0)).creationTimeMillis >= (long) this.field_74468_e)) {
				var2 = this.func_74460_a(true);

				if (var2 != null && callPacketOut(var2)) {
					Packet.writePacket(var2, this.socketOutputStream);
					var10000 = field_74467_d;
					var10001 = var2.getPacketId();
					var10000[var10001] += var2.getPacketSize() + 1;
					this.field_74464_B = 0;
					var1 = true;
				}
			}

			return var1;
		} catch (Exception var3) {
			if (!this.isTerminating) {
				this.onNetworkError(var3);
			}

			return false;
		}
	}
}
