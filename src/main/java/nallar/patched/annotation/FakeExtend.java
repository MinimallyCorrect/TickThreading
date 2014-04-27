package nallar.patched.annotation;

/**
 * This interface marks a class as not actually extending the class it extends,
 * this will be removed during patching and is only to allow MCP to remap it
 */
public @interface FakeExtend {
}
