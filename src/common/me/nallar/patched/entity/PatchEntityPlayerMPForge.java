package me.nallar.patched.entity;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemInWorldManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

public abstract class PatchEntityPlayerMPForge extends EntityPlayerMP {
	public PatchEntityPlayerMPForge(MinecraftServer par1MinecraftServer, World par2World, String par3Str, ItemInWorldManager par4ItemInWorldManager) {
		super(par1MinecraftServer, par2World, par3Str, par4ItemInWorldManager);
	}

	@Override
	@Declare
	public long getPlayerTime() {
		return worldObj.getWorldTime();
	}
}
