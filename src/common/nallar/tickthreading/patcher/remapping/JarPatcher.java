package nallar.tickthreading.patcher.remapping;

import LZMA.LzmaInputStream;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import cpw.mods.fml.repackage.com.nothome.delta.GDiffPatcher;
import nallar.tickthreading.Log;
import nallar.unsafe.UnsafeUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.regex.Pattern;

public enum JarPatcher {
	INSTANCE;
	private static final Pattern binpatchMatcher = Pattern.compile("binpatch/server/.*.binpatch");
	private GDiffPatcher patcher = new GDiffPatcher();
	private ListMultimap<String, DeltaClassPatch> patches;

	public void setup(InputStream binpatchesCompressed) {
		JarInputStream jis;
		try {
			LzmaInputStream binpatchesDecompressed = new LzmaInputStream(binpatchesCompressed);
			try {
				ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
				JarOutputStream jos = new JarOutputStream(jarBytes);
				Pack200.newUnpacker().unpack(binpatchesDecompressed, jos);
				jis = new JarInputStream(new ByteArrayInputStream(jarBytes.toByteArray()));
			} finally {
				binpatchesCompressed.close();
			}
		} catch (Exception e) {
			Log.severe("Failed to load binary patches", e);
			throw UnsafeUtil.throwIgnoreChecked(e);
		}

		patches = ArrayListMultimap.create();

		do {
			try {
				JarEntry entry = jis.getNextJarEntry();
				if (entry == null) {
					break;
				}
				if (binpatchMatcher.matcher(entry.getName()).matches()) {
					DeltaClassPatch cp = readPatch(entry, jis);
					if (cp != null) {
						patches.put(cp.sourceClassName, cp);
					}
				} else {
					jis.closeEntry();
				}
			} catch (IOException ignored) {
			}
		} while (true);
		Log.info("Read " + patches.size() + " binary patches");
	}

	private byte[] applyPatch(String name, String mappedName, byte[] inputData) {
		if (patches == null) {
			return inputData;
		}
		List<DeltaClassPatch> list = patches.get(name);
		if (list.isEmpty()) {
			return inputData;
		}
		for (DeltaClassPatch patch : list) {
			if (!patch.targetClassName.equals(mappedName) && !patch.sourceClassName.equals(name)) {
				Log.warning("Binary patch found " + patch.targetClassName + " for wrong class " + mappedName);
			}
			if (!patch.existsAtTarget && (inputData == null || inputData.length == 0)) {
				inputData = new byte[0];
			} else if (!patch.existsAtTarget) {
				Log.warning("Patcher expecting empty class data file for " + patch.targetClassName + ", but received non-empty");
			} else {
				int inputChecksum = Hashing.adler32().hashBytes(inputData).asInt();
				if (patch.inputChecksum != inputChecksum) {
					Log.severe("Class " + patch.targetClassName + " (" + patch.name + ") did not have expected checksum for binary patching.");
					continue;
				}
			}
			try {
				inputData = patcher.patch(inputData, patch.patch);
			} catch (IOException e) {
				Log.severe("Failed to patch class " + patch.targetClassName, e);
			}
		}
		return inputData;
	}

	private DeltaClassPatch readPatch(JarEntry patchEntry, JarInputStream jis) {
		ByteArrayDataInput input;
		try {
			input = ByteStreams.newDataInput(ByteStreams.toByteArray(jis));
		} catch (IOException e) {
			Log.warning("Unable to read binpatch file " + patchEntry.getName() + " - ignoring", e);
			return null;
		}
		String name = input.readUTF();
		String sourceClassName = input.readUTF();
		String targetClassName = input.readUTF();
		boolean exists = input.readBoolean();
		int inputChecksum = 0;
		if (exists) {
			inputChecksum = input.readInt();
		}
		int patchLength = input.readInt();
		byte[] patchBytes = new byte[patchLength];
		input.readFully(patchBytes);

		return new DeltaClassPatch(name, sourceClassName, targetClassName, exists, inputChecksum, patchBytes);
	}
}
