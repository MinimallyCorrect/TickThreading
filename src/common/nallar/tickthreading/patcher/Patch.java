package nallar.tickthreading.patcher;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes basic information for a patch
 */
@Retention (RetentionPolicy.RUNTIME)
public @interface Patch {
	// The name for the patch in the patch xml
	// default (or "") is treated as the name of the method
	String name() default "";

	// Required attributes, comma delimited. Default "" = none
	String requiredAttributes() default "";

	// If the constructor should be passed if no method is given and ambiguous parameters
	// Else, gives class
	boolean emptyConstructor() default true;
}
