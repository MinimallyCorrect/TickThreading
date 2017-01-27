package nallar.tickthreading.mod;

import me.nallar.modpatcher.ModPatcher;
import nallar.tickthreading.log.Log;
import nallar.tickthreading.util.PropertyUtil;
import nallar.tickthreading.util.Version;
import nallar.tickthreading.util.unsafe.UnsafeUtil;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.*;

@IFMLLoadingPlugin.Name("@MOD_NAME@Core")
@IFMLLoadingPlugin.MCVersion("@MC_VERSION@")
@IFMLLoadingPlugin.SortingIndex(1002)
public class CoreMod implements IFMLLoadingPlugin {
	public static boolean canLoadMinecraftClasses;

	static {
		if (PropertyUtil.get("removeSecurityManager", false)) {
			UnsafeUtil.removeSecurityManager();
		}

		ModPatcher.requireVersion("latest", "beta");
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
		ModPatcher.loadMixins("nallar.tickthreading.mixin");
		ModPatcher.loadPatches(CoreMod.class.getResourceAsStream("/patches/minecraft.xml"));
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
