package me.nallar.tickthreading.minecraft.tickcallables;

import java.util.concurrent.Callable;

import me.nallar.tickthreading.minecraft.ThreadManager;
import net.minecraft.src.World;

public abstract class TickCallable<T> implements Callable<T> {
	final World world;
	final String identifier;
	final ThreadManager manager;
	public final int hashCode;

	public TickCallable(World world, String identifier, ThreadManager manager, int hashCode) {
		super();
		this.world = world;
		this.identifier = identifier;
		this.manager = manager;
		this.hashCode = hashCode;
	}

	public abstract boolean isEmpty();
}
