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

public class VNSHillClimbingParamOpt extends ParameterOptimizationOperator {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String NO_OF_ITERATIONS2 = "number_of_iterations_for_iterated_hill_climbing";
	private static final String TYPE = "type_of_hill_climbing";
	private static final String T_PARAMETER = "parameter_t";
	private static final String PERTURBER = "perturber";
	private static final String INITIAL_PERCENTAGE_CHANGE = "initial_percentage_change";
	private static final String GROW_PERCENTAGE_CHANGE = "percentage_change_grow";
	private static final String NUMBER_OF_NEIGHBOURHOODS = "number_of_neighbourhoods";
	protected Operator[] operators;
	protected String[] parameters;
	protected String[][] values;
	private ParameterSet best;
	private OptimizationValueType[] types;

	public VNSHillClimbingParamOpt(OperatorDescription description) {
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
			vals[i1] = value;
		}
		Individual ind = new Individual(vals);
		best = setParametersAndEvaluate(ind);

		int no_of_iteration = getParameterAsInt(NO_OF_ITERATIONS);
		int no_of_iterations2 = getParameterAsInt(NO_OF_ITERATIONS2);
		int type = getParameterAsInt(TYPE);
		double t = getParameterAsDouble(T_PARAMETER);
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
			switch (type) {
			case 0: // Default
				for (int i0 = 0; i0 < no_of_iteration; i0++) {
					double[] values = new double[max.length];
					for (int i1 = 0; i1 < max.length; i1++) {
						values[i1] = vals[i1];
					}
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							values.length);
					values[el] = p.perturb(el, values[el]);
					if (types[el] == OptimizationValueType.VALUE_TYPE_INT) {
						values[el] = BigDecimal.valueOf(values[el])
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
					}
					if (values[el] > max[el]) {
						values[el] = max[el];
					}
					if (values[el] < min[el]) {
						values[el] = min[el];
					}

					Individual individual = new Individual(values);
					PerformanceVector solution = setParametersAndEvaluate(individual);

					if (solution.getMainCriterion().getFitness() > best
							.getMainCriterion().getFitness()) {
						best = solution;
						vals = values;
					}
				}
				break;
			case 1: // Default iterated
				for (int i0 = 0; i0 < no_of_iteration; i0++) {
					double[] values = new double[max.length];
					for (int i1 = 0; i1 < max.length; i1++) {
						values[i1] = vals[i1];
					}
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							values.length);
					values[el] = p.perturb(el, values[el]);
					if (types[el] == OptimizationValueType.VALUE_TYPE_INT) {
						values[el] = BigDecimal.valueOf(values[el])
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
					}
					if (values[el] > max[el]) {
						values[el] = max[el];
					}
					if (values[el] < min[el]) {
						values[el] = min[el];
					}

					Individual individual = new Individual(values);
					PerformanceVector solution = setParametersAndEvaluate(individual);

					for (int j = 0; j < no_of_iterations2; j++) {
						double[] v = new double[max.length];
						for (int k = 0; k < v.length; k++) {
							v[k] = values[k];
						}
						int e = RandomGenerator.getRandomGenerator(this)
								.nextInt(v.length);
						v[e] = p.perturb(e, v[e]);
						if (types[e] == OptimizationValueType.VALUE_TYPE_INT) {
							v[e] = BigDecimal.valueOf(v[e])
									.setScale(0, RoundingMode.HALF_EVEN)
									.doubleValue();
						}
						if (v[e] > max[e]) {
							v[e] = max[e];
						}
						if (v[e] < min[e]) {
							v[e] = min[e];
						}
						Individual individual2 = new Individual(v);
						PerformanceVector sol = setParametersAndEvaluate(individual2);
						if (sol.getMainCriterion().getFitness() > solution
								.getMainCriterion().getFitness()) {
							solution = sol;
							values = v;
						}
					}

