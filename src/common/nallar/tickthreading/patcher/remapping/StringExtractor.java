package nallar.tickthreading.patcher.remapping;

import javassist.bytecode.ConstPool;
import nallar.tickthreading.Log;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

public enum StringExtractor {
	;

	public static Iterable<String> getStrings(byte[] classFile) {
		return getStrings(new ByteArrayInputStream(classFile));
	}

	public static Iterable<String> getStrings(InputStream classFile) {
		DataInputStream dataInputStream = new DataInputStream(classFile);
		try {
			int magic = dataInputStream.readInt();
			if (magic != 0xCAFEBABE) {
				throw new IOException("Supplied file is not a valid class file.");
			}
			int minor = dataInputStream.readUnsignedShort();
			int major = dataInputStream.readUnsignedShort();
			if (Log.debug) {
				Log.debug("Reading constPool entry for " + major + '.' + minor);
			}
			ArrayList<String> strings = new ArrayList<String>();
			ConstPool constPool = new ConstPool(dataInputStream);
			for (int i = 1; true; i++) {
				try {
					strings.add(constPool.getUtf8Info(i));
				} catch (ClassCastException ignored) {
				} catch (NullPointerException e) {
					break;
				}
			}
			return strings;
		} catch (IOException e) {
			Log.severe("Failed to read constPool", e);
			return Collections.emptyList();
		} finally {
			try {
				dataInputStream.close();
			} catch (IOException ignored) {
			}
		}
	}
}
