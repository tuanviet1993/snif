package stream.tuple;

import java.util.HashMap;

public class BinaryDecisionTree {

	private BinaryDecisionTree truePath;

	private BinaryDecisionTree falsePath;

	private BinaryDecisionTree defaultPath = null;
	
	protected TreePredicate treePredicate;

	public Tuple invoke(HashMap<Object, Tuple> input) {
		if (treePredicate.canEvalute(input)) {
			if (treePredicate.invoke(input)) {
				return truePath.invoke(input);
			} else {
				return falsePath.invoke(input);
			}
		} else {
			if (defaultPath == null) {
				return null;
			}
			return defaultPath.invoke(input);
		}
	}

	public BinaryDecisionTree() {
	}

	public BinaryDecisionTree(TreePredicate predicate) {
		this.treePredicate = predicate;
	}

	public BinaryDecisionTree(BinaryDecisionTree truePath,
			BinaryDecisionTree falsePath, TreePredicate predicate) {
		this.truePath = truePath;
		this.falsePath = falsePath;
	}

	public void setTrue(BinaryDecisionTree truePath) {
		this.truePath = truePath;
	}

	public void setFalse(BinaryDecisionTree falsePath) {
		this.falsePath = falsePath;
	}

	public void setDefault(BinaryDecisionTree defaultPath) {
		this.defaultPath = defaultPath;
	}

	public static BinaryDecisionTree createTupleResultNode(final String string) {
		return 	new BinaryDecisionTree () {
			public Tuple invoke( HashMap<Object,Tuple> input) {
				return Tuple.createTuple(string);
			}
		};
	}
}
