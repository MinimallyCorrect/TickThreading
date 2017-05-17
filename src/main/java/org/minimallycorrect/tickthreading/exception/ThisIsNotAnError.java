package org.minimallycorrect.tickthreading.exception;

public class ThisIsNotAnError extends Error {
	private static final long serialVersionUID = 0;

	@Override
	public String getMessage() {
		return "This is not an error.";
	}
}
