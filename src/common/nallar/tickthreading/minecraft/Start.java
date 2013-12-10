package nallar.tickthreading.minecraft;

import javassist.JavassistClassLoader;

import java.net.*;

public class Start {
	public static void main(String[] args) {
		Thread thread = Thread.currentThread();
		JavassistClassLoader classLoader = new JavassistClassLoader(((URLClassLoader) thread.getContextClassLoader()).getURLs());
		thread.setContextClassLoader(classLoader);
	}
}
