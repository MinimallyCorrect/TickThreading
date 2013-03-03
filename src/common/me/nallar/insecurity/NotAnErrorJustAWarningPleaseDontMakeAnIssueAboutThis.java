package me.nallar.insecurity;

public class NotAnErrorJustAWarningPleaseDontMakeAnIssueAboutThis extends Throwable {
	@Override
	public String getMessage() {
		return "This is not an error.";
	}
}
