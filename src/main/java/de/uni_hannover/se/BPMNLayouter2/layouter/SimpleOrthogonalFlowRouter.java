package de.uni_hannover.se.BPMNLayouter2.layouter;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.activiti.bpmn.model.Artifact;
import org.activiti.bpmn.model.Association;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowElementsContainer;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.GraphicInfo;
import org.activiti.bpmn.model.Lane;
import org.activiti.bpmn.model.MessageFlow;
import org.activiti.bpmn.model.Pool;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SubProcess;

import de.uni_hannover.se.BPMNLayouter2.util.FlowInformation;

public class SimpleOrthogonalFlowRouter {

	static private BpmnModel model;

	public static void routeFlows(BpmnModel model) {
		SimpleOrthogonalFlowRouter.model = model;

		List<FlowNode> flowNodes = gatherAllFlowNodes(model);
		List<List<GraphicInfo>> flowNodeGraphicInfoLists = new ArrayList<>();

		for (FlowNode flowNode : flowNodes) {
			for (SequenceFlow sequenceFlow : flowNode.getOutgoingFlows()) {
				List<GraphicInfo> giList = updateFlowGraphicInfo(sequenceFlow);
				if (giList != null)
					flowNodeGraphicInfoLists.add(giList);
			}
		}

		for (MessageFlow messageFlow : model.getMessageFlows().values()) {
			List<GraphicInfo> giList = updateFlowGraphicInfo(messageFlow);
			if (giList != null)
				flowNodeGraphicInfoLists.add(giList);
		}

		for (Process process : model.getProcesses()) {
			for (Artifact artifact : process.getArtifacts()) {
				if (artifact instanceof Association) {
					Association association = (Association) artifact;
					List<GraphicInfo> giList = updateFlowGraphicInfo(association);
					if (giList != null)
						flowNodeGraphicInfoLists.add(giList);
				}
			}
		}
	}

