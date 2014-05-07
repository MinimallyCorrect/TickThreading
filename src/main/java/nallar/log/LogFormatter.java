package nallar.log;

import java.io.*;
import java.text.*;
import java.util.logging.*;

public class LogFormatter extends Formatter {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
	protected static final boolean colorEnabled = System.getProperty("colorLogs") != null;

	public static void setFormat(boolean u, SimpleDateFormat dateFormat) {
		if (dateFormat != null) {
			LogFormatter.dateFormat = dateFormat;
		}
	}

	protected boolean shouldColor() {
		return false;
	}

	private static String getColorForLevel(Level level) {
		return  "\033[" + getColorNumberForLevel(level) + 'm';
	}

	private static String getColorNumberForLevel(Level level) {
		if (level == Level.SEVERE) {
			return "31";
		} else if (level == Level.WARNING) {
			return "33";
		} else if (level == DebugLevel.DEBUG) {
			return "35";
		} else if (level == Level.INFO) {
			return "32";
		}
		return "39";
	}

	protected static String getEndColor() {
		return "\033[39m";
	}

	@Override
	public String format(LogRecord record) {
		StringBuilder formattedMessage = new StringBuilder();
		long millis = record.getMillis();
		String date;
		SimpleDateFormat dateFormat = LogFormatter.dateFormat;
		synchronized (dateFormat) {
			date = dateFormat.format(millis);
		}
		formattedMessage.append(date);
		Level level = record.getLevel();
		formattedMessage.append(" [");
		final boolean shouldColor = shouldColor();
		if (shouldColor) {
			formattedMessage.append(getColorForLevel(level));
		}
		formattedMessage.append(record.getLevel().getName().toUpperCase());
		if (shouldColor) {
			formattedMessage.append(getEndColor());
		}
		formattedMessage.append("] ");


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
				String output = stackTraceWriter.toString();
				if (throwable.getClass() == Throwable.class) {
					output = output.replace("java.lang.Throwable\n", "");
				}

				formattedMessage.append(output);
			}

			if (throwable.getCause() != null && throwable.getCause().getStackTrace().length == 0) {
				formattedMessage.append("Stack trace unavailable for cause: ").append(String.valueOf(throwable.getCause())).append('-').append(throwable.getCause().getClass().getName()).append(". Add -XX:-OmitStackTraceInFastThrow to your java parameters to see all stack traces.").append(LINE_SEPARATOR);
			}
		}

		return formattedMessage.toString();
	}
}
