package nallar.tickthreading.util;

import java.util.logging.*;

public class DebugLevel extends Level {
	public static DebugLevel DEBUG = new DebugLevel();
	private DebugLevel() {
		super("DEBUG", Level.SEVERE.intValue(), null);
	}
}
