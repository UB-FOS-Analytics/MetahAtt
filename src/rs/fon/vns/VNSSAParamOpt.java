package rs.fon.vns;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

public class VNSSAParamOpt extends ParameterOptimizationOperator {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String MAX_TEMPERATURE = "max_temperature";
	private static final String MIN_TEMPERATURE = "min_temperature";
	private static final String DECREASE_STEP = "decrease_step";
	private static final String PERTURBER = "perturber";
	private static final String INITIAL_PERCENTAGE_CHANGE = "initial_percentage_change";
	private static final String GROW_PERCENTAGE_CHANGE = "percentage_change_grow";
	private static final String NUMBER_OF_NEIGHBOURHOODS = "number_of_neighbourhoods";
	protected Operator[] operators;
	protected String[] parameters;
	protected String[][] values;
	private ParameterSet best;
	private OptimizationValueType[] types;

	public VNSSAParamOpt(OperatorDescription description) {
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
			} else if (types[j].equals(OptimizationValueType.VALUE_TYPE_INT)) {
				value = (int) Math.round(currentValues[j]) + "";
			} else {
				int i = (int) currentValues[j];
				value = values[j][i];
			}
			if (operators[j].getParameters().setParameter(parameters[j], value))
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
		for (int i = 0; i < parameterValuesList.size(); i++) {
			ParameterValues pv = parameterValuesList.get(i);
			ParameterValueRange parameterValueRange;
			if (pv instanceof ParameterValueRange) {
				parameterValueRange = (ParameterValueRange) pv;

				operators[index] = parameterValueRange.getOperator();
				parameters[index] = parameterValueRange.getParameterType()
						.getKey();
				min[index] = Double.valueOf(parameterValueRange.getMin());
				max[index] = Double.valueOf(parameterValueRange.getMax());

				ParameterType targetType = parameterValueRange
						.getParameterType();
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
		}

		RandomGenerator random = RandomGenerator.getRandomGenerator(this);
		PerformanceVector best = new PerformanceVector();

		double[] vals = new double[max.length];
		for (int i1 = 0; i1 < max.length; i1++) {
			double value = random.nextDouble()
					* (Double.isInfinite(max[i1]) ? 1000 : max[i1] - min[i1])
					+ min[i1];
			if (types[i1] == OptimizationValueType.VALUE_TYPE_INT) {
				value = BigDecimal.valueOf(value)
						.setScale(0, RoundingMode.HALF_EVEN).doubleValue();
			}
			vals[i1] = value;
		}
		Individual ind = new Individual(vals);
		best = setParametersAndEvaluate(ind);

		int no_of_iteration = getParameterAsInt(NO_OF_ITERATIONS);
		double maxTemp = getParameterAsDouble(MAX_TEMPERATURE);
		double minTemp = getParameterAsDouble(MIN_TEMPERATURE);
		double decreaseStep = getParameterAsDouble(DECREASE_STEP);
		int perturber = getParameterAsInt(PERTURBER);
		double initialChange = getParameterAsDouble(INITIAL_PERCENTAGE_CHANGE);
		double growChange = getParameterAsDouble(GROW_PERCENTAGE_CHANGE);
		int numberOfNeighbourhoods = getParameterAsInt(NUMBER_OF_NEIGHBOURHOODS);

		for (int i = 0; i < numberOfNeighbourhoods; i++) {
			Perturber p;
			if (perturber == 0) {
				p = new GaussianPerturber(1, DimensionSelector.ALEATORY,
						initialChange + i * growChange,
						RandomGenerator.getRandomGenerator(this));
			} else {
				p = new UniformPerturber(1, DimensionSelector.ALEATORY,
						initialChange + i * growChange,
						RandomGenerator.getRandomGenerator(this));
			}
			double temp = maxTemp;
			while (temp > minTemp) {
				for (int j = 0; j < no_of_iteration; j++) {
					double[] values = new double[max.length];
					for (int k = 0; k < values.length; k++) {
						values[k] = vals[k];
					}
					for (int k = 0; k < values.length; k++) {
						values[k] = p.perturb(k, values[k]);
						if (types[k] == OptimizationValueType.VALUE_TYPE_INT) {
							values[k] = BigDecimal.valueOf(values[k])
									.setScale(0, RoundingMode.HALF_EVEN)
									.doubleValue();
						}
						if (values[k] > max[k]) {
							values[k] = max[k];
						}
						if (values[k] < min[k]) {
							values[k] = min[k];
						}
					}

					Individual individual = new Individual(values);
					PerformanceVector solution = setParametersAndEvaluate(individual);

					if (solution.getMainCriterion().getFitness() > best
							.getMainCriterion().getFitness()) {
						best = solution;
						vals = values;
					} else {
						double rand = RandomGenerator.getRandomGenerator(this)
								.nextDouble();
						double exp = Math.exp((best.getMainCriterion()
								.getFitness() - solution.getMainCriterion()
								.getFitness())
								/ temp);
						if (rand < exp) {
							best = solution;
							vals = values;
						}
					}
				}
				temp *= decreaseStep;
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

		ParameterType type1 = new ParameterTypeDouble(MAX_TEMPERATURE,
				"Max temprature", 0.000000001, 1, 0.9);
		type1.setExpert(false);
		types.add(type1);

		ParameterType type2 = new ParameterTypeDouble(MIN_TEMPERATURE,
				"Min temperature", 0.00000001, 1, 0.1);
		type2.setExpert(false);
		types.add(type2);

		ParameterType type3 = new ParameterTypeDouble(DECREASE_STEP,
				"Decrease step", 0.0000001, 1, .8);
		type3.setExpert(false);
		types.add(type3);

		ParameterType type4 = new ParameterTypeCategory(PERTURBER,
				"Type of perturber", new String[] { "Gaussian", "Uniform" }, 0);
		type4.setExpert(false);
		types.add(type4);

		ParameterType type5 = new ParameterTypeDouble(
				INITIAL_PERCENTAGE_CHANGE,
				"Initial change of value in iteration", 0, 1, 0.2);
		type5.setExpert(false);
		types.add(type5);

		ParameterType type6 = new ParameterTypeDouble(GROW_PERCENTAGE_CHANGE,
				"Grow of change of value in iteration", 0, 1, 0.2);
		type6.setExpert(false);
		types.add(type6);

		ParameterType type21 = new ParameterTypeInt(NUMBER_OF_NEIGHBOURHOODS,
				"Number of neghbourhoods", 1, 100, 3);
		type21.setExpert(false);
		types.add(type21);

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
