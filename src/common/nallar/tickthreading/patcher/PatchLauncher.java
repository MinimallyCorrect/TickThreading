package nallar.tickthreading.patcher;

import nallar.tickthreading.Log;
import nallar.unsafe.UnsafeUtil;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public class PatchLauncher {
	public static void main(String[] args) {
		try {
			run(args);
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private static void run(String[] args) throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		addLibraries((URLClassLoader) classLoader);
		//JavassistClassLoader javassistClassLoader = new JavassistClassLoader(classLoader);
		//Thread.currentThread().setContextClassLoader(javassistClassLoader);

		Class<?> launchwrapper;
		try {
			launchwrapper = Class.forName("net.minecraft.launchwrapper.Launch", true, classLoader);
			System.err.printf(String.valueOf(launchwrapper.getClassLoader()));
			Class.forName("org.objectweb.asm.Type", true, classLoader);
			try {
				Method main = launchwrapper.getMethod("main", String[].class);
				String[] allArgs = new String[args.length + 2];
				allArgs[0] = "--tweakClass";
				allArgs[1] = "cpw.mods.fml.common.launcher.FMLServerTweaker";
				System.arraycopy(args, 0, allArgs, 2, args.length);
				main.invoke(null, (Object) allArgs);
			} catch (Exception e) {
				System.err.printf("A problem occurred running the Server launcher.");
				e.printStackTrace(System.err);
				System.exit(1);
			}
		} catch (Exception e) {
			System.err.printf("We appear to be missing one or more essential library files.\n" +
					"You will need to add them to your server before FML and Forge will run successfully.");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static void addLibraries(URLClassLoader classLoader) {
		File libraries = new File("libraries");
		addLibraries(classLoader, libraries);
		File current = new File(".");
		File[] files = current.listFiles();
		Arrays.sort(files, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return o2.getName().compareTo(o1.getName());
			}
		});
		boolean found = false;
		for (File file : files) {
			String lowerCase = file.getName().toLowerCase();
			if (lowerCase.contains("forge") && (lowerCase.endsWith(".jar") || lowerCase.endsWith(".zip"))) {
				Log.info("Found forge jar " + file);
				if (found) {
					Log.info("Found multiple forge jars, please ensure that only one jar with forge in the name is in the main minecraft folder");
				}
				addPathToClassLoader(file, classLoader);
				found = true;
			}
		}
		if (!found) {
			Log.info("Failed to find forge jar, rename your forge jar so that forge is in its name. If you have no forge jar, rename the server jar in the same manner.");
			System.exit(1);
		}
	}

	private static void addLibraries(URLClassLoader classLoader, File file) {
		if (file.isDirectory()) {
			//noinspection ConstantConditions
			for (File inner : file.listFiles()) {
				addLibraries(classLoader, inner);
			}
		}
		addPathToClassLoader(file, classLoader);
	}

	private static final Method addUrlMethod = getAddURLMethod();

	private static Method getAddURLMethod() {
		Method method;
		try {
			method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
		} catch (NoSuchMethodException e) {
			throw UnsafeUtil.throwIgnoreChecked(e);
		}
		method.setAccessible(true);
		return method;
	}

	public static void addPathToClassLoader(File path, URLClassLoader classLoader) {
		try {
			URL u = path.toURI().toURL();
			URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
			addUrlMethod.invoke(urlClassLoader, u);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
