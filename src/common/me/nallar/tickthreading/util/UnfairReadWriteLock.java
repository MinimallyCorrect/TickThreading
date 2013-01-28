package me.nallar.tickthreading.util;

import me.nallar.tickthreading.util.concurrent.TwoWayReentrantReadWriteLock;

public class UnfairReadWriteLock extends TwoWayReentrantReadWriteLock {
	public UnfairReadWriteLock() {
		this.fair = false;
	}
}
