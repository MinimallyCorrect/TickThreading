package me.nallar.patched;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.patcher.Declare;
import sun.misc.Resource;
import sun.misc.URLClassPath;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	@Declare
	public static int patchedClasses_;
	@Declare
	public static int usedPatchedClasses_;
	private static volatile Map<String, byte[]> replacedClasses;
	private File minecraftdir;
	private File patchedModsFolder;
	private URLClassPath ucp;
	private static PrintStream err;
	private Set<String> patchedURLs;
	private ThreadLocal<byte[]> buffer;

	private void log(Level level, Throwable t, String message) {
		try {
			FMLRelaunchLog.log(level, t, message);
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

	public static void staticConstruct() {
		EMPTY_BYTE_ARRAY = new byte[0];
		err = new PrintStream(new FileOutputStream(FileDescriptor.err));
	}

	public void construct() {
		try {
			buffer = new ThreadLocal<byte[]>();
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
					URL toAdd = file.toURI().toURL();
					if (!sources.contains(toAdd)) {
						sources.add(toAdd);
						ucp.addURL(toAdd);
					}
					log(Level.INFO, null, "Added tickthreading jar " + file + " to classpath");
					foundTT = true;
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
		if (sources.contains(url)) {
			log(Level.WARNING, null, "Attempted to add " + url.toString().replace("%", "%%") + " to classpath twice");
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
		if (replacedClasses == null) {
			return;
		}
		String url = url_.toString();
		File patchedModFile;
		try {
			patchedModFile = new File(patchedModsFolder, url.substring(url.lastIndexOf('/') + 1, url.length()));
			if (!patchedURLs.add(patchedModFile.getAbsolutePath().toString())) {
				return;
			}
		} catch (Exception e) {
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
						byte[] contents = readFullyUnsafe(zipFile.getInputStream(zipEntry));
						replacedClasses.put(name, contents);
					}
					RelaunchClassLoader.patchedClasses += patchedClasses;
					log(Level.INFO, null, "Loaded " + patchedClasses + " patched classes for " + patchedModFile.getName());
				} finally {
					zipFile.close();
				}
			}
		} catch (Exception e) {
			log(Level.SEVERE, e, "Failed to load patched classes for " + patchedModFile.getName());
		}
	}

	private byte[] getReplacementClassBytes(String file) {
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
							for (File file_ : patchedModsFolder.listFiles()) {
								loadPatchedClasses(file_.toURI().toURL(), replacedClasses);
							}
						}
						PatchRelaunchClassLoader.replacedClasses = replacedClasses;
					}
				}
			}
			if (replacedClasses == null) {
				return null;
			}
			byte[] data = replacedClasses.get(file);
			if (data != null) {
				usedPatchedClasses++;
			}
			return data;
		} catch (Throwable t) {
			log(Level.SEVERE, t, "Exception getting patched class for " + file);
		}
		return null;
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		if (invalidClasses.contains(name)) {
			throw new ClassNotFoundException(name);
		}
		Class<?> cachedClass = cachedClasses.get(name);
		if (cachedClass != null) {
			return cachedClass;
		}
		for (String st : classLoaderExceptions) {
			if (name.startsWith(st)) {
				return parent.loadClass(name);
			}
		}

		boolean transform = true;
		for (String st : transformerExceptions) {
			if (name.startsWith(st)) {
				transform = false;
				break;
			}
		}

		byte[] basicClass = null;

		String path = name.replace('.', '/') + ".class";
		if (name.indexOf('.') == -1) {
			for (String res : RESERVED) {
				if (name.toUpperCase(Locale.ENGLISH).startsWith(res)) {
					path = '_' + path;
					break;
				}
			}
		}

		CodeSource codeSource = null;
		InputStream classStream = null;
		try {
			byte[] patchedData = getReplacementClassBytes(path);
			boolean loaded = false;
			if (patchedData == null) {
				try {
					Resource classResource = ucp.getResource(path, true);
					if (classResource != null) {
						if (DEBUG_CLASSLOADING) {
							FMLLog.finest("Loading class %s from resource %s", name, classResource.getCodeSourceURL().toString());
						}
						if (patchedData == null) {
							ByteBuffer byteBuffer = classResource.getByteBuffer();
							if (byteBuffer == null) {
								basicClass = classResource.getBytes();
							} else {
								basicClass = new byte[byteBuffer.remaining()];
								byteBuffer.get(basicClass);
							}
						}
						CodeSigner[] codeSigners = classResource.getCodeSigners();
						if (codeSigners != null && codeSigners.length != 0) {
							codeSource = new CodeSource(classResource.getCodeSourceURL(), codeSigners);
						}
						loaded = true;
					}
				} catch (Throwable t) {
					if (!(t instanceof NoClassDefFoundError)) {
						log(Level.WARNING, t, "Failed to use fast I/O to read class " + name + " from " + path);
					}
				}
			}
			if (!loaded) {
				URL classURL = findResource(path);
				if (classURL == null) {
					if (DEBUG_CLASSLOADING) {
						FMLLog.finest("Failed to find class resource %s", path);
					}
					return null;
				}
				if (DEBUG_CLASSLOADING) {
					FMLLog.finest("Loading class %s from resource %s", name, classURL.toString());
				}
				classStream = classURL.openStream();
				basicClass = readFully(classStream);
			}
			basicClass = patchedData == null ? basicClass : patchedData;
		} catch (Throwable t) {
			log(Level.SEVERE, t, "Exception loading class " + name);
			return null;
		} finally {
			if (classStream != null) {
				try {
					classStream.close();
				} catch (IOException ignored) {
				}
			}
		}

		try {
			if (codeSource == null) {
				CodeSigner[] signers = null;
				int lastDot = name.lastIndexOf('.');
				String pkgname = lastDot == -1 ? "" : name.substring(0, lastDot);
				String fName = name.replace('.', '/') + ".class";
				URLConnection urlConnection = findCodeSourceConnectionFor(fName);
				if (urlConnection instanceof JarURLConnection && lastDot > -1) {
					JarURLConnection jarUrlConn = (JarURLConnection) urlConnection;
					JarFile jf = jarUrlConn.getJarFile();
					if (jf != null && jf.getManifest() != null) {
						Manifest mf = jf.getManifest();
						JarEntry ent = jf.getJarEntry(fName);
						Package pkg = getPackage(pkgname);
						signers = ent.getCodeSigners();
						if (pkg == null) {
							definePackage(pkgname, mf, jarUrlConn.getJarFileURL());
						} else {
							if (pkg.isSealed() && !pkg.isSealed(jarUrlConn.getJarFileURL())) {
								FMLLog.severe("The jar file %s is trying to seal already secured path %s", jf.getName(), pkgname);
							} else if (isSealed(pkgname, mf)) {
								FMLLog.severe("The jar file %s has a security seal for path %s, but that path is defined and not secure", jf.getName(), pkgname);
							}
						}
					}
				} else if (lastDot > -1) {
					Package pkg = getPackage(pkgname);
					if (pkg == null) {
						definePackage(pkgname, null, null, null, null, null, null, null);
					} else if (pkg.isSealed()) {
						FMLLog.severe("The URL %s is defining elements for sealed path %s", urlConnection.getURL(), pkgname);
					}
				}
				if (urlConnection != null) {
					codeSource = new CodeSource(urlConnection.getURL(), signers);
				}
			}
			byte[] transformedClass = transform ? runTransformers(name, basicClass) : basicClass;
			Class<?> cl = defineClass(name, transformedClass, 0, transformedClass.length, codeSource);
			cachedClasses.put(name, cl);
			return cl;
		} catch (Throwable e) {
			invalidClasses.add(name);
			if (DEBUG_CLASSLOADING) {
				FMLLog.log(Level.FINEST, e, "Exception encountered attempting classloading of %s", name);
			}
			throw new ClassNotFoundException(name, e);
		}
	}

	@Override
	public byte[] getClassBytes(String name) throws IOException {
		return null;
	}

	@Override
	protected byte[] runTransformers(String name, byte[] basicClass) {
		if (basicClass == null) {
			if (DEBUG_CLASSLOADING) {
				FMLLog.log(DEBUG_CLASSLOADING ? Level.WARNING : Level.FINE, "Could not find the class " + name + ". This is not necessarily an issue.");
			}
		}
		for (IClassTransformer transformer : transformers) {
			try {
				byte[] oldClass = basicClass;
				basicClass = transformer.transform(name, basicClass);
				if (basicClass == null && oldClass != null) {
					basicClass = oldClass;
					FMLLog.severe(transformer.getClass() + " returned a null class during transformation, ignoring.");
				}
			} catch (Throwable throwable) {
				String message = throwable.getMessage();
				if ((message == null ? "" : message).contains("for invalid side")) {
					invalidClasses.add(name);
					throw (RuntimeException) throwable;
				} else if (basicClass != null || DEBUG_CLASSLOADING) {
					FMLLog.log((DEBUG_CLASSLOADING && basicClass != null) ? Level.WARNING : Level.FINE, throwable, "Failed to transform " + name);
				}
			}
		}
		return basicClass;
	}

	private static byte[] EMPTY_BYTE_ARRAY;

	protected byte[] readFullyUnsafe(InputStream stream) {
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
			FMLLog.log(Level.WARNING, t, "Problem loading class");
			return EMPTY_BYTE_ARRAY;
		}
	}
}
