package me.nallar.tickthreading.patcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import me.nallar.tickthreading.Log;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class PatchConfig {
	Document configDocument;

	public PatchConfig(File configFile) throws IOException, SAXException {
		this(new FileInputStream(configFile));
	}

	public PatchConfig(InputStream configStream) throws IOException, SAXException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			configDocument = docBuilder.parse(configStream);
		} catch (ParserConfigurationException e) {
			//This exception is thrown, and no shorthand way of getting a DocumentBuilder without it.
			//Should not be thrown, as we do not do anything to the DocumentBuilderFactory.
			Log.severe("Java was bad, you shouldn't see this. DocBuilder instantiation via default docBuilderFactory failed", e);
		}
	}

	public void loadPatches(Class patchClass) {

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
}
