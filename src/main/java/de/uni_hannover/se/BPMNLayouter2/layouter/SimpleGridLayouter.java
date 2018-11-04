package de.uni_hannover.se.BPMNLayouter2.layouter;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.activiti.bpmn.model.Activity;
import org.activiti.bpmn.model.BoundaryEvent;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.CallActivity;
import org.activiti.bpmn.model.Event;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.GraphicInfo;
import org.activiti.bpmn.model.Lane;
import org.activiti.bpmn.model.MessageFlow;
import org.activiti.bpmn.model.Pool;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.SubProcess;

import de.uni_hannover.se.BPMNLayouter2.model.bpmn.BPMNUtils;
import de.uni_hannover.se.BPMNLayouter2.model.grid.Cell;
import de.uni_hannover.se.BPMNLayouter2.model.grid.Grid;
import de.uni_hannover.se.BPMNLayouter2.model.grid.GridPosition;
import de.uni_hannover.se.BPMNLayouter2.model.grid.GridSize;
import de.uni_hannover.se.BPMNLayouter2.util.Util;

public class SimpleGridLayouter extends Layouter {

	private enum Direction {
		DOWN, LEFT, RIGHT, UP
	}

	public HashMap<String, Grid<FlowNode>> grids = new HashMap<>();

	public Grid<FlowNode> mainGrid;

	private List<SequenceFlow> containerSpanningFlows = new ArrayList<>();
	private List<SequenceFlow> temporarySequenceFlows = new ArrayList<>();
	private HashMap<FlowNode, List<Cell<FlowNode>>> markedCellsMap = new HashMap<>();

	int cellWidth = 150;
	int cellHeight = (int) ((double)cellWidth * 0.8);

	int nodeWidth = (int) (cellWidth * 2 / 3); // 150 * 0.66 = 100
	int nodeHeight = (int) (nodeWidth * 0.8);
	
	int eventsize = (int) (cellWidth * 0.24);


	public SimpleGridLayouter(BpmnModel model) {
		super(model);
		mainGrid = new Grid<>();
		mainGrid.getColumns().get(0).remove(0);
	}

	public void applyGridToModel() {
		mainGrid.setCellsize(cellHeight, cellWidth);

		applyAbsoluteCoordinatesToModel();
		SimpleOrthogonalFlowRouter.routeFlows(model);
	}

	public Grid<FlowNode> layoutModelToGrid(boolean moveNodes) throws Exception {
		
		containerSpanningFlows = BPMNUtils.findAndTemporarilyRemoveFlowsBetweenPartitions(model.getProcesses(), model);
		temporarySequenceFlows = BPMNUtils.replaceAssociationsWithSequenceFlows(model);

		layoutCallActivities();
		layoutSubprocesses();
		layoutPools();
		layoutAndAppendProcessLanes();

		if (mainGrid.isEmpty()) {
			layoutSingleProcess(); // Process doesn't contains pools or lanes
		}

		recoverEdgesBetweenPartitions(model.getProcesses());

		if(moveNodes)
		{
			moveNodesWithInterleavingEdges();
		}

		BPMNUtils.removeTemporarySequenceFlowsFromModel(temporarySequenceFlows, model);
		return mainGrid;
	}

	public void applyAbsoluteCoordinatesToModel() {
		Point absolutePosition = new Point(0, 0);

		for (List<Cell<FlowNode>> col : mainGrid.getColumns()) {
			for (Cell<FlowNode> cell : col) {
				cell.absolutePosition = new Point(absolutePosition);
				if (cell.getValue() != null) {
					if (cell.getValue() instanceof SubProcess && BPMNUtils.activityIsExpanded(cell.getValue().getId(), model))
						updateSubprocessGraphicInfo(cell.getValue().getId(), absolutePosition);
					else if (cell.getValue() instanceof CallActivity && BPMNUtils.activityIsExpanded(cell.getValue().getId(), model))
						updateCallActivityGraphicInfo(cell.getValue().getId(), absolutePosition);
					else
						updateElementGraphicInfo(cell.getValue().getId(), absolutePosition);
					if (cell.getDocks().size() > 0) {
						updateBoundaryEventGraphicInfo(cell, absolutePosition);
					}
				}
				absolutePosition.y += cellHeight;
			}
			absolutePosition.y = 0;
			absolutePosition.x += cellWidth;
		}

		for (Pool pool : model.getPools()) {
			updatePoolGraphicInfo(pool);
		}

		for (Process process : model.getProcesses()) {
			for (Lane lane : process.getLanes()) {
				if (BPMNUtils.laneIsNotWithinPool(lane, model))
					updateLaneGraphicInfo(lane);
			}
		} 
	}

