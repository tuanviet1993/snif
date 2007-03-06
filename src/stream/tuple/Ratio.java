package stream.tuple;

/**
 * calculate the difference between minimal and maximal value 
 * 
 * note: as there have been unwanted duplicates, the ratio operator also filters out 
 *       duplicate seq numbers 
 * result is a Double attribute
 */
public class Ratio extends AggregationFunction<Tuple> {
	private static final String LAST = "last";
	private static final String MAX = "max";
	private static final String MIN = "min";
	private static final String COUNT = "count";
	private TupleAttribute maxID;
	private TupleAttribute minID;
	private TupleAttribute seqNrID;
	private TupleAttribute countID;
	private TupleAttribute lastID = null;
	String seqNrField;
	
	public Tuple invoke(Tuple aggregate, Tuple value) {
		assertType();
		if (aggregate == null) {
			aggregate = Tuple.createTuple(tupleTypeID);
			aggregate.setAttribute(aggregateField, 0.0);
			aggregate.setIntAttribute(countID, 0);
			aggregate.setIntAttribute(lastID, -1);
		} else {
			int aValue = (Integer) value.getAttribute(seqNrID);
			// last nr
			int lastNr = aggregate.getIntAttribute(lastID);
			if (lastNr == aValue) {
				// unwanted duplicat
				return aggregate;
			}
			// count
			int count = aggregate.getIntAttribute(countID);
			count ++;
			aggregate.setIntAttribute( countID, count);
			// update max/min
			if ( aggregate.getAttribute(maxID) == null) {
				aggregate.setIntAttribute( maxID, aValue);
				aggregate.setIntAttribute( minID, aValue);
				aggregate.setAttribute(aggregateField, 0f);
			} else {
				if ( aggregate.getIntAttribute(maxID) < aValue) {
					aggregate.setIntAttribute( maxID, aValue);
				}
				if ( aggregate.getIntAttribute(minID) > aValue) {
					aggregate.setIntAttribute( minID, aValue);
				}
			}
			// store last nr
			aggregate.setIntAttribute(lastID, aValue);
			// result: offset difference by one: {1} => 1.0, {1,2} => 1.0 
			Double ratio = ((double) count / (aggregate.getIntAttribute(maxID) - aggregate.getIntAttribute(minID) + 1.0));
			aggregate.setAttribute(aggregateField, ratio );
		}
		return aggregate;
	}
	public Ratio( String newTupleType, String seqNrField ) {
		super (newTupleType, "ratio");
		this.seqNrField = seqNrField;
	}
	protected void assertType() {
		super.assertType();
		if (lastID == null) {
			seqNrID = new TupleAttribute(seqNrField);
			countID = new TupleAttribute(COUNT);
			minID = new TupleAttribute(MIN);
			maxID = new TupleAttribute(MAX);
			lastID = new TupleAttribute(LAST);
		}
	}

	protected String[] getFields(){
		String list[] = new String [5];
		list[0] = aggregateField.getName();
		list[1] = COUNT;
		list[2] = MIN;
		list[3] = MAX;
		list[4] = LAST;
		return list;
	}
}
