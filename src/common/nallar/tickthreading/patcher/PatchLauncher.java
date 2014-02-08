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

	private static final String serverJarArgument = "--serverjar=";

	private static void run(String[] args) throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String loc = null;
		for (int i = 0; i < args.length; i++) {
			if (args[i].toLowerCase().startsWith(serverJarArgument)) {
				loc = args[i].substring(serverJarArgument.length());
			}
		}
		Arrays.copyOfRange(args, 0, args.length - 1);
		addLibraries((URLClassLoader) classLoader, loc);
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

	private static void addLibraries(URLClassLoader classLoader, String loc) {
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
		boolean found = loc != null;
		if (found) {
			File locFile = new File(loc);
			try {
				locFile = locFile.getCanonicalFile();
			} catch (IOException e) {
				Log.severe("", e);
			}
			Log.info("Adding specified server jar: " + loc + " @ " + locFile + " to libraries.");
			addPathToClassLoader(locFile, classLoader);
		} else {
			Log.severe("You have not specified a server jar, attempting to guess the forge jar location.");
			Log.severe("Please add --serverJar=<minecraft/forge/mcpc jar name here> at the end of your java arguments.");
			Log.severe("Example: java -Xmx=2G -XX:MaxPermSize=256m -XX:+AgressiveOpts -jar TT.jar --serverJar=mcpcIsFast.jar");
		}
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
			Log.info("Failed to find forge jar. Unless you have installed TT inside the server/forge jar, this won't work. Make sure 'forge' is in the name of the server jar.");
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
