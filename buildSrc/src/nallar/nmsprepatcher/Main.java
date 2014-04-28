package nallar.nmsprepatcher;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.jar.*;

public class Main {
	/**
	 * Called to load patches from a given directory
	 * @param patchDirectory patch directory
	 */
	public static void loadPatches(File patchDirectory) {
		PrePatcher.loadPatches(patchDirectory);
	}

	/**
	 * @param path  name of the class, seperated by '/'
	 * @param bytes bytes of the class
	 * @return
	 */
	private static byte[] manipulateBinary(String path, byte[] bytes) {
		return PrePatcher.patchCode(bytes, path);
	}

	/**
	 * @param path   name of the class, seperated by '/'
	 * @param source of the class
	 * @return
	 */
	private static String manipulateSource(String path, String source) {
		return PrePatcher.patchSource(source, path);
	}


	/**
	 * @param jar    File
	 * @param source if TRUE source, if FALSE binary
	 */
	public static void editJar(File jar, boolean source) throws Exception {
		HashMap<String, byte[]> stuff = Maps.newHashMap();

		// READING
		JarInputStream istream = new JarInputStream(new FileInputStream(jar));
		JarEntry entry;
		while ((entry = istream.getNextJarEntry()) != null) {
			byte[] classBytes = ByteStreams.toByteArray(istream);
			if (entry.getName().endsWith(source ? ".java" : ".class")) {
				// PARSING
				String name = entry.getName().replace('\\', '/');

				if (source) {
					String str = new String(classBytes, Charsets.UTF_8);
					str = manipulateSource(name, str);
					classBytes = str.getBytes(Charsets.UTF_8);
				} else {
					classBytes = manipulateBinary(name, classBytes);
				}
			}
			stuff.put(entry.getName(), classBytes);

			istream.closeEntry();
		}
		istream.close();

		// WRITING
		JarOutputStream ostream = new JarOutputStream(new FileOutputStream(jar));
		for (Entry<String, byte[]> e : stuff.entrySet()) {
			ostream.putNextEntry(new JarEntry(e.getKey()));
			ostream.write(e.getValue());
			ostream.closeEntry();
		}
		ostream.close();
	}
}