	private static List<GraphicInfo> updateFlowGraphicInfo(Object flowType) {

		FlowInformation fi = getFlowInformation(flowType);

		if (fi == null)
			return null;

		String iD = fi.iD;
		String sourceRef = fi.sourceRef;
		String targetRef = fi.targetRef;

		List<GraphicInfo> graphicInfoList = model.getFlowLocationGraphicInfo(iD);
		List<GraphicInfo> bends = new ArrayList<>();
		graphicInfoList.clear();

		GraphicInfo sourceFlowGI = new GraphicInfo();
		GraphicInfo sourceNodeGI = model.getGraphicInfo(sourceRef);
		FlowNode sourceNode = (FlowNode) model.getFlowElement(sourceRef);
		Point sourceNodeCenter = getCenterPoint(sourceNodeGI);

		GraphicInfo targetFlowGI = new GraphicInfo();
		GraphicInfo targetNodeGI = model.getGraphicInfo(targetRef);
		Point targetNodeCenter = getCenterPoint(targetNodeGI);

		if (model.getPool(sourceRef) != null) {
			sourceFlowGI.setX(targetNodeCenter.getX());

			if (sourceNodeGI.getY() < targetNodeGI.getY()) {
				sourceFlowGI.setY(sourceNodeGI.getY() + sourceNodeGI.getHeight());
				targetFlowGI.setY(targetNodeCenter.getY() - (targetNodeGI.getHeight() * 0.5));
			} else {
				sourceFlowGI.setY(sourceNodeGI.getY());
				targetFlowGI.setY(targetNodeCenter.getY() + (targetNodeGI.getHeight() * 0.5));
			}

			graphicInfoList.add(sourceFlowGI);

			targetFlowGI.setX(targetNodeCenter.getX());
			graphicInfoList.add(targetFlowGI);
			return null;
		} else if (model.getPool(targetRef) != null) {
			sourceFlowGI.setX(sourceNodeCenter.getX());

			graphicInfoList.add(sourceFlowGI);

			targetFlowGI.setX(sourceNodeCenter.getX());
			if (targetNodeGI.getY() < sourceNodeGI.getY()) {
				sourceFlowGI.setY(sourceNodeCenter.getY() - (sourceNodeGI.getHeight() * 0.5));
				targetFlowGI.setY(targetNodeGI.getY() + targetNodeGI.getHeight());
			} else {
				sourceFlowGI.setY(sourceNodeCenter.getY() + (sourceNodeGI.getHeight() * 0.5));
				targetFlowGI.setY(targetNodeGI.getY());
			}

			graphicInfoList.add(targetFlowGI);
			return null;
		} else if (sourceNode == null)
			return null;

		double sourceX = sourceNodeGI.getX(), sourceY = sourceNodeGI.getY();
		double targetX = targetNodeGI.getX(), targetY = targetNodeGI.getY();

		if (sourceNode instanceof BoundaryEvent) {
			sourceX = sourceNodeGI.getX() + sourceNodeGI.getWidth() / 2;
			sourceY = sourceNodeGI.getY() + sourceNodeGI.getHeight() / 2;

			targetY += targetNodeGI.getHeight() / 2;

			GraphicInfo bendGI = new GraphicInfo();
			bendGI.setX(sourceX);
			bendGI.setY(targetY);
			bends.add(bendGI);
		} else if (Math.abs(sourceNodeCenter.getY() - targetNodeCenter.getY()) < 3) {
			sourceY += sourceNodeGI.getHeight() / 2;
			if (sourceNodeCenter.getX() < targetNodeCenter.getX()) {
				sourceX += sourceNodeGI.getWidth();
				targetY += targetNodeGI.getHeight() / 2;
			}

		} else if (Math.abs(sourceNodeCenter.getX() - targetNodeCenter.getX()) < 3) {
			sourceX += sourceNodeGI.getWidth() / 2;
			if (sourceNodeCenter.getY() < targetNodeCenter.getY()) {
				sourceY += sourceNodeGI.getHeight();
				targetX += targetNodeGI.getWidth() / 2;
			} else {
				targetY += sourceNodeGI.getHeight();
				targetX += targetNodeGI.getWidth() / 2;
			}
		} else if (sourceNodeCenter.getY() > targetNodeCenter.getY()) {

			if (flowType instanceof MessageFlow) {

				int poolMiddleCoordinate = getPoolMiddleCoordinate((MessageFlow) flowType);
				int offset = 0;
				int direction = 1;

				while (horizontalLineIsOverlapping(poolMiddleCoordinate, sourceNodeCenter.x, targetNodeCenter.x, offset)) {
					
					if(direction == 1)
					{
						offset = (Math.abs(offset) + 5);
						direction *= -1;
					}else {
						offset = direction * offset;
						direction *= -1;
					}
				}

				GraphicInfo bend1 = new GraphicInfo();
				bend1.setX(sourceNodeCenter.x);
				bend1.setY(poolMiddleCoordinate + offset);
				bends.add(bend1);

				GraphicInfo bend2 = new GraphicInfo();
				bend2.setX(targetNodeCenter.x);
				bend2.setY(poolMiddleCoordinate + offset);
				bends.add(bend2);

			} else if (sourceNode.getOutgoingFlows().size() == 1) {
				sourceX += sourceNodeGI.getWidth();
				sourceY += sourceNodeGI.getHeight() / 2;
				targetX += targetNodeGI.getWidth() / 2;
				targetY += targetNodeGI.getHeight();
				GraphicInfo bendGI = new GraphicInfo();
				bendGI.setX(targetX);
				bendGI.setY(sourceY);
				bends.add(bendGI);
			} else if (sourceNode.getOutgoingFlows().size() > 1) {
				sourceX = sourceNodeGI.getX() + sourceNodeGI.getWidth() / 2;
				sourceY = sourceNodeGI.getY() + sourceNodeGI.getHeight() / 2;
				targetY += targetNodeGI.getHeight() / 2;
				GraphicInfo bendGI = new GraphicInfo();
				bendGI.setX(sourceX);
				bendGI.setY(targetY);
				bends.add(bendGI);
			}

		} else if (sourceNodeCenter.getY() < targetNodeCenter.getY()) {
			if (flowType instanceof MessageFlow) {
				int poolMiddleCoordinate = getPoolMiddleCoordinate((MessageFlow) flowType);
				int yOffset = 0;
				int yOffsetDirection = 1;

				while (horizontalLineIsOverlapping(poolMiddleCoordinate, sourceNodeCenter.x, targetNodeCenter.x, yOffset)) {
					yOffsetDirection *= -1;
					yOffset = yOffsetDirection * (Math.abs(yOffset) + 5);
				}
				
				GraphicInfo bend1 = new GraphicInfo();
				bend1.setX(sourceNodeCenter.x);
				bend1.setY(poolMiddleCoordinate + yOffset);
				bends.add(bend1);

				GraphicInfo bend2 = new GraphicInfo();
				bend2.setX(targetNodeCenter.x);
				bend2.setY(poolMiddleCoordinate + yOffset);
				bends.add(bend2);

			} else if (sourceNode.getOutgoingFlows().size() == 1) {
				sourceX += sourceNodeGI.getWidth();
				sourceY += sourceNodeGI.getHeight() / 2;
				targetX += targetNodeGI.getWidth() / 2;
				GraphicInfo bendGI = new GraphicInfo();
				bendGI.setX(targetX);
				bendGI.setY(sourceY);
				bends.add(bendGI);
			} else if (sourceNode.getOutgoingFlows().size() > 1) {
				sourceX = sourceNodeGI.getX() + sourceNodeGI.getWidth() / 2;
				sourceY = sourceNodeGI.getY() + sourceNodeGI.getHeight() / 2;
				targetY += targetNodeGI.getHeight() / 2;
				GraphicInfo bendGI = new GraphicInfo();
				bendGI.setX(sourceX);
				bendGI.setY(targetY);
				bends.add(bendGI);
			}

		}

		sourceFlowGI.setX(sourceNodeCenter.x);
		sourceFlowGI.setY(sourceNodeCenter.y);
		graphicInfoList.add(sourceFlowGI);

		graphicInfoList.addAll(bends);

		targetFlowGI.setX(targetNodeCenter.x);
		targetFlowGI.setY(targetNodeCenter.y);
		graphicInfoList.add(targetFlowGI);

		return graphicInfoList;
	}

