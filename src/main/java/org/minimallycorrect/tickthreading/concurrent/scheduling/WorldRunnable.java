package org.minimallycorrect.tickthreading.concurrent.scheduling;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraft.world.WorldServer;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;

@AllArgsConstructor
public class WorldRunnable implements Runnable {
	private final WorldServer world;

	@SneakyThrows
	@Override
	public void run() {
		val world = this.world;
		val server = world.getMinecraftServer();
		assert server != null;

		long TARGET_TPS = Config.$.targetTps;
		long TARGET_TICK_TIME = 1000000000 / TARGET_TPS;
		long startTime = System.nanoTime();
		float tickTime = 1;
		boolean waitForWorldTick = !Config.$.separatePerWorldTickLoops;

		while (true) {
			if (world.unloaded)
				return;
			if (waitForWorldTick)
				server.waitForWorldTick();

			try {
				world.tickWithEvents();
			} catch (Exception e) {
				Log.error("Exception in main tick loop", e);
			}

			long endTime = System.nanoTime();
			tickTime = tickTime * 0.98f + ((endTime - startTime) * 0.02f);

			long wait = (TARGET_TICK_TIME - (endTime - startTime)) / 1000000;
			if (wait > 0 && TARGET_TICK_TIME * TARGET_TPS / tickTime > TARGET_TPS) {
				Thread.sleep(wait);
				startTime = System.currentTimeMillis();
			} else {
				startTime = endTime;
			}
		}
	}
}
