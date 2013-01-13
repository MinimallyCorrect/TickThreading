package net.minecraft.network.packet;

public interface IPacketHandler {
	boolean onOutgoingPacket(NetHandler network, int packetID, Packet packet);
}