	private static boolean horizontalLineIsOverlapping(int y, int x1, int x2, int yOffset) {

		for (MessageFlow messageFlow : model.getMessageFlows().values()) {

			List<GraphicInfo> graphicInfoList = model.getFlowLocationGraphicInfo(messageFlow.getId());
			
			int currentYCoordinate = y + yOffset;

			for (int i = 0; i < graphicInfoList.size() - 1; i++) {
				
				if (graphicInfoList.get(i).getY() == graphicInfoList.get(i + 1).getY() && currentYCoordinate == graphicInfoList.get(i).getY()) {
					if (graphicInfoList.get(i).getX() < graphicInfoList.get(i + 1).getX()) {
						if (x1 > graphicInfoList.get(i).getX() && x1 < graphicInfoList.get(i + 1).getX())
							return true;

						if (x2 > graphicInfoList.get(i).getX() && x2 < graphicInfoList.get(i + 1).getX())
							return true;
					}
					if (graphicInfoList.get(i).getX() > graphicInfoList.get(i + 1).getX()) {
						if (x1 < graphicInfoList.get(i).getX() && x1 > graphicInfoList.get(i + 1).getX())
							return true;

						if (x2 < graphicInfoList.get(i).getX() && x2 > graphicInfoList.get(i + 1).getX())
							return true;
					}
				}
			}
		}

		return false;
	}
	
	private static boolean verticalLineIsOverlapping(int x, int y1, int y2, int xOffset) {

		for (MessageFlow messageFlow : model.getMessageFlows().values()) {

			List<GraphicInfo> graphicInfoList = model.getFlowLocationGraphicInfo(messageFlow.getId());
			
			int currentXCoordinate = x + xOffset;

			for (int i = 0; i < graphicInfoList.size() - 1; i++) {
				
				if (graphicInfoList.get(i).getX() == graphicInfoList.get(i + 1).getX() && currentXCoordinate == graphicInfoList.get(i).getX()) {
					if (graphicInfoList.get(i).getY() < graphicInfoList.get(i + 1).getY()) {
						if (x > graphicInfoList.get(i).getY() && x < graphicInfoList.get(i + 1).getY())
							return true;

						if (y2 > graphicInfoList.get(i).getY() && y2 < graphicInfoList.get(i + 1).getY())
							return true;
					}
					if (graphicInfoList.get(i).getY() > graphicInfoList.get(i + 1).getY()) {
						if (x < graphicInfoList.get(i).getY() && x > graphicInfoList.get(i + 1).getY())
							return true;

						if (y2 < graphicInfoList.get(i).getY() && y2 > graphicInfoList.get(i + 1).getY())
							return true;
					}
				}
			}
		}

		return false;
	}

