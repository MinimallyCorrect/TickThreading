package org.minimallycorrect.tickthreading.mod;

import lombok.val;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.minimallycorrect.tickthreading.log.Log;
import sun.misc.Signal;
import sun.misc.SignalHandler;

@Mod(modid = "@MOD_ID@", version = "@MOD_VERSION@", name = "@MOD_NAME@", acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[@MC_VERSION@]")
public class TickThreading {
	private static void handleSignal(String name, SignalHandler handler) {
		try {
			Signal.handle(new Signal(name), handler);
		} catch (IllegalArgumentException ignored) {
		}
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		SignalHandler handler = signal -> {
			Log.info("Received signal " + signal.getName() + ". Stopping server.");
			val server = FMLCommonHandler.instance().getMinecraftServerInstance();
			server.initiateShutdown();
			while (!server.isServerStopped()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignored) {
				}
			}
		};
		handleSignal("TERM", handler);
		handleSignal("INT", handler);
		handleSignal("HUP", handler);
	}
}
