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

	PacketTemplate getSuper() {
		if (parent != null)
			return parent;
		return this;
	}

	int getInt(byte buffer[], Attribute attribute) {
		return getInt(buffer, attribute.offset, attribute.type.size,
				TypeSpecifier.littleEndian);
	}

	public int getInt(byte buffer[], int offset, int size, boolean littleEndian) {
		if (offset + size > buffer.length)
			return -1;

		int step = 1;
		if (littleEndian) {
			offset += size - 1;
			step = -1;
		}
		int value = 0;
		while (size > 0) {
			int currByte = buffer[offset];
			if (currByte < 0) {
				currByte += 256;
			}
			value = (value << 8) + currByte;
			offset += step;
			size--;
		}
		return value;
	}

	String parseByteArray(byte buffer[], int byteOffset, String prefix) {
		// parse parent (extension)
		String postfix = typeName;
		if (parent != null) {
			postfix = parent.parseByteArray(buffer, byteOffset, prefix) + "."
					+ typeName;
		}
		// add prefix 
		if (!prefix.equals(""))
			postfix = prefix + "." + postfix;

		for (Attribute att : this.attributes) {
			if (att.type instanceof PacketTemplate) {
				// ((PacketTemplate) att.type).parseByteArray( buffer, att.offset + byteOffset , prefix + "." + att.name);
				((PacketTemplate) att.type).parseByteArray(buffer, att.offset
						+ byteOffset, postfix + "." + att.name);
			} else {
				// all information available.. do something with it
				// System.out.println( prefix + "." + att.name + ": " + (byteOffset + att.offset) + ", " + (att.elements) + " * " + (att.type.size)); 
				System.out.print(postfix + "." + att.name + ": "
						+ (byteOffset + att.offset) + ", " + (att.elements)
						+ " * " + (att.type.size));
				System.out.print("{");
				for (int i = 0; i < att.elements; i++) {
					int value = getInt(buffer, byteOffset + att.offset + i
							* att.type.size, att.type.size,
							TypeSpecifier.littleEndian);
					System.out.print(" " + value);
				}
				System.out.println("}");
			}
		}
		return postfix;
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
}

