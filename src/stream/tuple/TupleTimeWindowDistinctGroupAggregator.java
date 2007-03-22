package stream.tuple;

import java.util.Iterator;
import java.util.Set;

import stream.Function;
import stream.TimeStampedObject;
import stream.TimeWindowDistinctGroupAggregator;

/**
 * Time window operator. DISTINC + GROUP + AGGREGATE
 * 
 * A set of tuple attributes are specified: no two tuples with the same values are stored
 * A groupID tuple with the single attribute "groupID" can be used to assert that an empty
 * aggregate is emitted after time window time
 * 
 * @author mringwal
 *
 */

public class TupleTimeWindowDistinctGroupAggregator extends
TimeWindowDistinctGroupAggregator<Tuple, Object, Object, Tuple> {

	private static final String GROUPID_TUPLE_NAME = "groupID";
	private static final String GROUPID_FIELD_NAME = "groupID";

	protected AggregationFunction<Tuple> aggregator;
	protected TupleAttribute groupField;
	protected TupleAttribute[] distinctFields;
	protected TupleAttribute groupTupleGroupField;
	
	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupField);
		}
	};
	Function<Tuple,Object> fieldsDistincter = new Function<Tuple,Object>(){
		public Object invoke(Tuple argument) {
			if (distinctFields.length == 1) {
				return ( "" + argument.getAttribute(distinctFields[0])).hashCode();
			} else {
				StringBuffer hashWord = new StringBuffer();
				for (int i = 0; i<distinctFields.length; i++) {
					hashWord.append( ""+ argument.getAttribute(distinctFields[i]));
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
		
		this.distinctFields = new TupleAttribute[distinctFields.length];
		for (int i=0; i<distinctFields.length; i++) {
			this.distinctFields[i] = new TupleAttribute(distinctFields[i]);
		}

		this.distincter = fieldsDistincter;
		
		this.timewindow = timewindow;
		this.grouper = fieldGrouper;
		this.aggregator = aggregator;
		this.groupField = new TupleAttribute( groupField);
		this.groupTupleGroupField = new TupleAttribute( GROUPID_FIELD_NAME);

		registerType();
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
		this.groupField = new TupleAttribute( groupField);
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
		Set keySet = map.keySet();
		Iterator iterator = keySet.iterator();
		while (iterator.hasNext() ) {
			Object key = iterator.next();
			TimeStampedObject<Tuple> obj = map.get(key );
			if (grouper.invoke(obj.object).equals(gID) ) {
				aggregate = aggregator.invoke( aggregate, obj.object);
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
