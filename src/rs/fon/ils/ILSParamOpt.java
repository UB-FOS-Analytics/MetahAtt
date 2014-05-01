package rs.fon.ils;

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

public class ILSParamOpt extends ParameterOptimizationOperator {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String ACCEPTANCE = "acceptance_criterion";
	private static final String PCT_OF_CHANGE = "percentage_of_change";
	private static final String PERTURBER = "perturber";
	private static final String MAX_PERCENTAGE_CHANGE = "max_percentage_change";
	protected Operator[] operators;
	protected String[] parameters;
	protected String[][] values;
	private ParameterSet best;
	private OptimizationValueType[] types;

	public ILSParamOpt(OperatorDescription description) {
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
		double acc = getParameterAsDouble(ACCEPTANCE);
		double pct = getParameterAsDouble(PCT_OF_CHANGE);
		int perturber = getParameterAsInt(PERTURBER);
		double maxChange = getParameterAsDouble(MAX_PERCENTAGE_CHANGE);

		Perturber p;
		if (perturber == 0) {
			p = new GaussianPerturber(1, DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		} else {
			p = new UniformPerturber(1, DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		}
		for (int i = 0; i < no_of_iteration; i++) {
			double[] values = new double[max.length];
			for (int i1 = 0; i1 < max.length; i1++) {
				if (RandomGenerator.getRandomGenerator(this).nextDouble() > pct) {
					double value = p.perturb(i1, vals[i1]);
					if (types[i1] == OptimizationValueType.VALUE_TYPE_INT) {
						values[i1] = BigDecimal.valueOf(values[i1])
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
					}
					if (value > max[i1]) {
						value = max[i1];
					}
					if (value < min[i1]) {
						value = min[i1];
					}
					values[i1] = value;
				}
			}
			Individual individual = new Individual(values);
			PerformanceVector solution = setParametersAndEvaluate(individual);

			if (solution.getMainCriterion().getFitness() > best
					.getMainCriterion().getFitness() * acc) {
				best = solution;
				vals = values;
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
				"Number of iterations", 1, Integer.MAX_VALUE, 100);
		type.setExpert(false);
		types.add(type);

		type = new ParameterTypeDouble(ACCEPTANCE, "Acceptance criterion",
				0.01, 1, .95);
		type.setExpert(false);
		types.add(type);

		type = new ParameterTypeDouble(PCT_OF_CHANGE,
				"Percentage of attributes that change value in one iteration",
				0.01, 1, .25);
		type.setExpert(false);
		types.add(type);

		ParameterType type4 = new ParameterTypeCategory(PERTURBER,
				"Type of perturber", new String[] { "Gaussian", "Uniform" }, 0);
		type4.setExpert(false);
		types.add(type4);

		ParameterType type5 = new ParameterTypeDouble(MAX_PERCENTAGE_CHANGE,
				"Max change of value in iteration", 0, 1, 0.2);
		type5.setExpert(false);
		types.add(type5);

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
