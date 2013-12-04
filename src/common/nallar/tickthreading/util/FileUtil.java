package nallar.tickthreading.util;

import nallar.tickthreading.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public enum FileUtil {
	;

	public static void copyFile(File sourceFile, File destFile) throws IOException {
		if (!destFile.exists() && !destFile.createNewFile()) {
			Log.warning("Failed to create file " + destFile + " when copying from " + sourceFile);
		}

		FileChannel source = null;
		FileChannel destination = null;

		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());
		} finally {
			if (source != null) {
				source.close();
			}
			if (destination != null) {
				destination.close();
			}
		}
	}
}
