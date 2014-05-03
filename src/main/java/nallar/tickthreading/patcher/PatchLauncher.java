package nallar.tickthreading.patcher;

import nallar.insecurity.InsecurityManager;
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
		ClassLoader classLoader = PatchLauncher.class.getClassLoader();
		String loc = null;
		ArrayList<String> argsList = new ArrayList<String>(Arrays.asList(args));
		for (Iterator<String> i$ = argsList.iterator(); i$.hasNext(); ) {
			String arg = i$.next();
			if (arg.toLowerCase().startsWith(serverJarArgument)) {
				loc = arg.substring(serverJarArgument.length());
				i$.remove();
				break;
			}
		}
		addLibraries((URLClassLoader) classLoader, loc);
		try {
			InsecurityManager.init();
		} catch (Throwable t) {
			System.err.println("Failed to set up Security Manager. This is probably not a huge problem - but it could indicate classloading issues.");
		}
		ClassLoader patcherClassLoader = new PatcherClassLoader(classLoader);
		Class.forName("nallar.tickthreading.patcher.PatchHook", true, patcherClassLoader);

		Class<?> launchwrapper;
		try {
			launchwrapper = Class.forName("net.minecraft.launchwrapper.Launch", true, classLoader);
			System.out.println(String.valueOf(launchwrapper.getClassLoader()));
			Class.forName("org.objectweb.asm.Type", true, classLoader);
			Method main = launchwrapper.getMethod("main", String[].class);
			String[] allArgs = new String[args.length + 2];
			allArgs[0] = "--tweakClass";
			allArgs[1] = "cpw.mods.fml.common.launcher.FMLServerTweaker";
			System.arraycopy(args, 0, allArgs, 2, args.length);
			main.invoke(null, (Object) allArgs);
		} catch (ClassNotFoundException e) {
			System.err.println(e.toString());
			System.exit(1);
		} catch (Throwable t) {
			System.err.println("A problem occurred running the Server launcher.");
			t.printStackTrace(System.err);
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
				e.printStackTrace(System.err);
			}
			if (locFile.exists()) {
				System.out.println("Adding specified server jar: " + loc + " @ " + locFile + " to libraries.");
				addPathToClassLoader(locFile, classLoader);
			} else {
				System.err.println("Could not find specified server jar: " + loc + " @ " + locFile);
			}
			return;
		}
		System.err.println("You have not specified a server jar, attempting to guess the forge jar location.");
		System.err.println("Please add --serverJar=<minecraft/forge/mcpc jar name here> at the end of your java arguments.");
		System.err.println("Example: java -Xmx=2G -XX:MaxPermSize=256m -XX:+AgressiveOpts -jar TT.jar --serverJar=mcpcIsFast.jar");
		for (File file : files) {
			String lowerCase = file.getName().toLowerCase();
			if (lowerCase.contains("forge") && (lowerCase.endsWith(".jar") || lowerCase.endsWith(".zip"))) {
				System.out.println("Found forge jar " + file);
				if (found) {
					System.out.println("Found multiple forge jars, please ensure that only one jar with forge in the name is in the main minecraft folder");
				}
				addPathToClassLoader(file, classLoader);
				found = true;
			}
		}
		if (!found) {
			System.err.println("Failed to guess which jar is the forge jar.");
		}
	}

	private static void addLibraries(URLClassLoader classLoader, File file) {
		File[] files = file.listFiles();
		if (files != null) {
			for (File inner : files) {
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
			addUrlMethod.invoke(classLoader, u);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
