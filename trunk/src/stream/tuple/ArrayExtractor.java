package stream.tuple;

import stream.AbstractPipe;
import stream.tuple.Tuple.TupleType;

/**
 * 
 */

public class ArrayExtractor extends AbstractPipe<Tuple, Tuple> {

	int newType;
	TupleAttribute sizeField;
	TupleAttribute arrayField;
	TupleAttribute memberIDs[];
	int nrMappings;
	TupleType prototype;
	private TupleAttribute tupleTypeFieldID;
	
	public void process(Tuple o, int srcID, long timestamp) {
		PacketTuple packet = (PacketTuple) o;
		int nrElements = packet.getIntAttribute(sizeField);
		for (int itemNr = 0; itemNr < nrElements; itemNr++) {
			Tuple newTuple = Tuple.createTuple(newType);
			for (int i = 0; i < prototype.fieldAttributes.length; i++) {
				TupleAttribute aField = prototype.fieldAttributes[i];
				if ( !aField.equals(sizeField)  &&
					 !aField.equals(arrayField) && 
					 !aField.equals(tupleTypeFieldID) ) {
					String path = arrayField.getName() + "[" + itemNr+"]."+aField.getName();
					if ( ((PacketTuple) o).exists(path) ) {
						newTuple.setAttribute( aField, packet.getAttribute( path ));
					}
					else {
						newTuple.setAttribute( aField, packet.getAttribute( aField ));
					}
				}
			}
			transfer( newTuple, timestamp );
		}
	}
	public ArrayExtractor(String newType, String arraySize, String arrayField) {
		this.newType = Tuple.getTupleTypeID(newType);
		prototype = Tuple.createTuple(this.newType).getPrototype(); 
		
		tupleTypeFieldID = new TupleAttribute("TupleType");
		this.arrayField = new TupleAttribute(arrayField);
		sizeField = new TupleAttribute(arraySize);
	}
}
