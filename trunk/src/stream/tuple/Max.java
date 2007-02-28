package stream.tuple;

public class Max extends AggregationFunction<Tuple> {
	private TupleAttribute aggregateAttribute;
	private int tupleTypeID;
	
	//  ... "packets" "PacketsLastEpoch" 
	public Tuple invoke(Tuple aggregate, Tuple value) {
		if (aggregate == null) {
			Tuple tuple = Tuple.createTuple(tupleTypeID);
			tuple.setIntAttribute(aggregateAttribute, 0);
			return tuple;
		} else {
			Math.max(aggregate.getIntAttribute(aggregateAttribute), (Integer) value.getAttribute(aggregateAttribute));
			aggregate.setIntAttribute(aggregateAttribute, 1 + aggregate.getIntAttribute(aggregateAttribute));
			return aggregate;
		}
	}
	public Max( String newTupleType, String resultField) {
		tupleTypeID = Tuple.getTupleTypeID( newTupleType);
		aggregateAttribute = new TupleAttribute( resultField);
	}
}
