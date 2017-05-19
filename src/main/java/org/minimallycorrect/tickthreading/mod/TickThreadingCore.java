package org.minimallycorrect.tickthreading.mod;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.minimallycorrect.libloader.LibLoader;
import org.minimallycorrect.modpatcher.api.ModPatcher;
import org.minimallycorrect.tickthreading.config.Config;
import org.minimallycorrect.tickthreading.log.Log;
import org.minimallycorrect.tickthreading.util.PropertyUtil;
import org.minimallycorrect.tickthreading.util.Version;
import org.minimallycorrect.tickthreading.util.unsafe.UnsafeUtil;

import java.util.*;

@IFMLLoadingPlugin.Name("@MOD_NAME@Core")
@IFMLLoadingPlugin.MCVersion("@MC_VERSION@")
@IFMLLoadingPlugin.SortingIndex(1002)
public class TickThreadingCore implements IFMLLoadingPlugin {
	static {
		LibLoader.init();
		//Load config file early so invalid config crashes fast, not 2 minutes into loading a large modpack
		//noinspection ResultOfMethodCallIgnored
		Config.$.getClass();

		if (PropertyUtil.get("removeSecurityManager", false)) {
			UnsafeUtil.removeSecurityManager();
		}

		Log.info(Version.DESCRIPTION + " CoreMod initialised");

		if (Version.EXTENDED && !PropertyUtil.get("overrideExtended", false))
			throw new UnsupportedOperationException("TickThreading EXTENDED release is not currently usable");
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
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
