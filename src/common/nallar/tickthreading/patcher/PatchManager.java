package nallar.tickthreading.patcher;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import nallar.tickthreading.Log;
import nallar.tickthreading.mappings.ClassDescription;
import nallar.tickthreading.mappings.FieldDescription;
import nallar.tickthreading.mappings.Mappings;
import nallar.tickthreading.mappings.MethodDescription;
import nallar.tickthreading.util.CollectionsUtil;
import nallar.tickthreading.util.DomUtil;
import nallar.tickthreading.util.LocationUtil;
import nallar.tickthreading.util.VersionUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PatchManager {
	private Document configDocument;
	private Object patchTypes;
	// Patch name -> patch method descriptor
	private final Map<String, PatchMethodDescriptor> patches = new HashMap<String, PatchMethodDescriptor>();
	public final ClassRegistry classRegistry = new ClassRegistry();
	public File backupDirectory;
	public String patchEnvironment = "forge";
	public Map<String, CtClass> patchingClasses;

	public PatchManager(InputStream configStream, Class<? extends Patches> patchClass) throws IOException, SAXException {
		this(configStream, patchClass, new File(LocationUtil.directoryOf(patchClass).getAbsoluteFile().getParentFile(), "TickThreadingBackups"));
	}

	public PatchManager(InputStream configStream, Class<? extends Patches> patchClass, File backupDirectory) throws IOException, SAXException {
		loadPatches(patchClass);
		loadConfig(configStream);
		this.backupDirectory = backupDirectory;
	}

	public void loadBackups(Iterable<File> filesToLoad) {
		try {
			classRegistry.disableJavassistLoading = true;
			classRegistry.loadFiles(filesToLoad);
			classRegistry.loadPatchHashes(this);
			classRegistry.restoreBackups(backupDirectory);
			classRegistry.clearClassInfo();
			classRegistry.disableJavassistLoading = false;
		} catch (IOException e) {
			Log.severe("Failed to load jars for backup restore");
		}
	}

	void loadPatches(Class<? extends Patches> patchClass) {
		try {
			patchTypes = patchClass.getDeclaredConstructors()[0].newInstance(this, classRegistry);
		} catch (Exception e) {
			Log.severe("Failed to instantiate patch class", e);
		}
		for (Method method : patchClass.getDeclaredMethods()) {
			for (Annotation annotation : method.getDeclaredAnnotations()) {
				if (annotation instanceof Patch) {
					PatchMethodDescriptor patchMethodDescriptor = new PatchMethodDescriptor(method, (Patch) annotation);
					patches.put(patchMethodDescriptor.name, patchMethodDescriptor);
				}
			}
		}
	}

	void loadConfig(InputStream configInputStream) throws IOException, SAXException {
		configDocument = DomUtil.readDocumentFromInputStream(configInputStream);
	}

	public void obfuscate(Mappings mappings) {
		splitMultiClassPatches();
		NodeList minecraftClassNodes = ((Element) configDocument.getElementsByTagName("minecraftCommon").item(0)).getElementsByTagName("class");
		for (Element classElement : DomUtil.elementList(minecraftClassNodes)) {
			String className = classElement.getAttribute("id");
			ClassDescription deobfuscatedClass = new ClassDescription(className);
			ClassDescription obfuscatedClass = mappings.map(deobfuscatedClass);
			if (obfuscatedClass != null) {
				classElement.setAttribute("id", obfuscatedClass.name);
			}
			NodeList patchElements = classElement.getChildNodes();
			for (Element patchElement : DomUtil.elementList(patchElements)) {
				String textContent = patchElement.getTextContent().trim();
				List<MethodDescription> methodDescriptionList = MethodDescription.fromListString(deobfuscatedClass.name, textContent);
				if (!textContent.isEmpty()) {
					patchElement.setAttribute("deobf", methodDescriptionList.get(0).getShortName());
					//noinspection unchecked
					patchElement.setTextContent(MethodDescription.toListString((List<MethodDescription>) mappings.map(methodDescriptionList)));
				}
				String field = patchElement.getAttribute("field"), prefix = "";
				if (!field.isEmpty()) {
					if (field.startsWith("this.")) {
						field = field.substring("this.".length());
						prefix = "this.";
					}
					String after = "", type = className;
					if (field.indexOf('.') != -1) {
						after = field.substring(field.indexOf('.'));
						field = field.substring(0, field.indexOf('.'));
						if (!field.isEmpty() && (field.charAt(0) == '$') && prefix.isEmpty()) {
							Set<String> parameters = new HashSet<String>();
							for (MethodDescription methodDescription : methodDescriptionList) {
								parameters.add(methodDescription.getParameters());
							}
							if (parameters.size() == 1) {
								MethodDescription methodDescription = mappings.rmap(mappings.map(methodDescriptionList.get(0)));
								methodDescription = methodDescription == null ? methodDescriptionList.get(0) : methodDescription;
								type = methodDescription.getParameterList().get(Integer.valueOf(field.substring(1)) - 1);
								prefix = field + '.';
								field = after.substring(1);
								after = "";
							}
						}
					}
					FieldDescription obfuscatedField = mappings.map(new FieldDescription(type, field));
					if (obfuscatedField != null) {
						patchElement.setAttribute("field", prefix + obfuscatedField.name + after);
					}
				}
				String clazz = patchElement.getAttribute("class");
				if (!clazz.isEmpty()) {
					ClassDescription obfuscatedClazz = mappings.map(new ClassDescription(clazz));
					if (obfuscatedClazz != null) {
						patchElement.setAttribute("class", obfuscatedClazz.name);
					}
				}
			}
		}
		for (Element classElement : DomUtil.getElementsByTag(configDocument.getDocumentElement(), "class")) {
			for (Element patchElement : DomUtil.elementList(classElement.getChildNodes())) {
				Map<String, String> attributes = DomUtil.getAttributes(patchElement);
				for (String key : attributes.keySet()) {
					String code = patchElement.getAttribute(key);
					if (!code.isEmpty()) {
						String obfuscatedCode = mappings.obfuscate(code);
						if (!code.equals(obfuscatedCode)) {
							patchElement.setAttribute(key, obfuscatedCode);
							Log.info("Obfuscated " + code + " to " + obfuscatedCode);
						}
					}
				}
				String textContent = patchElement.getTextContent();
				if (textContent != null && !textContent.isEmpty()) {
					String obfuscatedTextContent = mappings.obfuscate(textContent);
					if (!textContent.equals(obfuscatedTextContent)) {
						patchElement.setTextContent(obfuscatedTextContent);
						Log.info("Obfuscated " + textContent + " to " + obfuscatedTextContent);
					}
				}
			}
		}
	}

	public Map<String, Integer> getHashes() {
		Map<String, Integer> hashes = new TreeMap<String, Integer>();
		List<Element> modElements = DomUtil.elementList(configDocument.getDocumentElement().getChildNodes());
		for (Element modElement : modElements) {
			for (Element classElement : DomUtil.getElementsByTag(modElement, "class")) {
				hashes.put(classElement.getAttribute("id"), DomUtil.getHash(classElement) + VersionUtil.TTVersionString().hashCode() * 31);
			}
		}
		return hashes;
	}

	public void runPatches() {
		List<Element> modElements = DomUtil.elementList(configDocument.getDocumentElement().getChildNodes());
		patchingClasses = new HashMap<String, CtClass>();
		for (Element modElement : modElements) {
			for (Element classElement : DomUtil.getElementsByTag(modElement, "class")) {
				String className = classElement.getAttribute("id");
				if (!classRegistry.shouldPatch(className)) {
					Log.info(className + " is already patched, skipping.");
					continue;
				}
				String environment = classElement.getAttribute("env");
				if (!environment.isEmpty() && !environment.equals(patchEnvironment)) {
					Log.info(className + " requires " + environment + ", not patched as we are using " + patchEnvironment);
					continue;
				}
				CtClass ctClass;
				try {
					ctClass = classRegistry.getClass(className);
				} catch (NotFoundException e) {
					Log.info("Not patching " + className + ", not found.");
					continue;
				}
				List<Element> patchElements = DomUtil.elementList(classElement.getChildNodes());
				boolean patched = false;
				for (Element patchElement : patchElements) {
					PatchMethodDescriptor patch = patches.get(patchElement.getTagName());
					if (patch == null) {
						Log.severe("Patch " + patchElement.getTagName() + " was not found.");
						continue;
					}
					try {
						Object result = patch.run(patchElement, ctClass);
						patched = true;
						if (result instanceof CtClass) {
							ctClass = (CtClass) result;
						}
					} catch (Exception e) {
						Log.severe("Failed to patch " + ctClass.getName() + " with " + patch.name, e);
					}
				}
				if (patched) {
					patchingClasses.put(className, ctClass);
				}
			}
		}
		for (Map.Entry<String, CtClass> entry : patchingClasses.entrySet()) {
			String className = entry.getKey();
			CtClass ctClass = entry.getValue();
			Patches.findUnusedFields(ctClass);
			try {
				ctClass.getClassFile().compact();
				classRegistry.update(className, ctClass.toBytecode());
			} catch (Exception e) {
				Log.severe("Javassist failed to save " + className, e);
			}
		}
		patchingClasses = null;
	}

	public void save(File file) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		Result output = new StreamResult(file);
		Source input = new DOMSource(configDocument);

		transformer.transform(input, output);
	}

	private void splitMultiClassPatches() {
		for (Element classElement : DomUtil.getElementsByTag(configDocument.getDocumentElement(), "class")) {
			String classNames = classElement.getAttribute("id");
			if (classNames.contains(",")) {
				for (String className : CollectionsUtil.split(classNames.trim())) {
					Element newClassElement = (Element) classElement.cloneNode(true);
					newClassElement.setAttribute("id", className);
					classElement.getParentNode().insertBefore(newClassElement, classElement);
				}
				classElement.getParentNode().removeChild(classElement);
			}
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

		public Object run(Element patchElement, CtClass ctClass) {
			Map<String, String> attributes = DomUtil.getAttributes(patchElement);
			String textContent = patchElement.getTextContent().trim();
			Map<String, String> attributesClean = new HashMap<String, String>(attributes);
			attributesClean.remove("code");
			Log.fine("Patching " + ctClass.getName() + " with " + this.name + '(' + CollectionsUtil.joinMap(attributesClean) + ')' + (textContent.isEmpty() ? "" : " {" + textContent + '}'));
			if (requiredAttributes != null && !attributes.keySet().containsAll(requiredAttributes)) {
				Log.severe("Missing required attributes " + requiredAttributes.toString() + " when patching " + ctClass.getName());
				return null;
			}
			if ("^all^".equals(textContent)) {
				attributes.put("silent", "true");
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
			} else if (isClassPatch || (!emptyConstructor && textContent.isEmpty())) {
				return run(ctClass, attributes);
			} else if (textContent.isEmpty()) {
				for (CtConstructor ctConstructor : ctClass.getDeclaredConstructors()) {
					run(ctConstructor, attributes);
				}
			} else if ("^static^".equals(textContent)) {
				CtConstructor ctBehavior = ctClass.getClassInitializer();
				if (ctBehavior == null) {
					Log.severe("No static initializer found patching " + ctClass.getName() + " with " + toString());
				} else {
					run(ctBehavior, attributes);
				}
			} else {
				List<MethodDescription> methodDescriptions = MethodDescription.fromListString(ctClass.getName(), textContent);
				for (MethodDescription methodDescription : methodDescriptions) {
					CtMethod ctMethod;
					try {
						ctMethod = methodDescription.inClass(ctClass);
					} catch (Throwable t) {
						Log.warning("", t);
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
					return patchMethod.invoke(patchTypes, ctClass);
				} else {
					return patchMethod.invoke(patchTypes, ctClass, attributes);
				}
			} catch (Exception e) {
				if (e instanceof InvocationTargetException) {
					e = (Exception) e.getCause();
				}
				if (e instanceof CannotCompileException && attributes.containsKey("code")) {
					Log.severe("Code: " + attributes.get("code"));
				}
				Log.severe("Error patching " + ctClass.getName() + " with " + toString(), e);
				return null;
			}
		}

		private Object run(CtBehavior ctBehavior, Map<String, String> attributes) {
			try {
				if (requiredAttributes == null) {
					return patchMethod.invoke(patchTypes, ctBehavior);
				} else {
					return patchMethod.invoke(patchTypes, ctBehavior, attributes);
				}
			} catch (Exception e) {
				if (e instanceof InvocationTargetException) {
					e = (Exception) e.getCause();
				}
				if (e instanceof CannotCompileException && attributes.containsKey("code")) {
					Log.severe("Code: " + attributes.get("code"));
				}
				Log.severe("Error patching " + ctBehavior.getName() + " in " + ctBehavior.getDeclaringClass().getName() + " with " + toString(), e);
				return null;
			}
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
