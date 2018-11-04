package de.uni_hannover.se.BPMNLayouter2.model.grid;

public class GridPosition implements Cloneable {
	
	public int row;
	public int column;
	
	public GridPosition(int row, int column)
	{
		this.row = row;
		this.column = column;
	}

	public GridPosition(GridPosition position) {
		this.row = position.row;
		this.column = position.column;
	}

	public GridPosition() {
		row = 0;
		column = 0;
	}
	
	public GridPosition clone()
	{
		GridPosition clone = new GridPosition(this);
		return clone;
	}

	public boolean equals(GridPosition gp)
	{
		return gp.row == row && gp.column == column;
	}
}
