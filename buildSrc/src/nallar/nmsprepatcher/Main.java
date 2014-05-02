package nallar.nmsprepatcher;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class Main {
	/**
	 * Called to load patches from a given directory
	 *
	 * @param patchDirectory patch directory
	 */
	public static void loadPatches(File patchDirectory) {
		try {
			PrePatcher.loadPatches(patchDirectory);
		} catch (Throwable t) {
			t.printStackTrace();
			Throwables.propagate(t);
		}
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

		File generatedSrcDirectory = new File("./buildSrc/generated/");

		if (source) {
			if (generatedSrcDirectory.exists()) {
				deleteDirectory(generatedSrcDirectory.toPath());
			}
			generatedSrcDirectory.mkdir();
		}

		// WRITING
		JarOutputStream ostream = new JarOutputStream(new FileOutputStream(jar));
		for (Entry<String, byte[]> e : stuff.entrySet()) {
			ostream.putNextEntry(new JarEntry(e.getKey()));
			ostream.write(e.getValue());
			ostream.closeEntry();

			if (source && e.getValue().length > 0) {
				File f = new File(generatedSrcDirectory, e.getKey());
				f.getParentFile().mkdirs();
				Files.write(e.getValue(), f);
			}
		}
		ostream.close();
	}

	public static void deleteDirectory(Path path) throws IOException {
		java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				java.nio.file.Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				// try to delete the file anyway, even if its attributes
				// could not be read, since delete-only access is
				// theoretically possible
				java.nio.file.Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc == null) {
					java.nio.file.Files.delete(dir);
					return FileVisitResult.CONTINUE;
				} else {
					// directory iteration failed; propagate exception
					throw exc;
				}
			}
		});
	}
}
