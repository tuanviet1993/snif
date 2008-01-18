package packetparser;

public class TypeSpecifier {

	String typeName;
	int elements = 1;
	int size = 2;
	static boolean littleEndian = true;
	boolean signed = false;

	boolean isStruct() {
		return false;
	}
	
	public TypeSpecifier(){
	}

	public TypeSpecifier( String name, int elements, int size, boolean signed){
		this.typeName = name;
		this.elements = elements;
		this.size = size;
		this.signed = signed;
	}

	public String toString(){
		StringBuffer buffer = new StringBuffer();
		buffer.append("typedef ");
		if (signed == false)
			buffer.append("u");
		buffer.append("int"+ 8*size);
		buffer.append("_t");
		buffer.append(" " + typeName );
		if (elements > 1){
			buffer.append("[" + elements + "]");
		} else if (elements < 1) {
			buffer.append("[" + "VARSIZE"+"]");
		}
		buffer.append( "; // littleEndian = " + littleEndian);
		return buffer.toString();		
	}
	
	public static void setEndianess(boolean littleEndian ) {
		TypeSpecifier.littleEndian = littleEndian;
	}

	public String getTypeName() {
		return typeName;
	}
}
