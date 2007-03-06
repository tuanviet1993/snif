package stream.tuple;

/** calculate the difference between minimal and maximal value */

public class MaxDiff extends AggregationFunction<Tuple> {
	private TupleAttribute maxAttr;
	private TupleAttribute minAttr;
	
	public Tuple invoke(Tuple aggregate, Tuple value) {
		int aValue = (Integer) value.getAttribute(aggregateField);
		if (aggregate == null) {
			aggregate = Tuple.createTuple(tupleTypeID);
			aggregate.setIntAttribute(aggregateField, 0);
		} else {
			if ( aggregate.getAttribute(maxAttr) == null) {
				aggregate.setIntAttribute( maxAttr, aValue);
				aggregate.setIntAttribute( minAttr, aValue);
				aggregate.setIntAttribute(aggregateField, 0);
			} else {
				if ( aggregate.getIntAttribute(maxAttr) < aValue) {
					aggregate.setIntAttribute( maxAttr, aValue);
				}
				if ( aggregate.getIntAttribute(minAttr) > aValue) {
					aggregate.setIntAttribute( minAttr, aValue);
				}
			}
			aggregate.setIntAttribute(aggregateField, aggregate.getIntAttribute(maxAttr) - aggregate.getIntAttribute(minAttr) );
		}
		return aggregate;
	}
	
	public MaxDiff( String newTupleType, String resultField) {
		super( newTupleType, resultField);
		minAttr = new TupleAttribute( "min");
		maxAttr = new TupleAttribute( "max");
	}
}
