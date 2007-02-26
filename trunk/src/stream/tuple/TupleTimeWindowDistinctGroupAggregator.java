package stream.tuple;

import java.util.Iterator;
import java.util.Set;

import stream.Function;
import stream.TimeStampedObject;
import stream.TimeWindowDistinctGroupAggregator;


public class TupleTimeWindowDistinctGroupAggregator extends
TimeWindowDistinctGroupAggregator<Tuple, Object, Object, Tuple> {

	protected AggregationFunction<Tuple> aggregator;
	protected String groupField;
	protected int groupFieldID;
	protected int[] distinctFieldID;
	
	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupFieldID);
		}
	};
	Function<Tuple,Object> fieldsDistincter = new Function<Tuple,Object>(){
		public Object invoke(Tuple argument) {
			if (distinctFieldID.length == 1) {
				return ( "" + argument.getAttribute(distinctFieldID[0])).hashCode();
			} else {
				StringBuffer hashWord = new StringBuffer();
				for (int i = 0; i<distinctFieldID.length; i++) {
					hashWord.append( ""+ argument.getAttribute(distinctFieldID[i]));
					hashWord.append( "#" );
				}
				// System.out.println("Tuple" + argument + ". Key: "+hashWord);
				return (Integer) hashWord.toString().hashCode();
			}
		}
	};

	public TupleTimeWindowDistinctGroupAggregator(
			int timewindow,
			AggregationFunction<Tuple> aggregator,
			String groupField,
			String... distinctFields ){
		
		super();
		this.timewindow = timewindow;
		this.distincter = fieldsDistincter;
		this.grouper = fieldGrouper;
		this.aggregator = aggregator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
		distinctFieldID = new int[distinctFields.length];
		for (int i=0; i<distinctFields.length; i++) {
			this.distinctFieldID[i] = Tuple.getAttributeId(distinctFields[i]);
		}
	}
	
	public TupleTimeWindowDistinctGroupAggregator(
			int timewindow,
			Function<Tuple, ? extends Object> distincter,
			Function <Tuple, ?> grouper,
			String groupField,
			AggregationFunction<Tuple> aggregator) {
		super();
		this.timewindow = timewindow;
		this.distincter = distincter;
		this.grouper    = grouper;
		this.aggregator = aggregator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
	}

	/**
	 * @param o
	 * @param timestamp
	 * @param gID
	 */
	protected void aggregateOverList(long timestamp, Object gID) {
		// aggregate over group id
		Tuple aggregate = aggregator.invoke( null, null);
		Set keySet = map.keySet();
		Iterator iterator = keySet.iterator();
		while (iterator.hasNext() ) {
			Object key = iterator.next();
			TimeStampedObject<Tuple> obj = map.get(key );
			if (grouper.invoke(obj.object).equals(gID) ) {
				aggregate = aggregator.invoke( aggregate, obj.object);
			}
		}
		aggregate.setAttribute( groupFieldID, gID);
		// System.out.println(aggregate);
		transfer( aggregate, timestamp);
	}

}
