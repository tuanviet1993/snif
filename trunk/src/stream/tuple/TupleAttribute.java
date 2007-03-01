package stream.tuple;

/**
 * Used to references to a particular Tuple Attribute
 * 
 * This is useful to provide different ways to refer to an attribute
 * 
 * @author mringwal
 *
 */
public class TupleAttribute {

	private int id = -1;
	private String name;
	
	/**
	 * Get ID for Attribute Name
	 * 
	 * @return ID for given attribute
	 */
	public int getID() {
		if (id<0) {
			id = Tuple.getAttributeId(name);
		}
		return id;
	}

	/**
	 * Get Attribute Name
	 * 
	 * @return ID for given attribute
	 */
	public String getName() {
		return name;
	}
	
	 /**
	  * Constructor
	  * 
	  * @param attributeName
	  */
	public TupleAttribute(String attributeName) {
		name = attributeName;
	}

	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final TupleAttribute other = (TupleAttribute) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
}
