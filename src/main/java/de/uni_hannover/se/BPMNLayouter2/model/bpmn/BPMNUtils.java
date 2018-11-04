package de.uni_hannover.se.BPMNLayouter2.model.bpmn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.Artifact;
import org.activiti.bpmn.model.Association;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.GraphicInfo;
import org.activiti.bpmn.model.Lane;
import org.activiti.bpmn.model.Pool;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SubProcess;

public class BPMNUtils {
	
	private static List<SequenceFlow> containerSpanningFlows;
	private static HashMap<FlowNode, SequenceFlow> markedFlows;
	
	public static List<SequenceFlow> findAndTemporarilyRemoveFlowsBetweenPartitions(List<Process> processes, BpmnModel model) {
		containerSpanningFlows = new ArrayList<>();
		markedFlows = new HashMap<>();

		List<List<FlowNode>> flowNodeContainers = new ArrayList<>();
		addPartitionFlowNodesToSeparateContainer(processes, flowNodeContainers, model);

		for (List<FlowNode> container : flowNodeContainers) {
			for (FlowNode node : container) {
				markAndSaveInterleavingEdges(container, node, model);
				removeAllMarkedEdges(model);
			}
		}
		return containerSpanningFlows;
	}
	
	private static void addPartitionFlowNodesToSeparateContainer(List<Process> processes,
			List<List<FlowNode>> flowNodeContainers, BpmnModel model) {
		for (Process process : processes) {
			if (process.getLanes().size() == 0) {
				addFlowNodesToNewFlowNodeContainer(flowNodeContainers, process.getFlowElements());
			} else {
				for (Lane lane : process.getLanes()) {
					List<FlowElement> laneFlowElements = gatherLaneFlowElements(lane, model);
					addFlowNodesToNewFlowNodeContainer(flowNodeContainers, laneFlowElements);
				}
			}
		}
	}
	
	private static void addFlowNodesToNewFlowNodeContainer(List<List<FlowNode>> flowNodeContainers,
			Collection<FlowElement> flowElements) {
		ArrayList<FlowNode> flowNodes = new ArrayList<>();
		flowNodeContainers.add(flowNodes);
		for (FlowElement element : flowElements) {
			if (element instanceof FlowNode)
				flowNodes.add((FlowNode) element);
		}
	}
	
	private static void markAndSaveInterleavingEdges(List<FlowNode> container, FlowNode node, BpmnModel model) {
		for (SequenceFlow sequenceFlow : node.getOutgoingFlows()) {
			FlowNode target = (FlowNode) model.getFlowElement(sequenceFlow.getTargetRef());
			if (!container.contains(target)) {
				containerSpanningFlows.add(sequenceFlow);
				markEdgesForRemoval(node, sequenceFlow, model);
			}
		}
	}
	
	private static void markEdgesForRemoval(FlowNode node, SequenceFlow sequenceFlow, BpmnModel model) {
		markedFlows.put(node, sequenceFlow);
	}
	
	
	private static void removeAllMarkedEdges(BpmnModel model) {
		for (FlowNode node : markedFlows.keySet()) {
			node.getOutgoingFlows().remove(markedFlows.get(node));

			String targetRef = markedFlows.get(node).getTargetRef();
			FlowNode target = (FlowNode) model.getFlowElement(targetRef);
			target.getIncomingFlows().remove(markedFlows.get(node));
		}
	}

	public static boolean isElementPartOfSequentialSequence(FlowNode node, FlowNode previousNode) {
		int incomingFlowSize = node.getIncomingFlows().size();
		int outgoingFlowSize = node.getOutgoingFlows().size();
		int outgoingFlowSizeOfPreviousNode = previousNode.getOutgoingFlows().size();
		return incomingFlowSize == 1 && outgoingFlowSize <= 1 && outgoingFlowSizeOfPreviousNode == 1;
	}

	public static int getBoundaryEventCount(FlowNode node) {
		int boundaryEventCount = 0;
		if (node instanceof Activity) {
			Activity activity = (Activity) node;
			boundaryEventCount += activity.getBoundaryEvents().size();
		} else if (node instanceof SubProcess) {
			SubProcess subProcess = (SubProcess) node;
			boundaryEventCount += subProcess.getBoundaryEvents().size();
		}
		return boundaryEventCount;
	}

	public static List<FlowElement> gatherLaneFlowElements(Lane lane, BpmnModel model) {
		List<String> laneFlowRefs = lane.getFlowReferences();
		List<FlowElement> laneFlowElements = new ArrayList<>();
	
		for (String ref : laneFlowRefs) {
			laneFlowElements.add(model.getFlowElement(ref));
		}
		return laneFlowElements;
	}

