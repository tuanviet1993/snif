package stream;

import java.util.HashMap;
import java.util.LinkedList;

public class TimeWindowGroupAggregator<I, K, O> extends AbstractPipe<I, O> implements TimeTriggered {

	protected int timewindow;
	protected Function<I,? extends K> grouper;
	protected GroupAggregationFunction<I,K,O> aggregator;
	protected LinkedList<TimeStampedObject<I>> window = new LinkedList<TimeStampedObject<I>>();
	protected LinkedList<TimeStampedObject<K>> initList = new LinkedList<TimeStampedObject<K>>();
	protected HashMap<K, Object> registeredGroups = new HashMap<K, Object>();

	public void handleTimerEvent(long timestamp) {
		// check entries for removal
		while (window.size() > 0 && window.getFirst().timestamp + timewindow <= timestamp) {
			K gId = grouper.invoke( window.getFirst().object );
			window.removeFirst();
			aggregateOverList(timestamp, gId);
		}
		// handle init timeouts for registered groupds
		while (initList.size()>0 && initList.getFirst().timestamp + timewindow <= timestamp) {
			K gID = initList.removeFirst().object;
			aggregateOverList( timestamp, gID);
		}
	}
	
	/**
	 * Register group explicitly with operator to receive an aggregate even if no other items are processed
	 * @param gID
	 * @param timestamp
	 */
	public void registerGroup(K gID, long timestamp ) {
		if (!registeredGroups.containsKey(gID)) {
			registeredGroups.put(gID, null);
			initList.addLast(new TimeStampedObject<K>(timestamp, gID));
			Scheduler.getInstance().registerTimeout(timestamp + timewindow,
					this);
		}
	}

	public void process(I o, int srcID, long timestamp) {
		// add to list
		window.addLast(new TimeStampedObject<I>(timestamp,o));
		// get group id
		K gID = grouper.invoke( o );
		// assert group is registered
		if (!registeredGroups.containsKey(gID)) {
			registeredGroups.put( gID, null);
		}
		// aggregate
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
