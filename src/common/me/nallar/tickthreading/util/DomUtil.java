package me.nallar.tickthreading.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.nallar.tickthreading.Log;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public enum DomUtil {
	;

	public static List<Element> getElementsByTag(Element element, String tagName) {
		return elementList(element.getElementsByTagName(tagName));
	}

	public static List<Element> elementList(NodeList nodeList) {
		List<Node> nodes = nodeList(nodeList);
		ArrayList<Element> elements = new ArrayList<Element>(nodeList.getLength());
		for (Node node : nodes) {
			if (node instanceof Element) {
				elements.add((Element) node);
			}
		}
		elements.trimToSize();
		return elements;
	}

	public static List<Node> nodeList(NodeList nodeList) {
		int length = nodeList.getLength();
		List<Node> nodes = new ArrayList<Node>(length);
		for (int i = 0; i < length; i++) {
			nodes.add(nodeList.item(i));
		}
		return nodes;
	}

	public static int getHash(Element node) {
		int hash = 5381;
		for (Element child : elementList(node.getChildNodes())) {
			hash += (hash << 5) + getHash(child);
		}
		hash += (hash << 5) + node.getTagName().hashCode();
		for (Map.Entry<String, String> entry : getAttributes(node).entrySet()) {
			hash += (hash << 5) + entry.getKey().hashCode();
			hash += (hash << 5) + entry.getValue().hashCode();
		}
		return hash;
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

	public static Document readDocumentFromInputStream(InputStream configInputStream) throws IOException, SAXException {
		if (configInputStream == null) {
			throw new NullPointerException("configInputStream");
		}
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			return docBuilder.parse(configInputStream);
		} catch (ParserConfigurationException e) {
			//This exception is thrown, and no shorthand way of getting a DocumentBuilder without it.
			//Should not be thrown, as we do not do anything to the DocumentBuilderFactory.
			Log.severe("Java was bad, this shouldn't happen. DocBuilder instantiation via default docBuilderFactory failed", e);
			configInputStream.close();
		}
		return null;
	}
}
