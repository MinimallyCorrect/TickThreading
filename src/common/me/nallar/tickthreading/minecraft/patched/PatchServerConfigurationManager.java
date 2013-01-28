package me.nallar.tickthreading.minecraft.patched;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import me.nallar.tickthreading.patcher.Declare;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public abstract class PatchServerConfigurationManager extends ServerConfigurationManager {
	@Declare
	public java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock playerUpdateLock_;
	@Declare
	public java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock playersUpdateLock_;

	public void construct() {
		ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
		playersUpdateLock = reentrantReadWriteLock.writeLock();
		playerUpdateLock = reentrantReadWriteLock.readLock();
	}

	public PatchServerConfigurationManager(MinecraftServer par1MinecraftServer) {
		super(par1MinecraftServer);
	}
}
