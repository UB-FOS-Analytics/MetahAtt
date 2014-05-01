package rs.fon.harmony;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import rs.fon.util.GaussianPerturber;
import rs.fon.util.Perturber;
import rs.fon.util.Perturber.DimensionSelector;
import rs.fon.util.UniformPerturber;

import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.meta.ParameterOptimizationOperator;
import com.rapidminer.operator.meta.ParameterSet;
import com.rapidminer.operator.performance.PerformanceVector;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.value.ParameterValueRange;
import com.rapidminer.parameter.value.ParameterValues;
import com.rapidminer.tools.RandomGenerator;
import com.rapidminer.tools.math.optimization.ec.es.Individual;
import com.rapidminer.tools.math.optimization.ec.es.OptimizationValueType;

public class HarmonySearchParamOpt extends ParameterOptimizationOperator {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String PITCH_ADJUSTMENT_RATE = "pitch_adjusted_rate";
	private static final String HARMONY_CONSIDERATION_RATE = "harmony_memory_consideration_rate";
	private static final String NUMBER_OF_PITCHES = "number_of_pitches";
	private static final String IMPROVISATION = "improvisation";
	private static final String PERTURBER = "perturber";
	private static final String MAX_PERCENTAGE_CHANGE = "max_percentage_change";
	protected Operator[] operators;
	protected String[] parameters;
	protected String[][] values;
	private ParameterSet best;
	private OptimizationValueType[] types;

	public HarmonySearchParamOpt(OperatorDescription description) {
		super(description);
	}

	@Override
	public int getParameterValueMode() {
		return VALUE_MODE_CONTINUOUS;
	}

	public PerformanceVector setParametersAndEvaluate(Individual individual)
			throws OperatorException {
		double[] currentValues = individual.getValues();
		for (int j = 0; j < currentValues.length; j++) {
			String value;
			if (types[j].equals(OptimizationValueType.VALUE_TYPE_DOUBLE)) {
				value = currentValues[j] + "";
			} else {
				value = (int) Math.round(currentValues[j]) + "";
			}
			operators[j].getParameters().setParameter(parameters[j], value);
		}
		return getPerformance(true);
	}

	@Override
	public double getCurrentBestPerformance() {
		if (best != null) {
			return best.getPerformance().getMainCriterion().getFitness();
		} else {
			return Double.NaN;
		}
	}

