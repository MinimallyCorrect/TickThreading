package nallar.patched.forge;

import java.util.logging.Level;

import cpw.mods.fml.common.FMLLog;

public abstract class PatchIMCMessage {
	private String sender;
	public String key;

	public void construct() {
		if (key != null && key.toLowerCase().contains("security")) {
			FMLLog.log(Level.WARNING, new Throwable(), key + " from " + sender);
		}
	}
}
