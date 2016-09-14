package nallar.tickthreading.log;

import nallar.tickthreading.util.PropertyUtil;

import java.io.*;
import java.text.*;
import java.util.logging.*;

public class LogFormatter extends Formatter {
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");
	protected static final boolean colorEnabled = PropertyUtil.get("logColor", false);
	private static final boolean simplifyMcLoggerName = PropertyUtil.get("logSimplifyMinecraftServer", true);
	private static final String endColor = "\033[39m";
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss");

	public static void setDateFormat(SimpleDateFormat dateFormat) {
		if (dateFormat != null) {
			LogFormatter.dateFormat = dateFormat;
		}
	}

	private static String getStartColor(Level level) {
		return "\033[" + getColorForLevel(level) + 'm';
	}

	private static int getColorForLevel(Level level) {
		if (level == Level.SEVERE) {
			return 31;
		} else if (level == Level.WARNING) {
			return 33;
		} else if (level == DebugLevel.DEBUG) {
			return 35;
		} else if (level == Level.INFO) {
			return 32;
		}
		return 39;
	}

	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
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
		final boolean shouldColor = colorEnabled;
		if (shouldColor) {
			formattedMessage.append(getStartColor(level));
		}
		formattedMessage.append(record.getLevel().getName().toUpperCase());
		if (shouldColor) {
			formattedMessage.append(endColor);
		}
		formattedMessage.append("] ");


		String loggerName = record.getLoggerName();
		String message = record.getMessage();
		if (simplifyMcLoggerName && loggerName.equals("Minecraft-Server")) {
			loggerName = "Minecraft";
		} else if (message.startsWith("[") && message.contains("]")) {
			loggerName = null;
		} else if (loggerName.contains(".")) {
			loggerName = loggerName.substring(loggerName.lastIndexOf('.') + 1);
		}
		if (loggerName != null) {
			formattedMessage.append('[').append(loggerName).append("] ");
		}

		formattedMessage.append(message).append(LINE_SEPARATOR);

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
