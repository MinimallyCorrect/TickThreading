package nallar.patched.forge;

import cpw.mods.fml.common.FMLLog;

import java.util.logging.*;

public abstract class PatchIMCMessage {
	private String sender;
	public String key;

	public void construct() {
		if (key != null && key.toLowerCase().contains("security")) {
			FMLLog.log(Level.WARNING, new Throwable(), key + " from " + sender);
		}
	}
}
