package stream.tuple;

/**
 * calculate the difference between minimal and maximal value 
 * 
 * note: as there have been unwanted duplicates, the ratio operator also filters out 
 *       duplicate seq numbers 
 * result is a Double attribute
 */
public class Ratio extends AggregationFunction<Tuple> {
	private int tupleTypeID;
	private TupleAttribute ratioID;
	private TupleAttribute maxID;
	private TupleAttribute minID;
	private TupleAttribute seqNrID;
	private TupleAttribute countID;
	private TupleAttribute lastID;
	
	public Tuple invoke(Tuple aggregate, Tuple value) {
		if (aggregate == null) {
			aggregate = Tuple.createTuple(tupleTypeID);
			aggregate.setAttribute(ratioID, 0.0);
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
				aggregate.setAttribute(ratioID, 0f);
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
			aggregate.setAttribute(ratioID, ratio );
		}
		return aggregate;
	}
	public Ratio( String newTupleType, String seqNrField ) {
		tupleTypeID = Tuple.getTupleTypeID( newTupleType);
		ratioID = new TupleAttribute("ratio");
		seqNrID = new TupleAttribute(seqNrField);
		countID = new TupleAttribute("count");
		minID = new TupleAttribute("min");
		maxID = new TupleAttribute("max");
		lastID = new TupleAttribute("last");
	}
}
