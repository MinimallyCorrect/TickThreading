package nallar.patched.annotation;

/**
 * Makes an inner class (name must be specified) of the class which is being extended public
 */
public @interface ExposeInner {
	String value();
}
