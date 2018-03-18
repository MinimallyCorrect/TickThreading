package org.minimallycorrect.tickthreading.mod;

import java.util.Map;

import org.minimallycorrect.libloader.LibLoader;
import org.minimallycorrect.modpatcher.api.ModPatcher;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;
import org.minimallycorrect.tickthreading.util.PropertyUtil;
import org.minimallycorrect.tickthreading.util.Version;
import org.minimallycorrect.tickthreading.util.unsafe.UnsafeUtil;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("@MOD_NAME@Core")
@IFMLLoadingPlugin.SortingIndex(1002)
public class TickThreadingCore implements IFMLLoadingPlugin {
	//noinspection ResultOfMethodCallIgnored
	static {
		Log.classString("");
		LibLoader.init();
		//Load config file early so invalid config crashes fast, not 2 minutes into loading a large modpack
		Config.$.getClass();

		// By default, disable FML security manager.
		// All it does currently is stop System.exit() calls
		// It adds some (small) overhead to many operations and we don't really need it
		if (PropertyUtil.get("removeSecurityManager", true))
			UnsafeUtil.removeSecurityManager();

		Log.info(Version.DESCRIPTION + " CoreMod initialised");
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[0];
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return ModPatcher.getSetupClass();
	}

	@Override
	public void injectData(Map<String, Object> map) {
		ModPatcher.loadMixins("org.minimallycorrect.tickthreading.mixin");
		ModPatcher.loadPatches(TickThreadingCore.class.getResourceAsStream("/patches/minecraft.xml"));
		if (Version.EXTENDED)
			ModPatcher.loadPatches(TickThreadingCore.class.getResourceAsStream("/patches/minecraft-extended.xml"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