	public static List<FlowNode> getFlowNodesFromFlowElementsList(Collection<FlowElement> flowElements) {
		List<FlowNode> flowNodes = new ArrayList<>();
		for (FlowElement e : flowElements) {
			if (e instanceof FlowNode && !(e instanceof BoundaryEvent)) {
				flowNodes.add((FlowNode) e);
			}
		}
		return flowNodes;
	}

	public static Process getProcessFromModel(String processRef, BpmnModel model) {
		for (Process p : model.getProcesses()) {
			if (p.getId().equals(processRef))
				return p;
		}
		return null;
	}
	

	public static List<FlowNode> findAllSubsequentFlowNodesInGroup(FlowNode startingFlowNode, BpmnModel model) {
		List<FlowNode> collectedFlowNodes = new ArrayList<>();
		collectedFlowNodes.add(startingFlowNode);
		
		try {
			findFlowNodesRecursive(startingFlowNode, collectedFlowNodes, model);
		}catch(NullPointerException e)
		{
			e.printStackTrace();
			return new ArrayList<>();
		}

		return collectedFlowNodes;
	}
	
	public static FlowNode getSubsequentNode(FlowNode node, BpmnModel model) {
		String targetRef;
		if (node.getOutgoingFlows().size() == 0) {
			BoundaryEvent boundaryEvent = ((Activity) node).getBoundaryEvents().iterator().next();
			targetRef = boundaryEvent.getOutgoingFlows().iterator().next().getTargetRef();

		} else {
			targetRef = node.getOutgoingFlows().iterator().next().getTargetRef();
		}

		FlowNode firstTargetNode = (FlowNode) model.getFlowElement(targetRef);
		return firstTargetNode;
	}
	
	private static void findFlowNodesRecursive(FlowNode startingFlowNode, List<FlowNode> collectedFlowNodes, BpmnModel model) throws NullPointerException{
		for (SequenceFlow flow : startingFlowNode.getOutgoingFlows()) {
			if (flow.getTargetRef() == null)
				return;

			FlowNode targetNode = (FlowNode) model.getFlowElement(flow.getTargetRef());

			if (containerSpanningFlows.contains(flow))
				return;

			if (collectedFlowNodes.contains(targetNode))
				return;

			collectedFlowNodes.add(targetNode);
			findFlowNodesRecursive(targetNode, collectedFlowNodes, model);
		}
	}

	public static boolean laneIsNotWithinPool(Lane lane, BpmnModel model) {
		for (Pool pool : model.getPools()) {
			String processRef = pool.getProcessRef();
			Process process = getProcessFromModel(processRef, model);
			if (process != null && process.getLanes().contains(lane))
				return false;
		}
		return true;
	}

	public static boolean isProcessInPool(Process process, BpmnModel model) {
		for (Pool pool : model.getPools()) {
			if (process == model.getProcess(pool.getId()))
				return true;
		}
		return false;
	}
	
	public static boolean activityIsExpanded(String id, BpmnModel model) {
		GraphicInfo gi = model.getGraphicInfo(id);
		return (gi.getExpanded() != null && gi.getExpanded());
	}

	public static List<SequenceFlow> replaceAssociationsWithSequenceFlows(BpmnModel model) {
		List<SequenceFlow> temporarySequenceFlows = new ArrayList<>();
		
		for(Process process : model.getProcesses()){
			for(Artifact artifact : process.getArtifacts()) {
				if(artifact instanceof Association) {
					String sourceRef = ((Association) artifact).getSourceRef();
					String targetRef = ((Association) artifact).getTargetRef();
					
					SequenceFlow tempFlow = new SequenceFlow();
					tempFlow.setSourceRef(sourceRef);
					tempFlow.setTargetRef(targetRef);
					
					if(!(model.getFlowElement(sourceRef) instanceof FlowNode))
						continue;
					
					FlowNode sourceNode = ((FlowNode)model.getFlowElement(sourceRef));
					FlowNode targetNode = ((FlowNode)model.getFlowElement(targetRef));
					
					if(targetNode == null)
						continue;

					sourceNode.getOutgoingFlows().add(tempFlow);
					targetNode.getIncomingFlows().add(tempFlow);
					
					temporarySequenceFlows.add(tempFlow);
				}
			}
		}
		
		return temporarySequenceFlows;
	}

	public static void removeTemporarySequenceFlowsFromModel(List<SequenceFlow> temporarySequenceFlows, BpmnModel model) {
		
		for(SequenceFlow flow : temporarySequenceFlows) {
			String sourceRef = flow.getSourceRef();
			String targetRef = flow.getTargetRef();
			
			((FlowNode)model.getFlowElement(sourceRef)).getOutgoingFlows().remove(flow);
			((FlowNode)model.getFlowElement(targetRef)).getIncomingFlows().remove(flow);
		}
	}

}
