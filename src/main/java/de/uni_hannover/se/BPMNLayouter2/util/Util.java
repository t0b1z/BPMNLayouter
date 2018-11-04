package de.uni_hannover.se.BPMNLayouter2.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.Artifact;
import org.activiti.bpmn.model.Association;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SubProcess;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.DOMBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class Util {

	static boolean firstSortDone = false;
	static List<FlowNode> L;
	static List<SequenceFlow> B;
	static BpmnModel model;
	
	/*
	public static HashMap<String, Element> removeAndGetExtensions(String filename) throws Exception
	{
		HashMap<String, Element> extensionsMap = new HashMap<>();

		Document document = parseXMLFile(filename);
        Element rootElement = document.getRootElement();              
        
        Iterator<Element> extensionIter = rootElement.getDescendants(new ElementFilter("extensionElements"));
        
        while(extensionIter.hasNext())
        {
        	Element currentElement = extensionIter.next();
        	Element extensionParent = (Element) currentElement.getParent();
        	String id = extensionParent.getAttributeValue("id");        	
        	extensionsMap.put(id, currentElement);
        }
        
        for(Element element : extensionsMap.values())
        	element.detach();

     	safeXMLFile(filename + "_extensionless", document);
        
		return extensionsMap;
	}
	*/

	private static void safeXMLFile(String filename, Document document) throws IOException {
		XMLOutputter xmlOutput = new XMLOutputter();

     	xmlOutput.setFormat(Format.getPrettyFormat());
     	xmlOutput.output(document, new FileWriter(filename));
	}

	public static List<FlowNode> topologicalSortNodes(Collection<FlowNode> flowNodes, BpmnModel model) {

		Util.model = model;
		L = new ArrayList<FlowNode>();
		B = new ArrayList<SequenceFlow>();

		Collection<FlowNode> flowNodeList = new ArrayList<>();
		flowNodeList.addAll(flowNodes);

		List<FlowNode> S = new ArrayList<>();

		// algorithm from kitzmann09
		while (!flowNodeList.isEmpty()) {
			S.clear();
			for (FlowNode element : flowNodeList) {
				if (element.getIncomingFlows().size() == 0
						|| allIncomingElementsAlreadyAdded(element.getIncomingFlows(), L))
					S.add(element);
			}
			if (!S.isEmpty()) {
				while (!S.isEmpty()) {
					// ordinary top-sort
					FlowNode n = S.get(0);
					flowNodeList.remove(S.remove(0));
					L.add(n);

					if (n instanceof Activity) {
						Activity activity = (Activity) n;
						List<BoundaryEvent> boundaryEvents = activity.getBoundaryEvents();
						L.addAll(boundaryEvents);

						for (BoundaryEvent boundaryEvent : boundaryEvents) {
							String boundaryEventChildRef = getBoundaryEventChildRef(boundaryEvent, model);
							FlowNode boundaryEventChildNode = (FlowNode) model.getFlowElement(boundaryEventChildRef);
							L.add(boundaryEventChildNode);
							flowNodeList.remove(boundaryEventChildNode);
						}
					}
				}
			} else {
				FlowNode J = null;
				// cycle found
				for (FlowNode element : flowNodeList) {
					// find loop entry
					if (anyIncomingElementAlreadyAdded(element.getIncomingFlows(), L)) {
						J = element;
						break;
					}
				}

				assert J != null;

				// process loop entry
				for (SequenceFlow incomingFlow : J.getIncomingFlows()) {

					if (!L.contains((FlowNode) model.getFlowElement(incomingFlow.getSourceRef())))
						B.add(incomingFlow);
				}

				for (SequenceFlow s : B) {
					swapSourceAndTarget(s);
				}
			}

		}

		for (SequenceFlow s : B) {
			swapSourceAndTarget(s);
		}

		return L;
	}


	private static String getBoundaryEventChildRef(BoundaryEvent boundaryEvent, BpmnModel model) {

		if (boundaryEvent.getOutgoingFlows().size() == 1)
			return boundaryEvent.getOutgoingFlows().get(0).getTargetRef();

		return getAssociationTargetRef(boundaryEvent, model);
	}

	private static String getAssociationTargetRef(BoundaryEvent boundaryEvent, BpmnModel model) {
		String targetRef = searchTargetRefInProcesses(boundaryEvent, model);

		if (targetRef == null)
			targetRef = searchTargetRefInSubProcesses(boundaryEvent, model);

		return targetRef;
	}

	private static String searchTargetRefInSubProcesses(BoundaryEvent boundaryEvent, BpmnModel model2) {
		for (Process process : model.getProcesses()) {
			for (SubProcess subprocess : process.findFlowElementsOfType(SubProcess.class)) {
				for (Artifact artifact : subprocess.getArtifacts()) {
					if (artifact instanceof Association) {
						Association association = (Association) artifact;
						if (association.getSourceRef() == boundaryEvent.getId())
							return association.getTargetRef();
					}
				}
			}
		}
		return null;
	}

	private static String searchTargetRefInProcesses(BoundaryEvent boundaryEvent, BpmnModel model) {
		for (Process process : model.getProcesses()) {
			for (Artifact artifact : process.getArtifacts()) {
				if (artifact instanceof Association) {
					Association association = (Association) artifact;
					if (association.getSourceRef().equals(boundaryEvent.getId()))
						return association.getTargetRef();
				}
			}

		}
		return null;
	}

	private static boolean anyIncomingElementAlreadyAdded(Collection<SequenceFlow> incomingFlows, List<FlowNode> L) {
		for (SequenceFlow incomingFlow : incomingFlows) {
			if (L.contains((FlowNode) model.getFlowElement(incomingFlow.getSourceRef())))
				return true;
		}
		return false;
	}

	private static boolean allIncomingElementsAlreadyAdded(Collection<SequenceFlow> incomingFlows, List<FlowNode> L) {

		for (SequenceFlow incomingFlow : incomingFlows) {
			if (!L.contains((FlowNode) model.getFlowElement(incomingFlow.getSourceRef())))
				return false;
		}
		return true;
	}

	private static void swapSourceAndTarget(SequenceFlow s) {

		FlowNode source = (FlowNode) model.getFlowElement(s.getSourceRef());
		FlowNode target = (FlowNode) model.getFlowElement(s.getTargetRef());

		source.getOutgoingFlows().remove(s); // Remove outoing from loop node
		source.getIncomingFlows().add(s); // Add incoming from loop node
		target.getIncomingFlows().remove(s); // Remove Incoming to chosen start node
		target.getOutgoingFlows().add(s); // Add Outgoing to chosen start node

		s.setSourceRef(target.getId());
		s.setTargetRef(source.getId());
	}

	public static void writeModel(BpmnModel model, String name) throws IOException, FileNotFoundException {
		byte[] xml = new BpmnXMLConverter().convertToXML(model);
		try (FileOutputStream out = new FileOutputStream(name)) {
			out.write(xml);
			}
	}

	public static BpmnModel readBPMFile(File file)
			throws XMLStreamException, FactoryConfigurationError, FileNotFoundException {
		XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(file));
		BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(reader);
		return model;
	}

	private static List<BoundaryEvent> getBoundaryEvents(FlowNode node) {
		if (node instanceof Activity) {
			Activity activity = (Activity) node;
			return activity.getBoundaryEvents();
		}
		return null;
	}

	public static void addXMLElementsBackToFile(HashMap<String, Element> extensionMap, String filename) throws IOException, ParserConfigurationException, SAXException {
		
		Document document = parseXMLFile(filename);
        Element rootElement = document.getRootElement();              
        
        Iterator<Element> elementIterator = rootElement.getDescendants(new ElementFilter());
        
        ArrayList<Element> parents = new ArrayList<>();
        
        while(elementIterator.hasNext())
        {
        	Element currentElement = elementIterator.next();
        	String id = currentElement.getAttributeValue("id");
        	
        	if(id != null && extensionMap.containsKey(id))
        	{
        		parents.add(currentElement);
        	}
        }
        
        for(Element parent : parents)
        {
        	String id = parent.getAttributeValue("id");
        	parent.addContent(extensionMap.get(id));
        }
        
        safeXMLFile(filename, document);
	}

	private static Document parseXMLFile(String filename)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();
        org.w3c.dom.Document w3cDocument = documentBuilder.parse(filename);
        Document document = new DOMBuilder().build(w3cDocument);
		return document;
	}

	public static HashMap<String, Element> removeAndGetElementsFromXML(String filePath, String elementName) throws ParserConfigurationException, SAXException, IOException {
		HashMap<String, Element> elementMap = new HashMap<>();

		Document document = parseXMLFile(filePath);
        Element rootElement = document.getRootElement();              
        
        Iterator<Element> elementIterator = rootElement.getDescendants(new ElementFilter(elementName));
        
        while(elementIterator.hasNext())
        {
        	Element currentElement = elementIterator.next();
        	Element sedParent = (Element) currentElement.getParent();
        	String id = sedParent.getAttributeValue("id");        	
        	elementMap.put(id, currentElement);
        }
        
        for(Element element : elementMap.values())
        	element.detach();

     	safeXMLFile(filePath, document);
        
		return elementMap;
	}
}
