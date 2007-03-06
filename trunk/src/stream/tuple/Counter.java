package stream.tuple;

public class Counter extends AggregationFunction<Tuple> {
	//  ... "packets" "PacketsLastEpoch" 
	public Tuple invoke(Tuple aggregate, Tuple value) {
		assertType();
		if (aggregate == null) {;
			Tuple tuple = Tuple.createTuple(tupleTypeID);
			tuple.setIntAttribute(aggregateField, 0);
			return tuple;
		} else {
			aggregate.setIntAttribute(aggregateField, 1 + aggregate.getIntAttribute(aggregateField));
			return aggregate;
		}
	}
	public Counter( String newTupleType, String resultField) {
		super(newTupleType, resultField);
	}
}
