package de.uni_hannover.se.BPMNLayouter2.model.grid;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.activiti.bpmn.model.FlowNode;

public class Grid<T> {
	
	private List<List<Cell<T>>> columns = new ArrayList<List<Cell<T>>>();
	private HashMap<T, Cell<T>> cellmap = new HashMap<>();
	private GridPosition gridPosition = new GridPosition(0,0);
	private Point absolutePosition = new Point();
	private Dimension absoluteSize = new Dimension();
	private int cellHeight, cellWidth;
	private Cell<T> destCell;
	private boolean isEmpty = true;
	
	public Grid()
	{
		columns.add(new ArrayList<Cell<T>>());
		columns.get(0).add(new Cell<T>(0,0));
	}
	
	public GridSize getGridSize()
	{
		return new GridSize(columns.get(0).size(), columns.size());
	}
	
	public void addRowsBelowAndAbovePosition(GridPosition position, int rowCount) {
		for (int i = 1; i < rowCount; i++) {
			if (i % 2 == 0)
				this.addRowAbove(position.row);

			if (i % 2 == 1) {
				this.addRowBelow(position.row);
			}
		}
	}
	
	public void createAndReserveCells(GridPosition position, Grid<FlowNode> spGrid) {
		GridPosition subProcessPosition = new GridPosition(position);

		for (List<Cell<FlowNode>> col : spGrid.getColumns()) {
			for (Iterator<Cell<FlowNode>> iterator = col.iterator(); iterator.hasNext(); iterator.next()) {
				
				if(subProcessPosition.row > this.getGridSize().rows()-1)
					this.addLastRow();
				
				addValue(null, subProcessPosition);
				getCell(subProcessPosition).flag = CellFlag.RESERVED;
				subProcessPosition.row++;
			}
			subProcessPosition.row = position.row;
			subProcessPosition.column++;
		}
	}
	
	public void shiftFlowNode(T node, int distance) throws ArrayIndexOutOfBoundsException{

		Cell<T> sourceCell = this.getCellByValue(node);

		int col = getColumnOf(sourceCell);
		int row = getColumns().get(col).indexOf(sourceCell);

		destCell = null;
		while (destCell == null) {
			List<Cell<T>> column = null;

			// add columns if the destination column doesn't exist
			if (getColumns().size() <= col + distance) {
				addColumn();
				continue;
			}

			column = getColumns().get(col + distance);

			// add rows if the destination row doesn't exist
			if (column.size() <= row) {
				column.add(new Cell<T>(col, column.size() - 1));
				continue;
			}

			destCell = column.get(row);
		}

		moveCellContent(sourceCell, destCell);
	}
	
	public void appendGrid(Grid<T> g)
	{
		isEmpty = false;
		int colIndex = 0;
		int originalRowSize = getGridSize().rows();
		
		for (List<Cell<T>> column : g.getColumns()) {
			if (this.columns.size() == colIndex) {
				this.addColumn(originalRowSize);
			}
			for (Cell<T> cell : column) {
				this.getColumns().get(colIndex).add(cell);
			}
			colIndex++;
		}
		
		//fill up the remaining columns with cells to match row numbers
		while(colIndex != columns.size())
		{
			while(this.getColumns().get(0).size() != this.getColumns().get(colIndex).size())
				this.getColumns().get(colIndex).add(new Cell<T>(0, 0));
			colIndex++;
		}
		
		cellmap.putAll(g.cellmap);
	}

	public T getValueFromCell(int row, int col)
	{
		return (T) columns.get(col).get(row).getValue();
	}
	
	public Cell<T> addValueToCell(T value, int row, int col)
	{
		int freeColumn = col;
		
		while(freeColumn >= columns.size())
			addColumn(getGridSize().rows());
		
		Cell<T> cell = columns.get(freeColumn).get(row);
	
		while(cell.getValue() != null)
		{
			addColumn(getGridSize().rows());
			freeColumn++;
			cell = columns.get(freeColumn).get(row);
		}
		
		if(value != null)
		{
			cell.setValue(value);
			cellmap.put(value, cell);
		}
		return cell;
	}
	
