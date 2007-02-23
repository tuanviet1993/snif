package stream.tuple;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Tuple interface
 * 
 * Tuples always have a type. Type-safe access is provided by different accessor methods
 * To create a tuple a factory-method is used
 * @author mringwal
 */
public class Tuple {


	static class TupleType {
		String name;
		int    id;
		int fields[];
		int id2field[];
		String fieldNames[];
		
		/**
		 * @param name
		 * @param id
		 * @param fields
		 */
		public TupleType(String name, int id) {
			this.name = name;
			this.id = id;
		}
	}
	static HashMap<String, Integer> registeredTuples = new HashMap<String,Integer>();  
	static HashMap<String, Integer> registeredAttributeNames = new HashMap<String,Integer>();;
	static ArrayList<TupleType> tuplesList = new ArrayList<TupleType>();
	public static ArrayList<String> attributeList = new ArrayList<String>();
	
	TupleType prototype;
	int tupleTypeId;
	Object values[];
	
	/**
	 * can be calles multiple times
	 * @param attribute
	 * @return
	 */
	public static int registerTupleField( String attribute) {
		if (registeredAttributeNames.containsKey(attribute)) {
			return getAttributeId( attribute );
		}
		int fieldID = registeredAttributeNames.size();
		attributeList.add(attribute);
		registeredAttributeNames.put(attribute, fieldID);
		return  fieldID;
	}
	
	public static int registerTupleType( String type, String... fields) {
		if (registeredTuples.containsKey(type)) {
			throw new RuntimeException("Tuple "+type+" registered twice");
		}
		if (registeredAttributeNames.size() == 0) {
			registerTupleField("TupleType");
		}
		int newTupleID = tuplesList.size();
		TupleType newType = new TupleType( type, newTupleID);
		String newFields[] = new String[fields.length+1];
		newFields[0] = "TupleType";
		System.arraycopy(fields, 0, newFields, 1, fields.length);
		newType.fields = new int[newFields.length];
		// assure field names exist
		for (String field : newFields) {
			Integer fieldID = registeredAttributeNames.get(field);
			if (fieldID == null) {
				fieldID = registeredAttributeNames.size();
				attributeList.add(field);
				registeredAttributeNames.put(field, fieldID);
			}
		}
		// enter
		newType.fieldNames = newFields;
		newType.id2field = new int[attributeList.size()];
		for (int i = 0; i< newType.id2field.length; i++ ) {
			newType.id2field[i]=-1;
		}
		for (int i = 0; i < newFields.length;i++) {
			Integer fieldID = registeredAttributeNames.get(newFields[i]);
			newType.fields[i] = fieldID;
			newType.id2field[fieldID] = i;
		}
		tuplesList.add(newType);
		registeredTuples.put( type, newTupleID);
		return newTupleID;
	}
	
	public static int  getTupleTypeID( String type) {
		Integer typeID = registeredTuples.get( type );
		if (typeID == null) {
			throw new RuntimeException("TupleType "+type+" not registered");
		}
		return typeID;
	}
	
	public static Tuple createTuple(String type) {
		return createTuple( getTupleTypeID( type));
	}
	
	public static Tuple createTuple(int typeID) {
		TupleType prototype = tuplesList.get( typeID);
		Tuple newTuple = new Tuple();
		newTuple.tupleTypeId = typeID;
		newTuple.values = new Object[ prototype.fields.length];
		newTuple.values[0] = prototype.name;
		newTuple.prototype = prototype;
		return newTuple;
	}
	
	public static int getAttributeId(String attributeName) {
		Integer fieldID = registeredAttributeNames.get(attributeName);
		if (fieldID == null) {
			throw new RuntimeException("TupleField "+attributeName+" not registered");
		}
		return fieldID;
	}

	public  Object getAttribute(int attributeID) {
		return values[prototype.id2field[attributeID]];
	}
	public  int getIntAttribute(int attributeID) {
		return (Integer) values[prototype.id2field[attributeID]];
	}
	public  int getIntAttribute(String attributeID) {
		return (Integer) values[prototype.id2field[Tuple.getAttributeId(attributeID)]];
	}
	public  Object getAttribute(String attributeID) {
		return values[prototype.id2field[Tuple.getAttributeId(attributeID)]];
	}
	public  String getStringAttribute(int attributeID) {
		return (String) values[prototype.id2field[attributeID]];
	}
	
	public  void 	setAttribute(int attributeID, Object value) {
		values[prototype.id2field[attributeID]] = value;
	}
	public  void    setIntAttribute(int attributeID, int value) {
		values[prototype.id2field[attributeID]] = new Integer(value);
	}
	public  void 	setStringAttribute(int attributeID, String value) {
		values[prototype.id2field[attributeID]] = value;
	}
	public String getType() {
		return prototype.name;
	}
	
	public TupleType getPrototype() {
		return prototype;
	}
	
	public String toString() {
		StringBuffer result = new StringBuffer();
//		result.append( getType() );
		result.append(" { ");
		boolean first = true;
		for (int i = 0; i < prototype.fields.length; i++) {
			if (!first)
				result.append(", ");
			result.append( attributeList.get( prototype.fields[i]));
			result.append(" = ");
			result.append( values[i] );
			first = false;
		}
		result.append(" }");
		return result.toString();
	}
	
	public static void main( String args[]) {
		registerTupleType( "IDTuple", "nodeID" );
		int metricType = registerTupleType( "MetricTuple", "value", "nodeID" );
		Tuple idTuple = createTuple( "IDTuple");
		Tuple metricTuple = createTuple( metricType );
		int nodeIDAttrID = getAttributeId("nodeID");
		idTuple.setIntAttribute(nodeIDAttrID, 20);
		metricTuple.setIntAttribute(nodeIDAttrID, 10);
		System.out.println("idTuple: " + idTuple);
		System.out.println("metricTuple: " + metricTuple);
	}
}
