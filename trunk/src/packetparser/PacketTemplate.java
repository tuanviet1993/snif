package packetparser;

import java.util.Vector;

class Attribute {

	String name;

	int offset;

	int elements;

	TypeSpecifier type;

	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("  ");
		if (type.isStruct())
			result.append("struct ");
		result.append(type.typeName);
		result.append(" ");
		result.append(name);
		if (elements > 1)
			result.append("[" + elements + "] ");
		result.append("; // offset = " + offset + ", size = ");
		if (elements > 1)
			result.append(elements + " * ");
		result.append(type.size);
		result.append(", littleEndian = " + TypeSpecifier.littleEndian);
		return result.toString();
	}
}


public class PacketTemplate extends TypeSpecifier {

	/** nr of elements is directly determined by another packet field */
	public static int variableSizedDirect = -1;
	
	/** nr of elements is indirectly determined by packet size */
	public static int variableSizedIndirect = -2;
	
	/** list of attribute / fields in this struct */
	Vector<Attribute> attributes = new Vector<Attribute>();

	/** this PacketTemplate is a PDU encapsulated in another packet */
	PacketTemplate parent;

	/** it starts at the byte offset */
	int fieldOffset = 0;
	
	/** size of this struct without dynamic fields */
	int packetSize = 0;

	/** it expands this particular field in the parent template */
	Attribute expands;

	/** but only if the guardField */
	Attribute guardField;

	/** is set to a specific gaurdValue */
	int guardValue;

	/** this PacketTemplate is extended by others */
	Vector<PacketTemplate> extensions = new Vector<PacketTemplate>();

	/** variable sized packets */
	boolean fixedLength = true;

	/** attribute denoting ... */
	Attribute lengthField;
	
	int lengthPos = 0;

	int lengthOffset = 0;

	int getSize() {
		return packetSize;
	}

	boolean isFixedSize() {
		return fixedLength;
	}

	boolean isStruct() {
		return true;
	}

	boolean isInstanceOf( String name) {
		if (name.equals(typeName)) return true;
		if (parent == null) return false;
		return parent.isInstanceOf(name);
		
	}
	
	public void addAttribute(String name, TypeSpecifier type, int elements) {
		Attribute att = new Attribute();
		att.name = name;
		att.type = type;
		att.elements = elements;

		// set attribute start offset to current packet size
		att.offset = fieldOffset;

		// increase packet size
		if (att.elements >= 0) {
			packetSize += type.size * elements;
			fieldOffset += type.size * elements;
		}
		attributes.add(att);
	}

	public Attribute getAttribute(String attribute) {
		for (Object attObj : attributes) {
			Attribute att = (Attribute) attObj;
			if (att.name.equals(attribute)) {
				return att;
			}
		}
		return null;
	}
	PacketTemplate getSuper() {
		if (parent != null)
			return parent;
		return this;
	}

	public String [] getAttributeNames(){
		String [] result = new String[ attributes.size()];
		int i=0;
		for (Attribute att : this.attributes) {
			result[i++] = att.name;
		}
		return result;
	}

	public Vector<Attribute> getAttributes() {
		return attributes;
	}

	/******** toString *****/
	
	/**
	 * return PacketTemplate as C struct
	 */
	public String toString() {
		StringBuffer result = new StringBuffer();
		result.append("struct " + typeName + " ");
		if (parent != null) {
			result.append(" : " + parent.typeName + "." + expands.name );
			if (guardField != null) {
				result.append( " ( "+guardField.name + "==" + guardValue + " )");
			}
		}
		result.append("\n{\n");
		for (Attribute att : this.attributes) {
			result.append("    " + att.toString());
			result.append("\n");
		}
		result.append("};\n");
		return result.toString();
	}

	/**
	 * return PacketTemplate as list of attributes 
	 * @return
	 */
	public String toStringFlat() {
		StringBuffer result = new StringBuffer();
		result.append(typeName + " ");
		if (parent != null) {
			result.append(" : " + parent.typeName + "." + expands.name );
			if (guardField != null) {
				result.append( " ( "+guardField.name + "==" + guardValue + " )");
			}
		}
		result.append("\n");
		if (extensions.size() > 0) {
			for (PacketTemplate child : extensions) {
				result.append("Extensions: " + child.typeName + "\n");
			}
		}
		result.append(toStringFlat(""));
		return result.toString();
	}

	private String toStringFlat(String prefix) {
		StringBuffer result = new StringBuffer();
		if (parent != null) {
			result.append(parent.toStringFlatStart(prefix, expands.name));
		}
		for (Attribute att : this.attributes) {
			addFlatObject(prefix, result, att);
		}
		if (parent != null) {
			result.append(parent.toStringFlatEnd(prefix, expands.name));
		}
		return result.toString();
	}

	/**
	 * @param result
	 * @param att
	 */
	private void addFlatObject(String prefix, StringBuffer result, Attribute att) {
		if (!prefix.equals(""))
			prefix += ".";
		if (att.type instanceof PacketTemplate) {
			PacketTemplate myPacket = (PacketTemplate) att.type;
			result.append(myPacket.toStringFlat(prefix + att.name));
		} else {
			result.append(prefix + att.name + "\n");
		}
	}

	private String toStringFlatStart(String prefix, String name) {

		StringBuffer result = new StringBuffer();
		if (parent != null) {
			result.append(parent.toStringFlatStart(prefix, expands.name));
		}
		for (Attribute att : this.attributes) {
			if (att.name.equals(name))
				break;
			addFlatObject(prefix, result, att);
		}
		return result.toString();
	}

	private String toStringFlatEnd(String prefix, String name) {

		StringBuffer result = new StringBuffer();
		boolean after = false;
		for (Attribute att : this.attributes) {
			if (after) {
				addFlatObject(prefix, result, att);
			}
			if (att.name.equals(name)) {
				after = true;
			}
		}
		if (parent != null) {
			result.append(parent.toStringFlatEnd(prefix, expands.name));
		}
		return result.toString();
	}

}