					if (solution.getMainCriterion().getFitness() > best
							.getMainCriterion().getFitness()) {
						best = solution;
						vals = values;
					}
				}
				break;
			case 2: // Stochastic
				for (int i0 = 0; i0 < no_of_iteration; i0++) {
					double[] values = new double[max.length];
					for (int i1 = 0; i1 < max.length; i1++) {
						values[i1] = vals[i1];
					}
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							values.length);
					values[el] = p.perturb(el, values[el]);
					if (types[el] == OptimizationValueType.VALUE_TYPE_INT) {
						values[el] = BigDecimal.valueOf(values[el])
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
					}
					if (values[el] > max[el]) {
						values[el] = max[el];
					}
					if (values[el] < min[el]) {
						values[el] = min[el];
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
						double exp = (1. / (1. + Math.exp((best
								.getMainCriterion().getFitness() - solution
								.getMainCriterion().getFitness())
								/ t)));
						if (rand < exp) {
							best = solution;
							vals = values;
						}
					}
				}
				break;
			case 3: // Stochastic iterated
				for (int i0 = 0; i0 < no_of_iteration; i0++) {
					double[] values = new double[max.length];
					for (int i1 = 0; i1 < max.length; i1++) {
						values[i1] = vals[i1];
					}
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							values.length);
					values[el] = p.perturb(el, values[el]);
					if (types[el] == OptimizationValueType.VALUE_TYPE_INT) {
						values[el] = BigDecimal.valueOf(values[el])
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
					}
					if (values[el] > max[el]) {
						values[el] = max[el];
					}
					if (values[el] < min[el]) {
						values[el] = min[el];
					}

					Individual individual = new Individual(values);
					PerformanceVector solution = setParametersAndEvaluate(individual);

					for (int j = 0; j < no_of_iterations2; j++) {
						double[] v = new double[max.length];
						for (int k = 0; k < v.length; k++) {
							v[k] = values[k];
						}
						int e = RandomGenerator.getRandomGenerator(this)
								.nextInt(v.length);
						v[e] = p.perturb(e, v[e]);
						if (types[e] == OptimizationValueType.VALUE_TYPE_INT) {
							v[e] = BigDecimal.valueOf(v[e])
									.setScale(0, RoundingMode.HALF_EVEN)
									.doubleValue();
						}
						if (v[e] > max[e]) {
							v[e] = max[e];
						}
						if (v[e] < min[e]) {
							v[e] = min[e];
						}

						Individual individual2 = new Individual(v);
						PerformanceVector sol = setParametersAndEvaluate(individual2);

						if (sol.getMainCriterion().getFitness() > solution
								.getMainCriterion().getFitness()) {
							solution = sol;
							values = v;
						} else {
							double rand = RandomGenerator.getRandomGenerator(
									this).nextDouble();
							double exp = (1. / (1. + Math.exp((solution
									.getMainCriterion().getFitness() - sol
									.getMainCriterion().getFitness())
									/ t)));
							if (rand < exp) {
								solution = sol;
								values = v;
							}
						}
					}

					if (solution.getMainCriterion().getFitness() > best
							.getMainCriterion().getFitness()) {
						best = solution;
						vals = values;
					} else {
						double rand = RandomGenerator.getRandomGenerator(this)
								.nextDouble();
						double exp = (1. / (1. + Math.exp((best
								.getMainCriterion().getFitness() - solution
								.getMainCriterion().getFitness())
								/ t)));
						if (rand < exp) {
							best = solution;
							vals = values;
						}
					}
				}
			default:
				break;
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

		type = new ParameterTypeInt(NO_OF_ITERATIONS2,
				"Number of iterations for iterated hill climbing", 1,
				Integer.MAX_VALUE, 10);
		type.setExpert(false);
		types.add(type);

		type = new ParameterTypeDouble(T_PARAMETER,
				"Parameter t used for acceptance of \"bad solution\"", 0.01,
				1000, 2);
		type.setExpert(false);
		types.add(type);

		type = new ParameterTypeCategory(TYPE, "Type of hill climbing",
				new String[] { "Default", "Iterated default", "Stochastic",
		"Iterated stochastic" }, 0);
		type.setExpert(false);
		types.add(type);

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

		ParameterType type1 = new ParameterTypeInt(NUMBER_OF_NEIGHBOURHOODS,
				"Number of neghbourhoods", 1, 100, 3);
		type1.setExpert(false);
		types.add(type1);

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
