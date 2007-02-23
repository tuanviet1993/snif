package stream.tuple;

/** calculate the difference between minimal and maximal value */

public class MaxDiff extends AggregationFunction<Tuple> {
	private int aggregateField;
	private int tupleTypeID;
	private int maxID;
	private int minID;
	
	public Tuple invoke(Tuple aggregate, Tuple value) {
		int aValue = (Integer) value.getAttribute(aggregateField);
		if (aggregate == null) {
			aggregate = Tuple.createTuple(tupleTypeID);
			aggregate.setIntAttribute(aggregateField, 0);
		} else {
			if ( aggregate.getAttribute(maxID) == null) {
				aggregate.setIntAttribute( maxID, aValue);
				aggregate.setIntAttribute( minID, aValue);
				aggregate.setIntAttribute(aggregateField, 0);
			} else {
				if ( aggregate.getIntAttribute(maxID) < aValue) {
					aggregate.setIntAttribute( maxID, aValue);
				}
				if ( aggregate.getIntAttribute(minID) > aValue) {
					aggregate.setIntAttribute( minID, aValue);
				}
			}
			aggregate.setIntAttribute(aggregateField, aggregate.getIntAttribute(maxID) - aggregate.getIntAttribute(minID) );
		}
		return aggregate;
	}
	public MaxDiff( String newTupleType, String resultField) {
		tupleTypeID = Tuple.getTupleTypeID( newTupleType);
		aggregateField = Tuple.getAttributeId(resultField);
		minID = Tuple.registerTupleField("min");
		maxID = Tuple.registerTupleField("max");
	}
}
