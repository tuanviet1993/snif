package stream.tuple;

import stream.Function;
import stream.Scheduler;
import stream.TimeStampedObject;
import stream.TimeWindowGroupAggregator;

public class TupleTimeWindowGroupAggregator extends TimeWindowGroupAggregator<Tuple,Object,Tuple> {

	private static final String GROUPID_TUPLE_NAME = "groupID";
	private static final String GROUPID_FIELD_NAME = "groupID";
	
	protected AggregationFunction<Tuple> aggregator;
	protected String groupFieldName;
	protected TupleAttribute groupField;
	protected TupleAttribute groupTupleGroupField;
	
	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupField);
		}
	};;

	
	public TupleTimeWindowGroupAggregator(int timewindow, Function <Tuple, ?> grouper, String groupField, AggregationFunction<Tuple> aggregator, String name) {
		this.timewindow = timewindow;
		this.grouper    = grouper;
		this.aggregator = aggregator;
		this.groupFieldName = groupField;
		this.groupField = new TupleAttribute( groupField);
		this.groupTupleGroupField = new TupleAttribute( GROUPID_FIELD_NAME);
		registerType();
	}

	public TupleTimeWindowGroupAggregator(int timewindow, String groupField, AggregationFunction<Tuple> aggregator, String name) {
		this.timewindow = timewindow;
		this.groupFieldName = groupField;
		this.groupField = new TupleAttribute( groupField);
		this.aggregator = aggregator;
		this.grouper = fieldGrouper;
		this.groupTupleGroupField = new TupleAttribute( GROUPID_FIELD_NAME);
		registerType();
	}
	
	/**
	 *  Handle GROUPID_TUPLE_NAME tuples that contain a group id
	 */
	public void process(Tuple o, int srcID, long timestamp) {
		// check for "group" tuple
		if (o.getType() == GROUPID_TUPLE_NAME) {
			registerGroup(o.getAttribute(groupTupleGroupField), timestamp);
		} else {
			super.process( o, srcID, timestamp );
		}
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
		aggregate.setAttribute( groupField, gID);
		// System.out.println(aggregate);
		transfer( aggregate, timestamp);
	}

	protected void registerType() {
		String[] aggregateFields = aggregator.getFields();
		String tupleType = aggregator.getTupleType();
		String [] allAttributes = new String[aggregateFields.length+1];
		allAttributes[0] = groupField.getName();
		System.arraycopy(aggregateFields, 0, allAttributes, 1, aggregateFields.length);
		Tuple.registerTupleType(tupleType, allAttributes);

		// register "group" tuple with "group" field
		String[] groupTupleField = {GROUPID_FIELD_NAME};
		Tuple.registerTupleType(GROUPID_TUPLE_NAME, groupTupleField);
	}
}

