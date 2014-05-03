package nallar.tickthreading.patcher;

import javassist.CannotCompileException;
import javassist.ClassLoaderPool;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.MethodDescription;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.DomUtil;
import nallar.unsafe.UnsafeUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class Patcher {
	private final ClassPool classPool;
	private final ClassPool preSrgClassPool;
	private Object patchClassInstance;
	private Object preSrgPatchClassInstance;
	private Map<String, PatchMethodDescriptor> patchMethods = new HashMap<String, PatchMethodDescriptor>();
	private Map<String, PatchGroup> classToPatchGroup = new HashMap<String, PatchGroup>();

	public Patcher(InputStream config, Class<?> patchesClass) {
		for (Method method : patchesClass.getDeclaredMethods()) {
			for (Annotation annotation : method.getDeclaredAnnotations()) {
				if (annotation instanceof Patch) {
					PatchMethodDescriptor patchMethodDescriptor = new PatchMethodDescriptor(method, (Patch) annotation);
					patchMethods.put(patchMethodDescriptor.name, patchMethodDescriptor);
				}
			}
		}
		classPool = new ClassLoaderPool(false);
		preSrgClassPool = new ClassLoaderPool(true);
		try {
			patchClassInstance = patchesClass.getDeclaredConstructors()[0].newInstance(classPool);
			preSrgPatchClassInstance = patchesClass.getDeclaredConstructors()[0].newInstance(preSrgClassPool);
		} catch (Exception e) {
			Log.severe("Failed to instantiate patch class", e);
		}
		try {
			readPatchesFromXmlDocument(DomUtil.readDocumentFromInputStream(config));
		} catch (Throwable t) {
			throw UnsafeUtil.throwIgnoreChecked(t);
		}
	}

	private void readPatchesFromXmlDocument(Document document) {
		List<Element> patchGroupElements = DomUtil.elementList(document.getDocumentElement().getChildNodes());
		for (Element patchGroupElement : patchGroupElements) {
			new PatchGroup(patchGroupElement);
		}
	}

	public byte[] preSrgTransformation(String name, String transformedName, byte[] originalBytes) {
		PatchGroup patchGroup = getPatchGroup(name);
		if (patchGroup != null && patchGroup.preSrg) {
			return patchGroup.getClassBytes(name);
		}
		return originalBytes;
	}

	private PatchGroup getPatchGroup(String name) {
		return classToPatchGroup.get(name);
	}

	public byte[] postSrgTransformation(String name, String transformedName, byte[] originalBytes) {
		PatchGroup patchGroup = getPatchGroup(transformedName);
		if (patchGroup != null && !patchGroup.preSrg) {
			return patchGroup.getClassBytes(transformedName);
		}
		return originalBytes;
	}

	private class PatchGroup {
		public final String name;
		public final boolean preSrg;
		public final boolean onDemand;
		public final ClassPool classPool;
		private final Map<String, ClassPatchDescriptor> patches;
		private final Map<String, byte[]> patchedBytes = new HashMap<String, byte[]>();
		private final List<ClassPatchDescriptor> classPatchDescriptors = new ArrayList<ClassPatchDescriptor>();
		private boolean ranPatches = false;

		private PatchGroup(Element element) {
			Map<String, String> attributes = DomUtil.getAttributes(element);
			name = element.getTagName();
			preSrg = attributes.containsKey("preSrg");
			if (preSrg) {
				classPool = preSrgClassPool;
			} else {
				classPool = Patcher.this.classPool;
			}
			onDemand = attributes.containsKey("onDemand");
			patches = onDemand ? new HashMap<String, ClassPatchDescriptor>() : null;

			for (Element classElement : DomUtil.elementList(element.getChildNodes())) {
				ClassPatchDescriptor classPatchDescriptor = new ClassPatchDescriptor(classElement);
				classPatchDescriptors.add(classPatchDescriptor);
				classToPatchGroup.put(classPatchDescriptor.name, this);
				if (onDemand) {
					if (patches.put(classPatchDescriptor.name, classPatchDescriptor) != null) {
						throw new Error("Duplicate class patch for " + classPatchDescriptor.name + ", but onDemand is set.");
					}
				}
			}
		}

		public byte[] getClassBytes(String name) {
			if (onDemand) {
				try {
					return patches.get(name).runPatches().toBytecode();
				} catch (Throwable t) {
					Log.severe("Failed to patch " + name + " in patch group " + name + '.', t);
				}
			} else {
				runPatchesIfNeeded();
			}
			byte[] bytes = patchedBytes.remove(name);
			if (bytes == null) {
				throw new RuntimeException("Class " + name + " not in this patch group.");
			}
			return bytes;
		}

		private void runPatchesIfNeeded() {
			if (ranPatches) {
				return;
			}
			ranPatches = true;
			Set<CtClass> patchedClasses = new HashSet<CtClass>();
			for (ClassPatchDescriptor classPatchDescriptor : classPatchDescriptors) {
				try {
					patchedClasses.add(classPatchDescriptor.runPatches());
				} catch (Throwable t) {
					Log.severe("Failed to patch " + classPatchDescriptor.name + " in patch group " + name + '.', t);
				}
			}
			for (CtClass ctClass : patchedClasses) {
				try {
					patchedBytes.put(ctClass.getName(), ctClass.toBytecode());
				} catch (Throwable t) {
					Log.severe("Failed to get patched bytes for " + ctClass.getName() + " in patch group " + name + '.', t);
				}
			}
		}

		private class ClassPatchDescriptor {
			private final Map<String, String> attributes;
			public final String name;
			public final List<PatchDescriptor> patches = new ArrayList<PatchDescriptor>();

			private ClassPatchDescriptor(Element element) {
				attributes = DomUtil.getAttributes(element);
				name = attributes.get("id");
				for (Element patchElement : DomUtil.elementList(element.getChildNodes())) {
					patches.add(new PatchDescriptor(patchElement));
				}
			}

			public CtClass runPatches() throws NotFoundException {
				CtClass ctClass = classPool.get(name);
				for (PatchDescriptor patchDescriptor : patches) {
					PatchMethodDescriptor patchMethodDescriptor = patchMethods.get(patchDescriptor.getPatch());
					patchMethodDescriptor.run(patchDescriptor, ctClass, preSrg ? preSrgPatchClassInstance : patchClassInstance);
				}
				return ctClass;
			}
		}
	}

	private static class PatchDescriptor {
		private final Map<String, String> attributes;
		private final String methods;
		private final String patch;

		public PatchDescriptor(Element element) {
			attributes = DomUtil.getAttributes(element);
			methods = element.getTextContent().trim();
			patch = element.getTagName();
		}

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

		public String getPatch() {
			return patch;
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

		public Object run(PatchDescriptor patchDescriptor, CtClass ctClass, Object patchClassInstance) {
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
					run(ctBehavior, attributes, patchClassInstance);
				}
			} else if (isClassPatch || (!emptyConstructor && methods.isEmpty())) {
				return run(ctClass, attributes, patchClassInstance);
			} else if (methods.isEmpty()) {
				for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
					run(ctConstructor, attributes, patchClassInstance);
				}
			} else if ("^static^".equals(methods)) {
				CtConstructor ctBehavior = ctClass.getClassInitializer();
				if (ctBehavior == null) {
					Log.severe("No static initializer found patching " + ctClass.getName() + " with " + toString());
				} else {
					run(ctBehavior, attributes, patchClassInstance);
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
					run(ctMethod, attributes, patchClassInstance);
				}
			}
			return null;
		}

		private Object run(CtClass ctClass, Map<String, String> attributes, Object patchClassInstance) {
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

		private Object run(CtBehavior ctBehavior, Map<String, String> attributes, Object patchClassInstance) {
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
