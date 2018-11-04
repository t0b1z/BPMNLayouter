package de.uni_hannover.se.BPMNLayouter2.model.grid;

public class GridSize
{
	private int rows = 0;
	private int columns = 0;
	
	public GridSize(int rows, int columns){
		this.rows=rows;
		this.columns=columns;
	}
	
	public int rows()
	{
		return rows;
	}
	
	public int columns()
	{
		return columns;
	}
}