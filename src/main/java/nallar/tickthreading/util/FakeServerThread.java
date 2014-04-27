package nallar.tickthreading.util;

import net.minecraft.server.ThreadMinecraftServer;

public class FakeServerThread extends ThreadMinecraftServer {
	private final Runnable runnable;

	public FakeServerThread(Runnable runnable, String name, boolean daemon) {
		super(null, name);
		this.setDaemon(daemon);
		this.runnable = runnable;
	}

	@Override
	public void run() {
		this.runnable.run();
	}
}
