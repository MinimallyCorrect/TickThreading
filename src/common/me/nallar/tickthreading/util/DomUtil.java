package me.nallar.tickthreading.util;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DomUtil {
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
}
