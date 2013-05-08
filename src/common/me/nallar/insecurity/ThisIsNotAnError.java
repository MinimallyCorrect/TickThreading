package me.nallar.insecurity;

class ThisIsNotAnError extends Throwable {
	@Override
	public String getMessage() {
		return "This is not an error.";
	}
}
