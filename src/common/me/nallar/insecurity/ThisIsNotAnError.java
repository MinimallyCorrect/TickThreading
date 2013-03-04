package me.nallar.insecurity;

public class ThisIsNotAnError extends Throwable {
	@Override
	public String getMessage() {
		return "This is not an error.";
	}
}
