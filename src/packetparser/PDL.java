/**
 * 
 */
package packetparser;

/**
 * @todo: make usable from outside
 * @todo: determine packet type
 * @todo: parse byte stream, [requires condition processing]
 * @todo: conditions
 * @todo: expressions
 * @todo: struct array
 */
import java.util.*;

/**
 * @author mringwal
 * 
 */
public class PDL {

	// show verbose parsing info
	boolean verbose = false;

	// Hastable for storing typedef types
	Hashtable<String,TypeSpecifier> types = new Hashtable<String,TypeSpecifier>();

	// Hastable for storing struct definitions
	Hashtable<String,PacketTemplate> structs = new Hashtable<String,PacketTemplate>();

	// Hastable for storing values
	Hashtable<String,Object> values = new Hashtable<String,Object>();
	
	// parser instance
	static Parser parser = null;

	// Returns true if the given string is
	// a typedef type.
	boolean isType(String type) {

		if (types.get(type) != null) {
			return true;
		}
		return false;
	}

	// Returns true if the given string is
	// a struct.
	boolean isStruct(String type) {
		if (structs.get(type) != null) {
			return true;
		}
		return false;
	}

	// Add a typedef
	void addType(String type, TypeSpecifier typeDefinition) {
		// System.out.println( "Parser.add(" + type + ")" );
		types.put(type, typeDefinition);
	}

	// Add a struct
	void addStruct(String type, PacketTemplate structDefinition) {
		// add link to parent struct
		if (structDefinition.parent != null) {
			structDefinition.parent.extensions.add(structDefinition);
		}
		structs.put(type, structDefinition);
	}

	// Add a value
	void addValue(String name, Object value) {
		values.put(name, value);
	}

	// Prints out all the variables
	void printValues() {
		System.out.println("\n=== Constants ===");
		Enumeration myEnum = values.keys();
		while (myEnum.hasMoreElements()) {
			Object valueName = myEnum.nextElement();
			String value = (String) values.get(valueName);
			System.out.println(valueName.toString() + "=" + value);
		}
	}

	// Prints out all the types used in parsing the c source
	void printTypes() {
		System.out.println("\n=== Types ===");
		Enumeration myEnum = types.keys();
		while (myEnum.hasMoreElements()) {
			Object typeName = myEnum.nextElement();
			TypeSpecifier typeObject = (TypeSpecifier) types.get(typeName);
			System.out.println(typeObject.toString());
		}
	}

	// Prints out all the tystructs used in parsing the c source
	void printStructs() {
		System.out.println("\n=== Structs ===");
		Enumeration myEnum = structs.keys();
		while (myEnum.hasMoreElements()) {
			Object typeName = myEnum.nextElement();
			PacketTemplate packetObject = (PacketTemplate) structs
					.get(typeName);
			System.out.println(packetObject.toString());
		}
	}

	// Prints out all the tystructs used in parsing the c source
	void printPackets() {
		System.out.println("\n=== Packets ===");
		Enumeration myEnum = structs.keys();
		while (myEnum.hasMoreElements()) {
			Object typeName = myEnum.nextElement();
			PacketTemplate packetObject = (PacketTemplate) structs
					.get(typeName);
			System.out.println(packetObject.toStringFlat());
		}
	}

	public PacketTemplate getDefaultPacket() {
		
		String defPackName = getStringValue("defaults.packet");
		return structs.get(defPackName);
	}

	public PhyConfig getSnifferConfig() {

		PhyConfig config = new PhyConfig();

		// assert default packet
		PacketTemplate defPack = getDefaultPacket();
		if (defPack == null) {
			String defPackName = (String) values.get("defaults.packet");
			if (defPackName != null)
				System.out.println("defaults.packet " + defPackName + " not defined");
			else {
				System.out.println("No defaults.packet set");
			}
			return null;
		}
		
		config.fixedSize = defPack.isFixedSize();
		config.packetSize = defPack.getSize();
		if (!defPack.isFixedSize() ) {
			config.lengthPos = defPack.lengthPos;
			config.lengthOffset = defPack.lengthOffset;
		}
		
		Attribute crc = defPack.getAttribute("crc");
		if (crc != null) {
			config.CRCpos = crc.offset;
			config.hasCRC = true;
			String crcString = (String) values.get("cc.crc");
			if (crcString != null) {
				if (crcString.startsWith("0x"))
					config.CRCpoly = Integer.parseInt(crcString.substring(2), 16);
				else 
					config.CRCpoly = Integer.parseInt(crcString, 10);
			}
			else {
				System.out.println("ERROR: CRC field present, but no cc.crc polyniom set");
				return null;
			}
		} else {
			config.hasCRC = false;
		}
		
		String sopString = (String) values.get("cc.sop");
		if (sopString != null) {
			if (sopString.startsWith("0x"))
				config.SOP = Integer.parseInt(sopString.substring(2), 16);
			else 
				config.SOP = Integer.parseInt(sopString, 10);
		}
		else {
			System.out.println("ERROR: No cc.sop set");
			return null;
		}

		String freqString = (String) values.get("cc.freq");
		if (freqString != null) {
			config.frequency = Integer.parseInt(freqString, 10);
		}
		else {
			System.out.println("ERROR: No cc.freq set");
		}
		return config;
	}

