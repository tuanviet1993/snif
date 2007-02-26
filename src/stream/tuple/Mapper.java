package stream.tuple;

import stream.AbstractPipe;

public class Mapper extends AbstractPipe<Tuple, Tuple> {

	int[] from;
	int[] to;
	int newType;
	int nrMappings;
	
	String fromString[];
	String toString[];
	
	public void process(Tuple o, int srcID, long timestamp) {
		Tuple newTuple = Tuple.createTuple(newType);
		for (int i=0; i< nrMappings ; i++) {
			newTuple.setAttribute( to[i], o.getAttribute(from[i]));
		}
		transfer( newTuple, timestamp );

	}
	public Mapper(String newType, String... mapping) {
		this.newType = Tuple.getTupleTypeID(newType);
		 nrMappings = mapping.length / 2;
		from = new int[nrMappings];
		to =   new int[nrMappings];
		fromString = new String[nrMappings];
		toString =   new String[nrMappings];
		for (int i = 0; i < nrMappings; i++) {
			fromString[i] = mapping[i*2]; 
			from[i] = Tuple.getAttributeId( mapping[i*2]);
			toString[i] = mapping[i*2+1]; 
			to[i] = Tuple.getAttributeId( mapping[i*2+1]);
		}
	}
}
