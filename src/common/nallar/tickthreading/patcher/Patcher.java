package nallar.tickthreading.patcher;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.MethodDescription;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.DomUtil;
import nallar.unsafe.UnsafeUtil;
import org.w3c.dom.Document;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class Patcher {
	ClassPool classPool;
	Patches patchClassInstance;
	Map<String, PatchMethodDescriptor> patchMethods = new HashMap<String, PatchMethodDescriptor>();
	Map<String, PatchDescriptor> patches = new HashMap<String, PatchDescriptor>();

	public void loadPatches(InputStream config, Class<? extends Patches> patchesClass, ClassPool classPool) {
		try {
			patchClassInstance = (Patches) patchesClass.getDeclaredConstructors()[0].newInstance(this, classPool);
		} catch (Exception e) {
			Log.severe("Failed to instantiate patch class", e);
		}
		for (Method method : patchesClass.getDeclaredMethods()) {
			for (Annotation annotation : method.getDeclaredAnnotations()) {
				if (annotation instanceof Patch) {
					PatchMethodDescriptor patchMethodDescriptor = new PatchMethodDescriptor(method, (Patch) annotation);
					patchMethods.put(patchMethodDescriptor.name, patchMethodDescriptor);
				}
			}
		}
		try {
			Document document = DomUtil.readDocumentFromInputStream(config);
		} catch (Throwable t) {
			throw UnsafeUtil.throwIgnoreChecked(t);
		}
	}

	private class PatchDescriptor {
		private Map<String, String> attributes = new HashMap<String, String>();
		private String methods;

		public String set(String name, String value) {
			return attributes.put(name, value);
		}

		public String get(String name) {
			return attributes.get(name);
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}

		public String getMethods() {
			return methods;
		}

		public void setMethods(String methods) {
			this.methods = methods.trim();
		}
	}

	public class PatchMethodDescriptor {
		public final String name;
		public final List<String> requiredAttributes;
		public final Method patchMethod;
		public final boolean isClassPatch;
		public final boolean emptyConstructor;

		public PatchMethodDescriptor(Method method, Patch patch) {
			String name = patch.name();
			if (Arrays.asList(method.getParameterTypes()).contains(Map.class)) {
				this.requiredAttributes = CollectionsUtil.split(patch.requiredAttributes());
			} else {
				this.requiredAttributes = null;
			}
			if (name == null || name.isEmpty()) {
				name = method.getName();
			}
			this.name = name;
			emptyConstructor = patch.emptyConstructor();
			isClassPatch = method.getParameterTypes()[0].equals(CtClass.class);
			patchMethod = method;
		}

		public Object run(PatchDescriptor patchDescriptor, CtClass ctClass) {
			String methods = patchDescriptor.getMethods();
			Map<String, String> attributes = patchDescriptor.getAttributes();
			Map<String, String> attributesClean = new HashMap<String, String>(attributes);
			attributesClean.remove("code");
			Log.fine("Patching " + ctClass.getName() + " with " + this.name + '(' + CollectionsUtil.joinMap(attributesClean) + ')' + (methods.isEmpty() ? "" : " {" + methods + '}'));
			if (requiredAttributes != null && !attributes.keySet().containsAll(requiredAttributes)) {
				Log.severe("Missing required attributes " + requiredAttributes.toString() + " when patching " + ctClass.getName());
				return null;
			}
			if ("^all^".equals(methods)) {
				patchDescriptor.set("silent", "true");
				List<CtBehavior> ctBehaviors = new ArrayList<CtBehavior>();
				Collections.addAll(ctBehaviors, ctClass.getDeclaredMethods());
				Collections.addAll(ctBehaviors, ctClass.getDeclaredConstructors());
				CtBehavior initializer = ctClass.getClassInitializer();
				if (initializer != null) {
					ctBehaviors.add(initializer);
				}
				for (CtBehavior ctBehavior : ctBehaviors) {
					run(ctBehavior, attributes);
				}
			} else if (isClassPatch || (!emptyConstructor && methods.isEmpty())) {
				return run(ctClass, attributes);
			} else if (methods.isEmpty()) {
				for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
					run(ctConstructor, attributes);
				}
			} else if ("^static^".equals(methods)) {
				CtConstructor ctBehavior = ctClass.getClassInitializer();
				if (ctBehavior == null) {
					Log.severe("No static initializer found patching " + ctClass.getName() + " with " + toString());
				} else {
					run(ctBehavior, attributes);
				}
			} else {
				List<MethodDescription> methodDescriptions = MethodDescription.fromListString(ctClass.getName(), methods);
				for (MethodDescription methodDescription : methodDescriptions) {
					CtMethod ctMethod;
					try {
						ctMethod = methodDescription.inClass(ctClass);
					} catch (Throwable t) {
						if (!attributes.containsKey("allowMissing")) {
							Log.warning("", t);
						}
						continue;
					}
					run(ctMethod, attributes);
				}
			}
			return null;
		}

		private Object run(CtClass ctClass, Map<String, String> attributes) {
			try {
				if (requiredAttributes == null) {
					return patchMethod.invoke(patchClassInstance, ctClass);
				} else {
					return patchMethod.invoke(patchClassInstance, ctClass, attributes);
				}
			} catch (Throwable t) {
				if (t instanceof InvocationTargetException) {
					t = t.getCause();
				}
				if (t instanceof CannotCompileException && attributes.containsKey("code")) {
					Log.severe("Code: " + attributes.get("code"));
				}
				Log.severe("Error patching " + ctClass.getName() + " with " + toString(), t);
				return null;
			}
		}

		private Object run(CtBehavior ctBehavior, Map<String, String> attributes) {
			try {
				if (requiredAttributes == null) {
					return patchMethod.invoke(patchClassInstance, ctBehavior);
				} else {
					return patchMethod.invoke(patchClassInstance, ctBehavior, attributes);
				}
			} catch (Throwable t) {
				if (t instanceof InvocationTargetException) {
					t = t.getCause();
				}
				if (t instanceof CannotCompileException && attributes.containsKey("code")) {
					Log.severe("Code: " + attributes.get("code"));
				}
				Log.severe("Error patching " + ctBehavior.getName() + " in " + ctBehavior.getDeclaringClass().getName() + " with " + toString(), t);
				return null;
			}
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