	private void addAllExpandedCallActivityProcessesToProcessGridMap(List<Process> processes, FlowElement flowElement) {
		CallActivity callActivity = (CallActivity) flowElement;
		if (BPMNUtils.activityIsExpanded(callActivity.getId(), model)) {
			addCallActivityProcessToGridMap(processes, callActivity);
		}
	}

	private void addBoundaryEventDock(Grid<FlowNode> grid, BoundaryEvent boundaryEvent) {
		FlowNode attachedToNode = boundaryEvent.getAttachedToRef();
		grid.getCellByValue(attachedToNode).addDock(boundaryEvent);
	}

	private void addCallActivityProcessToGridMap(List<Process> processes, CallActivity callActivity) {
		String id = callActivity.getCalledElement();
		for (Process callActivityProcess : processes) {
			if (callActivityProcess.getId().equals(id)) {
				addProcessToGridMap(callActivityProcess);
			}
		}
	}

	private void addGridToMainGrid(Grid<FlowNode> processGrid) {
		
		if(!mainGrid.isEmpty())
		{
			mainGrid.addLastRow();
		}
		
		processGrid.getGridPosition().row = mainGrid.getGridSize().rows();
		mainGrid.appendGrid(processGrid);
		
		//if(processGrid.getGridPosition().row == 0)
			//mainGrid.addLastRow();
	}

	private void addLanesToGridMap(List<Lane> lanes) {
		for (Lane lane : lanes) {
			List<FlowNode> flowNodes = gatherFlowNodesFromLane(lane);
			Grid<FlowNode> laneGrid = layoutFlowNodesToGrid(flowNodes);
			grids.put(lane.getId(), laneGrid);
		}

	}

	private List<FlowNode> gatherFlowNodesFromLane(Lane lane) {
		List<String> flowReferences = lane.getFlowReferences();

		List<FlowNode> flowNodes = new ArrayList<>();
		for (String ref : flowReferences) {
			FlowElement e = model.getFlowElement(ref);
			if (e instanceof FlowNode && !(e instanceof BoundaryEvent)) {
				flowNodes.add((FlowNode) e);
			}
		}
		return flowNodes;
	}

	

	private void addProcessToGridMap(Process process) {
		List<FlowNode> flowNodes = BPMNUtils.getFlowNodesFromFlowElementsList(process.getFlowElements());
		Grid<FlowNode> processGrid = layoutFlowNodesToGrid(flowNodes);
		grids.put(process.getId(), processGrid);
	}

	private void createAndReserveCellsForSubprocess(SubProcess sp, GridPosition position, Grid<FlowNode> grid) {
		Grid<FlowNode> spGrid = grids.get(sp.getId());
		if(spGrid == null) {
			addSubprocessGridMap(sp);
			spGrid = grids.get(sp.getId());
		}
		int spRows = grids.get(sp.getId()).getGridSize().rows();
		
		if(sp.getBoundaryEvents().size() > 0)
		{
			spRows--;
			spRows--;
		}
		
		grid.addRowsBelowAndAbovePosition(position, spRows);
		grid.createAndReserveCells(position, spGrid);
	}

	private void addSubprocessGridMap(SubProcess sp) {
		Collection<FlowElement> flowElements = sp.getFlowElements();
		List<FlowNode> flowNodes = BPMNUtils.getFlowNodesFromFlowElementsList(flowElements);
		Grid<FlowNode> subprocessGrid = layoutFlowNodesToGrid(flowNodes);
		grids.put(sp.getId(), subprocessGrid);
	}

	private void appendLanesToMainGrid(Process process) {
		
		if(!mainGrid.isEmpty())
		{
			mainGrid.addLastRow();
		}
		
		for (Lane lane : process.getLanes()) {
			Grid<FlowNode> laneGrid = grids.get(lane.getId());
			laneGrid.getGridPosition().row = mainGrid.getGridSize().rows();
			mainGrid.appendGrid(laneGrid);
		}
	}