	// create physical packet description for Sniffer
	void printSnifferConfig() {

		PhyConfig config = getSnifferConfig();
		config.printConfig();
	}

	public PacketTemplate getPacketTemplate(String name) {
		return structs.get(name);
	}
	
	public PacketTemplate getNestedPacketTemplate( byte[] buffer, PacketTemplate packet) {
		for (PacketTemplate child : packet.extensions ) {
			// check for conditions 
			if (child.guardField == null) {
				return getNestedPacketTemplate(buffer,child);
			}
			Attribute attribute = child.guardField;
			if ( DecodedPacket.getInt(buffer, attribute.offset, attribute.type.size,
			TypeSpecifier.littleEndian) == child.guardValue) {
				// System.out.println("getNestedPacketTemplate: "+packet.typeName+" is a " + child.typeName);
				return getNestedPacketTemplate(buffer, child);
			}
		}
		return packet;
	}
	
	public PacketTemplate getPacketTemplate( byte[] buffer) {
		PacketTemplate defPacket = getDefaultPacket();
		return getNestedPacketTemplate(buffer, defPacket);
	}
	
	public DecodedPacket decodePacket( byte[] buffer) {
		PacketTemplate packet = getPacketTemplate(buffer);
		if (packet == null) return null;
		return new DecodedPacket( buffer, packet);
	}
	
	public static PDL readDescription( String path) {

		try {
			parser = new Parser(new java.io.FileInputStream(path));
		} catch (java.io.FileNotFoundException e) {
			System.out.println("File " + path + " not found.");
			return null;
		}

		PDL.addBasicTypes();
		try {
			Parser.TranslationUnit();
		} catch (ParseException e) {
			System.out.println("Encountered errors during parse.");
			System.out.println(e.getMessage());
			parser = null;
		}
		
		// set endianess (little is default)
		if ( parser.getStringValue("defaults.endianness").equalsIgnoreCase("big") ) {
			TypeSpecifier.setEndianess( false );
		}
		return parser;
	}

	/**
	 * 
	 */
	public void dumpDescription() {
		System.out.println("Packet Description parsed successfully.");
		printTypes();
		printStructs();
		printPackets();
		printValues();
		printSnifferConfig();
	}
	

	/**
	 * 
	 */
	static void addBasicTypes() {
		// get started with some types
		parser.types.put("byte",  new TypeSpecifier("byte",  1, 1, true));
		parser.types.put("char",  new TypeSpecifier("char",  1, 1, false));
		parser.types.put("short", new TypeSpecifier("short", 1, 2, true));
		parser.types.put("int",   new TypeSpecifier("int",   1, 2, true));
		parser.types.put("long",       new TypeSpecifier("long",  1, 4, true));
		parser.types.put("uint16_t", new TypeSpecifier("uint16_t", 1, 2, false));
		parser.types.put( "int16_t", new TypeSpecifier( "int16_t", 1, 2, true));
		parser.types.put("uint8_t",  new TypeSpecifier("uint8_t",  1, 1, false));
		parser.types.put( "int8_t",  new TypeSpecifier( "int8_t",  1, 1, true));
	}

	public int getValue(String attribute) {
		Object value = values.get(attribute);
		if (value == null) {
			System.out.println("PDL.getAttribute(\""+attribute+"\") failed");
			System.exit(10);
		}
		return Integer.parseInt((String) value);
	}
	
	public String getStringValue(String attribute) {
		Object value = values.get(attribute);
		if (value == null) {
			System.out.println("PDL.getAttribute(\""+attribute+"\") failed");
			System.exit(10);
		}
		String strValue = (String) value;
		// remote quotes, if any
		if (strValue.charAt(0) == '"' && strValue.charAt(strValue.length()-1) == '"') {
			strValue = strValue.substring(1, strValue.length() -1 );
		}
		return strValue;
	}
	
	// Run the parser
	public static void main(String args[]) {

		//  System.out.println("PDL Parser Version 0.1Alpha");
		// readDescription(args[0]);
		
		byte [] packetRaw = { 0, 0, 2, 0, 11,   0, 0,   0, 0, 0,   0, 0, 0,  0, 0, 0 };
		PDL parser = readDescription( "packetdefinitions/ewsn07.h");
		parser.dumpDescription();
		DecodedPacket packet = parser.decodePacket(packetRaw);
		System.out.print( packet );
	}
}
