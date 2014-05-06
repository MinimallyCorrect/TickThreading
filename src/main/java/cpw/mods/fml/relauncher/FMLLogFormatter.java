package cpw.mods.fml.relauncher;

import nallar.tickthreading.LogFormatter;

@SuppressWarnings("EmptyClass")
public class FMLLogFormatter extends LogFormatter {
	// This class would be loaded before TT's classloader can init as it is required for FML's logging
	// This replaces the FML version with one that delegates to mine.
}
