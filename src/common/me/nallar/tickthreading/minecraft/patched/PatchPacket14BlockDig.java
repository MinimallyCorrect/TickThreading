package me.nallar.tickthreading.minecraft.patched;

import net.minecraft.network.packet.Packet14BlockDig;

public abstract class PatchPacket14BlockDig extends Packet14BlockDig {
	@Override
	public boolean canProcessAsync() {
		return true;
	}
}
