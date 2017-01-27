package nallar.tickthreading.mod;

import nallar.tickthreading.reporting.Metrics;
import nallar.tickthreading.util.Version;
import net.minecraftforge.fml.common.Mod;

@Mod(modid = "@MOD_ID@", version = "@MOD_VERSION@", name = "@MOD_NAME@", acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[@MC_VERSION@]")
public class TickThreading {
	static {
		new Metrics("@MOD_NAME@", Version.VERSION);
	}
}
