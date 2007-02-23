package stream;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

public class TimeWindowDistinctGroupAggregator<I, J, K, O> extends AbstractPipe<I, O> implements TimeTriggered {
	
	protected int timewindow;
	protected Function<I,? extends J> distincter;
	protected Function<I,? extends K> grouper;
	protected GroupAggregationFunction<I,K,O> aggregator;
	protected LinkedList<TimeStampedObject<J>> window = new LinkedList<TimeStampedObject<J>>();
	protected HashMap<J,TimeStampedObject<I>> map = new HashMap<J,TimeStampedObject<I>>();
	
	public void handleTimerEvent(long timestamp) {
		while (window.size() > 0 && window.getFirst().timestamp + timewindow <= timestamp ) {
			J key = window.removeFirst().object;
			TimeStampedObject<I> obj = map.get(key);
			if (obj == null) {
				// object for key already removed
				continue;
			}
			if (obj.timestamp + timewindow <= timestamp) {
				K gId = grouper.invoke( obj.object );
				map.remove(key);
				aggregateOverList(timestamp, gId);
			}
		}
	}
	
	public void process(I o, int srcID, long timestamp) {
		// insert into HashMap
		J distinctKey = distincter.invoke(o);
		map.put( distinctKey, new TimeStampedObject<I>(timestamp,o));
		// get group id
		K gID = grouper.invoke( o );
		aggregateOverList( timestamp, gID);
		// register timeout
		window.addLast(new TimeStampedObject<J>(timestamp,distinctKey));
		Scheduler.getInstance().registerTimeout( timestamp + timewindow, this );
	}
	
	/**
	 * @param o
	 * @param timestamp
	 * @param gID
	 */
	protected void aggregateOverList(long timestamp, K gID) {
		// aggregate over group id
		Set<J> keySet = map.keySet();
		Iterator<J> iterator = keySet.iterator();
		O aggregate = aggregator.invoke(null, gID, null);
		while (iterator.hasNext() ) {
			J key = iterator.next();
			TimeStampedObject<I> obj = map.get(key );
			if (grouper.invoke(obj.object).equals(gID) ) {
				aggregate = aggregator.invoke( aggregate, gID, obj.object);
			}
		}
		transfer( aggregate, timestamp);
		// System.out.println( aggregate );
	}

	public TimeWindowDistinctGroupAggregator (
			int timewindow,
			Function<I, ? extends J> distincter,
			Function<I, ? extends K> grouper,
			GroupAggregationFunction<I,K,O> aggregator,
			String name)
	{
		this.timewindow = timewindow;
		this.distincter = distincter;
		this.grouper    = grouper;
		this.aggregator = aggregator;
		this.name       = name;
	}
	
	protected TimeWindowDistinctGroupAggregator(String name) {
		this.name = name;
	}

	public static void main(String args[]) {
		Random rand = new Random();
		LinkedHashMap<Integer,Integer> map = new LinkedHashMap<Integer,Integer>();
		for (int i=0; i<30;i++) {
			map.put( rand.nextInt(100), i);
		}
		Set<Integer> keySet = map.keySet();
		Iterator<Integer> iterator = keySet.iterator();
		while (iterator.hasNext() ) {
			Integer key = iterator.next();
			System.out.println("Key: "+key+" value "+map.get(key));
		}
	}
}
