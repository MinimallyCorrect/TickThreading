package nallar.patched.forge;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;

import java.util.logging.Level;

public abstract class PatchGameRegistry extends GameRegistry {
	public static void onPlayerLogout(EntityPlayer player) {
		for (IPlayerTracker tracker : playerTrackers) {
			try {
				tracker.onPlayerLogout(player);
			} catch (Exception e) {
				FMLLog.log(Level.WARNING, e, "Tracker " + tracker + " failed to handle onPlayerLogout for " + player);
			}
		}
	}
}
