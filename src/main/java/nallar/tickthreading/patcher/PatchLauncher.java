package nallar.tickthreading.patcher;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class PatchLauncher {
	public static void main(String[] args) {
		try {
			run(args);
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private static void loadPropertiesFromFile(File file) throws IOException {
		if (!file.exists()) {
			Files.write(file.toPath(), ("colorLogs=true\r\n" +
					"serverJar=\r\n" +
					"chunkPopulationRange=1\r\n" +
					"fullLoggerName=\r\n").getBytes());
		}
		String data = new String(Files.readAllBytes(file.toPath()));
		data = data.replace("\r\n", "\n");
		for (String line : data.split("\n")) {
			String[] parts = line.split("=");
			if (parts.length == 2) {
				String value = parts[1];
				String key = parts[0];
				if (!key.isEmpty() && !value.isEmpty()) {
					System.setProperty(key, value);
				}
			}
		}
	}

	public static String[] startupArgs;
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
		startupArgs = args = argsList.toArray(new String[argsList.size()]);
		loadPropertiesFromFile(new File("ttlaunch.properties"));
		if (loc == null) {
			loc = System.getProperty("serverJar");
		}
		if (System.getProperty("tickthreading.launcherWaitForKeyPress") != null) {
			System.out.println("Waiting for enter key press to continue;");
			new Scanner(System.in).nextLine();
		}
		if (System.getProperty("tickthreading.debug") == null) {
			System.out.println("THIS IS AN UNSTABLE BUILD OF TICKTHREADING.");
			System.out.println("It may cause world corruption, exceptions everywhere or even blow up your server.");
			Thread.sleep(5000);
		}
		addLibraries((URLClassLoader) classLoader, loc);

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
		if (loc == null) {
			System.err.println("You have not specified a server jar");
			System.err.println("Please add --serverJar=<minecraft/forge/mcpc jar name here> at the end of your java arguments.");
			System.err.println("Example: java -Xmx=2G -XX:MaxPermSize=256m -XX:+AgressiveOpts -jar TT.jar --serverJar=mcpc953.jar");
			System.exit(1);
		}
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
			System.exit(1);
		}
		File libraries = new File("libraries");
		addLibraries(classLoader, libraries);
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
			throw new RuntimeException(e);
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
