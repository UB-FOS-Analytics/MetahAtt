package rs.fon.vns;

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

public class VNSHillClimbingAttributeWeighting extends OperatorChain {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String NO_OF_ITERATIONS2 = "number_of_iterations_for_iterated_hill_climbing";
	private static final String TYPE = "type_of_hill_climbing";
	private static final String T_PARAMETER = "parameter_t";
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
	public VNSHillClimbingAttributeWeighting(OperatorDescription description)
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

			switch (type) {
			case 0: // Default
				for (int i1 = 0; i1 < no_of_iteration; i1++) {
					double[] w = new double[exampleSet.getAttributes().size()];
					for (int j = 0; j < w.length; j++) {
						w[j] = weights[j];
					}
					int z = w.length;
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							w.length);
					w[el] = p.perturb(el, w[el]);
					if (w[el] > 1) {
						w[el] = 1;
					}
					if (w[el] < 0) {
						w[el] = 0;
					}
					for (int j = 0; j < w.length; j++) {
						if (w[j] == 0) {
							z--;
						}
					}
					if (z == 0) {
						int q = RandomGenerator.getRandomGenerator(this)
								.nextInt(w.length);
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
							.getMainCriterion().getFitness()) {
						bestPerformance = performance;
						weights = w;
						result = r;
					}
				}
				break;
			case 1: // Iterated default
				for (int i1 = 0; i1 < no_of_iteration; i1++) {
					double[] w = new double[exampleSet.getAttributes().size()];
					for (int j = 0; j < w.length; j++) {
						w[j] = weights[j];
					}
					int z = w.length;
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							w.length);
					w[el] = p.perturb(el, w[el]);
					if (w[el] > 1) {
						w[el] = 1;
					}
					if (w[el] < 0) {
						w[el] = 0;
					}
					for (int j = 0; j < w.length; j++) {
						if (w[j] == 0) {
							z--;
						}
					}
					if (z == 0) {
						int q = RandomGenerator.getRandomGenerator(this)
								.nextInt(w.length);
						w[q] = p.perturb(q, w[q]);
						if (w[q] > 1) {
							w[q] = 1;
						}
						if (w[q] < 0) {
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
					for (int j = 0; j < no_of_iterations2; j++) {
						double[] w2 = new double[exampleSet.getAttributes()
						                         .size()];
						for (int jw = 0; jw < w2.length; jw++) {
							w2[jw] = w[jw];
						}
						int z2 = w2.length;
						el = RandomGenerator.getRandomGenerator(this).nextInt(
								w2.length);
						w2[el] = p.perturb(el, w2[el]);
						if (w2[el] > 1) {
							w2[el] = 1;
						}
						if (w2[el] < 0) {
							w2[el] = 0;
						}
						for (int j1 = 0; j1 < w2.length; j1++) {
							if (w2[j1] == 0) {
								z2--;
							}
						}
						if (z2 == 0) {
							int qq = RandomGenerator.getRandomGenerator(this)
									.nextInt(w2.length);
							w2[qq] = p.perturb(qq, w2[qq]);
							if (w2[qq] > 1) {
								w2[qq] = 1;
							}
							if (w2[qq] < 0) {
								w2[qq] = 0.01;
							}
						}

						AttributeWeightedExampleSet r2 = createWeightedExampleSet(
								w2).createCleanClone();

						exampleSetInnerSource.deliver(r2);
						inputExtender.passDataThrough();
						getSubprocess(0).execute();
						PerformanceVector performance2 = performanceInnerSink
								.getData(PerformanceVector.class);

						if (performance2.getMainCriterion().getFitness() > performance
								.getMainCriterion().getFitness()) {
							performance = performance2;
							w = w2;
							r = r2;
						}
					}
					if (performance.getMainCriterion().getFitness() > bestPerformance
							.getMainCriterion().getFitness()) {
						bestPerformance = performance;
						weights = w;
						result = r;
					}
				}
				break;
			case 2: // Stochastic
				for (int i1 = 0; i1 < no_of_iteration; i1++) {
					double[] w = new double[exampleSet.getAttributes().size()];
					for (int j = 0; j < w.length; j++) {
						w[j] = weights[j];
					}
					int z = w.length;
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							w.length);
					w[el] = p.perturb(el, w[el]);
					if (w[el] > 1) {
						w[el] = 1;
					}
					if (w[el] < 0) {
						w[el] = 0;
					}
					for (int j = 0; j < weights.length; j++) {
						if (w[j] == 0) {
							z--;
						}
					}
					if (z == 0) {
						int q = RandomGenerator.getRandomGenerator(this)
								.nextInt(w.length);
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
							.getMainCriterion().getFitness()) {
						bestPerformance = performance;
						weights = w;
						result = r;
					} else {
						double rand = RandomGenerator.getRandomGenerator(this)
								.nextDouble();
						double exp = (1. / (1. + Math.exp((bestPerformance
								.getMainCriterion().getFitness() - performance
								.getMainCriterion().getFitness())
								/ t)));
						if (rand < exp) {
							bestPerformance = performance;
							weights = w;
							result = r;
						}
					}
				}
				break;
			case 3: // Stochastic iterated
				for (int i1 = 0; i1 < no_of_iteration; i1++) {
					double[] w = new double[exampleSet.getAttributes().size()];
					for (int j = 0; j < w.length; j++) {
						w[j] = weights[j];
					}
					int z = w.length;
					int el = RandomGenerator.getRandomGenerator(this).nextInt(
							w.length);
					w[el] = p.perturb(el, w[el]);
					if (w[el] > 1) {
						w[el] = 1;
					}
					if (w[el] < 0) {
						w[el] = 0;
					}
					for (int j = 0; j < weights.length; j++) {
						if (w[j] == 0) {
							z--;
						}
					}
					if (z == 0) {
						int q = RandomGenerator.getRandomGenerator(this)
								.nextInt(w.length);
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

					for (int j = 0; j < no_of_iterations2; j++) {
						double[] w2 = new double[exampleSet.getAttributes()
						                         .size()];
						for (int jw = 0; jw < w2.length; jw++) {
							w2[jw] = w[jw];
						}
						int z2 = w2.length;
						el = RandomGenerator.getRandomGenerator(this).nextInt(
								w2.length);
						w2[el] = p.perturb(el, w2[el]);
						if (w2[el] > 1) {
							w2[el] = 1;
						}
						if (w2[el] < 0) {
							w2[el] = 0;
						}
						for (int j1 = 0; j1 < w2.length; j1++) {
							if (w2[j1] == 0) {
								z2--;
							}
						}
						if (z2 == 0) {
							int qq = RandomGenerator.getRandomGenerator(this)
									.nextInt(w2.length);
							w2[qq] = p.perturb(qq, w2[qq]);
							if (w2[qq] > 1) {
								w2[qq] = 1;
							}
							if (w2[qq] <= 0) {
								w2[qq] = 0.01;
							}
						}

						AttributeWeightedExampleSet r2 = createWeightedExampleSet(
								w2).createCleanClone();

						exampleSetInnerSource.deliver(r2);
						inputExtender.passDataThrough();
						getSubprocess(0).execute();
						PerformanceVector performance2 = performanceInnerSink
								.getData(PerformanceVector.class);

						if (performance2.getMainCriterion().getFitness() > performance
								.getMainCriterion().getFitness()) {
							performance = performance2;
							w = w2;
							r = r2;
						} else {
							double rand = RandomGenerator.getRandomGenerator(
									this).nextDouble();
							double exp = (1. / (1. + Math
									.exp((performance.getMainCriterion()
											.getFitness() - performance2
											.getMainCriterion().getFitness())
											/ t)));
							if (rand < exp) {
								performance = performance2;
								w = w2;
								r = r2;
							}
						}
					}

					if (performance.getMainCriterion().getFitness() > bestPerformance
							.getMainCriterion().getFitness()) {
						bestPerformance = performance;
						weights = w;
						result = r;
					} else {
						double rand = RandomGenerator.getRandomGenerator(this)
								.nextDouble();
						double exp = (1. / (1. + Math.exp((bestPerformance
								.getMainCriterion().getFitness() - performance
								.getMainCriterion().getFitness())
								/ t)));
						if (rand < exp) {
							bestPerformance = performance;
							weights = w;
							result = r;
						}
					}
				}
				break;
			default:
				break;
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

		ParameterType type1 = new ParameterTypeInt(NUMBER_OF_NEIGHBOURHOODS,
				"Number of neghbourhoods", 1, 100, 3);
		type1.setExpert(false);
		types.add(type1);

		ParameterType type5 = new ParameterTypeDouble(
				INITIAL_PERCENTAGE_CHANGE,
				"Initial change of value in iteration", 0, 1, 0.2);
		type5.setExpert(false);
		types.add(type5);

		ParameterType type6 = new ParameterTypeDouble(GROW_PERCENTAGE_CHANGE,
				"Grow of change of value in iteration", 0, 1, 0.2);
		type6.setExpert(false);
		types.add(type6);

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
