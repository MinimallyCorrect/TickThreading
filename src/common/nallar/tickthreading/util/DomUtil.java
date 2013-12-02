package nallar.tickthreading.util;

import nallar.tickthreading.Log;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public enum DomUtil {
	;

	public static List<Element> getElementsByTag(Element element, String tagName) {
		return elementList(element.getElementsByTagName(tagName));
	}

	public static List<Element> elementList(NodeList nodeList) {
		List<Node> nodes = nodeList(nodeList);
		ArrayList<Element> elements = new ArrayList<Element>(nodes.size());
		for (Node node : nodes) {
			if (node instanceof Element) {
				elements.add((Element) node);
			}
		}
		elements.trimToSize();
		return elements;
	}

	public static List<Node> nodeList(NodeList nodeList) {
		return new NodeListWhichIsActuallyAList(nodeList);
	}

	public static int getHash(Element node) {
		int hash = 5381;
		for (Element child : elementList(node.getChildNodes())) {
			hash += (hash << 5) + getHash(child);
		}
		hash += (hash << 5) + node.getTagName().length();
		hash += (hash << 5) + node.getAttributes().getLength();
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

	private static class NodeListWhichIsActuallyAList extends AbstractList<Node> implements List<Node> {
		private final NodeList nodeList;

		NodeListWhichIsActuallyAList(NodeList nodeList) {
			this.nodeList = nodeList;
		}

		@Override
		public Node get(int index) {
			return nodeList.item(index);
		}

		@Override
		public int size() {
			return nodeList.getLength();
		}
	}

}
