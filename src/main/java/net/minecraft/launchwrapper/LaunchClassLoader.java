package net.minecraft.launchwrapper;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import nallar.insecurity.InsecurityManager;
import nallar.tickthreading.patcher.PatchHook;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.jar.Attributes.*;
import java.util.logging.*;

public class LaunchClassLoader extends URLClassLoader {
	private IClassTransformer deobfuscationTransformer;
	private final List<URL> sources;
	private final ClassLoader parent = getClass().getClassLoader();

	private final List<IClassTransformer> transformers = new ArrayList<IClassTransformer>(2);
	private final Map<String, Class<?>> cachedClasses = new HashMap<String, Class<?>>(1000);
	private final Set<String> invalidClasses = new HashSet<String>(1000);

	private final Set<String> classLoaderExceptions = new HashSet<String>();
	private final Set<String> transformerExceptions = new HashSet<String>();
	private final Map<String, byte[]> resourceCache = new HashMap<String, byte[]>(1000);

	private IClassNameTransformer renameTransformer;

	private static final String[] RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

	private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
	private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
	private static final boolean DEBUG_SAVE = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingSave", "false"));
	private static File tempFolder = null;
	public static LaunchClassLoader instance;

	public LaunchClassLoader(URL[] sources) {
		super(sources, null);
		instance = this;
		this.sources = new ArrayList<URL>(Arrays.asList(sources));
		Thread.currentThread().setContextClassLoader(this);

		// classloader exclusions
		addClassLoaderExclusion("java.");
		addClassLoaderExclusion("javassist.");
		addClassLoaderExclusion("sun.");
		addClassLoaderExclusion("org.lwjgl.");
		addClassLoaderExclusion("org.apache.logging.");
		addClassLoaderExclusion("net.minecraft.launchwrapper.");

		// transformer exclusions
		addTransformerExclusion("argo.");
		addTransformerExclusion("javax.");
		addTransformerExclusion("org.objectweb.asm.");
		addTransformerExclusion("com.google.common.");
		addTransformerExclusion("org.bouncycastle.");
		addTransformerExclusion("net.minecraft.launchwrapper.injector.");

		if (DEBUG_SAVE) {
			int x = 1;
			tempFolder = new File(Launch.minecraftHome, "CLASSLOADER_TEMP");
			while (tempFolder.exists() && x <= 10) {
				tempFolder = new File(Launch.minecraftHome, "CLASSLOADER_TEMP" + x++);
			}

			if (tempFolder.exists()) {
				LogWrapper.info("DEBUG_SAVE enabled, but 10 temp directories already exist, clean them and try again.");
				tempFolder = null;
			} else {
				LogWrapper.info("DEBUG_SAVE Enabled, saving all classes to \"%s\"", tempFolder.getAbsolutePath().replace('\\', '/'));
				if (!tempFolder.mkdirs()) {
					LogWrapper.info("Failed to make tempFolder: " + tempFolder);
				}
			}
		}
	}

	private boolean initedTTPatcher = false;
	public static final long launchTime = System.currentTimeMillis();

