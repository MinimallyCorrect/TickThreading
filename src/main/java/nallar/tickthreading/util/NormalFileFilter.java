package nallar.tickthreading.util;

import java.io.*;

public enum NormalFileFilter implements FileFilter {
	$;

	@Override
	public boolean accept(final File file) {
		return !file.isDirectory() & file.isFile();
	}
}
