package nallar.tickthreading.patcher.remapping;

import LZMA.LzmaInputStream;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.common.io.OutputSupplier;
import cpw.mods.fml.repackage.com.nothome.delta.GDiffPatcher;
import nallar.tickthreading.Log;
import nallar.tickthreading.util.FileUtil;
import nallar.tickthreading.util.IterableEnumerationWrapper;
import nallar.unsafe.UnsafeUtil;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public enum JarPatcher {
	INSTANCE;
	private static final Pattern binpatchMatcher = Pattern.compile("binpatch/server/.*.binpatch");
	private GDiffPatcher patcher = new GDiffPatcher();
	private ListMultimap<String, DeltaClassPatch> patches;

	private void waitForStupid() {
		try {
			// THANKS WINDOWS/JAVA
			// closing a file handle on time would be stupid, right?
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void patchAll(File file) throws IOException {
		waitForStupid();
		File tempFile = new File(file.getName() + ".tmp");
		FileUtil.copyFile(file, tempFile);
		waitForStupid();
		file.delete(); // Yes, we try twice. Because that makes it work. No, I don't know why.
		file.delete();
		final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file));
		try {
			JarFile input = new JarFile(tempFile);
			try {
				for (ZipEntry zipEntry : new IterableEnumerationWrapper<ZipEntry>((Enumeration<ZipEntry>) ((ZipFile) input).entries())) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ByteStreams.copy(input.getInputStream(zipEntry), baos);
					byte[] clazz = baos.toByteArray();
					clazz = applyPatch(zipEntry.getName(), clazz);
					ZipEntry outputEntry = new ZipEntry(zipEntry.getName());
					jarOutputStream.putNextEntry(outputEntry);
					ByteStreams.copy(new ByteArrayInputStream(clazz), jarOutputStream);
				}
			} finally {
				input.close();
			}
		} finally {
			jarOutputStream.close();
		}
	}

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

	private byte[] applyPatch(String name, byte[] inputData) {
		if (patches == null) {
			return inputData;
		}
		List<DeltaClassPatch> list = patches.get(name);
		if (list.isEmpty()) {
			return inputData;
		}
		for (DeltaClassPatch patch : list) {
			if (!patch.sourceClassName.equals(name)) {
				Log.warning("Binary patch found " + patch.targetClassName + " for wrong class " + name);
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
