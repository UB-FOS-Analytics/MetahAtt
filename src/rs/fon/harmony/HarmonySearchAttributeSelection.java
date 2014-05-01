package rs.fon.harmony;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.tools.RandomGenerator;

public class HarmonySearchAttributeSelection extends OperatorChain {

	// Params
	private static final String NO_OF_ITERATIONS = "number_of_iterations";
	private static final String PITCH_ADJUSTMENT_RATE = "pitch_adjusted_rate";
	private static final String HARMONY_CONSIDERATION_RATE = "harmony_memory_consideration_rate";
	private static final String NUMBER_OF_PITCHES = "number_of_pitches";
	private static final String IMPROVISATION = "improvisation";

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
	public HarmonySearchAttributeSelection(OperatorDescription description)
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
		double pitchAdjustmentRate = getParameterAsDouble(PITCH_ADJUSTMENT_RATE);
		double harmonyMemory = getParameterAsDouble(HARMONY_CONSIDERATION_RATE);
		int pitches = getParameterAsInt(NUMBER_OF_PITCHES);
		double improvisation = getParameterAsDouble(IMPROVISATION);

		List<double[]> listOfPitches = new ArrayList<double[]>();
		for (int i = 0; i < pitches; i++) {
			double[] w = new double[weights.length];
			int z = w.length;
			for (int j = 0; j < w.length; j++) {
				w[j] = RandomGenerator.getRandomGenerator(this).nextInt();
				if (w[j] == 0) {
					z--;
				}
			}
			if (z == 0) {
				int el = RandomGenerator.getRandomGenerator(this).nextInt(
						w.length);
				w[el] = 1;
			}
			listOfPitches.add(w);
		}

		for (int i = 0; i < no_of_iteration; i++) {
			for (int i1 = 0; i1 < listOfPitches.size(); i1++) {
				if (RandomGenerator.getRandomGenerator(this).nextDouble() < harmonyMemory) {
					double[] array = listOfPitches.get(i1);
					for (int j = 0; j < array.length; j++) {
						if (RandomGenerator.getRandomGenerator(this)
								.nextDouble() < harmonyMemory) {
							listOfPitches.get(i1)[j] = weights[j];
						}
					}
					if (RandomGenerator.getRandomGenerator(this).nextDouble() < pitchAdjustmentRate) {
						for (int j = 0; j < array.length; j++) {
							if (RandomGenerator.getRandomGenerator(this)
									.nextDouble() < pitchAdjustmentRate) {
								listOfPitches.get(i1)[j] = BigDecimal
										.valueOf(
												listOfPitches.get(i1)[j]
														* (1 + improvisation))
														.setScale(0, RoundingMode.HALF_EVEN)
														.doubleValue();
							} else {
								listOfPitches.get(i1)[j] = BigDecimal
										.valueOf(
												listOfPitches.get(i1)[j]
														* (1 - improvisation))
														.setScale(0, RoundingMode.HALF_EVEN)
														.doubleValue();
							}
						}
					} else {
						if (RandomGenerator.getRandomGenerator(this)
								.nextDouble() < harmonyMemory) {
							double[] array1 = listOfPitches.get(i1);
							for (int j = 0; j < array1.length; j++) {
								if (RandomGenerator.getRandomGenerator(this)
										.nextDouble() < harmonyMemory) {
									listOfPitches.get(RandomGenerator
											.getRandomGenerator(this).nextInt(
													listOfPitches.size()))[j] = weights[j];
								}
							}
						}
					}
					int z = listOfPitches.get(i1).length;
					for (int j = 0; j < listOfPitches.get(i1).length; j++) {
						if (listOfPitches.get(i1)[j] == 0) {
							z--;
						}
					}
					if (z == 0) {
						int el = RandomGenerator.getRandomGenerator(this)
								.nextInt(listOfPitches.get(i1).length);
						listOfPitches.get(i1)[el] = 1;
					}
				}
				AttributeWeightedExampleSet r = createWeightedExampleSet(
						listOfPitches.get(i1)).createCleanClone();

				exampleSetInnerSource.deliver(r);
				inputExtender.passDataThrough();
				getSubprocess(0).execute();

				PerformanceVector performance = performanceInnerSink
						.getData(PerformanceVector.class);

				if (performance.getMainCriterion().getFitness() > bestPerformance
						.getMainCriterion().getFitness()) {
					bestPerformance = performance;
					result = r;
					weights = listOfPitches.get(i1);
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

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));

		return types;
	}
}
