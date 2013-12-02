package nallar.patched.forge;

import com.google.common.collect.*;
import cpw.mods.fml.common.DuplicateModsFoundException;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import nallar.tickthreading.patcher.Declare;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PatchLoader extends Loader {
	public static boolean isModLoaded(String modname) {
		return instance().isModLoadedFast(modname);
	}

	@Override
	@Declare
	public boolean isModLoadedFast(String modname) {
		return namedMods.containsKey(modname);
	}

	@Override
	protected void identifyDuplicates(List<ModContainer> mods) {
		Map<String, List<ModContainer>> modsMap = new HashMap<String, List<ModContainer>>();
		for (ModContainer mc : mods) {
			String source;
			try {
				source = mc.getSource().getCanonicalPath() + mc.getModId();
			} catch (IOException e) {
				source = mc.getSource().getAbsolutePath().replace(File.separator + '.' + File.separator, File.separator) + mc.getModId();
			}
			List<ModContainer> modsList = modsMap.get(source);
			if (modsList == null) {
				modsMap.put(source, modsList = new ArrayList<ModContainer>());
			}
			modsList.add(mc);
		}

		for (List<ModContainer> modContainers : modsMap.values()) {
			if (modContainers.size() > 1) {
				modContainers.remove(modContainers.size() - 1);
				for (ModContainer modContainer : modContainers) {
					mods.remove(modContainer);
				}
			}
		}

		TreeMultimap<ModContainer, File> dupsearch = TreeMultimap.create(new ModIdComparator(), Ordering.arbitrary());
		for (ModContainer mc : mods) {
			if (mc.getSource() != null) {
				dupsearch.put(mc, mc.getSource());
			}
		}

		ImmutableMultiset<ModContainer> duplist = Multisets.copyHighestCountFirst(dupsearch.keys());
		SetMultimap<ModContainer, File> dupes = LinkedHashMultimap.create();
		for (Multiset.Entry<ModContainer> e : duplist.entrySet()) {
			if (e.getCount() > 1) {
				FMLLog.severe("Found a duplicate mod %s at %s", e.getElement().getModId(), dupsearch.get(e.getElement()));
				dupes.putAll(e.getElement(), dupsearch.get(e.getElement()));
			}
		}
		if (!dupes.isEmpty()) {
			throw new DuplicateModsFoundException(dupes);
		}
	}
}
