package stream;

import java.util.LinkedList;

public class TimeWindowGroupAggregator<I, K, O> extends AbstractPipe<I, O> implements TimeTriggered {

	protected int timewindow;
	protected Function<I,? extends K> grouper;
	protected GroupAggregationFunction<I,K,O> aggregator;
	protected LinkedList<TimeStampedObject<I>> window = new LinkedList<TimeStampedObject<I>>();
	
	public void handleTimerEvent(long timestamp) {
		// check entries for removal
		while (window.size() > 0 && window.getFirst().timestamp + timewindow <= timestamp) {
			K gId = grouper.invoke( window.getFirst().object );
			window.removeFirst();
			aggregateOverList(timestamp, gId);
		}
	}
	
	public void process(I o, int srcID, long timestamp) {
		// add to list
		window.addLast(new TimeStampedObject<I>(timestamp,o));
		// get group id
		K gID = grouper.invoke( o );
		aggregateOverList(timestamp, gID);
		// register timeout
		Scheduler.getInstance().registerTimeout( timestamp + timewindow, this );
	}

	/**
	 * @param o
	 * @param timestamp
	 * @param gID
	 */
	protected void aggregateOverList(long timestamp, K gID) {
		// aggregate over group id
		O aggregate = aggregator.invoke( null, gID, null);
		for (TimeStampedObject<I> element : window) {
			if (grouper.invoke(element.object).equals(gID) ) {
				aggregate = aggregator.invoke( aggregate, gID, element.object);
			}
		}
		transfer( aggregate, timestamp);
	}

	public TimeWindowGroupAggregator ( int timewindow, Function<I, ? extends K> grouper,
			GroupAggregationFunction<I,K,O> aggregator){
		this.timewindow = timewindow;
		this.grouper    = grouper;
		this.aggregator = aggregator;
	}

	protected TimeWindowGroupAggregator() {
	}
}
