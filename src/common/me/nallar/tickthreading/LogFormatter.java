package me.nallar.tickthreading;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {
	static final String LINE_SEPARATOR = System.getProperty("line.separator");
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
	private int writtenSize = 0;

	public static void setFormat(boolean u, SimpleDateFormat dateFormat) {
	}

	@Override
	public String format(LogRecord record) {
		StringBuilder formattedMessage = new StringBuilder();
		formattedMessage.append(dateFormat.format(record.getMillis()));
		Level level = record.getLevel();

		String name = level.getLocalizedName();
		if (name == null) {
			name = level.getName();
		}

		formattedMessage.append(" [").append(name == null ? "" : name).append("] ");

		String loggerName = record.getLoggerName();
		formattedMessage
				.append('[').append(loggerName == null ? "" : loggerName).append("] ")
				.append(record.getMessage()).append(LINE_SEPARATOR);

		// No need to throw this, we're in a log formatter!
		@SuppressWarnings ("ThrowableResultOfMethodCallIgnored")
		Throwable throwable = record.getThrown();
		if (throwable != null) {
			if (throwable.getStackTrace().length == 0) {
				formattedMessage.append("Stack trace unavailable. Add -XX:-OmitStackTraceInFastThrow to your java parameters to see all stack traces.").append(LINE_SEPARATOR);
			} else {
				StringWriter stackTraceWriter = new StringWriter();
				// No need to close this - StringWriter.close() does nothing, and PrintWriter.close() just calls it.
				//noinspection IOResourceOpenedButNotSafelyClosed
				throwable.printStackTrace(new PrintWriter(stackTraceWriter));
				formattedMessage.append(stackTraceWriter.toString());
			}
		}

		if ((writtenSize += formattedMessage.length()) > (1024 * 1024 * 50)) { // 50MB
			writtenSize = Integer.MIN_VALUE;
			Log.disableDiskWriting("No more log messages will be recorded to disk, exceeded 50MB log size.");
		}

		return formattedMessage.toString();
	}
}
