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

public class VNSAttributeSelection extends OperatorChain {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
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
	public VNSAttributeSelection(OperatorDescription description)
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

			for (int j = 0; j < no_of_iteration; j++) {
				double[] w = new double[exampleSet.getAttributes().size()];
				for (int k = 0; k < w.length; k++) {
					w[k] = weights[k];
				}
				double zeros = weights.length;
				for (int k = 0; k < weights.length; k++) {
					w[k] = BigDecimal.valueOf(p.perturb(k, w[k]))
							.setScale(0, RoundingMode.HALF_EVEN).doubleValue();
					if (w[k] > 1) {
						w[k] = 1;
					}
					if (w[k] < 0) {
						w[k] = 0;
					}
					if (w[k] == 0) {
						zeros--;
					}
				}
				if (zeros == 0) {
					int q = RandomGenerator.getRandomGenerator(this).nextInt(
							weights.length);
					w[q] = w[q] == 1 ? 0 : 1;
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
				}
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
