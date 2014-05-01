package rs.fon.util;

import java.util.Random;

import com.rapidminer.tools.RandomGenerator;

public class GaussianPerturber extends Perturber {

	RandomGenerator rg;

	public GaussianPerturber(int numberOfDimensions,
			DimensionSelector dimensionSelector, double maxPercentageChange,
			RandomGenerator rg) {
		super(numberOfDimensions, dimensionSelector, maxPercentageChange);
		this.rg = rg;
	}

	@Override
	public double perturb(int dimension, double value) {
		double maxDelta = maxPercentageChange;
		double delta = (rg.nextGaussian() * (maxDelta / 2)) + (maxDelta / 2);
		if (new Random().nextBoolean()) {
			delta = -1 * delta;
		}

		return value + delta;
	}
}
