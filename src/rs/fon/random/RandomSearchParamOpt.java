package rs.fon.random;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.meta.ParameterOptimizationOperator;
import com.rapidminer.operator.meta.ParameterSet;
import com.rapidminer.operator.performance.PerformanceVector;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.value.ParameterValueRange;
import com.rapidminer.parameter.value.ParameterValues;
import com.rapidminer.tools.RandomGenerator;
import com.rapidminer.tools.math.optimization.ec.es.Individual;
import com.rapidminer.tools.math.optimization.ec.es.OptimizationValueType;

public class RandomSearchParamOpt extends ParameterOptimizationOperator {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	protected Operator[] operators;
	protected String[] parameters;
	protected String[][] values;
	private ParameterSet best;
	private OptimizationValueType[] types;

	public RandomSearchParamOpt(OperatorDescription description) {
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
			if (types[i1] == OptimizationValueType.VALUE_TYPE_INT) {
				value = BigDecimal.valueOf(value)
						.setScale(0, RoundingMode.HALF_EVEN).doubleValue();
			}
			vals[i1] = value;
		}
		Individual ind = new Individual(vals);
		best = setParametersAndEvaluate(ind);

		int no_of_iteration = getParameterAsInt(NO_OF_ITERATIONS);

		for (int i = 0; i < no_of_iteration; i++) {
			double[] values = new double[max.length];
			for (int i1 = 0; i1 < max.length; i1++) {
				double value = random.nextDouble()
						* (Double.isInfinite(max[i1]) ? 1000000 : max[i1]
								- min[i1]) + min[i1];
				if (types[i1] == OptimizationValueType.VALUE_TYPE_INT) {
					values[i1] = BigDecimal.valueOf(values[i1])
							.setScale(0, RoundingMode.HALF_EVEN).doubleValue();
				}
				values[i1] = value;
			}
			Individual individual = new Individual(values);
			PerformanceVector solution = setParametersAndEvaluate(individual);

			if (solution.getMainCriterion().getFitness() > best
					.getMainCriterion().getFitness()) {
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

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
