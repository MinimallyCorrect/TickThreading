package nallar.patched;

import net.minecraft.profiler.Profiler;

import java.util.*;

public abstract class PatchProfiler extends Profiler {
	@Override
	public void startSection(String par1Str) {
	}

	/**
	 * End section
	 */
	@Override
	public void endSection() {
	}

	/**
	 * Get profilingEnabled data
	 */
	@Override
	public List getProfilingData(String par1Str) {
		return null;
	}

	/**
	 * End current section and start a new section
	 */
	@Override
	public void endStartSection(String par1Str) {
	}
}
