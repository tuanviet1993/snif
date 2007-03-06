package stream.tuple;

import java.util.HashMap;

public class BinaryDecisionTree {

	protected BinaryDecisionTree truePath;

	protected BinaryDecisionTree falsePath;

	protected BinaryDecisionTree defaultPath = null;
	
	protected TreePredicate treePredicate;

	protected String resultTuple = null;
	protected int resultTupleID = -1;
	
	public Tuple invoke(HashMap<Object, Tuple> input) {
		// result tuple?
		if (resultTuple != null) {
			if (resultTupleID < 0) {
				resultTupleID = Tuple.getTupleTypeID(resultTuple);
			}
			return Tuple.createTuple(resultTupleID);
		}
		
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
		BinaryDecisionTree resultNode = new BinaryDecisionTree();
		resultNode.resultTuple = string;
		return resultNode;
	}

	public String getResultType() {
		return resultTuple;
	}
	
	public String[] getResultAttributes() {
		return new String[0];
	}
}