	private GridPosition calulateNodePosition(FlowNode node, Grid<FlowNode> grid) {

		GridPosition position = new GridPosition(0, 0);
		Cell<FlowNode> previousCell = null;

		int incomingFlowCount = node.getIncomingFlows().size();
		int outgoingFlowCount = node.getOutgoingFlows().size();
		int boundaryEventCount = BPMNUtils.getBoundaryEventCount(node);
		boolean positionSetByMarker = false;
		
		if (incomingFlowCount == 0) {
			if (outgoingFlowCount > 1) {
				prepareGridForSplit(node, grid, position, outgoingFlowCount);
			}
			if(boundaryEventCount > 0)
			{
				prepareGridForBoundaryEvents(node, grid, position, boundaryEventCount, 1);
			}
			return position;
		}
		
		previousCell = getPreviousCell(node, grid);
		GridPosition previousPosition = getPositionOfPreviousCell(grid, previousCell);
		
		if(previousCell.getValue() instanceof SubProcess) {
			Grid<FlowNode> subprocessGrid = grids.get(previousCell.getValue().getId());
			previousPosition.row += Math.floor((double)subprocessGrid.getGridSize().rows()/2.0);
		}
		
		position = new GridPosition(previousPosition);
		
		if (isCellMarkerForPositionSetByPreviousNode(previousCell)) {

			List<Cell<FlowNode>> markedCells = markedCellsMap.get(previousCell.getValue());
			
			
			if (isfollowingCellAlreadyInGrid(node, grid)) {
				calculatePositionOfLoopElement(position, previousCell, markedCells);
			} else {
				position = markedCells.get(0).gridPosition;
				markedCells.remove(markedCells.get(0));
			}
			positionSetByMarker  = true;
		}

		if (!positionSetByMarker && BPMNUtils.isElementPartOfSequentialSequence(node, previousCell.getValue())) {
			
			position.column = previousPosition.column + 1;

			if (elementIsPartOfLoop(node, grid)) {
				position = calculatePositionForLoopElement(grid, position);
			}
		}

		if (outgoingFlowCount > 1) {
			if(positionSetByMarker)
			{
				position.column--;
				prepareGridForSplit(node, grid, position, outgoingFlowCount);
				position.column++;
			}else if(nodeLoopsBack(node, grid)){
				if(outgoingFlowCount - 1 == 1)
				{
					position = getPositionOfPreviousCell(grid, previousCell);
					position.column = previousPosition.column + 1;
				}
			}
			else
			{
				prepareGridForSplit(node, grid, position, outgoingFlowCount);
				previousCell = getPreviousCell(node, grid);
				position = getPositionOfPreviousCell(grid, previousCell);
				position.column = previousPosition.column + 1;
			}
		}

		if (incomingFlowCount > 1) {
			int rowSum = 0;
			int prevCellCount = 0;
			for (SequenceFlow e : node.getIncomingFlows()) {
				FlowNode previousNode = (FlowNode) model.getFlowElement(e.getSourceRef());
				if (grid.getCellByValue(previousNode) != null) { // one of the previous nodes is already in the grid
					rowSum += grid.getCellByValue(previousNode).gridPosition.row;
					prevCellCount++;
				}
			}

			position.column = previousPosition.column + 1;
			if(prevCellCount > 0)
				position.row = rowSum / prevCellCount;
		}
		
		if(boundaryEventCount > 0)
		{
			position.column = previousPosition.column + 1;
			
			while(grid.getCell(position) != null && grid.getCell(position).getValue() != null)
				position.column = position.column + 1;
			
			prepareGridForBoundaryEvents(node, grid, position, boundaryEventCount, 2);
		}


		
		return position;
	}

	private boolean nodeLoopsBack(FlowNode node, Grid<FlowNode> grid) {
		for(SequenceFlow flow : node.getOutgoingFlows())
		{
			String targetRef = flow.getTargetRef();
			FlowNode targetNode = (FlowNode) model.getFlowElement(targetRef);
			if(grid.getCellByValue(targetNode) != null)
				return true;
		}
		return false;
	}

	private void prepareGridForBoundaryEvents(FlowNode node, Grid<FlowNode> grid, GridPosition position, int boundaryEventCount, int colCount) {
		for(int colCounter = 0; colCounter < colCount; colCounter++)
		{
			if(grid.getColumns().size() < position.column + colCounter + 2)
				grid.addColumn();
		}
		markCellsForBoundaryEvents(node, position, boundaryEventCount, grid);
	}

	private void markCellsForBoundaryEvents(FlowNode node, GridPosition position, int boundaryEventCount, Grid<FlowNode> grid) {
		List<Cell<FlowNode>> markedCells = new ArrayList<Cell<FlowNode>>();
		markedCellsMap.put(node, markedCells);
		for (int i = 0; i < boundaryEventCount; i++) {
			
			GridPosition markedCellPosition = new GridPosition(position.row + i + 1, position.column + 1);
			
			if(node instanceof SubProcess && grids.get(node.getId()) != null)
			{
				int subprocessRows = grids.get(node.getId()).getGridSize().rows();
				int subprocessColumns = grids.get(node.getId()).getGridSize().columns();
				markedCellPosition.row += subprocessRows - 1;
				markedCellPosition.column += subprocessColumns - 1;
			}
			
			while(grid.getGridSize().rows() < markedCellPosition.row + 1)
				grid.addRowBelow(position.row + i);
			
			while(grid.getGridSize().columns() < markedCellPosition.column + 1)
				grid.addColumn();
			
			Cell<FlowNode> markedCell = grid.getCell(markedCellPosition);
			markedCells.add(0, markedCell);
		}
	}

