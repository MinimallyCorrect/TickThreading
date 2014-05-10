package nallar.tickthreading.util;

public class ThisIsNotAnError extends Error {
	@Override
	public String getMessage() {
		return "This is not an error.";
	}
}