	public Cell<T> getCellByValue(T  value)
	{
		return cellmap.get(value);
	}
	
	public void addColumn(int rows) {
		ArrayList<Cell<T>> col = new ArrayList<Cell<T>>();
		fillColumnWithRows(col, rows);
		columns.add(col);
	}
	
	public void addColumn() {
		ArrayList<Cell<T>> col = new ArrayList<Cell<T>>();
		fillColumnWithRows(col, getGridSize().rows());
		columns.add(col);
	}

	private void fillColumnWithRows(ArrayList<Cell<T>> col, int rows) {
		for(int i = 0; i < rows; i++)
			col.add(new Cell<T>(i, columns.size()));;
	}

	public void addRowAbove(int rowIndex) {
		for(List<Cell<T>> row : columns)
		{
			row.add(rowIndex, new Cell<T>(rowIndex, row.get(0).gridPosition.column));
			for(int i = rowIndex+1; i < row.size(); i++)
			{
				row.get(i).gridPosition.row++;
			}
		}
	}
	
	public void addRowBelow(int rowIndex) {
		for(List<Cell<T>> row : columns)
		{
			row.add(rowIndex+1, new Cell<T>(rowIndex+1, row.get(0).gridPosition.column));
			
			for(int i = rowIndex+2; i < row.size(); i++)
			{
				row.get(i).gridPosition.row++;
			}
		}
	}

	public List<List<Cell<T>>> getColumns() {
		return columns;
	}

	public int getColumnOf(Cell<T> cell) {
		for(List<Cell<T>> column : columns)
		{
			if(column.contains(cell))
				return columns.indexOf(column);
		}	
		return -1;
	}

	public void updateCellMap(Cell<T> newCell) {
		cellmap.remove(newCell.getValue());
		cellmap.put(newCell.getValue(), newCell);
	}

	public void moveCellContent(Cell<T> sourceCell, Cell<T> destCell) {
		destCell.setValue(sourceCell.getValue());
		CellFlag sourceFlag = sourceCell.flag;
		destCell.flag = sourceFlag;
		sourceCell.flag = CellFlag.FREE;
		sourceCell.setValue(null);
		updateCellMap(destCell);
	}

	public Cell<T> getCell(GridPosition position) {
		try {
			return columns.get(position.column).get(position.row);
		}catch(IndexOutOfBoundsException e) {
			return null;
		}
	}

	public void addValue(T value, GridPosition position) {
		addValueToCell(value, position.row, position.column);
	}

	public void addLastRow() {
		addRowBelow(columns.get(0).size() - 1);
	}

	public void setCellsize(int cellHeight, int cellWidth)
	{
		this.cellHeight = cellHeight;
		this.cellWidth = cellWidth;
	}
	
	public GridPosition getGridPosition() {
		return gridPosition;
	}

	public void setGridPosition(GridPosition gridPosition) {
		this.gridPosition = gridPosition;
	}

	public Point getAbsolutePosition() {
		absolutePosition.x = gridPosition.column * cellWidth;
		absolutePosition.y = gridPosition.row * cellHeight;
		return absolutePosition;
	}

	public Dimension getAbsoluteSize() {
		absoluteSize.setSize(columns.size() * cellWidth, columns.get(0).size() *  cellHeight);
		return absoluteSize;
	}

	public void moveCellContent(GridPosition sourcePosition, GridPosition destPosition) {
		Cell<T> source = getCell(sourcePosition);
		Cell<T> target = getCell(destPosition);
		moveCellContent(source, target);
	}

	public boolean isEmpty() {
		return cellmap.isEmpty() && isEmpty;
	}

	public HashMap<T, Cell<T>> getCellMap() {
		return cellmap;
	}

	public Cell<T> getCell(int row, int column) {
		return getCell(new GridPosition(row, column));
	}
}
 