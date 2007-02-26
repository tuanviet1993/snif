package stream.tuple;

import stream.Function;
import stream.TimeStampedObject;
import stream.TimeWindowGroupAggregator;

public class TupleTimeWindowGroupAggregator extends TimeWindowGroupAggregator<Tuple,Object,Tuple> {

	protected AggregationFunction<Tuple> aggregator;
	protected String groupField;
	protected int groupFieldID;
	
	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupFieldID);
		}
	};;

	
	public TupleTimeWindowGroupAggregator(int timewindow, Function <Tuple, ?> grouper, String groupField, AggregationFunction<Tuple> aggregator, String name) {
		this.timewindow = timewindow;
		this.grouper    = grouper;
		this.aggregator = aggregator;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
	}

	public TupleTimeWindowGroupAggregator(int timewindow, String groupField, AggregationFunction<Tuple> aggregator, String name) {
		this.timewindow = timewindow;
		this.groupField = groupField;
		this.groupFieldID = Tuple.getAttributeId( groupField);
		this.aggregator = aggregator;
		this.grouper = fieldGrouper;
	}
	
	/**
	 * @param o
	 * @param timestamp
	 * @param gID
	 */
	protected void aggregateOverList(long timestamp, Object gID) {
		// aggregate over group id
		Tuple aggregate = aggregator.invoke( null, null);
		for (TimeStampedObject<Tuple> element : window) {
			if (grouper.invoke(element.object).equals(gID) ) {
				aggregate = aggregator.invoke( aggregate, element.object);
			}
		}
		aggregate.setAttribute( groupFieldID, gID);
		// System.out.println(aggregate);
		transfer( aggregate, timestamp);
	}

}
