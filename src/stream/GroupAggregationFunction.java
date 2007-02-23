package stream;

/**
 * An aggregation-function is a binary function that computes an aggregate. The
 * function is called with the last aggregate and the next value that should be
 * added to the aggregate. Calling the function with an aggregate set to
 * <code>null</code> initializes the aggregation-function, i.e., the function
 * acts as it is called the very first time.
 * 
 * @param <P> the type of the aggregated values.
 * @param <A> the return type of the function, i.e., the type of the aggregate.
 * @param <K> the group id
 */

public abstract class GroupAggregationFunction<P, K, A> {
	public abstract A invoke(A aggregate, K id, P value);
}
