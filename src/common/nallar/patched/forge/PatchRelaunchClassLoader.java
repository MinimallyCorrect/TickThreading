package nallar.patched.forge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import nallar.tickthreading.Log;
import nallar.unsafe.UnsafeUtil;
import sun.misc.URLClassPath;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	private static final ThreadLocal<byte[]> buffer = new ThreadLocal<byte[]>();
	private static final PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err));
	private static volatile Map<String, byte[]> replacedClasses;
	private File minecraftdir;
	private File patchedModsFolder;
	private URLClassPath ucp;
	private Set<String> patchedURLs;

	private static void log(Level level, Throwable t, String message, Object... data) {
		try {
			FMLRelaunchLog.log(level, t, message, data);
		} catch (Throwable t_) {
			t_.printStackTrace(err);
		}
	}

	public PatchRelaunchClassLoader(URL[] sources) {
		super(sources);
	}

	private static File locationOf(Class clazz) {
		String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
		path = path.contains("!") ? path.substring(0, path.lastIndexOf('!')) : path;
		if (!path.isEmpty() && path.charAt(0) == '/') {
			path = "file:" + path;
		}
		try {
			return new File(new URL(path).toURI());
		} catch (Exception e) {
			Log.severe("", e);
			return new File(path);
		}
	}

	public void construct() {
		try {
			patchedURLs = new HashSet<String>();
			Field field = URLClassLoader.class.getDeclaredField("ucp");
			field.setAccessible(true);
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, (field.getModifiers() & ~Modifier.FINAL) & ~Modifier.PRIVATE);
			ucp = (URLClassPath) field.get(this);
			minecraftdir = new File(".").getAbsoluteFile();
			FMLRelaunchLog.info("This server is patched with @MOD_NAME@ v@MOD_VERSION@ for MC@MC_VERSION@");
			log(Level.INFO, null, "Searching for patched mods in minecraft dir " + minecraftdir);
			patchedModsFolder = new File(minecraftdir, "patchedMods");
			if (!patchedModsFolder.exists()) {
				minecraftdir = locationOf(net.minecraft.util.Tuple.class).getParentFile();
				log(Level.INFO, null, "Searching for patched mods in minecraft dir " + minecraftdir);
				patchedModsFolder = new File(minecraftdir, "patchedMods");
			}
			boolean foundTT = false;
			for (File file : new File(minecraftdir, "mods").listFiles()) {
				if (file.getName().toLowerCase().contains("tickthreading") && file.getName().endsWith(".jar")) {
					URL toAdd = new URL(file.toURI().toURL().toString().replace("/./", "/"));
					if (!sources.contains(toAdd)) {
						sources.add(toAdd);
						ucp.addURL(toAdd);
					}
					log(Level.INFO, null, "Added tickthreading jar " + file + " to classpath");
					foundTT = true;
				}
			}
			for (URL url : ucp.getURLs()) {
				if (url.toString().contains("patchedMods")) {
					log(Level.WARNING, null, url.toString().replace("%", "%%") + " is in the classpath, but it appears to be in the patchedMods directory");
				}
			}
			if (!foundTT) {
				log(Level.SEVERE, null, "Failed to find TT jar in mods folder - make sure it has 'tickthreading' in its name!");
			}
		} catch (Throwable t) {
			log(Level.SEVERE, t, "Failed to initialise RelaunchClassLoader");
		}
	}

	@Override
	public void addURL(URL url) {
		if (url.toString().contains("patchedMods")) {
			log(Level.WARNING, null, "Adding " + url.toString().replace("%", "%%") + " to classpath when it appears to be in the patchedMods directory");
		}
		if (sources.contains(url)) {
			log(Level.FINE, null, "Attempted to add " + url.toString().replace("%", "%%") + " to classpath twice");
			return;
		}
		ucp.addURL(url);
		sources.add(url);
		if (replacedClasses == null) {
			getReplacementClassBytes("");
		}
		synchronized (RelaunchClassLoader.class) {
			loadPatchedClasses(url, replacedClasses);
		}
	}

	private void loadPatchedClasses(URL url_, Map<String, byte[]> replacedClasses) {
		String url;
		try {
			url = url_.toURI().getPath();
		} catch (URISyntaxException e) {
			log(Level.SEVERE, e, "Not loading classes from %s, malformed URL.", url_.toString());
			return;
		}
		if (replacedClasses == null) {
			log(Level.WARNING, null, "Not loading patched classes in " + url.replace("%", "%%") + ", replacedClasses was not set.");
			return;
		}
		if (!url.isEmpty() && url.charAt(url.length() - 1) == '/') {
			url = url.substring(0, url.length() - 1);
		}
		if (url.isEmpty() || ".".equals(url)) {
			log(Level.WARNING, new Throwable(), "Failed to add patched classes from empty URL", url);
			return;
		}
		File patchedModFile;
		try {
			patchedModFile = new File(patchedModsFolder, url.substring(url.lastIndexOf('/') + 1, url.length()));
			if (!patchedURLs.add(patchedModFile.getAbsolutePath())) {
				return;
			}
			if (patchedModsFolder.equals(patchedModFile)) {
				throw new Exception("patched mods file = patched mods folder");
			}
		} catch (Exception e) {
			log(Level.SEVERE, e, "Failed to add patched classes in URL %s", url);
			return;
		}
		try {
			if (patchedModFile.exists()) {
				ZipFile zipFile = new ZipFile(patchedModFile);
				try {
					Enumeration<ZipEntry> zipEntryEnumeration = (Enumeration<ZipEntry>) zipFile.entries();
					int patchedClasses = 0;
					while (zipEntryEnumeration.hasMoreElements()) {
						ZipEntry zipEntry = zipEntryEnumeration.nextElement();
						String name = zipEntry.getName();
						if (!name.toLowerCase().endsWith(".class") || replacedClasses.containsKey(name)) {
							continue;
						}
						patchedClasses++;
						byte[] contents = readFully(zipFile.getInputStream(zipEntry));
						replacedClasses.put(name, contents);
					}
					if (patchedClasses > 0) {
						log(Level.INFO, null, "Loaded " + patchedClasses + " patched classes for " + patchedModFile.getName() + '.');
					}
				} finally {
					zipFile.close();
				}
			}
		} catch (Exception e) {
			log(Level.SEVERE, e, "Failed to load patched classes for " + patchedModFile.getName());
		}
	}

	private byte[] getReplacementClassBytes(String classFile) {
		try {
			Map<String, byte[]> replacedClasses = PatchRelaunchClassLoader.replacedClasses;
			if (replacedClasses == null) {
				synchronized (RelaunchClassLoader.class) {
					replacedClasses = PatchRelaunchClassLoader.replacedClasses;
					if (replacedClasses == null && minecraftdir != null) {
						replacedClasses = new ConcurrentHashMap<String, byte[]>();
						for (URL url_ : sources) {
							loadPatchedClasses(url_, replacedClasses);
						}
						if (patchedModsFolder.isDirectory()) {
							File modsFolder = new File(minecraftdir, "mods");
							log(Level.INFO, null, "Loading patched mod classes from " + patchedModsFolder);
							for (File file : patchedModsFolder.listFiles()) {
								if (!new File(modsFolder, file.getName()).exists()) {
									log(Level.INFO, null, "Skipping jar %s which is not in the mods folder.", file.toString());
									continue;
								}
								loadPatchedClasses(file.toURI().toURL(), replacedClasses);
							}
						}
						PatchRelaunchClassLoader.replacedClasses = replacedClasses;
					}
				}
			}
			if (replacedClasses == null) {
				return null;
			}
			return replacedClasses.get(classFile);
		} catch (Throwable t) {
			log(Level.SEVERE, t, "Exception getting patched class for " + classFile);
		}
		return null;
	}

	@Override
	public byte[] getClassBytes(String name) throws IOException {
		byte[] data;

		if (name.indexOf('.') == -1) {
			for (String res : RESERVED) {
				if (name.toUpperCase(Locale.ENGLISH).startsWith(res)) {
					data = getClassBytes('_' + name);
					if (data != null) {
						return data;
					}
				}
			}
		}

		InputStream classStream = null;
		try {
			URL classResource = findResource(name.replace('.', '/') + ".class");
			if (classResource == null) {
				if (DEBUG_CLASSLOADING) {
					FMLRelaunchLog.finest("Failed to find class resource %s", name.replace('.', '/') + ".class");
				}
				return null;
			}
			classStream = classResource.openStream();
			if (DEBUG_CLASSLOADING) {
				FMLRelaunchLog.finest("Loading class %s from resource %s", name, classResource.toString());
			}
			data = readFully(classStream);
			byte[] data2 = getReplacementClassBytes(name.replace('.', '/') + ".class");
			return data2 == null ? data : data2;
		} finally {
			if (classStream != null) {
				try {
					classStream.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	@Override
	protected byte[] runTransformers(String name, byte[] basicClass) {
		if (name.startsWith("nallar.patched") && !name.contains("$")) {
			throw UnsafeUtil.throwIgnoreChecked(new ClassNotFoundException("Don't load these classes, they will slow down access to the classes they patch as the JVM will have to lookup methods"));
		}
		if (basicClass == null && DEBUG_CLASSLOADING) {
			FMLRelaunchLog.log(Level.WARNING, "Could not find the class " + name + ". This is not necessarily an issue.");
		}
		for (IClassTransformer transformer : transformers) {
			try {
				byte[] oldClass = basicClass;
				basicClass = transformer.transform(name, basicClass);
				if (basicClass == null && oldClass != null) {
					basicClass = oldClass;
					FMLRelaunchLog.severe(transformer.getClass() + " returned a null class during transformation, ignoring.");
				}
			} catch (Throwable throwable) {
				String message = throwable.getMessage();
				if (message != null && message.contains("for invalid side")) {
					throw UnsafeUtil.throwIgnoreChecked(throwable);
				} else if (basicClass != null || DEBUG_CLASSLOADING) {
					FMLRelaunchLog.log((DEBUG_CLASSLOADING && basicClass != null) ? Level.WARNING : Level.FINE, throwable, "Failed to transform " + name);
				}
			}
		}
		return basicClass;
	}

	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

	@Override
	protected byte[] readFully(InputStream stream) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream(stream.available());

			int readBytes;
			byte[] buffer = this.buffer.get();

			if (buffer == null) {
				buffer = new byte[1048576];
				this.buffer.set(buffer);
			}

			while ((readBytes = stream.read(buffer, 0, buffer.length)) != -1) {
				bos.write(buffer, 0, readBytes);
			}

			return bos.toByteArray();
		} catch (Throwable t) {
			FMLRelaunchLog.log(Level.SEVERE, t, "Problem loading class");
			return EMPTY_BYTE_ARRAY;
		}
	}
}
