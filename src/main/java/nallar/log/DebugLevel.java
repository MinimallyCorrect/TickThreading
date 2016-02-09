package nallar.log;

import java.util.logging.*;

public class DebugLevel extends Level {
	public static final DebugLevel DEBUG = new DebugLevel();
	private static final long serialVersionUID = 0;

	private DebugLevel() {
		super("DEBUG", Level.SEVERE.intValue(), null);
	}
}
