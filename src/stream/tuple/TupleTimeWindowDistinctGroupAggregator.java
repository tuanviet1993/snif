package stream.tuple;

import java.util.Iterator;
import java.util.Set;

import stream.Function;
import stream.TimeStampedObject;
import stream.TimeWindowDistinctGroupAggregator;


public class TupleTimeWindowDistinctGroupAggregator extends
TimeWindowDistinctGroupAggregator<Tuple, Object, Object, Tuple> {

	protected AggregationFunction<Tuple> aggregator;
	protected TupleAttribute groupFieldID;
	protected TupleAttribute[] distinctFields;
	
	Function<Tuple,Object> fieldGrouper = new Function<Tuple,Object>() {
		public Object invoke(Tuple argument) {
			return argument.getAttribute(groupFieldID);
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
		this.groupFieldID = new TupleAttribute( groupField);
		
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
		this.groupFieldID = new TupleAttribute( groupField);
		
		registerType();
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

	protected void registerType() {
		String[] aggregateFields = aggregator.getFields();
		String tupleType = aggregator.getTupleType();
		String [] allAttributes = new String[aggregateFields.length+1];
		allAttributes[0] = groupFieldID.getName();
		System.arraycopy(aggregateFields, 0, allAttributes, 1, aggregateFields.length);
		Tuple.registerTupleType(tupleType, allAttributes);
	}
}
