package nallar.patched;

import java.util.List;

import net.minecraft.profiler.Profiler;

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
