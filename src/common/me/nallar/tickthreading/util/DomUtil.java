package me.nallar.tickthreading.util;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import me.nallar.tickthreading.Log;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

	public static String nodeToString(Node node) {
		TransformerFactory transFactory = TransformerFactory.newInstance();
		try {
			Transformer transformer = transFactory.newTransformer();
			StringWriter buffer = new StringWriter();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(new DOMSource(node), new StreamResult(buffer));
			return buffer.toString();
		} catch (TransformerException e) {
			Log.severe("Failed to convert " + node + " to string.", e);
		}
		return "";
	}

	public static int getHash(Node node) {
		return nodeToString(node).hashCode();
	}
}