	private void calculatePositionOfLoopElement(GridPosition position, Cell<FlowNode> previousCell,
			List<Cell<FlowNode>> markedCells) {
		int loopColumn = (previousCell.gridPosition.column + (position.column - 1)) / 2;
		position.row = markedCells.get(0).gridPosition.row;
		position.column = (loopColumn - 1);
		shrinkMarkedRows(previousCell.getValue());
	}

	private boolean isfollowingCellAlreadyInGrid(FlowNode node, Grid<FlowNode> grid) {
		for (SequenceFlow outgoing : node.getOutgoingFlows()) {
			FlowNode targetNode = (FlowNode) model.getFlowElement(outgoing.getTargetRef());
			if (grid.getCellByValue(targetNode) != null)
				return true;
		}
		
		return false;
	}

	private boolean isCellMarkerForPositionSetByPreviousNode(Cell<FlowNode> previousCell) {
		return markedCellsMap.get(previousCell.getValue()) != null && !markedCellsMap.get(previousCell.getValue()).isEmpty();
	}

	private void prepareGridForSplit(FlowNode node, Grid<FlowNode> grid, GridPosition position, int outgoingFlowSize) {
		
		if(grid.getColumns().size() <= position.column+2)
		{
			grid.addColumn();
			grid.addColumn();
		}
		
		markCellsForSplit(node, position, outgoingFlowSize, grid);
	}

	private boolean elementIsPartOfLoop(FlowNode node, Grid<FlowNode> grid) {
		int outgoingFlowSize = BPMNUtils.getBoundaryEventCount(node);
		if (outgoingFlowSize == 1) {
			Cell<FlowNode> subsequentCell = getSubsequentCell(node, grid);
			if (subsequentCell != null && subsequentCell.getValue() != null) {
				return true;
			}
		}
		return false;
	}

	private GridPosition calculatePositionForLoopElement(Grid<FlowNode> grid, GridPosition position) {
		GridPosition preferedPosition = new GridPosition(position);
		preferedPosition.column -= 2;
		if (grid.getCell(preferedPosition).getValue() == null) {
			position = preferedPosition;
		} else {
			grid.addRowAbove(position.column);
			position.row--;
			position.column--;
		}
		return position;
	}

	private Cell<FlowNode> getSubsequentCell(FlowNode node, Grid<FlowNode> grid) {
		FlowNode firstTargetNode = BPMNUtils.getSubsequentNode(node, model);
		Cell<FlowNode> targetCell = grid.getCellByValue(firstTargetNode);
		return targetCell;
	}

	private Cell<FlowNode> getPreviousCell(FlowNode node, Grid<FlowNode> grid) {
		Cell<FlowNode> previousCell;
		FlowNode previousFlowNode;
		previousFlowNode = getRightMostFlowNode(node.getIncomingFlows(), grid);

		if (previousFlowNode instanceof BoundaryEvent) {
			BoundaryEvent be = (BoundaryEvent) previousFlowNode;
			previousFlowNode = be.getAttachedToRef();
		}

		previousCell = grid.getCellByValue(previousFlowNode);
		return previousCell;
	}

	private GridPosition getPositionOfPreviousCell(Grid<FlowNode> grid, Cell<FlowNode> previousCell) {

		GridPosition position = new GridPosition();

		if (previousCell != null) {
			if (previousCell.getValue() instanceof SubProcess  || previousCell.getValue() instanceof CallActivity) {
				Activity activity = (Activity) previousCell.getValue();
				position = previousCell.gridPosition.clone();

				if (BPMNUtils.activityIsExpanded(activity.getId(), model))
					{
						if (previousCell.getValue() instanceof CallActivity) {
							position.column += grids.get(((CallActivity)activity).getCalledElement()).getGridSize().columns() - 1;
						}
						else if (previousCell.getValue() instanceof SubProcess) {
							position.column += grids.get((activity).getId()).getGridSize().columns() - 1;
						}
					}

			} else {
				position = previousCell.gridPosition.clone();
			}
		} else
			position.column = grid.getColumns().size() - 1;
		return position;
	}


