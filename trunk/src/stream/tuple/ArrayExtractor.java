package stream.tuple;

import stream.AbstractPipe;
import stream.tuple.Tuple.TupleType;

/**
 * 
 */

public class ArrayExtractor extends AbstractPipe<Tuple, Tuple> {

	int newType;
	String sizeField;
	int arrayFieldID;
	String arrayField;
	int memberIDs[];
	String members[];
	int nrMappings;
	TupleType prototype;
	private int tupleTypeFieldID;
	
	public void process(Tuple o, int srcID, long timestamp) {
		PacketTuple packet = (PacketTuple) o;
		int nrElements = packet.getIntAttribute(sizeField);
		for (int itemNr = 0; itemNr < nrElements; itemNr++) {
			Tuple newTuple = Tuple.createTuple(newType);
			for (int i = 0; i < prototype.fields.length; i++) {
				String aField = prototype.fieldNames[i];
				int aFieldID = prototype.fields[i];
				if ( aField != sizeField &&
					 aFieldID != arrayFieldID && 
					 aFieldID != tupleTypeFieldID) {
					String field = Tuple.attributeList.get( aFieldID);
					String path = arrayField + "[" + itemNr+"]."+field;
					if ( ((PacketTuple) o).exists(path) ) {
						newTuple.setAttribute( aFieldID, packet.getAttribute( path ));
					}
					else {
						newTuple.setAttribute( aFieldID, packet.getAttribute( aFieldID ));
					}
				}
			}
			transfer( newTuple, timestamp );
		}
	}
	public ArrayExtractor(String newType, String arraySize, String arrayField, String name) {
		this.newType = Tuple.getTupleTypeID(newType);
		arrayFieldID = Tuple.getAttributeId( arrayField );
		tupleTypeFieldID = Tuple.getAttributeId( "TupleType");
		this.arrayField = arrayField;
		sizeField = arraySize;
		prototype = Tuple.createTuple(this.newType).getPrototype(); 
		this.name = name;
	}
}
