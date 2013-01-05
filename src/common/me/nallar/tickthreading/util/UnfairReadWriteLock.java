package me.nallar.tickthreading.util;

public class UnfairReadWriteLock extends TwoWayReentrantReadWriteLock {
	public UnfairReadWriteLock() {
		this.unfair = true;
	}
}
