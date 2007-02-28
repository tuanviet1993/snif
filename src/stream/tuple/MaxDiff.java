package stream.tuple;

/** calculate the difference between minimal and maximal value */

public class MaxDiff extends AggregationFunction<Tuple> {
	private TupleAttribute aggregateAttribute;
	private int tupleTypeID;
	private TupleAttribute maxAttr;
	private TupleAttribute minAttr;
	
	public Tuple invoke(Tuple aggregate, Tuple value) {
		int aValue = (Integer) value.getAttribute(aggregateAttribute);
		if (aggregate == null) {
			aggregate = Tuple.createTuple(tupleTypeID);
			aggregate.setIntAttribute(aggregateAttribute, 0);
		} else {
			if ( aggregate.getAttribute(maxAttr) == null) {
				aggregate.setIntAttribute( maxAttr, aValue);
				aggregate.setIntAttribute( minAttr, aValue);
				aggregate.setIntAttribute(aggregateAttribute, 0);
			} else {
				if ( aggregate.getIntAttribute(maxAttr) < aValue) {
					aggregate.setIntAttribute( maxAttr, aValue);
				}
				if ( aggregate.getIntAttribute(minAttr) > aValue) {
					aggregate.setIntAttribute( minAttr, aValue);
				}
			}
			aggregate.setIntAttribute(aggregateAttribute, aggregate.getIntAttribute(maxAttr) - aggregate.getIntAttribute(minAttr) );
		}
		return aggregate;
	}
	
	public MaxDiff( String newTupleType, String resultField) {
		tupleTypeID = Tuple.getTupleTypeID( newTupleType);
		aggregateAttribute = new TupleAttribute(resultField);
		minAttr = new TupleAttribute( "min");
		maxAttr = new TupleAttribute( "max");
	}
}
