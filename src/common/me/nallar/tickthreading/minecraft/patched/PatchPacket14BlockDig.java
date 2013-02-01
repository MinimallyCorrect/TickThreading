package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.minecraft.TickThreading;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet10Flying;
import net.minecraft.network.packet.Packet14BlockDig;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public abstract class PatchPacket14BlockDig extends Packet14BlockDig {
	@Override
	public boolean canProcessAsync() {
		return true;
	}
}
