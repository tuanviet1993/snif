package stream.tuple;

import java.util.HashMap;

import stream.Function;
import stream.GroupedEvaluator;

public class GroupingEvaluator extends
	GroupedEvaluator<Tuple, Object, Object, Tuple> {

	protected TupleAttribute groupAttribute;
	protected TupleAttribute[] distinctAttributes;

	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupAttribute);
		}
	};
	Function<Tuple,Object> fieldsDistincter = new Function<Tuple,Object>(){
		public Object invoke(Tuple argument) {
			if (distinctAttributes.length == 1) {
				return ( "" + argument.getAttribute(distinctAttributes[0])).hashCode();
			} else {
				StringBuffer hashWord = new StringBuffer();
				for (int i = 0; i<distinctAttributes.length; i++) {
					hashWord.append( ""+ argument.getAttribute(distinctAttributes[i]));
					hashWord.append( "#" );
				}
				// System.out.println("Tuple" + argument + ". Key: "+hashWord);
				return (Integer) hashWord.toString().hashCode();
			}
		}
	};

	public GroupingEvaluator(
			GroupTupleEvaluationFunction<Tuple> evaluator,
			String groupField,
			String... distinctFields){
		super();
		this.distincter = fieldsDistincter;
		this.grouper = fieldGrouper;
		this.evaluator = evaluator;
		groupAttribute = new TupleAttribute( groupField );
		distinctAttributes = new TupleAttribute[distinctFields.length];
		for (int i=0; i<distinctFields.length; i++) {
			this.distinctAttributes[i] = new TupleAttribute(distinctFields[i]);
		}
	}
	
	public GroupingEvaluator(Function<Tuple,?> distincter,
			Function<Tuple,?> grouper,
			String groupField,
			GroupTupleEvaluationFunction<Tuple> evaluator) {
		super();
		this.distincter = distincter;
		this.grouper    = grouper;
		this.evaluator = evaluator;
		groupAttribute = new TupleAttribute( groupField );
	}
	
	public GroupingEvaluator( GroupTupleEvaluationFunction<Tuple> evaluator,
			Function<Tuple,? extends Object> distincter,
			String groupField ) {
		super();
		this.distincter = distincter;
		this.grouper    = fieldGrouper;;
		this.evaluator = evaluator;
		groupAttribute = new TupleAttribute( groupField );
	}

	public static GroupingEvaluator createBinaryTreeEvaluator(final BinaryDecisionTree theTree, final String groupField, final String name) {
		// register result tuples
		registerTreeResultTuples( theTree, groupField);
		return new GroupingEvaluator (
				new GroupTupleEvaluationFunction<Tuple>() {
					TupleAttribute nodeId = new TupleAttribute(groupField);
					BinaryDecisionTree tree = theTree;
					public Tuple process(Object gID, HashMap<Object, Tuple> input) {
						Tuple tuple = tree.invoke( input );
						if (tuple != null) {
							tuple.setAttribute(nodeId, gID);
						}
						return tuple;
					}
				},
				new Function<Tuple,Object>() {
					public String invoke(Tuple metric) {
						return metric.getType();
					}
				},
				groupField
		);
	}

	private static void registerTreeResultTuples(BinaryDecisionTree theTree, String groupField) {
		String resultType = theTree.getResultType();
		if (resultType == null) {
			if (theTree.defaultPath != null) {
				registerTreeResultTuples( theTree.defaultPath, groupField );
			}
			if (theTree.truePath != null) {
				registerTreeResultTuples( theTree.truePath, groupField );
			}
			if (theTree.falsePath != null) {
				registerTreeResultTuples( theTree.falsePath, groupField );
			}
		} else {
			String[] resultAttributes = theTree.getResultAttributes();
			String [] allAttributes = new String[resultAttributes.length+1];
			allAttributes[0] = groupField;
			System.arraycopy(resultAttributes, 0, allAttributes, 1, resultAttributes.length);
			Tuple.registerTupleType ( resultType, allAttributes);
		}
	}
}
