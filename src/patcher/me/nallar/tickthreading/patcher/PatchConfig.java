package me.nallar.tickthreading.patcher;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class PatchConfig {
	Document configDocument;

	public PatchConfig(File configFile) throws IOException, SAXException, ParserConfigurationException {
		this(new FileInputStream(configFile));
	}

	public PatchConfig(InputStream configStream) throws IOException, SAXException, ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		configDocument = docBuilder.parse(configStream);

	}
}