	public void registerTransformer(String transformerClassName) {
		try {
			if (!initedTTPatcher) {
				initedTTPatcher = true;
				try {
					FMLRelaunchLog.finest("Dummy log message to make sure that FMLRelaunchLog has been set up.");
				} catch (Throwable t) {
					System.err.println("Failure in FMLRelaunchLog");
					t.printStackTrace(System.err);
				}
				try {
					InsecurityManager.init();
				} catch (Throwable t) {
					System.err.println("Failed to set up Security Manager. This is probably not a huge problem - but it could indicate classloading issues.");
				}
				Class.forName("nallar.tickthreading.patcher.PatchHook");
			}
			IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).getConstructor().newInstance();
			if (transformer instanceof IClassNameTransformer && renameTransformer == null) {
				renameTransformer = (IClassNameTransformer) transformer;
			}
			if (transformerClassName.equals("cpw.mods.fml.common.asm.transformers.DeobfuscationTransformer")) {
				deobfuscationTransformer = transformer;
				ArrayList<IClassTransformer> oldTransformersList = new ArrayList<IClassTransformer>(transformers);
				transformers.clear();
				IClassTransformer eventTransformer = null;
				for (IClassTransformer transformer_ : oldTransformersList) {
					if (transformer_.getClass().getName().equals("net.minecraftforge.transformers.EventTransformer")) {
						eventTransformer = transformer_;
					} else {
						transformers.add(transformer_);
					}
				}
				transformers.add(transformer);
				if (eventTransformer == null) {
					FMLRelaunchLog.severe("Failed to find event transformer.");
				} else {
					transformers.add(eventTransformer);
				}
			} else {
				transformers.add(transformer);
			}
		} catch (Exception e) {
			LogWrapper.log(Level.SEVERE, "Critical problem occurred registering the ASM transformer class %s", transformerClassName);
		}
	}

	public boolean excluded(String name) {
		for (final String exception : classLoaderExceptions) {
			if (name.startsWith(exception)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Class<?> findClass(final String name) throws ClassNotFoundException {
		if (invalidClasses.contains(name)) {
			throw new ClassNotFoundException(name);
		}

		if (excluded(name)) {
			return parent.loadClass(name);
		}

		Class<?> cached = cachedClasses.get(name);
		if (cached != null) {
			return cached;
		}

		for (final String exception : transformerExceptions) {
			if (name.startsWith(exception)) {
				try {
					final Class<?> clazz = super.findClass(name);
					cachedClasses.put(name, clazz);
					return clazz;
				} catch (ClassNotFoundException e) {
					invalidClasses.add(name);
					throw e;
				}
			}
		}

		try {
			final String transformedName = transformName(name);
			cached = cachedClasses.get(transformedName);
			if (cached != null) {
				return cached;
			}

			final String untransformedName = untransformName(name);

			final int lastDot = untransformedName.lastIndexOf('.');
			final String packageName = lastDot == -1 ? "" : untransformedName.substring(0, lastDot);
			final String fileName = untransformedName.replace('.', '/') + ".class";
			URLConnection urlConnection = findCodeSourceConnectionFor(fileName);

			CodeSigner[] signers = null;

			byte[] classBytes = null;
			if (lastDot > -1 && !untransformedName.startsWith("net.minecraft.")) {
				if (urlConnection instanceof JarURLConnection) {
					final JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
					final JarFile jarFile = jarURLConnection.getJarFile();

					if (jarFile != null && jarFile.getManifest() != null) {
						final Manifest manifest = jarFile.getManifest();
						final JarEntry entry = jarFile.getJarEntry(fileName);

						Package pkg = getPackage(packageName);
						classBytes = getClassBytes(untransformedName);
						signers = entry.getCodeSigners();
						if (pkg == null) {
							definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
						} else {
							if (pkg.isSealed() && !pkg.isSealed(jarURLConnection.getJarFileURL())) {
								LogWrapper.severe("The jar file %s is trying to seal already secured path %s", jarFile.getName(), packageName);
							} else if (isSealed(packageName, manifest)) {
								LogWrapper.severe("The jar file %s has a security seal for path %s, but that path is defined and not secure", jarFile.getName(), packageName);
							}
						}
					}
				} else {
					Package pkg = getPackage(packageName);
					if (pkg == null) {
						definePackage(packageName, null, null, null, null, null, null, null);
					} else if (pkg.isSealed()) {
						LogWrapper.severe("The URL %s is defining elements for sealed path %s", urlConnection.getURL(), packageName);
					}
				}
			}

			if (classBytes == null) {
				classBytes = getClassBytes(untransformedName);
			}

			final byte[] transformedClass = runTransformers(untransformedName, transformedName, classBytes);
			if (DEBUG_SAVE) {
				saveTransformedClass(transformedClass, transformedName);
			}

			final CodeSource codeSource = urlConnection == null ? null : new CodeSource(urlConnection.getURL(), signers);
			final Class<?> clazz = defineClass(transformedName, transformedClass, 0, transformedClass.length, codeSource);
			cachedClasses.put(transformedName, clazz);
			return clazz;
		} catch (Throwable e) {
			invalidClasses.add(name);
			if (DEBUG) {
				LogWrapper.log(Level.FINE, e, "Exception encountered attempting classloading of %s", name);
			}
			throw new ClassNotFoundException(name, e);
		}
	}

	private static void saveTransformedClass(final byte[] data, final String transformedName) {
		if (tempFolder == null) {
			return;
		}

		final File outFile = new File(tempFolder, transformedName.replace('.', File.separatorChar) + ".class");
		final File outDir = outFile.getParentFile();

		if (!outDir.exists() && !outDir.mkdirs()) {
			LogWrapper.warning("Failed to make outDir: " + outDir);
		}

		if (outFile.exists() && !outFile.delete()) {
			LogWrapper.warning("Failed to delete outFile: " + outFile);
		}

		try {
			LogWrapper.fine("Saving transformed class \"%s\" to \"%s\"", transformedName, outFile.getAbsolutePath().replace('\\', '/'));

			final OutputStream output = new FileOutputStream(outFile);
			output.write(data);
			output.close();
		} catch (IOException ex) {
			LogWrapper.log(Level.WARNING, ex, "Could not save transformed class \"%s\"", transformedName);
		}
	}

	private String untransformName(final String name) {
		if (renameTransformer != null) {
			return renameTransformer.unmapClassName(name);
		}

		return name;
	}

	private String transformName(final String name) {
		if (renameTransformer != null) {
			return renameTransformer.remapClassName(name);
		}

		return name;
	}

	private static boolean isSealed(final String path, final Manifest manifest) {
		Attributes attributes = manifest.getAttributes(path);
		if (attributes == null) {
			attributes = manifest.getMainAttributes();
		}
		return attributes != null && "true".equalsIgnoreCase(attributes.getValue(Name.SEALED));
	}

	private URLConnection findCodeSourceConnectionFor(final String name) {
		final URL resource = findResource(name);
		if (resource != null) {
			try {
				return resource.openConnection();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return null;
	}

	@SuppressWarnings("ConstantConditions")
	private static byte[] runTransformer(final String name, final String transformedName, byte[] basicClass, final IClassTransformer transformer) {
		try {
			return transformer.transform(name, transformedName, basicClass);
		} catch (Throwable t) {
			String message = t.getMessage();
			if (message != null && message.contains("for invalid side")) {
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				} else if (t instanceof Error) {
					throw (Error) t;
				} else {
					throw new RuntimeException(t);
				}
			} else if (basicClass != null || DEBUG_FINER) {
				FMLRelaunchLog.log((DEBUG_FINER && basicClass != null) ? Level.WARNING : Level.FINE, t, "Failed to transform " + name);
			}
			return basicClass;
		}
	}

	private byte[] runTransformers(final String name, final String transformedName, byte[] basicClass) {
		basicClass = PatchHook.preSrgTransformationHook(name, transformedName, basicClass);
		if (deobfuscationTransformer == null) {
			if (transformedName.startsWith("net.minecraft.") && !transformedName.contains("ClientBrandRetriever")) {
				FMLRelaunchLog.log(Level.WARNING, new Throwable(), "Transforming " + name + " before SRG transformer has been added.");
			}
			for (final IClassTransformer transformer : transformers) {
				basicClass = runTransformer(name, transformedName, basicClass, transformer);
			}
			return basicClass;
		}
		basicClass = transformUpToSrg(name, transformedName, basicClass);
		basicClass = PatchHook.postSrgTransformationHook(name, transformedName, basicClass);
		basicClass = transformAfterSrg(name, transformedName, basicClass);
		return basicClass;
	}

	private final HashMap<String, byte[]> cachedSrgClasses = new HashMap<String, byte[]>();

	private byte[] transformUpToSrg(final String name, final String transformedName, byte[] basicClass) {
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
			return cached;
		}
		for (final IClassTransformer transformer : transformers) {
			basicClass = runTransformer(name, transformedName, basicClass, transformer);
			if (transformer == deobfuscationTransformer) {
				cachedSrgClasses.put(transformedName, basicClass);
				return basicClass;
			}
		}
		throw new RuntimeException("No SRG transformer!" + transformers.toString() + " -> " + deobfuscationTransformer);
	}

	private byte[] transformAfterSrg(final String name, final String transformedName, byte[] basicClass) {
		boolean pastSrg = false;
		for (final IClassTransformer transformer : transformers) {
			if (pastSrg) {
				basicClass = runTransformer(name, transformedName, basicClass, transformer);
			} else if (transformer == deobfuscationTransformer) {
				pastSrg = true;
			}
		}
		if (!pastSrg) {
			throw new RuntimeException("No SRG transformer!");
		}
		return basicClass;
	}

	public byte[] getPreSrgBytes(String name) {
		name = untransformName(name);
		try {
			return getClassBytes(name);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public byte[] getSrgBytes(String name) {
		final String transformedName = transformName(name);
		name = untransformName(name);
		byte[] cached = cachedSrgClasses.get(transformedName);
		if (cached != null) {
			return cached;
		}
		try {
			byte[] bytes = getClassBytes(name);
			return transformUpToSrg(name, transformedName, bytes);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	@Override
	public void addURL(final URL url) {
		super.addURL(url);
		sources.add(url);
	}

	public List<URL> getSources() {
		return sources;
	}

	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
	private static final ThreadLocal<byte[]> loadBuffer = new ThreadLocal<byte[]>() {
		@Override
		public byte[] initialValue() {
			return new byte[1048756];
		}
	};

	protected static byte[] readFully(InputStream stream) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream(stream.available());

			int readBytes;
			byte[] buffer = loadBuffer.get();

			while ((readBytes = stream.read(buffer, 0, buffer.length)) != -1) {
				bos.write(buffer, 0, readBytes);
			}

			return bos.toByteArray();
		} catch (Throwable t) {
			FMLRelaunchLog.log(Level.SEVERE, t, "Problem loading class");
			return EMPTY_BYTE_ARRAY;
		}
	}

	public List<IClassTransformer> getTransformers() {
		return Collections.unmodifiableList(transformers);
	}

	public void addClassLoaderExclusion(String toExclude) {
		classLoaderExceptions.add(toExclude);
	}

	public void addTransformerExclusion(String toExclude) {
		transformerExceptions.add(toExclude);
	}

	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private static final byte[] CACHE_MISS = new byte[0];

	public byte[] getClassBytes(String name) throws IOException {
		if (name.startsWith("java/")) {
			return null;
		}
		byte[] cached = resourceCache.get(name);
		if (cached != null) {
			return cached == CACHE_MISS ? null : cached;
		}
		if (name.indexOf('.') == -1) {
			String upperCaseName = name.toUpperCase(Locale.ENGLISH);
			for (final String reservedName : RESERVED_NAMES) {
				if (upperCaseName.startsWith(reservedName)) {
					final byte[] data = getClassBytes('_' + name);
					if (data != null) {
						resourceCache.put(name, data);
						return data;
					}
				}
			}
		}

		InputStream classStream = null;
		try {
			final String resourcePath = name.replace('.', '/') + ".class";
			final URL classResource = findResource(resourcePath);

			if (classResource == null) {
				if (DEBUG) LogWrapper.finest("Failed to find class resource %s", resourcePath);
				resourceCache.put(name, CACHE_MISS);
				return null;
			}
			classStream = classResource.openStream();

			if (DEBUG) LogWrapper.finest("Loading class %s from resource %s", name, classResource.toString());
			final byte[] data = readFully(classStream);
			resourceCache.put(name, data);
			return data;
		} finally {
			closeSilently(classStream);
		}
	}

	private static void closeSilently(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
			}
		}
	}

	public void clearNegativeEntries(Set<String> entriesToClear) {
		for (String entry : entriesToClear) {
			if (resourceCache.get(entry) == CACHE_MISS) {
				resourceCache.remove(entry);
			}
		}
	}

	public static void testForTTChanges() {
		// Do nothing, just make this method exist.
	}
}
