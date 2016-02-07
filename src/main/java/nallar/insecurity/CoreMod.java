package nallar.insecurity;

import me.nallar.modpatcher.ModPatcher;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.*;

@IFMLLoadingPlugin.MCVersion("@MC_VERSION@")
@IFMLLoadingPlugin.SortingIndex(1001)
public class CoreMod implements IFMLLoadingPlugin {
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
		InsecurityManager.init();
		return ModPatcher.getSetupClass("latest", "beta");
	}

	@Override
	public void injectData(Map<String, Object> map) {

	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
