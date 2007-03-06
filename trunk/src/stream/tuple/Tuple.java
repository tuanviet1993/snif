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
		// Tuple Type Name
		String name;
		// Tuple Type ID
		int    id;
		// Quick mapping from attribute id to field nr
		int id2field[];
		// Tuple Attribute 
		TupleAttribute fieldAttributes[];
		
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
	public static void registerTupleField( String attribute) {
		if (registeredAttributeNames.containsKey(attribute)) {
			return; // getAttributeId( attribute );
		}
		int fieldID = registeredAttributeNames.size();
		attributeList.add(attribute);
		registeredAttributeNames.put(attribute, fieldID);
		return; //   fieldID;
	}
	
	public static int registerTupleType( String type, String... fields) {
		if (registeredTuples.containsKey(type)) {
			// compare
			int oldTupleID = registeredTuples.get( type );
			TupleType oldTuple = tuplesList.get( oldTupleID);
			// check, if attribues match
			for (String field : fields ) {
				// check if 
				int fieldID = getAttributeId( field );
				if (oldTuple.id2field[fieldID] < 0) {
					throw new RuntimeException("Tuple "+type+" registered twice with different fields. Previous registration lacks field "+field);
				}
			}
			for (TupleAttribute attribute : oldTuple.fieldAttributes) {
				if (attribute.getName().equals("TupleType")) {
					continue;
				}
				boolean found = false;
				for (String field : fields ) {
					if (attribute.getName().equals(field)) {
						found = true;
						break;
					}
				}
				if (!found) {
					throw new RuntimeException("Tuple "+type+" registered twice with different fields. New registration lacks field "+attribute.getName());
				}
			}
		}
		// assert all fields are registered
		if (registeredAttributeNames.size() == 0) {
			registerTupleField("TupleType");
		}
		// register new tuple type
		int newTupleID = tuplesList.size();
		TupleType newType = new TupleType( type, newTupleID);
		tuplesList.add(newType);
		registeredTuples.put( type, newTupleID);

		// create prototype
		newType.fieldAttributes = new TupleAttribute[fields.length+1];
		newType.fieldAttributes[0] = new TupleAttribute("TupleType");
		int position = 1;
		for (String field : fields) {;
			registerTupleField(field);
			TupleAttribute newField = new TupleAttribute( field );
			newType.fieldAttributes[position] = newField;
			position++;
		}
		newType.id2field = new int[attributeList.size()];
		for (int i = 0; i< newType.id2field.length; i++ ) {
			newType.id2field[i]=-1;
		}
		for (int i = 0; i<newType.fieldAttributes.length; i++) {
			newType.id2field[newType.fieldAttributes[i].getID()] = i;
		}
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
		newTuple.values = new Object[ prototype.fieldAttributes.length];
		newTuple.values[0] = prototype.name;
		newTuple.prototype = prototype;
		return newTuple;
	}
	
	protected static int getAttributeId(String attributeName) {
		Integer fieldID = registeredAttributeNames.get(attributeName);
		if (fieldID == null) {
			throw new RuntimeException("TupleField "+attributeName+" not registered");
		}
		return fieldID;
	}

	public Object getAttribute(TupleAttribute attribute) {
		return values[prototype.id2field[attribute.getID()]];
	}
	
	public int getIntAttribute(TupleAttribute aggregateAttribute) {
		return (Integer) values[prototype.id2field[aggregateAttribute.getID()]];
	}

	public String getStringAttribute(TupleAttribute aggregateAttribute) {
		return (String) values[prototype.id2field[aggregateAttribute.getID()]];
	}
	

	public  void setAttribute(TupleAttribute attribute, Object value) {
		values[prototype.id2field[attribute.getID()]] = value;
	}

	public void setIntAttribute(TupleAttribute aggregateAttribute, int i) {
		values[prototype.id2field[aggregateAttribute.getID()]] = new Integer(i);
	}

	public void setStringAttribute(TupleAttribute attribute, String value) {
		values[prototype.id2field[attribute.getID()]] = value;
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
		for (int i = 0; i < prototype.fieldAttributes.length; i++) {
			if (!first)
				result.append(", ");
			result.append( prototype.fieldAttributes[i].getName());
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
		idTuple.values[idTuple.prototype.id2field[nodeIDAttrID]] = new Integer(20);
		metricTuple.values[metricTuple.prototype.id2field[nodeIDAttrID]] = new Integer(10);
		System.out.println("idTuple: " + idTuple);
		System.out.println("metricTuple: " + metricTuple);
	}
}
