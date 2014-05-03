package nallar.patched.forge;

import cpw.mods.fml.common.Loader;
import nallar.patched.annotation.ExposeInner;
import nallar.tickthreading.patcher.Declare;

@ExposeInner("ModIdComparator")
public abstract class PatchLoader extends Loader {
	public static boolean isModLoaded(String modname) {
		return instance().isModLoadedFast(modname);
	}

	@Override
	@Declare
	public boolean isModLoadedFast(String modname) {
		return namedMods.containsKey(modname);
	}
}
