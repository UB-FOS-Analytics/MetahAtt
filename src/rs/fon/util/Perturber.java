package rs.fon.util;


public abstract class Perturber {

	static public enum DimensionSelector {

		ALEATORY, SEQUENCIAL
	};

	protected int numberOfDimensions;
	protected DimensionSelector dimensionSelector;
	protected double maxPercentageChange;

	public Perturber(int numberOfDimensions,
			DimensionSelector dimensionSelector, double maxPercentageChange) {
		super();
		this.numberOfDimensions = numberOfDimensions;
		this.dimensionSelector = dimensionSelector;
		this.maxPercentageChange = maxPercentageChange;
	}

	public abstract double perturb(int dimension, double value);
}
