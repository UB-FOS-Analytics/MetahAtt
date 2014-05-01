package rs.fon.vns;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import rs.fon.util.GaussianPerturber;
import rs.fon.util.Perturber;
import rs.fon.util.Perturber.DimensionSelector;
import rs.fon.util.UniformPerturber;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.AttributeWeights;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.AttributeWeightedExampleSet;
import com.rapidminer.operator.OperatorChain;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.performance.PerformanceVector;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.PortPairExtender;
import com.rapidminer.operator.ports.metadata.GenerateNewMDRule;
import com.rapidminer.operator.ports.metadata.SubprocessTransformRule;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.tools.RandomGenerator;

public class VNSSAAttributeSelection extends OperatorChain {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String MAX_TEMPERATURE = "max_temperature";
	private static final String MIN_TEMPERATURE = "min_temperature";
	private static final String DECREASE_STEP = "decrease_step";
	private static final String PERTURBER = "perturber";
	private static final String INITIAL_PERCENTAGE_CHANGE = "initial_percentage_change";
	private static final String GROW_PERCENTAGE_CHANGE = "percentage_change_grow";
	private static final String NUMBER_OF_NEIGHBOURHOODS = "number_of_neighbourhoods";

	// Ports
	private final InputPort exampleSetInput = getInputPorts().createPort(
			"example set", ExampleSet.class);
	private final OutputPort weightsOutput = getOutputPorts().createPort(
			"weights");
	private final OutputPort exampleSetOutput = getOutputPorts().createPort(
			"example set");
	private final OutputPort performanceOutput = getOutputPorts().createPort(
			"performance");
	private final InputPort performanceInnerSink = getSubprocess(0)
			.getInnerSinks().createPort("performance", PerformanceVector.class);
	private final OutputPort exampleSetInnerSource = getSubprocess(0)
			.getInnerSources().createPort("example set");
	private final PortPairExtender inputExtender = new PortPairExtender(
			"input", getInputPorts(), getSubprocess(0).getInnerSources());

	// Fields
	private ExampleSet exampleSet;

	// Constructor
	public VNSSAAttributeSelection(OperatorDescription description)
			throws UndefinedParameterError {
		super(description, "Performance evaluation");

		inputExtender.start();
		getTransformer().addPassThroughRule(exampleSetInput,
				exampleSetInnerSource);
		getTransformer().addRule(inputExtender.makePassThroughRule());
		getTransformer().addRule(new SubprocessTransformRule(getSubprocess(0)));
		getTransformer().addPassThroughRule(performanceInnerSink,
				performanceOutput);
		getTransformer().addPassThroughRule(exampleSetInput, exampleSetOutput);
		getTransformer().addRule(
				new GenerateNewMDRule(weightsOutput, AttributeWeights.class));
	}

	@Override
	public void doWork() throws OperatorException {
		this.exampleSet = exampleSetInput.getData(ExampleSet.class);

		double[] weights = new double[exampleSet.getAttributes().size()];
		PerformanceVector bestPerformance = new PerformanceVector();
		AttributeWeights attWeights = new AttributeWeights();
		for (int i = 0; i < weights.length; i++) {
			weights[i] = 1;
		}
		AttributeWeightedExampleSet result = createWeightedExampleSet(weights);

		exampleSetInnerSource.deliver(result);
		inputExtender.passDataThrough();
		getSubprocess(0).execute();
		bestPerformance = performanceInnerSink.getData(PerformanceVector.class);

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
				p = new GaussianPerturber(exampleSet.getAttributes().size(),
						DimensionSelector.ALEATORY, initialChange + i
						* growChange,
						RandomGenerator.getRandomGenerator(this));
			} else {
				p = new UniformPerturber(exampleSet.getAttributes().size(),
						DimensionSelector.ALEATORY, initialChange + i
						* growChange,
						RandomGenerator.getRandomGenerator(this));
			}
			double temp = maxTemp;
			while (temp > minTemp) {
				for (int i1 = 0; i1 < no_of_iteration; i1++) {
					double[] w = new double[exampleSet.getAttributes().size()];
					for (int j = 0; j < w.length; j++) {
						w[j] = weights[j];
					}
					double zeros = weights.length;
					for (int j = 0; j < weights.length; j++) {
						w[j] = BigDecimal.valueOf(p.perturb(j, w[j]))
								.setScale(0, RoundingMode.HALF_EVEN)
								.doubleValue();
						if (w[j] > 1) {
							w[j] = 1;
						}
						if (w[j] < 0) {
							w[j] = 0;
						}
						if (w[j] == 0) {
							zeros--;
						}
					}
					if (zeros == 0) {
						int q = RandomGenerator.getRandomGenerator(this)
								.nextInt(weights.length);
						w[q] = 1;
					}

					AttributeWeightedExampleSet r = createWeightedExampleSet(w)
							.createCleanClone();

					exampleSetInnerSource.deliver(r);
					inputExtender.passDataThrough();
					getSubprocess(0).execute();

					PerformanceVector performance = performanceInnerSink
							.getData(PerformanceVector.class);

					if (performance.getMainCriterion().getFitness() > bestPerformance
							.getMainCriterion().getFitness()) {
						bestPerformance = performance;
						result = r;
						weights = w;
					} else {
						double rand = RandomGenerator.getRandomGenerator(this)
								.nextDouble();
						double exp = Math.exp((bestPerformance
								.getMainCriterion().getFitness() - performance
								.getMainCriterion().getFitness())
								/ temp);
						if (rand < exp) {
							bestPerformance = performance;
							result = r;
							weights = w;
						}
					}
				}
				temp *= decreaseStep;
			}
		}
		int index = 0;
		for (Attribute att : exampleSet.getAttributes()) {
			int i = 1;
			for (Attribute attribute : result.getAttributes()) {
				if (att.getName().equalsIgnoreCase(attribute.getName())) {
					attWeights.setWeight(attribute.getName(), weights[index++]);
					i--;
				}
			}
			if (i == 1) {
				attWeights.setWeight(att.getName(), 0d);
			}
		}
		attWeights.normalize();

		exampleSetOutput.deliver(exampleSet);
		weightsOutput.deliver(attWeights);
		performanceOutput.deliver(bestPerformance);
	}

	private AttributeWeightedExampleSet createWeightedExampleSet(
			double[] weights) {
		AttributeWeightedExampleSet result = new AttributeWeightedExampleSet(
				exampleSet, null);
		int index = 0;
		for (Attribute attribute : exampleSet.getAttributes()) {
			result.setWeight(attribute, weights[index++]);
		}
		return result;
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
