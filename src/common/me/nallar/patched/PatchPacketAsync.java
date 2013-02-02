package me.nallar.patched;

import net.minecraft.network.packet.Packet;

public abstract class PatchPacketAsync extends Packet {
	@Override
	public boolean canProcessAsync() {
		return true;
	}
}
