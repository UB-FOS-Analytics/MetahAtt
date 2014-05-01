package rs.fon.ils;

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
import com.rapidminer.tools.RandomGenerator;

public class ILSAttributeWeighting extends OperatorChain {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String ACCEPTANCE = "acceptance_criterion";
	private static final String PCT_OF_CHANGE = "percentage_of_change";
	private static final String PERTURBER = "perturber";
	private static final String MAX_PERCENTAGE_CHANGE = "max_percentage_change";

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

	public ILSAttributeWeighting(OperatorDescription description) {
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

		int no_of_iteration = getParameterAsInt(NO_OF_ITERATIONS);
		double acc = getParameterAsDouble(ACCEPTANCE);
		double pct = getParameterAsDouble(PCT_OF_CHANGE);
		int perturber = getParameterAsInt(PERTURBER);
		double maxChange = getParameterAsDouble(MAX_PERCENTAGE_CHANGE);

		Perturber p;
		if (perturber == 0) {
			p = new GaussianPerturber(exampleSet.getAttributes().size(),
					DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		} else {
			p = new UniformPerturber(exampleSet.getAttributes().size(),
					DimensionSelector.ALEATORY, maxChange,
					RandomGenerator.getRandomGenerator(this));
		}

		double[] weights = new double[exampleSet.getAttributes().size()];
		PerformanceVector bestPerformance = new PerformanceVector();
		AttributeWeights attWeights = new AttributeWeights();
		for (int j = 0; j < weights.length; j++) {
			weights[j] = 1;
		}
		AttributeWeightedExampleSet result = createWeightedExampleSet(weights);

		exampleSetInnerSource.deliver(result);
		inputExtender.passDataThrough();
		getSubprocess(0).execute();
		bestPerformance = performanceInnerSink.getData(PerformanceVector.class);

		for (int i = 0; i < no_of_iteration; i++) {
			double[] w = new double[exampleSet.getAttributes().size()];
			for (int j = 0; j < w.length; j++) {
				w[j] = weights[j];
			}
			int z = w.length;
			for (int j = 0; j < w.length; j++) {
				if (RandomGenerator.getRandomGenerator(this).nextDouble() > pct) {
					w[j] = p.perturb(j, w[j]);
				}
				if (w[j] > 1) {
					w[j] = 1;
				}
				if (w[j] < 0) {
					w[j] = 0;
				}
				if (w[j] == 0) {
					z--;
				}
			}
			if (z == 0) {
				int q = RandomGenerator.getRandomGenerator(this).nextInt(
						w.length);
				w[q] = p.perturb(q, w[q]);
				if (w[q] > 1) {
					w[q] = 1;
				}
				if (w[q] <= 0) {
					w[q] = 0.01;
				}
			}

			AttributeWeightedExampleSet r = createWeightedExampleSet(w)
					.createCleanClone();

			exampleSetInnerSource.deliver(r);
			inputExtender.passDataThrough();
			getSubprocess(0).execute();
			PerformanceVector performance = performanceInnerSink
					.getData(PerformanceVector.class);

			if (performance.getMainCriterion().getFitness() > bestPerformance
					.getMainCriterion().getFitness() * acc) {
				bestPerformance = performance;
				weights = w;
				result = r;
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