	private FlowNode getRightMostFlowNode(List<SequenceFlow> incomingFlows, Grid<FlowNode> grid) {

		FlowNode rightMostNode = (FlowNode) model.getFlowElement(incomingFlows.iterator().next().getSourceRef());
		int rightMostColumn = 0;

		if (rightMostNode instanceof BoundaryEvent) {
			BoundaryEvent be = (BoundaryEvent) rightMostNode;
			FlowNode attachedTo = be.getAttachedToRef();
			rightMostColumn = grid.getCellByValue(attachedTo).gridPosition.column;
		} else {
			rightMostColumn = grid.getCellByValue(rightMostNode).gridPosition.column;
		}

		for (SequenceFlow flow : incomingFlows) {
			FlowNode node = (FlowNode) model.getFlowElement(flow.getSourceRef());

			if (node instanceof BoundaryEvent) {
				BoundaryEvent be = (BoundaryEvent) node;
				node = be.getAttachedToRef();
			}

			Cell<FlowNode> cell = grid.getCellByValue(node);
			
			if(cell == null)
				continue;

			if (cell.gridPosition.column > rightMostColumn) {
				rightMostNode = node;
				rightMostColumn = cell.gridPosition.column;
			}

			if (node instanceof SubProcess) {
				GraphicInfo gi = model.getGraphicInfo(node.getId());
				if (gi.getExpanded() != null)
					if (gi.getExpanded() == true) {
						Grid<FlowNode> spGrid = grids.get(node.getId());
						if (cell.gridPosition.column + spGrid.getGridSize().columns() > rightMostColumn) {
							rightMostNode = node;
							rightMostColumn = cell.gridPosition.column + spGrid.getGridSize().columns();
						}
					}
			}

			if (node instanceof CallActivity) {
				CallActivity callActivity = (CallActivity) node;
				GraphicInfo gi = model.getGraphicInfo(callActivity.getId());
				if (gi.getExpanded() != null)
					if (gi.getExpanded() == true) {
						Grid<FlowNode> caGrid = grids.get(callActivity.getCalledElement());
						if (cell.gridPosition.column + caGrid.getGridSize().columns() > rightMostColumn) {
							rightMostNode = node;
							rightMostColumn = cell.gridPosition.column + caGrid.getGridSize().columns();
						}
					}
			}

		}
		return rightMostNode;
	}

	private void layoutAndAppendProcessLanes() {
		for (Process process : model.getProcesses()) {
			if (process.getLanes().size() != 0 && !BPMNUtils.isProcessInPool(process, model)) {
				addLanesToGridMap(process.getLanes());
				appendLanesToMainGrid(process);
			}
		}
	}

	private void layoutCallActivities() {
		for (Process process : model.getProcesses()) {
			for (FlowElement callActivity : process.findFlowElementsOfType(CallActivity.class)) {
				addAllExpandedCallActivityProcessesToProcessGridMap(model.getProcesses(), callActivity);
			}
		}
	}

	private Grid<FlowNode> layoutFlowNodesToGrid(List<FlowNode> flowNodes) {
		Grid<FlowNode> grid = new Grid<>();
		List<FlowNode> sortedList = Util.topologicalSortNodes(flowNodes, model);

		for (FlowNode node : sortedList) {
			if (node instanceof BoundaryEvent) {
				addBoundaryEventDock(grid, (BoundaryEvent) node);
			} else {
				GridPosition position = calulateNodePosition(node, grid);

				if (node instanceof SubProcess && BPMNUtils.activityIsExpanded(node.getId(), model))
					createAndReserveCellsForSubprocess((SubProcess) node, position, grid);

				grid.addValue(node, position);
				GridPosition actualPosition = grid.getCellByValue(node).gridPosition;
				
				if(!actualPosition.equals(position))
				{
					if(markedCellsMap.containsKey(node))
					{
						int columnDifference = actualPosition.column - position.column;
						
						List<Cell<FlowNode>> newMarkedCellList = new ArrayList<>();
						
						for(Cell<FlowNode> cell : markedCellsMap.get(node))
						{
							GridPosition cellPosition = cell.gridPosition;
							cellPosition.column += columnDifference;
							
							newMarkedCellList.add(grid.getCell(cellPosition));
						}
						
						markedCellsMap.put(node, newMarkedCellList);
					}
				}
			}
		}
		return grid;
	}

	private void layoutPools() {
		for (Pool pool : model.getPools()) {
			Process process = model.getProcess(pool.getId());
			
			if(process == null)
			{
				addClosedPoolToGridMap(pool.getId());
				addGridToMainGrid(grids.get(pool.getId()));
			}
			else if (process.getLanes().size() == 0) {
				addProcessToGridMap(process);
				addGridToMainGrid(grids.get(process.getId()));
			} else {
				addLanesToGridMap(process.getLanes());
				appendLanesToMainGrid(process);
			}
		}
	}

	private void addClosedPoolToGridMap(String poolId) {
		Grid<FlowNode> poolGrid = new Grid<>();
		grids.put(poolId, poolGrid);
	}

	private void layoutSingleProcess() {

		for (Process process : model.getProcesses()) {
			if (grids.containsKey(process.getId()))
				continue;
			else {
				addProcessToGridMap(process);
				addGridToMainGrid(grids.get(process.getId()));
			}
		}
	}

	private void layoutSubprocesses() {
		for (Process process : model.getProcesses()) {
			for (SubProcess subprocess : process.findFlowElementsOfType(SubProcess.class)) {
				if(BPMNUtils.activityIsExpanded(subprocess.getId(), model))
					addSubprocessGridMap(subprocess);
			}
		}
	}


