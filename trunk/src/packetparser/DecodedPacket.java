package packetparser;

public class DecodedPacket {

    // String type;
    // Vector attributes = new Vector();
	PacketTemplate template;
	byte rawData[];
	int hashCode;
	
    DecodedPacket(byte rawData[], PacketTemplate template){
    	this.rawData = rawData;
    	this.template = template;
    	calcHash();
    }
    
    DecodedPacket( DecodedPacket other){
    	rawData = new byte[other.rawData.length];
    	System.arraycopy(other.rawData, 0, rawData, 0, other.rawData.length);
    	template = other.template;
    	calcHash();
    }
    
    private void calcHash() {
    	StringBuffer content = new StringBuffer();
    	for (int i = 0; i < rawData.length; i++) {
    		content.append( ".");
    		content.append( rawData[i]);
    	}
    	hashCode = content.toString().hashCode();
    	if (template != null) {
    		hashCode ^= template.hashCode();
    	}
    }
    
    public boolean equals(Object obj) {
    	if (! (obj instanceof DecodedPacket)) return false;
    	DecodedPacket packet = (DecodedPacket) obj;
    	return packet.hashCode == hashCode;
    }
    
    public int hashCode() {
    	return hashCode;
    }
    
    public int getByte( int offset) {
    	if (rawData[offset] >= 0) return rawData[offset];
    	return rawData[offset]+256;
    }
    
    public  Integer getIntAttribute( String attribute) {
    	Integer result = getIntAttribute( template, 0, attribute);
    	if (result == null) {
    		System.out.println("Cannot get attribute "+attribute + " for type " + template);
    		System.exit(10);
    	}
    	return result;
    }
    
    private Integer getIntAttribute(PacketTemplate type, int offset, String attribute) {

    	// TODO match "(TypeName)" 

    	boolean arrayAccess = false;
    	boolean structAccess = false;

    	int dotPos = attribute.indexOf("."); 
    	int arrayPos = attribute.indexOf("[");
    	
    	String field = attribute;
    	String rest = "";
    	String arrayIdx = "";
    	
    	if ( dotPos >= 0 && arrayPos >= 0) {
    		if (dotPos < arrayPos) {
    			structAccess = true;
        		field = attribute.substring( 0, dotPos);
        		rest = attribute.substring( dotPos+1);
    		} else {
    			arrayAccess = true;
    			structAccess = true;
        		field = attribute.substring(0, arrayPos);
        		arrayIdx = attribute.substring( arrayPos+1, dotPos - 1);
        		rest = attribute.substring( dotPos+1);
    		}
    	} else if ( dotPos >= 0) {
    		structAccess = true;
    		field = attribute.substring( 0,dotPos);
    		rest = attribute.substring( dotPos+1);
    	} else if ( arrayPos >= 0) {
    		arrayAccess = true;
    		field = attribute.substring( 0,arrayPos);
    		arrayIdx = attribute.substring( arrayPos+1);
    		rest = "";
    	}
    	
    	if (structAccess) {
    		if (field.equals( type.typeName )) {
    			return getIntAttribute( type, offset, rest);
    		}
    		if ( type.isInstanceOf(field))
    			return getIntAttribute( type.getSuper(), offset, attribute);
    	}

		for (Object attObj : type.attributes) {
			Attribute att = (Attribute) attObj;
			if (att.name.equals(field)) {
				if (arrayAccess) {
					offset += Integer.parseInt( arrayIdx) * att.type.size;
				}
				if (structAccess) {
					if ( rest.equals("length")) {
						if (att.elements >= 0) {
							return att.elements;
						}
						if (att.elements == PacketTemplate.variableSizedDirect) {
							return template.getInt( rawData, offset + type.lengthPos, type.lengthField.type.size, TypeSpecifier.littleEndian);
						}
						if (att.elements == PacketTemplate.variableSizedIndirect) {
							// get total (sub-)struct size
							int structSize = getIntAttribute( type.getSuper(), 0, type.expands.name+".length" );
							return ( structSize - type.packetSize ) / att.type.size;
						}
					}
					return getIntAttribute( (PacketTemplate) att.type, offset + att.offset, rest );
				}
				// check for single variable sized component
				if (type.fixedLength == false && att.offset > type.lengthPos){
					offset += type.getInt( rawData, offset + type.lengthPos, type.lengthField.type.size, TypeSpecifier.littleEndian);
				}
		    	return template.getInt( rawData, offset + att.offset, att.type.size, TypeSpecifier.littleEndian);
			}
		}
    	return null;
    }
    
	/**
	 * Prefix a number and append to buffer
	 * @param buffer
	 * @param chars
	 * @param hex
	 */
	private void appendHex( StringBuffer buffer, int chars, String hex) {
		for (int i=0; i < chars-hex.length(); i++) {
			buffer.append("0");
		}
		buffer.append(hex);
	}

	/**
	 * @param result
	 */
	private void appendData(StringBuffer result) {
		result.append( "\n");
		int offset = 0; 
		if (rawData != null && rawData.length > 0 ) {
			while (offset < rawData.length) {
				result.append("    ");
				appendHex(result, 4, ""+offset);
				result.append(":");
				int count = rawData.length - offset;
				if (count > 16) { count = 16; };
				for (int i = 0; i<count; i++) {
					result.append(" ");
					appendHex( result, 2, Integer.toHexString(getByte(offset++)));
				}
				result.append("\n");
			}
		}
	}
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	appendData( result );
    	return template.typeName + " " + result.toString();
    }

	public boolean exists(String attribute) {
		return  getIntAttribute(template, 0, attribute) != null;
	}
	
	public byte [] getRaw(){
		return rawData;
	}
	
	public static void main (String args[]) {
		Integer aInteger = 17;
		Integer bInteger = 17;
		
		byte aData[] =  { 1,2,3 };
		byte bData[] =  { 1,2,3 };
		String aString = "abcde";
		String bString = "abcde";
		
		DecodedPacket aPacket = new DecodedPacket( aData, null);
		DecodedPacket bPacket = new DecodedPacket( bData, null);
		System.out.println(" aPacket == bPacket " + aPacket.equals( bPacket) );
		DecodedPacket cPacket = new DecodedPacket( bPacket );
		System.out.println(" b = c " + bPacket.equals( cPacket) );
		System.out.println(" aData "+aData.hashCode() );
		System.out.println(" bData "+bData.hashCode() );
		System.out.println(" aData == bData "+aData.equals(bData) );
		System.out.println(" aData "+aData );
		System.out.println(" bData "+bData );
		System.out.println(" aPacket "+aPacket.hashCode() );
		System.out.println(" bPacket "+bPacket.hashCode() );
		System.out.println(" aString "+aString.hashCode() );
		System.out.println(" bString "+bString.hashCode() );
		System.out.println(" aInteger "+aInteger.hashCode() );
		System.out.println(" bInteger "+bInteger.hashCode() );
	}

	public int getLength() {
		return rawData.length;
	}
}
