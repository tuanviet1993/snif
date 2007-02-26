package stream;

import java.util.HashMap;

public class GroupedEvaluator<I, J, K, O> extends AbstractPipe<I,O> {

	private HashMap<K,HashMap<J,I>> groups = new HashMap<K,HashMap<J,I>>();
	
	protected Function<I,? extends J> distincter;
	protected Function<I,? extends K> grouper;
	protected GroupEvaluationFunction<I,J,K,O> evaluator;

	public void process(I o, int srcID, long timestamp) {
		// get group id
		K gID = grouper.invoke( o );
		HashMap<J,I> group = groups.get(gID);
		if (group == null) {
			group = new HashMap<J,I>();
			groups.put(gID, group);
		}
		// insert into HashMap
		J distinctKey = distincter.invoke(o);
		group.put( distinctKey, o);
		O result = evaluator.process(gID, group);
		if (result != null) {
			transfer( result, timestamp);
		}
	}
	
	/**
	 * @param grouper
	 * @param distincter
	 * @param evaluator
	 */
	public GroupedEvaluator(Function<I, ? extends K> grouper,
			Function<I, ? extends J> distincter, GroupEvaluationFunction<I,J,K,O> evaluator
		) {
		this.distincter = distincter;
		this.evaluator = evaluator;
		this.grouper = grouper;
	}

	protected GroupedEvaluator() {
	}
}
