package nallar.tickthreading.patcher.remapping;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import nallar.tickthreading.Log;
import nallar.tickthreading.util.IterableEnumerationWrapper;

public class ByteSource {
	public static final Map<String, byte[]> classes = new HashMap<String, byte[]>();
	private static final byte[] buffer = new byte[1048576];

	public static void addFiles(File... files) {
		for (File file : files) {
			if (file.isDirectory()) {
				addFiles(file.listFiles());
				continue;
			}
			String extension = file.getName().toLowerCase();
			extension = extension.substring(extension.lastIndexOf('.') + 1);
			if (!"jar".equals(extension) && !"zip".equals(extension)) {
				continue;
			}
			try {
				ZipFile zipFile = new ZipFile(file);
				try {
					addClasses(zipFile);
				} finally {
					zipFile.close();
				}
			} catch (IOException e) {
				Log.severe("Can't load file " + file, e);
			}
		}
	}

	private static void addClasses(final ZipFile zipFile) {
		for (ZipEntry zipEntry : (Iterable<ZipEntry>) new IterableEnumerationWrapper(zipFile.entries())) {
			if (zipEntry.isDirectory()) {
				continue;
			}
			String name = zipEntry.getName();
			int dotClass = name.lastIndexOf(".class");
			if (dotClass == -1) {
				continue;
			}
			name = name.substring(0, dotClass).replace('\\', '.').replace('/', '.');
			if (classes.containsKey(name)) {
				continue;
			}
			try {
				InputStream stream = zipFile.getInputStream(zipEntry);
				byte[] bytes = readFully(stream);
				classes.put(name, bytes);
				stream.close();
			} catch (Throwable t) {
				Log.severe("Failed to open class " + name + " in " + zipFile, t);
			}
		}
	}

	protected static byte[] readFully(InputStream stream) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(stream.available());

		int readBytes;

		while ((readBytes = stream.read(buffer, 0, buffer.length)) != -1) {
			bos.write(buffer, 0, readBytes);
		}

		return bos.toByteArray();
	}
}
