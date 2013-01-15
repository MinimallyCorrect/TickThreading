package me.nallar.tickthreading.minecraft.patched;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.management.PlayerInstance;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;

public abstract class PatchPlayerInstance extends PlayerInstance {
	public PatchPlayerInstance(PlayerManager par1PlayerManager, int par2, int par3) {
		super(par1PlayerManager, par2, par3);
	}

	@Declare
	public ChunkCoordIntPair getLocation() {
		return chunkLocation;
	}
}
