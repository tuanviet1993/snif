package stream.tuple;

import java.util.HashMap;

import stream.Predicate;

public class TupleChangePredicate extends Predicate<Tuple> {
	
	HashMap<Object, Tuple> nodes = new HashMap<Object, Tuple>();
	int idFieldID;
	int compareFieldIDs[];
	int nrFields;
	
	public Tuple getTuple(Object nodeID) {
		return nodes.get( nodeID);
	}
	
	public boolean invoke(Tuple o, long timestamp) {
		Object nodeID = o.getAttribute(idFieldID);
		Tuple lastState = nodes.get( nodeID );
		boolean stateChange = false;
		if (lastState == null ) {
			stateChange = true;
		}
		else if (! lastState.getType().equals(o.getType() )) {
			stateChange = true;
		} else {
			for (int i = 0; i < nrFields; i++) {
				Object oldValue = lastState.getAttribute(compareFieldIDs[i]);
				Object newValue = o.getAttribute(compareFieldIDs[i]);
				if ( !oldValue.toString().equals( newValue.toString())) {
					stateChange = true;
					break;
				}
			}
		}
		if (stateChange) {
			nodes.put(nodeID, o);
			return true;
		}
		return false;
	}

	public TupleChangePredicate( String groupField, String... compareFields) {
		idFieldID = Tuple.getAttributeId(groupField);
		nrFields = compareFields.length;
		compareFieldIDs = new int[nrFields];
		for (int i = 0; i < nrFields; i++) {
			compareFieldIDs[i] = Tuple.getAttributeId( compareFields[i]);
		}
	}
}
