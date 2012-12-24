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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.mappings.ClassDescription;
import me.nallar.tickthreading.mappings.Mappings;
import me.nallar.tickthreading.mappings.MethodDescription;
import me.nallar.tickthreading.util.DomUtil;
import me.nallar.tickthreading.util.ListUtil;
import me.nallar.tickthreading.util.LocationUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class PatchManager {
	private static int cachedPatchVersion;
	private static Properties patchStatus = new Properties();
	private static File patchStatusLocation = new File(LocationUtil.directoryOf(PatchManager.class).getParentFile(), "TickThreading/patch.conf");

	static {
		if (patchStatusLocation.exists()) {
			try {
				patchStatus.load(patchStatusLocation.toURI().toURL().openStream());
			} catch (IOException e) {
				Log.severe("Failed to load patch properties", e);
			}
		} else {
			patchStatusLocation.getParentFile().mkdirs();
		}
	}

	private Document configDocument;
	private Object patchTypes;
	// Patch name -> patch method descriptor
	private Map<String, PatchMethodDescriptor> patches = new HashMap<String, PatchMethodDescriptor>();
	public final ClassRegistry classRegistry = new ClassRegistry();

	public PatchManager(InputStream configStream, Class patchClass) throws IOException, SAXException {
		loadPatches(patchClass);
		configDocument = loadConfig(configStream);
	}

	public void loadPatches(Class patchClass) {
		try {
			patchTypes = patchClass.newInstance();
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

	public static Document loadConfig(InputStream configInputStream) throws IOException, SAXException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			return docBuilder.parse(configInputStream);
		} catch (ParserConfigurationException e) {
			//This exception is thrown, and no shorthand way of getting a DocumentBuilder without it.
			//Should not be thrown, as we do not do anything to the DocumentBuilderFactory.
			Log.severe("Java was bad, you shouldn't see this. DocBuilder instantiation via default docBuilderFactory failed", e);
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
				if (!patchElement.getTextContent().isEmpty()) {
					patchElement.setTextContent(MethodDescription.toListString(mappings.map(MethodDescription.fromListString(deobfuscatedClass.name, patchElement.getTextContent()))));
				}
			}
		}
	}

	public static boolean shouldPatch() {
		Object result = patchStatus.get("patchVersion");
		if (result instanceof Integer) {
			return getPatchVersion() != (Integer) result;
		} else {
			return true;
		}
	}

	public static int getPatchVersion() {
		if (cachedPatchVersion == -1) {
			try {
				InputStream inputStream = PatchManager.class.getResourceAsStream("patches.xml");
				if (inputStream == null) {
					inputStream = new File("patches.xml").toURL().openStream();
				}
				if (inputStream != null) {
					cachedPatchVersion = Integer.valueOf(DomUtil.getElementsByTag(loadConfig(inputStream).getDocumentElement(), "mod").get(0).getAttribute("version"));
				}
			} catch (Exception e) {
				Log.severe("Failed to load patch version", e);
				cachedPatchVersion = 0;
			}
		}
		return cachedPatchVersion;
	}

	public void runPatches() {
		List<Element> modElements = DomUtil.elementList(configDocument.getDocumentElement().getFirstChild().getChildNodes());
		for (Element modElement : modElements) {
			for (Element classElement : DomUtil.getElementsByTag(modElement, "class")) {
				try {
					CtClass ctClass = classRegistry.getClass(classElement.getAttribute("id"));
					List<Element> patchElements = DomUtil.elementList(classElement.getChildNodes());
					boolean patched = false;
					for (Element patchElement : patchElements) {
						PatchMethodDescriptor patch = patches.get(patchElement.getTagName());
						if (patch == null) {
							Log.severe("Patch " + patchElement.getTagName() + " was not found.");
							continue;
						}
						try {
							patch.run(patchElement, ctClass);
							patched = true;
						} catch (Exception e) {
							Log.severe("Failed to patch " + ctClass + " with " + patch.name + " :(");
						}
					}
					if (patched) {
						try {
							classRegistry.update(classElement.getAttribute("id"), ctClass.toBytecode());
						} catch (Exception e) {
							Log.severe("Javassist failed to save " + ctClass.getName(), e);
						}
					}
				} catch (NotFoundException e) {
					Log.info("Not patching " + classElement.getAttribute("id") + ", not found.");
				}
			}
		}
		try {
			classRegistry.save();
		} catch (IOException e) {
			Log.severe("Failed to save patched classes", e);
		}
		patchStatus.put("patchVersion", getPatchVersion());
		try {
			patchStatus.store(new FileOutputStream(patchStatusLocation), "Patch status");
		} catch (IOException e) {
			Log.severe("Failed to save patch status", e);
		}
	}

	public void save(File file) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		Result output = new StreamResult(file);
		Source input = new DOMSource(configDocument);

		transformer.transform(input, output);
	}

	public static Map<String, String> getAttributes(Node node) {
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
		public List<String> requiredAttributes;
		public Method patchMethod;

		public PatchMethodDescriptor(Method method, Patch patch) {
			this.name = patch.name();
			this.requiredAttributes = ListUtil.split(patch.requiredAttributes());
			if (this.name == null || this.name.isEmpty()) {
				this.name = method.getName();
			}
			patchMethod = method;
		}

		public void run(Element patchElement, CtClass ctClass) {
			if (patchElement.getTextContent().isEmpty()) {
				run(ctClass);
			} else {
				List<MethodDescription> methodDescriptions = MethodDescription.fromListString(ctClass.getSimpleName(), patchElement.getTextContent());
				for (MethodDescription methodDescription : methodDescriptions) {
					try {
						run(methodDescription.inClass(ctClass));
					} catch (Exception e) {
						Log.severe("Error patching " + methodDescription.getMCPName() + " in " + ctClass, e);
					}
				}
			}
		}

		private void run(CtClass clazz) {
			try {
				patchMethod.invoke(patchTypes, clazz);
			} catch (Exception e) {
				Log.severe("Failed to invoke class patch " + this, e);
			}
		}

		private void run(CtMethod method) {
			try {
				patchMethod.invoke(patchTypes, method);
			} catch (Exception e) {
				Log.severe("Failed to invoke method patch " + this, e);
			}
		}
	}
}
