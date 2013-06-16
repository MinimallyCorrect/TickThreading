package nallar.nmsprepatcher;

import java.io.File;

public class Main {
	public static void main(String[] args) {
		File patchDirectory = new File(args[0]);
		File sourceDirectory = new File(args[1]);
		PrePatcher.patch(patchDirectory, sourceDirectory);
	}
}
