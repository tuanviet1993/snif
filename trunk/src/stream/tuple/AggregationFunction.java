package stream.tuple;

/**
 * An aggregation-function is a binary function that computes an aggregate. The
 * function is called with the last aggregate and the next value that should be
 * added to the aggregate. Calling the function with an aggregate set to
 * <code>null</code> initializes the aggregation-function, i.e., the function
 * acts as it is called the very first time. The value is not evaluated in this case.
 * 
 * 
 * @param <P> the type of the aggregated values.
 * @param <A> the return type of the function, i.e., the type of the aggregate.
 * @param <K> the group id
 */

public abstract class AggregationFunction<P> {
	public abstract Tuple invoke(Tuple aggregate, P value);

	protected TupleAttribute aggregateField;
	protected int tupleTypeID;
	
	protected AggregationFunction ( String newTupleType, String resultField) {
		tupleTypeID = Tuple.getTupleTypeID( newTupleType);
		aggregateField = new TupleAttribute(resultField);
	}
}
