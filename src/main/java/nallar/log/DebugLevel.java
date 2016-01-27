package nallar.log;

import java.util.logging.*;

public class DebugLevel extends Level {
	private static final long serialVersionUID = 0;
	public static DebugLevel DEBUG = new DebugLevel();
	private DebugLevel() {
		super("DEBUG", Level.SEVERE.intValue(), null);
	}
}
