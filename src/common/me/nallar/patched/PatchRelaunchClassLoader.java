package me.nallar.patched;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import cpw.mods.fml.relauncher.RelaunchClassLoader;
import me.nallar.tickthreading.Log;
import sun.misc.URLClassPath;

public abstract class PatchRelaunchClassLoader extends RelaunchClassLoader {
	private static volatile Map<String, byte[]> replacedClasses;
	private File minecraftdir;
	private File patchedModsFolder;
	private URLClassPath ucp;

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
			Field field = URLClassLoader.class.getDeclaredField("ucp");
			field.setAccessible(true);
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, (field.getModifiers() & ~Modifier.FINAL) & ~Modifier.PRIVATE);
			ucp = (URLClassPath) field.get(this);
			minecraftdir = locationOf(net.minecraft.util.Tuple.class).getParentFile();
			patchedModsFolder = new File(minecraftdir, "patchedMods");
			boolean foundTT = false;
			for (File file : new File(minecraftdir, "mods").listFiles()) {
				if (file.getName().toLowerCase().contains("tickthreading") && file.getName().endsWith(".jar")) {
					URL toAdd = file.toURI().toURL();
					if (!sources.contains(toAdd)) {
						sources.add(toAdd);
						ucp.addURL(toAdd);
					}
					foundTT = true;
				}
			}
			if (!foundTT) {
				System.err.println("Failed to find TT jar in mods folder - make sure it has 'tickthreading' in its name!");
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@Override
	public void addURL(URL url) {
		if (sources.contains(url)) {
			FMLLog.warning("Added " + url.toString().replace("%", "%%") + " to classpath twice");
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
		} catch (Exception e) {
			return;
		}
		try {
			if (patchedModFile.exists()) {
				ZipFile zipFile = new ZipFile(patchedModFile);
				try {
					Enumeration<ZipEntry> zipEntryEnumeration = (Enumeration<ZipEntry>) zipFile.entries();
					while (zipEntryEnumeration.hasMoreElements()) {
						ZipEntry zipEntry = zipEntryEnumeration.nextElement();
						String name = zipEntry.getName();
						if (!name.toLowerCase().endsWith(".class") || replacedClasses.containsKey(name)) {
							continue;
						}
						byte[] contents = readFully(zipFile.getInputStream(zipEntry));
						replacedClasses.put(name, contents);
					}
				} finally {
					zipFile.close();
				}
			}
		} catch (Exception e) {
			System.out.println("Failed to load patched classes for " + patchedModFile.getName());
			e.printStackTrace();
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
						PatchRelaunchClassLoader.replacedClasses = replacedClasses;
					}
				}
			}
			return replacedClasses == null ? null : replacedClasses.get(file);
		} catch (Throwable t) {
			t.printStackTrace();
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
					FMLLog.finest("Failed to find class resource %s", name.replace('.', '/') + ".class");
				}
				return null;
			}
			classStream = classResource.openStream();
			if (DEBUG_CLASSLOADING) {
				FMLLog.finest("Loading class %s from resource %s", name, classResource.toString());
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
}
