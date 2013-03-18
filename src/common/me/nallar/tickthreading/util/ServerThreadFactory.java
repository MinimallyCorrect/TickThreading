package me.nallar.tickthreading.util;

import java.util.concurrent.ThreadFactory;

public class ServerThreadFactory implements ThreadFactory {
	private final String name;

	public ServerThreadFactory(String name) {
		this.name = name;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new FakeServerThread(r, name, true);
	}
}