	private void markCellsForSplit(FlowNode node, GridPosition position, int size, Grid<FlowNode> grid) {
		List<Cell<FlowNode>> markedCells = new ArrayList<Cell<FlowNode>>();
		markedCellsMap.put(node, markedCells);
		for (int i = 0; i < size - 1; i += 2) {
			
			int currentRow = position.row;
			
			GridPosition upperCellPosition = new GridPosition(currentRow - (i + 1), position.column + 2);
			while(grid.getCell(upperCellPosition) == null || grid.getCell(upperCellPosition).getValue() != null)
			{
				grid.addRowAbove(currentRow);
				currentRow++;
				upperCellPosition = new GridPosition(currentRow - (i + 1), position.column + 2);
			}
			Cell<FlowNode> upperCell = grid.getCell(upperCellPosition);
			assert(upperCell != null);
			markedCells.add(upperCell);

			GridPosition lowerCellPosition = new GridPosition(currentRow + (i + 1), position.column + 2);
			
			while(grid.getCell(lowerCellPosition) == null || grid.getCell(lowerCellPosition).getValue() != null)
				grid.addRowBelow(currentRow);

			Cell<FlowNode> lowerCell = grid.getCell(lowerCellPosition);			
			assert(lowerCell != null);
			markedCells.add(lowerCell);
		}

		if (size % 2 != 0)
		{
			GridPosition middelCellPosition = new GridPosition(position.row + 1, position.column + 2);
			Cell<FlowNode> middleCell = grid.getCell(middelCellPosition);
			markedCells.add(middleCell);
		}

	}

	private void moveNodes(FlowNode startingFlowNode, int distance, Direction dir) throws Exception {

		switch (dir) {
		case RIGHT:
			// collect all the nodes
			List<FlowNode> nodesPreparedForMove = BPMNUtils.findAllSubsequentFlowNodesInGroup(startingFlowNode, model);
			// reverse list
			Collections.reverse(nodesPreparedForMove);
			// shift everything to one side
			for (FlowNode node : nodesPreparedForMove) {
				try {
					mainGrid.shiftFlowNode(node, distance);
				}catch(ArrayIndexOutOfBoundsException E) {
					System.out.println("Node can't be moved");
					System.out.println("Name:\t\t" + node.getName());
					System.out.println("Dist:\t\t" + distance);
					throw new Exception();
				}
			}
			break;
		default:
			return;
		}

	}

	private void moveNodesWithInterleavingEdges() throws Exception {

		for (SequenceFlow flow : containerSpanningFlows) {
			FlowNode source = (FlowNode) model.getFlowElement(flow.getSourceRef());
			FlowNode target = (FlowNode) model.getFlowElement(flow.getTargetRef());

			Cell<FlowNode> srcCell = mainGrid.getCellByValue(source);
			Cell<FlowNode> trgCell = mainGrid.getCellByValue(target);

			int distance = mainGrid.getColumnOf(srcCell) - mainGrid.getColumnOf(trgCell);

			if (distance == 0) {
				continue;
			}
			try {
				moveNodes(target, distance, Direction.RIGHT);
			}catch(Exception e) {
				throw new Exception();
			}
		}

		for (MessageFlow flow : model.getMessageFlows().values()) {
			FlowNode source = (FlowNode) model.getFlowElement(flow.getSourceRef());
			FlowNode target = (FlowNode) model.getFlowElement(flow.getTargetRef());

			Cell<FlowNode> srcCell = mainGrid.getCellByValue(source);
			Cell<FlowNode> trgCell = mainGrid.getCellByValue(target);

			int distance = mainGrid.getColumnOf(srcCell) - mainGrid.getColumnOf(trgCell);

			if (distance <= 0 || model.getPool(flow.getSourceRef()) != null || model.getPool(flow.getTargetRef()) != null) {
				continue;
			}

			try {
				moveNodes(target, distance, Direction.RIGHT);
			}catch(Exception e) {
				e.printStackTrace();
				throw new Exception();
			}
		}
	}

	private void recoverEdgesBetweenPartitions(List<Process> processes) {
		for (SequenceFlow flow : containerSpanningFlows) {

			FlowNode source = (FlowNode) model.getFlowElement(flow.getSourceRef());
			FlowNode target = (FlowNode) model.getFlowElement(flow.getTargetRef());

			source.getOutgoingFlows().add(flow);
			target.getIncomingFlows().add(flow);
		}
	}

	private void shrinkMarkedRows(FlowNode node) {
		
		List<Cell<FlowNode>> markedRows = markedCellsMap.get(node);
		
		if (markedRows.size() == 2) {
			markedRows.clear();
		}
	}

