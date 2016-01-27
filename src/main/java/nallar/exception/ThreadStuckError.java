package nallar.exception;

/**
 * Thrown via Thread.stop() to try to resolve a deadlock, should be caught in Thread.run(), and thread should attempt to resume working.
 * This is a bad idea according to good java developers, because it results in undefined behaviour.
 * Which is correct, but in some cases we prefer undefined behaviour to definitely broken behaviour!
 */
public class ThreadStuckError extends Error {
	private static final long serialVersionUID = 0;
	private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

	public ThreadStuckError() {
		super();
	}

	public ThreadStuckError(final String message) {
		super(message);
	}

	public ThreadStuckError(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ThreadStuckError(final Throwable cause) {
		super(cause);
	}

	@Override
	public StackTraceElement[] getStackTrace() {
		return EMPTY_STACK_TRACE;
	}
}
