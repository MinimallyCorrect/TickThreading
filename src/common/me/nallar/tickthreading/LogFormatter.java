package me.nallar.tickthreading;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
	private static final String lineSeparator = System.getProperty("line.separator");
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");

	@Override
	public String format(LogRecord record) {
		StringBuilder formattedMessage = new StringBuilder();
		formattedMessage.append(this.dateFormat.format(record.getMillis()));
		Level level = record.getLevel();

		if (level == Level.FINEST) {
			formattedMessage.append(" [FINEST] ");
		} else if (level == Level.FINER) {
			formattedMessage.append(" [FINER] ");
		} else if (level == Level.FINE) {
			formattedMessage.append(" [FINE] ");
		} else if (level == Level.INFO) {
			formattedMessage.append(" [INFO] ");
		} else if (level == Level.WARNING) {
			formattedMessage.append(" [WARNING] ");
		} else if (level == Level.SEVERE) {
			formattedMessage.append(" [SEVERE] ");
		} else {
			formattedMessage.append(" [").append(level.getLocalizedName()).append("] ");
		}

		if (record.getLoggerName() != null) {
			formattedMessage.append('[').append(record.getLoggerName()).append("] ");
		}

		formattedMessage.append(record.getMessage().replace("\r\n", "\n").replace("\n", lineSeparator).trim()).append(lineSeparator);

		Throwable throwable = record.getThrown();
		if (throwable != null) {
			StringWriter throwableDump = new StringWriter();
			throwable.printStackTrace(new PrintWriter(throwableDump));
			formattedMessage.append(throwableDump.toString());
			try {
				throwableDump.close();
			} catch (IOException e) {
				// Not logged, might cause infinitely recursive logging failure
			}
		}

		return formattedMessage.toString();
	}
}
