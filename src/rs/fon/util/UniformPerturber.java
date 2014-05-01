package rs.fon.util;

import com.rapidminer.tools.RandomGenerator;

public class UniformPerturber extends Perturber {

	RandomGenerator rg;

	public UniformPerturber(int numberOfDimensions,
			DimensionSelector dimensionSelector, double maxPercentageChange,
			RandomGenerator rg) {
		super(numberOfDimensions, dimensionSelector, maxPercentageChange);
		this.rg = rg;
	}

	@Override
	public double perturb(int dimension, double value) {
		double maxDelta = maxPercentageChange;
		double delta = rg.nextDouble() * maxDelta;
		if (rg.nextBoolean()) {
			delta = -1 * delta;
		}

		return value + delta;
	}
}
