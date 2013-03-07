package me.nallar.patched.network;

import net.minecraft.network.packet.Packet;

public abstract class PatchPacketAsync extends Packet {
	@Override
	public boolean canProcessAsync() {
		return true;
	}
}
