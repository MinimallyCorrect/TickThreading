package nallar.tickthreading.mod;

import me.nallar.libloader.LibLoader;
import me.nallar.modpatcher.api.ModPatcher;
import nallar.tickthreading.config.Config;
import nallar.tickthreading.log.Log;
import nallar.tickthreading.util.PropertyUtil;
import nallar.tickthreading.util.Version;
import nallar.tickthreading.util.unsafe.UnsafeUtil;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

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
		ModPatcher.loadMixins("nallar.tickthreading.mixin");
		ModPatcher.loadPatches(TickThreadingCore.class.getResourceAsStream("/patches/minecraft.xml"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
