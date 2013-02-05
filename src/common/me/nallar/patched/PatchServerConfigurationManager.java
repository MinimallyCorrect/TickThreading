package me.nallar.patched;

import me.nallar.tickthreading.patcher.Declare;
import me.nallar.tickthreading.util.concurrent.TwoWayReentrantReadWriteLock;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public abstract class PatchServerConfigurationManager extends ServerConfigurationManager {
	@Declare
	public java.util.concurrent.locks.Lock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.Lock playersUpdateLock_;

	public void construct() {
		TwoWayReentrantReadWriteLock reentrantReadWriteLock = new TwoWayReentrantReadWriteLock();
		playersUpdateLock = reentrantReadWriteLock.writeLock();
		playerUpdateLock = reentrantReadWriteLock.readLock();
	}

	@Override
	public void readPlayerDataFromFile(EntityPlayerMP par1EntityPlayerMP) {
		NBTTagCompound var2 = this.mcServer.worldServers[0].getWorldInfo().getPlayerNBTTagCompound();

		if (par1EntityPlayerMP.getCommandSenderName().equals(this.mcServer.getServerOwner()) && var2 != null) {
			par1EntityPlayerMP.readFromNBT(var2);
		} else {
			this.playerNBTManagerObj.readPlayerData(par1EntityPlayerMP);
		}
		par1EntityPlayerMP.posY += 0.05;
	}

	public PatchServerConfigurationManager(MinecraftServer par1MinecraftServer) {
		super(par1MinecraftServer);
	}
}
