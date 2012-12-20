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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.nallar.tickthreading.Log;
import me.nallar.tickthreading.mcp.Mappings;
import me.nallar.tickthreading.util.ListUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class PatchConfig {
	Document configDocument;

	public PatchConfig() {

	}

	public PatchConfig(File configFile, Class patchClass) throws IOException, SAXException {
		this();
		loadPatches(patchClass);
		loadConfig(configFile);
	}

	public void loadPatches(Class patchClass) {
		for (Method method : patchClass.getDeclaredMethods()) {
			for (Annotation annotation : method.getDeclaredAnnotations()) {
				if (annotation instanceof Patch) {
					Patch patch = (Patch) annotation;
					PatchMethodInfo patchMethodInfo = new PatchMethodInfo();
					patchMethodInfo.name = patch.name();
					patchMethodInfo.requiredAttributes = ListUtil.split(patch.requiredAttributes());
					if (patchMethodInfo.name == null || patchMethodInfo.name.isEmpty()) {
						patchMethodInfo.name = method.getName();
					}
				}
			}
		}
	}

	public void loadConfig(File configFile) throws IOException, SAXException {
		loadConfig(new FileInputStream(configFile));
	}

	public void loadConfig(InputStream configInputStream) throws IOException, SAXException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			configDocument = docBuilder.parse(configInputStream);
		} catch (ParserConfigurationException e) {
			//This exception is thrown, and no shorthand way of getting a DocumentBuilder without it.
			//Should not be thrown, as we do not do anything to the DocumentBuilderFactory.
			Log.severe("Java was bad, you shouldn't see this. DocBuilder instantiation via default docBuilderFactory failed", e);
		}
	}

	public void obfuscate(Mappings mappings) {

	}

	public void save(File file) throws TransformerException {
		Transformer transformer = TransformerFactory.newInstance().newTransformer();
		Result output = new StreamResult(new File("output.xml"));
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

	public class PatchMethodInfo {
		public String name;
		public List<String> requiredAttributes;
	}
}
