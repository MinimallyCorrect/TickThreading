package me.nallar.tickthreading.patcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.mappings.ClassDescription;
import me.nallar.tickthreading.mappings.FieldDescription;
import me.nallar.tickthreading.mappings.Mappings;
import me.nallar.tickthreading.mappings.MethodDescription;
import me.nallar.tickthreading.util.CollectionsUtil;
import me.nallar.tickthreading.util.DomUtil;
import me.nallar.tickthreading.util.LocationUtil;
import me.nallar.tickthreading.util.VersionUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PatchManager {
	private Document configDocument;
	private Object patchTypes;
	// Patch name -> patch method descriptor
	private final Map<String, PatchMethodDescriptor> patches = new HashMap<String, PatchMethodDescriptor>();
	public final ClassRegistry classRegistry = new ClassRegistry();
	private final File backupDirectory;
	public String patchEnvironment = "forge";

	public PatchManager(InputStream configStream, Class<Patches> patchClass) throws IOException, SAXException {
		loadPatches(patchClass);
		configDocument = loadConfig(configStream);
		backupDirectory = new File(LocationUtil.directoryOf(patchClass).getAbsoluteFile().getParentFile(), "TickThreadingBackups");
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

	void loadPatches(Class<Patches> patchClass) {
		try {
			patchTypes = patchClass.getDeclaredConstructors()[0].newInstance(classRegistry);
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

	private static Document loadConfig(InputStream configInputStream) throws IOException, SAXException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			return docBuilder.parse(configInputStream);
		} catch (ParserConfigurationException e) {
			//This exception is thrown, and no shorthand way of getting a DocumentBuilder without it.
			//Should not be thrown, as we do not do anything to the DocumentBuilderFactory.
			Log.severe("Java was bad, this shouldn't happen. DocBuilder instantiation via default docBuilderFactory failed", e);
		} finally {
			if (configInputStream != null) {
				configInputStream.close();
			}
		}
		return null;
	}

	public void obfuscate(Mappings mappings) {
		NodeList classNodes = ((Element) configDocument.getElementsByTagName("minecraftCommon").item(0)).getElementsByTagName("class");
		for (Element classElement : DomUtil.elementList(classNodes)) {
			String className = classElement.getAttribute("id");
			ClassDescription deobfuscatedClass = new ClassDescription(className);
			ClassDescription obfuscatedClass = mappings.map(deobfuscatedClass);
			classElement.setAttribute("id", obfuscatedClass.name);
			NodeList patchElements = classElement.getChildNodes();
			for (Element patchElement : DomUtil.elementList(patchElements)) {
				List<MethodDescription> methodDescriptionList = MethodDescription.fromListString(deobfuscatedClass.name, patchElement.getTextContent());
				if (!patchElement.getTextContent().isEmpty()) {
					patchElement.setAttribute("deobf", methodDescriptionList.get(0).getShortName());
					patchElement.setTextContent(MethodDescription.toListString(mappings.map(methodDescriptionList)));
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
	}

	public Map<String, Integer> getHashes() {
		Map<String, Integer> hashes = new TreeMap<String, Integer>();
		List<Element> modElements = DomUtil.elementList(configDocument.getDocumentElement().getChildNodes());
		for (Element modElement : modElements) {
			for (Element classElement : DomUtil.getElementsByTag(modElement, "class")) {
				hashes.put(classElement.getAttribute("id"), DomUtil.getHash(classElement) + VersionUtil.versionString().hashCode() * 31);
			}
		}
		return hashes;
	}

	public static boolean shouldPatch(Iterable<File> files) {
		try {
			PatchManager patchManager = new PatchManager(PatchMain.class.getResourceAsStream("/patches.xml"), Patches.class);
			patchManager.classRegistry.loadFiles(files);
			patchManager.classRegistry.finishModifications();
			patchManager.classRegistry.loadPatchHashes(patchManager);
			boolean result = patchManager.classRegistry.shouldPatch();
			patchManager.classRegistry.clearClassInfo();
			return result;
		} catch (Exception e) {
			Log.severe("Failed to determine whether patches should run", e);
		}
		return false;
	}

	public void runPatches() {
		List<Element> modElements = DomUtil.elementList(configDocument.getDocumentElement().getChildNodes());
		Map<String, CtClass> patchedClasses = new HashMap<String, CtClass>();
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
					patchedClasses.put(className, ctClass);
				}
			}
		}
		for (Map.Entry<String, CtClass> entry : patchedClasses.entrySet()) {
			String className = entry.getKey();
			CtClass ctClass = entry.getValue();
			try {
				classRegistry.update(className, ctClass.toBytecode());
			} catch (Exception e) {
				Log.severe("Javassist failed to save " + className, e);
			}
		}
		try {
			classRegistry.save(backupDirectory);
		} catch (IOException e) {
			Log.severe("Failed to save patched classes", e);
			if (e.getMessage().contains("Couldn't rename ")) {
				Log.severe("Make sure none of the mods/server jar are currently open in any running programs" +
						"If you are using linux, check what has open files with the lsof command.");
			}
		}
	}

	public void save(File file) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		Result output = new StreamResult(file);
		Source input = new DOMSource(configDocument);

		transformer.transform(input, output);
	}

	private static Map<String, String> getAttributes(Node node) {
		NamedNodeMap attributeMap = node.getAttributes();
		HashMap<String, String> attributes = new HashMap<String, String>(attributeMap.getLength());
		for (int i = 0; i < attributeMap.getLength(); i++) {
			Node attr = attributeMap.item(i);
			if (attr instanceof Attr) {
				attributes.put(((Attr) attr).getName(), ((Attr) attr).getValue());
			}
		}
		return attributes;
	}

	public class PatchMethodDescriptor {
		public String name;
		public final List<String> requiredAttributes;
		public final Method patchMethod;
		public final boolean isClassPatch;
		public final boolean emptyConstructor;

		public PatchMethodDescriptor(Method method, Patch patch) {
			this.name = patch.name();
			if (Arrays.asList(method.getParameterTypes()).contains(Map.class)) {
				this.requiredAttributes = CollectionsUtil.split(patch.requiredAttributes());
			} else {
				this.requiredAttributes = null;
			}
			if (this.name == null || this.name.isEmpty()) {
				this.name = method.getName();
			}
			emptyConstructor = patch.emptyConstructor();
			isClassPatch = method.getParameterTypes()[0].equals(CtClass.class);
			patchMethod = method;
		}

		public Object run(Element patchElement, CtClass ctClass) {
			Map<String, String> attributes = getAttributes(patchElement);
			Log.fine("Patching " + ctClass.getName() + " with " + this.name + '(' + CollectionsUtil.joinMap(attributes) + ')');
			if (requiredAttributes != null && !attributes.keySet().containsAll(requiredAttributes)) {
				Log.severe("Missing required attributes " + requiredAttributes.toString() + " when patching " + ctClass.getName());
				return null;
			}
			if (isClassPatch || (!emptyConstructor && patchElement.getTextContent().isEmpty())) {
				return run(ctClass, attributes);
			} else if (patchElement.getTextContent().isEmpty()) {
				run(ctClass.getConstructors()[0], attributes);
			} else {
				List<MethodDescription> methodDescriptions = MethodDescription.fromListString(ctClass.getName(), patchElement.getTextContent());
				Log.fine("Patching methods " + methodDescriptions.toString());
				for (MethodDescription methodDescription : methodDescriptions) {
					run(methodDescription.inClass(ctClass), attributes);
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
