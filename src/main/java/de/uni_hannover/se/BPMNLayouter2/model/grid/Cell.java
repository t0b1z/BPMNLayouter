package de.uni_hannover.se.BPMNLayouter2.model.grid;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class Cell<T> {
	
	private T value;
	public GridPosition gridPosition;
	public Point absolutePosition;
	public CellFlag flag = CellFlag.FREE;
	private List<T> docks = new ArrayList<>();
	private HashMap<T, Point> dockPositionMap = new HashMap<>();
	
	public Cell(T value, int row, int col) {
		this.value  = value;
		flag = CellFlag.TAKEN;
		gridPosition = new GridPosition(row, col);
	}
	
	public Cell(int row, int col)
	{
		gridPosition = new GridPosition(row,col);
	}
	
	public String toString()
	{
		return value.toString();
	}

	public T getValue() {
		return value;
	}
	
	public void setValue(T value)
	{
		flag = CellFlag.TAKEN;
		this.value = value;
	}
	
	public void addDock(T value)
	{
		docks.add(value);
	}

	public List<T> getDocks() {
		return docks;
	}

	public Point getAbsolutPositionOfDock(T attachedTo) {
		return dockPositionMap.get(attachedTo);
	}
	
	public void setAbsolutePositionOfDock(T dock, Point position)
	{
		dockPositionMap.put(dock, position);
	}
}
