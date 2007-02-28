package stream.tuple;

import java.util.HashMap;

public class TreeAttributePredicate extends TreePredicate {

	private Tuple tuple; 
	private String tupleType;
	private TupleAttribute attribute;
	private Comparator comparator;
	private FloatComparator comparator2;
	private int value;
	private float value2;
	
	public abstract interface IntComparator {
		public abstract boolean invoke (int a, int b);
	}
	
	public enum Comparator implements IntComparator {
		
		equal {
			public boolean invoke (int a, int b) {
				return a == b;
			}
		},
		notEqual {
			public boolean invoke (int a, int b) {
				return a != b;
			}
		}
		, less {
			public boolean invoke (int a, int b) {
				return a < b;
			}
		},
		lessOrEqual {
			public boolean invoke (int a, int b) {
				return a <= b;
			}
		},

		greater {
			public boolean invoke (int a, int b) {
				return a > b;
			}
		},
		greaterOrEqual {
			public boolean invoke (int a, int b) {
				return a >= b;
			}
		},
	}

	public abstract interface FloatComparator {
		public abstract boolean invoke (float a, float b);
	}
	
	public enum Comparator2 implements FloatComparator {
		
		equal {
			public boolean invoke (float a, float b) {
				return a == b;
			}
		},
		notEqual {
			public boolean invoke (float a, float b) {
				return a != b;
			}
		}
		, less {
			public boolean invoke (float a, float b) {
				return a < b;
			}
		},
		lessOrEqual {
			public boolean invoke (float a, float b) {
				return a <= b;
			}
		},

		greater {
			public boolean invoke (float a, float b) {
				return a > b;
			}
		},
		greaterOrEqual {
			public boolean invoke (float a, float b) {
				return a >= b;
			}
		},
	}

	@Override
	public boolean canEvalute(HashMap<Object, Tuple> input) {
		tuple =  input.get(tupleType);
//		if (tuple == null) {
//			System.out.println("Treepredicate: cannot missing " + tupleType);
//		}
		return tuple != null;
	}

	@Override
	/**
	 * assert canEvaluate is called before invoke
	 */
	public boolean invoke(HashMap<Object, Tuple> input) {
		if (comparator != null) {
			int currValue = tuple.getIntAttribute(attribute);
			return comparator.invoke( currValue, value );
		} 
		if (comparator2 != null) {
			Object value = tuple.getAttribute(attribute);
			float currValue = 0.0f;
			if (value instanceof Float) {
				currValue = (Float) value;
			} else if (value instanceof Double ) {
				currValue = (float) (double) (Double) value;
			}
			return comparator2.invoke( currValue, value2 );
		} 
		return false;
	}

	/**
	 * @param input
	 * @param tupleType
	 * @param attribute
	 * @param value
	 */
	public TreeAttributePredicate(String tupleType,  String attribute, Comparator comparator,  int value) {
		this.tupleType = tupleType;
		this.attribute = new TupleAttribute( attribute );
		this.comparator = comparator;
		this.value = value;
	}
	public TreeAttributePredicate(String tupleType,  String attribute, Comparator2 comparator,  float value) {
		this.tupleType = tupleType;
		this.attribute = new TupleAttribute( attribute );
		this.comparator2 = comparator;
		this.value2 = value;
	}

}
