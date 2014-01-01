package nallar.tickthreading.util;

import nallar.tickthreading.Log;

import java.io.*;
import java.nio.channels.*;

public enum FileUtil {
	;

	public static void copyFile(File sourceFile, File destFile) throws IOException {
		if (!destFile.exists() && !destFile.createNewFile()) {
			Log.warning("Failed to create file " + destFile + " when copying from " + sourceFile);
		}

		FileInputStream fileInputStream = new FileInputStream(sourceFile);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(destFile);
			try {
				FileChannel fileChannel = fileInputStream.getChannel();
				fileOutputStream.getChannel().transferFrom(fileChannel, 0, fileChannel.size());
			} finally {
				fileOutputStream.close();
			}
		} finally {
			fileInputStream.close();
		}
	}
}