	private void updateBoundaryEventGraphicInfo(Cell<FlowNode> cell, Point absolutePosition) {

		int x = absolutePosition.x, y = absolutePosition.y;
		int spGridModifierX = 0;
		int spGridModifierY = 0;

		if(cell.getValue() instanceof SubProcess && BPMNUtils.activityIsExpanded(cell.getValue().getId(), model))
		{
			Grid<FlowNode> subprocessGrid = grids.get(cell.getValue().getId());
			spGridModifierX = subprocessGrid.getAbsoluteSize().width - cellWidth;
			spGridModifierY = subprocessGrid.getAbsoluteSize().height - cellHeight + (int)(eventsize * 0.5);
		}
		
		for (int i = 0; i < cell.getDocks().size(); i++) {
			FlowNode dock = cell.getDocks().get(i);
			dock.setName("");
			GraphicInfo gi = model.getGraphicInfo(dock.getId());
			switch (i) {
			case 0:
				x = (int) (absolutePosition.x + cellWidth * 0.4) + spGridModifierX;
				y = (int) (absolutePosition.y + cellHeight * 0.65) + spGridModifierY;
				cell.setAbsolutePositionOfDock(dock, new Point(x, y));
				break;
			case 1:
				x = (int) (absolutePosition.x + cellWidth * 0.6) + spGridModifierX;
				y = (int) (absolutePosition.y + cellHeight * 0.65) + spGridModifierY;
				cell.setAbsolutePositionOfDock(dock, new Point(x, y));
				break;
			case 2:
				//
				break;
			case 3:
				//
				break;
			}

			gi.setX(x);
			gi.setY(y);
			gi.setWidth(eventsize);
			gi.setHeight(eventsize);

		}
	}

	private void updateCallActivityGraphicInfo(String id, Point absolutePosition) {
		GraphicInfo caGraphicInfo = model.getGraphicInfo(id);

		CallActivity callActivity = (CallActivity) model.getFlowElement(id);
		if (callActivity.getCalledElement() == null || caGraphicInfo.getExpanded() == null
				|| caGraphicInfo.getExpanded() == false) {
			int xMargin = (cellWidth - nodeWidth) / 2;
			int yMargin = (cellHeight - nodeHeight) / 2;
			caGraphicInfo.setX(absolutePosition.x + xMargin);
			caGraphicInfo.setY(absolutePosition.y + yMargin);
			caGraphicInfo.setWidth(nodeWidth);
			caGraphicInfo.setHeight(nodeHeight);
			return;
		}

		Grid<FlowNode> processGrid = grids.get(callActivity.getCalledElement());
		processGrid.setCellsize(cellHeight, cellWidth);

		caGraphicInfo.setX(absolutePosition.x);
		caGraphicInfo.setY(absolutePosition.y + processGrid.getAbsoluteSize().getHeight() * 0.05);
		caGraphicInfo.setWidth(processGrid.getAbsoluteSize().getWidth());
		caGraphicInfo.setHeight(processGrid.getAbsoluteSize().getHeight() * 0.9);

		Point processElementPosition = new Point(absolutePosition);
		for (List<Cell<FlowNode>> col : processGrid.getColumns()) {
			for (Cell<FlowNode> cell : col) {
				cell.absolutePosition = new Point(processElementPosition);
				if (cell.getValue() != null) {
					updateElementGraphicInfo(cell.getValue().getId(), processElementPosition);
					if (cell.getDocks().size() > 0) {
						updateBoundaryEventGraphicInfo(cell, processElementPosition);
					}
				}
				processElementPosition.y += cellHeight;
			}
			processElementPosition.y = absolutePosition.y;
			processElementPosition.x += cellWidth;
		}
	}

	private void updateElementGraphicInfo(String key, Point absolutePosition) {
		GraphicInfo elementGraphicInfo = model.getGraphicInfo(key);

		if (model.getFlowElement(key) instanceof Activity) {
			elementGraphicInfo.setWidth(nodeWidth);
			elementGraphicInfo.setHeight(nodeWidth * 0.8);
		}
		
		if (model.getFlowElement(key) instanceof Event) {
			elementGraphicInfo.setWidth(eventsize);
			elementGraphicInfo.setHeight(eventsize);
		}

		int xMargin = (int) ((cellWidth - elementGraphicInfo.getWidth()) / 2);
		int yMargin = (int) ((cellHeight - elementGraphicInfo.getHeight()) / 2);

		elementGraphicInfo.setX(absolutePosition.x + xMargin);
		elementGraphicInfo.setY(absolutePosition.y + yMargin);
	}

	private void updateLaneGraphicInfo(Lane lane) {
		String laneId = lane.getId();
		Grid<FlowNode> laneGrid = grids.get(laneId);
		laneGrid.setCellsize(cellHeight, cellWidth);

		GraphicInfo flowNodeGraphicInfo = model.getGraphicInfo(lane.getId());
		flowNodeGraphicInfo.setX(laneGrid.getAbsolutePosition().x);
		flowNodeGraphicInfo.setY(laneGrid.getAbsolutePosition().y);
		flowNodeGraphicInfo.setWidth(mainGrid.getAbsoluteSize().getWidth());
		flowNodeGraphicInfo.setHeight(laneGrid.getAbsoluteSize().getHeight());
	}

