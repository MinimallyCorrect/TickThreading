package me.nallar.patched.network;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.network.packet.Packet51MapChunk;

public abstract class PatchPacket51MapChunk extends Packet51MapChunk {
	@Override
	@Declare
	public void setData(byte[] data) {
		this.chunkData = data;
		this.tempLength = data.length;
	}
}