	@Override
	public void doWork() throws OperatorException {
		List<ParameterValues> parameterValuesList = parseParameterValues(getParameterList(PARAMETER_PARAMETERS));
		if (parameterValuesList == null) {
			throw new UserError(this, 922);
		}

		if (parameterValuesList.size() == 0) {
			throw new UserError(this, 922);
		}

		// initialize data structures
		this.operators = new Operator[parameterValuesList.size()];
		this.parameters = new String[parameterValuesList.size()];
		double[] min = new double[parameterValuesList.size()];
		double[] max = new double[parameterValuesList.size()];
		this.types = new OptimizationValueType[parameterValuesList.size()];

		// get parameter values and fill data structures
		int index = 0;
		for (Iterator<ParameterValues> iterator = parameterValuesList
				.iterator(); iterator.hasNext();) {
			ParameterValueRange parameterValueRange = (ParameterValueRange) iterator
					.next();
			operators[index] = parameterValueRange.getOperator();
			parameters[index] = parameterValueRange.getParameterType().getKey();
			min[index] = Double.valueOf(parameterValueRange.getMin());
			max[index] = Double.valueOf(parameterValueRange.getMax());

			ParameterType targetType = parameterValueRange.getParameterType();
			if (targetType == null) {
				throw new UserError(this, 906,
						parameterValueRange.getOperator() + "."
								+ parameterValueRange.getKey());
			}
			if (targetType instanceof ParameterTypeDouble) {
				types[index] = OptimizationValueType.VALUE_TYPE_DOUBLE;
			} else if (targetType instanceof ParameterTypeInt) {
				types[index] = OptimizationValueType.VALUE_TYPE_INT;
			} else {
				throw new UserError(this, 909, targetType.getKey());
			}
			index++;
		}

		RandomGenerator random = RandomGenerator.getRandomGenerator(this);
		PerformanceVector best = new PerformanceVector();

		double[] vals = new double[max.length];
		for (int i1 = 0; i1 < max.length; i1++) {
			double value = random.nextDouble()
					* (Double.isInfinite(max[i1]) ? 1000 : max[i1] - min[i1])
					+ min[i1];
			vals[i1] = value;
		}
		Individual ind = new Individual(vals);
		best = setParametersAndEvaluate(ind);

		int no_of_iteration = getParameterAsInt(NO_OF_ITERATIONS);
		double pitchAdjustmentRate = getParameterAsDouble(PITCH_ADJUSTMENT_RATE);
		double harmonyMemory = getParameterAsDouble(HARMONY_CONSIDERATION_RATE);
		int pitches = getParameterAsInt(NUMBER_OF_PITCHES);
		double improvisation = getParameterAsDouble(IMPROVISATION);
		int perturber = getParameterAsInt(PERTURBER);
		double maxChange = getParameterAsDouble(MAX_PERCENTAGE_CHANGE);

		List<double[]> listOfPitches = new ArrayList<double[]>();
		for (int i = 0; i < pitches; i++) {
			double[] w = new double[max.length];
			for (int j = 0; j < w.length; j++) {
				w[j] = RandomGenerator.getRandomGenerator(this).nextBoolean() ? (Double
						.isInfinite(max[j]) ? 1000 : max[j]) : min[j];
			}
			listOfPitches.add(w);
		}

		Perturber p;
		if (perturber == 0) {
			p = new GaussianPerturber(1, DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		} else {
			p = new UniformPerturber(1, DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		}
		for (int i = 0; i < no_of_iteration; i++) {
			for (int j = 0; j < listOfPitches.size(); j++) {
				double[] array = listOfPitches.get(j);
				for (int j1 = 0; j1 < array.length; j1++) {
					if (RandomGenerator.getRandomGenerator(this).nextDouble() < harmonyMemory) {
						listOfPitches.get(j)[j1] = vals[j1];
					}
				}
				if (RandomGenerator.getRandomGenerator(this).nextDouble() < pitchAdjustmentRate) {
					for (int j1 = 0; j1 < array.length; j1++) {
						if (RandomGenerator.getRandomGenerator(this)
								.nextDouble() < pitchAdjustmentRate) {
							listOfPitches.get(j)[j1] = p.perturb(j1,
									listOfPitches.get(j)[j1]
											* (1 + improvisation));
							if (types[j1] == OptimizationValueType.VALUE_TYPE_INT) {
								listOfPitches.get(j)[j1] = BigDecimal
										.valueOf(listOfPitches.get(j)[j1])
										.setScale(0, RoundingMode.HALF_EVEN)
										.doubleValue();
							}
							if (listOfPitches.get(j)[j1] > max[j1]) {
								listOfPitches.get(j)[j1] = max[j1];
							}
							if (listOfPitches.get(j)[j1] < min[j1]) {
								listOfPitches.get(j)[j1] = min[j1];
							}
						} else {
							listOfPitches.get(j)[j1] = p.perturb(j1,
									listOfPitches.get(j)[j1]
											* (1 - improvisation));
							if (types[j1] == OptimizationValueType.VALUE_TYPE_INT) {
								listOfPitches.get(j)[j1] = BigDecimal
										.valueOf(listOfPitches.get(j)[j1])
										.setScale(0, RoundingMode.HALF_EVEN)
										.doubleValue();
							}
							if (listOfPitches.get(j)[j1] > max[j1]) {
								listOfPitches.get(j)[j1] = max[j1];
							}
							if (listOfPitches.get(j)[j1] < min[j1]) {
								listOfPitches.get(j)[j1] = min[j1];
							}
						}
					}
				} else {
					if (RandomGenerator.getRandomGenerator(this).nextDouble() < harmonyMemory) {
						double[] array1 = listOfPitches.get(j);
						for (int j1 = 0; j1 < array1.length; j1++) {
							if (RandomGenerator.getRandomGenerator(this)
									.nextDouble() < harmonyMemory) {
								listOfPitches.get(j)[j1] = listOfPitches
										.get(RandomGenerator
												.getRandomGenerator(this)
												.nextInt(listOfPitches.size()))[j1];
							}
						}
					}
				}
				Individual individual = new Individual(listOfPitches.get(j));
				PerformanceVector solution = setParametersAndEvaluate(individual);

				if (solution.getMainCriterion().getFitness() > best
						.getMainCriterion().getFitness()) {
					best = solution;
					vals = listOfPitches.get(j);
				}
			}
		}

		ParameterSet bestSet = null;

		String[] bestValues = new String[parameters.length];
		for (int j = 0; j < parameters.length; j++) {
			bestValues[j] = String.valueOf(vals[j]);
		}
		bestSet = new ParameterSet(operators, parameters, bestValues, best);
		passResultsThrough();

		deliver(bestSet);
	}

	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		ParameterType type = new ParameterTypeInt(NO_OF_ITERATIONS,
				"Number of iterations per neighbourhood step", 1,
				Integer.MAX_VALUE, 100);
		type.setExpert(false);
		types.add(type);

		ParameterType type1 = new ParameterTypeInt(NUMBER_OF_PITCHES,
				"Number of pitches", 1, 100, 3);
		type1.setExpert(false);
		types.add(type1);

		ParameterType type2 = new ParameterTypeDouble(PITCH_ADJUSTMENT_RATE,
				"Pitch adjustment rate", 0.00000001, 1, 0.5);
		type2.setExpert(false);
		types.add(type2);

		ParameterType type3 = new ParameterTypeDouble(
				HARMONY_CONSIDERATION_RATE,
				"Harmony memory consideration rate", 0.0000001, 1, .8);
		type3.setExpert(false);
		types.add(type3);

		ParameterType type5 = new ParameterTypeDouble(IMPROVISATION,
				"Improvisation rate", 0.0000001, 1, .15);
		type5.setExpert(false);
		types.add(type5);

		ParameterType type4 = new ParameterTypeCategory(PERTURBER,
				"Type of perturber", new String[] { "Gaussian", "Uniform" }, 0);
		type4.setExpert(false);
		types.add(type4);

		ParameterType type69 = new ParameterTypeDouble(MAX_PERCENTAGE_CHANGE,
				"Max change of value in iteration", 0, 1, 0.2);
		type69.setExpert(false);
		types.add(type69);

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
