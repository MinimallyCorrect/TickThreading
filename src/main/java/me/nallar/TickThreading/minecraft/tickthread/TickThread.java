package me.nallar.tickthreading.minecraft.tickthread;

import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.World;

abstract class TickThread extends Thread {
	final World world;
	final String identifier;
	final ThreadManager manager;
	final int hashCode;

	TickThread(World world, String identifier, ThreadManager manager, int hashCode) {
		super();
		this.world = world;
		this.identifier = identifier;
		this.manager = manager;
		this.hashCode = hashCode;
		this.setDaemon(true);
	}
}
