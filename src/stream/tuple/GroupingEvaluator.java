package stream.tuple;

import java.util.HashMap;

import stream.Function;
import stream.GroupedEvaluator;

public class GroupingEvaluator extends
	GroupedEvaluator<Tuple, Object, Object, Tuple> {

	protected String groupField;
	protected int groupFieldID;
	protected int[] distinctFieldID;

	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupFieldID);
		}
	};
	Function<Tuple,Object> fieldsDistincter = new Function<Tuple,Object>(){
		public Object invoke(Tuple argument) {
			if (distinctFieldID.length == 1) {
				return ( "" + argument.getAttribute(distinctFieldID[0])).hashCode();
			} else {
				StringBuffer hashWord = new StringBuffer();
				for (int i = 0; i<distinctFieldID.length; i++) {
					hashWord.append( ""+ argument.getAttribute(distinctFieldID[i]));
					hashWord.append( "#" );
				}
				// System.out.println("Tuple" + argument + ". Key: "+hashWord);
				return (Integer) hashWord.toString().hashCode();
			}
		}
	};

	public GroupingEvaluator(
			String name,
			GroupTupleEvaluationFunction<Tuple> evaluator,
			String groupField, String... distinctFields){
		
		super(name);
		this.distincter = fieldsDistincter;
		this.grouper = fieldGrouper;
		this.evaluator = evaluator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
		distinctFieldID = new int[distinctFields.length];
		for (int i=0; i<distinctFields.length; i++) {
			this.distinctFieldID[i] = Tuple.getAttributeId(distinctFields[i]);
		}
	}
	
	public GroupingEvaluator(String name,
			Function<Tuple,?> distincter,
			Function<Tuple,?> grouper,
			String groupField,
			GroupTupleEvaluationFunction<Tuple> evaluator) {
		super(name);
		this.distincter = distincter;
		this.grouper    = grouper;
		this.evaluator = evaluator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
	}
	
	public GroupingEvaluator( String name,
			GroupTupleEvaluationFunction<Tuple> evaluator,
			Function<Tuple,? extends Object> distincter,
			String groupField ) {
		super(name);
		this.distincter = distincter;
		this.grouper    = fieldGrouper;;
		this.evaluator = evaluator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
	}

	public static GroupingEvaluator createBinaryTreeEvaluator(final BinaryDecisionTree theTree, final String groupField, final String name) {
		return new GroupingEvaluator (
				name,
				new GroupTupleEvaluationFunction<Tuple>() {
					int nodeId = Tuple.getAttributeId(groupField);
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
}
