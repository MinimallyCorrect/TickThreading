package javassist;

import nallar.tickthreading.PatchLog;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.*;
import java.net.*;

/**
 * This is in the javassist package (which isn't sealed) to access package-local javassist internals needed to load
 * from the classloader at runtime for patching purposes.
 */
public class ClassLoaderPool extends ClassPool {
	private final boolean preSrg;

	public ClassLoaderPool(boolean preSrg) {
		this.preSrg = preSrg;
		this.appendSystemPath();
		this.importPackage("java.util");
		this.importPackage("nallar.collections");
	}

	@Override
	protected void cacheCtClass(String className, CtClass c, boolean dynamic) {
		super.cacheCtClass(className, c, dynamic);
	}

	@Override
	public CtClass getCached(String className) {
		return super.getCached(className);
	}

	@Override
	protected synchronized CtClass get0(String className, boolean useCache) throws NotFoundException {
		return super.get0(className, true);
	}

	byte[] getClassBytesRuntime(String className) {
		if (LaunchClassLoader.instance.excluded(className.replace('/', '.'))) {
			return null;
		}
		byte[] bytes = preSrg ? LaunchClassLoader.instance.getPreSrgBytes(className) : LaunchClassLoader.instance.getSrgBytes(className);
		if (bytes == null) {
			PatchLog.fine("Failed to find class " + className + ", preSrg: " + preSrg);
		}
		return bytes;
	}

	@Override
	public URL find(String className) {
		byte[] bytes = getClassBytesRuntime(className);
		if (bytes != null) {
			try {
				return new URL(null, "runtimeclass:" + className.replace(".", "/"), new Handler(bytes));
			} catch (MalformedURLException e) {
				PatchLog.severe("Failed to make fake URL for " + className, e);
			}
		}
		return source.find(className);
	}

	@Override
	InputStream openClassfile(String className) throws NotFoundException {
		byte[] bytes = getClassBytesRuntime(className);
		if (bytes != null) {
			return new ByteArrayInputStream(bytes);
		}
		return source.openClassfile(className);
	}

	@Override
	void writeClassfile(String className, OutputStream out) throws NotFoundException, IOException, CannotCompileException {
		byte[] bytes = getClassBytesRuntime(className);
		if (bytes != null) {
			out.write(bytes);
		} else {
			source.writeClassfile(className, out);
		}
	}

	public static class Handler extends URLStreamHandler {
		final byte[] data;

		public Handler(byte[] data) {
			this.data = data;
		}

		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			return new MockHttpURLConnection(u, data);
		}

		public static class MockHttpURLConnection extends HttpURLConnection {
			private final byte[] data;

			protected MockHttpURLConnection(URL url, byte[] data) {
				super(url);
				this.data = data;
			}

			@Override
			public InputStream getInputStream() throws IOException {
				return new ByteArrayInputStream(data);
			}

			@Override
			public void connect() throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public void disconnect() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean usingProxy() {
				return false;
			}

		}
	}
}
