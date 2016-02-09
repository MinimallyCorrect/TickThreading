package nallar.exception;

public class ConcurrencyError extends Error {
	private static final long serialVersionUID = 0;

	public ConcurrencyError(String s) {
		super(s);
	}
}
