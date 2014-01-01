package nallar.tickthreading;

import java.io.*;
import java.text.*;
import java.util.logging.*;

public class LogFormatter extends Formatter {
	static final String LINE_SEPARATOR = System.getProperty("line.separator");
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private int writtenSize = 0;

	public static void setFormat(boolean u, SimpleDateFormat dateFormat) {
		if (dateFormat != null) {
			LogFormatter.dateFormat = dateFormat;
		}
	}

	@Override
	public String format(LogRecord record) {
		StringBuilder formattedMessage = new StringBuilder();
		formattedMessage.append(dateFormat.format(record.getMillis()));
		Level level = record.getLevel();

		String name = level.getName().toUpperCase();

		formattedMessage.append(" [").append(name == null ? "" : name).append("] ");

		String loggerName = record.getLoggerName();
		formattedMessage
				.append('[').append(loggerName == null ? "" : loggerName).append("] ")
				.append(record.getMessage()).append(LINE_SEPARATOR);

		// No need to throw this, we're in a log formatter!
		@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
		Throwable throwable = record.getThrown();
		if (throwable != null) {
			if (throwable.getClass().getName().endsWith("ThreadStuckError")) {
				return "";
			}
			if (throwable.getStackTrace().length == 0) {
				formattedMessage.append("Stack trace unavailable for ").append(String.valueOf(throwable)).append('-').append(throwable.getClass().getName()).append(". Add -XX:-OmitStackTraceInFastThrow to your java parameters to see all stack traces.").append(LINE_SEPARATOR);
			} else {
				StringWriter stackTraceWriter = new StringWriter();
				// No need to close this - StringWriter.close() does nothing, and PrintWriter.close() just calls it.
				//noinspection IOResourceOpenedButNotSafelyClosed
				throwable.printStackTrace(new PrintWriter(stackTraceWriter));
				formattedMessage.append(throwable.getClass() == Throwable.class ? stackTraceWriter.toString().replace("java.lang.Throwable\n", "") : stackTraceWriter.toString());
			}
		}

		if ((writtenSize += formattedMessage.length()) > (1024 * 1024 * 50)) { // 50MB
			writtenSize = Integer.MIN_VALUE;
			Log.disableDiskWriting("No more log messages will be recorded to disk, exceeded 50MB log size.");
		}

		return formattedMessage.toString();
	}
}