	private static FlowInformation getFlowInformation(Object flowType) {
		FlowInformation fi = new FlowInformation();

		if (flowType instanceof SequenceFlow) {
			SequenceFlow seqFlow = (SequenceFlow) flowType;
			fi.iD = seqFlow.getId();
			fi.sourceRef = seqFlow.getSourceRef();
			fi.targetRef = seqFlow.getTargetRef();
		}

		if (flowType instanceof MessageFlow) {
			MessageFlow msgFlow = (MessageFlow) flowType;
			fi.iD = msgFlow.getId();
			fi.sourceRef = msgFlow.getSourceRef();
			fi.targetRef = msgFlow.getTargetRef();
		}

		if (flowType instanceof Association) {
			Association association = (Association) flowType;
			fi.iD = association.getId();
			fi.sourceRef = association.getSourceRef();
			fi.targetRef = association.getTargetRef();

			if (!(model.getFlowElement(fi.sourceRef) instanceof FlowNode))
				return null;

			if (!(model.getFlowElement(fi.targetRef) instanceof FlowNode))
				return null;
		}
		return fi;
	}

	private static int getPoolMiddleCoordinate(MessageFlow flow) {
		Pool sourceNodePool = getPoolOfNode(flow.getSourceRef());
		Pool targetNodePool = getPoolOfNode(flow.getTargetRef());

		Lane sourceNodeLane = getLaneOfNode(flow.getSourceRef());
		Lane targetNodeLane = getLaneOfNode(flow.getTargetRef());

		GraphicInfo sourceNodeGI;
		GraphicInfo targetNodeGI;

		if (sourceNodePool != null) {
			sourceNodeGI = model.getGraphicInfo(sourceNodePool.getId());
		} else if (sourceNodeLane != null) {
			sourceNodeGI = model.getGraphicInfo(sourceNodeLane.getId());
		} else {
			return 0;
		}

		if (targetNodePool != null) {
			targetNodeGI = model.getGraphicInfo(targetNodePool.getId());
		} else if (targetNodeLane != null) {
			targetNodeGI = model.getGraphicInfo(targetNodeLane.getId());
		} else {
			return 0;
		}

		if (sourceNodeGI.getY() > targetNodeGI.getY()) {
			return (int) (sourceNodeGI.getY() - 60);
		} else {
			return (int) (targetNodeGI.getY() - 60);
		}
	}

	private static Lane getLaneOfNode(String iD) {

		for (Process process : model.getProcesses()) {
			for (Lane lane : process.getLanes()) {
				if (lane.getFlowReferences().contains(iD))
					return lane;
			}
		}
		return null;
	}

	private static Pool getPoolOfNode(String ref) {

		for (Pool pool : model.getPools()) {
			Process process = model.getProcess(pool.getId());

			if (process == null)
				continue;

			if (process.getFlowElement(ref) != null)
				return pool;

			for (SubProcess subprocess : process.findFlowElementsOfType(SubProcess.class)) {
				if (subprocess.getFlowElement(ref) != null)
					return pool;
			}
		}

		return null;
	}

	private static Point getCenterPoint(GraphicInfo sourceNodeGI) {
		int x = (int) (sourceNodeGI.getX() + sourceNodeGI.getWidth() / 2);
		int y = (int) (sourceNodeGI.getY() + sourceNodeGI.getHeight() / 2);
		return new Point(x, y);
	}

	private static List<FlowNode> gatherAllFlowNodes(BpmnModel model) {
		List<FlowNode> flowNodes = new ArrayList<FlowNode>();
		for (Process process : model.getProcesses()) {
			flowNodes.addAll(gatherAllFlowNodes(process));
		}
		return flowNodes;
	}

	private static List<FlowNode> gatherAllFlowNodes(FlowElementsContainer flowElementsContainer) {
		List<FlowNode> flowNodes = new ArrayList<FlowNode>();
		for (FlowElement flowElement : flowElementsContainer.getFlowElements()) {
			if (flowElement instanceof FlowNode) {
				flowNodes.add((FlowNode) flowElement);
			}
			if (flowElement instanceof FlowElementsContainer) {
				flowNodes.addAll(gatherAllFlowNodes((FlowElementsContainer) flowElement));
			}
		}
		return flowNodes;
	}

}