	private void updateLaneWithinPoolGraphicInfo(Lane lane) {
		String laneId = lane.getId();
		Grid<FlowNode> laneGrid = grids.get(laneId);
		laneGrid.setCellsize(cellHeight, cellWidth);

		int offset = 30;

		GraphicInfo flowNodeGraphicInfo = model.getGraphicInfo(lane.getId());
		flowNodeGraphicInfo.setX(laneGrid.getAbsolutePosition().x + offset);
		flowNodeGraphicInfo.setY(laneGrid.getAbsolutePosition().y);
		flowNodeGraphicInfo.setWidth(mainGrid.getAbsoluteSize().getWidth() - offset);
		flowNodeGraphicInfo.setHeight(laneGrid.getAbsoluteSize().getHeight());
	}

	private void updatePoolGraphicInfo(Pool pool) {
		String processRef = pool.getProcessRef();
		Grid<FlowNode> poolGrid = grids.get(processRef);
		
		if(poolGrid == null)
			poolGrid = grids.get(pool.getId());

		if (poolGrid == null) {
			Process process = BPMNUtils.getProcessFromModel(processRef, model);
			if (process != null && process.getLanes().size() > 0) {
				Point position = null;
				Dimension size = new Dimension();

				for (Lane lane : BPMNUtils.getProcessFromModel(processRef, model).getLanes()) {
					Grid<FlowNode> laneGrid = grids.get(lane.getId());
					updateLaneGraphicInfo(lane);
					if (position == null || laneGrid.getAbsolutePosition().y < position.y)
						position = laneGrid.getAbsolutePosition();

					size.height += laneGrid.getAbsoluteSize().getHeight();

					updateLaneWithinPoolGraphicInfo(lane);
				}

				GraphicInfo poolGraphicInfo = model.getGraphicInfo(pool.getId());

				poolGraphicInfo.setX(position.x);
				poolGraphicInfo.setY(position.y);
				poolGraphicInfo.setWidth(mainGrid.getAbsoluteSize().getWidth());
				poolGraphicInfo.setHeight(size.getHeight());

			}
			return;
		}

		poolGrid.setCellsize(cellHeight, cellWidth);

		GraphicInfo poolGraphicInfo = model.getGraphicInfo(pool.getId());
		poolGraphicInfo.setX(poolGrid.getAbsolutePosition().x);
		poolGraphicInfo.setY(poolGrid.getAbsolutePosition().y);
		poolGraphicInfo.setWidth(mainGrid.getAbsoluteSize().getWidth());
		poolGraphicInfo.setHeight(poolGrid.getAbsoluteSize().getHeight());
	}

	private void updateSubprocessGraphicInfo(String subprocessId, Point absolutePosition) {
		GraphicInfo flowNodeGraphicInfo = model.getGraphicInfo(subprocessId);

		Grid<FlowNode> spGrid = grids.get(subprocessId);
		spGrid.setCellsize(cellHeight, cellWidth);
		
		if (!subprocessIsEmpty(subprocessId)) {
			flowNodeGraphicInfo.setX(absolutePosition.x);
			flowNodeGraphicInfo.setY(absolutePosition.y + spGrid.getAbsoluteSize().getHeight() * 0.05);
			flowNodeGraphicInfo.setWidth(spGrid.getAbsoluteSize().getWidth());
			flowNodeGraphicInfo.setHeight(spGrid.getAbsoluteSize().getHeight() * 0.9);
		} else {
			updateElementGraphicInfo(subprocessId, absolutePosition);
			return;
		}

		Point spElementPosition = new Point(absolutePosition);
		for (List<Cell<FlowNode>> col : spGrid.getColumns()) {
			for (Cell<FlowNode> cell : col) {
				cell.absolutePosition = new Point(spElementPosition);
				if (cell.getValue() != null) {
					updateElementGraphicInfo(cell.getValue().getId(), spElementPosition);
					if (cell.getDocks().size() > 0) {
						updateBoundaryEventGraphicInfo(cell, spElementPosition);
					}
				}
				spElementPosition.y += cellHeight;
			}
			spElementPosition.y = absolutePosition.y;
			spElementPosition.x += cellWidth;
		}
	}

	private boolean subprocessIsEmpty(String subprocessId) {
		if(grids.get(subprocessId).getColumns().size() > 1)
			return false;
		if(grids.get(subprocessId).getCell(new GridPosition(0,0)).getValue() != null)
			return false;
		return true;
	}
}
