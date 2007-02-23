package model;

import stream.ITimeStampedObject;

/**
 * Representation of a wireless network package
 * 
 * @author mringwal
 *
 */

public class Packet implements Comparable,  ITimeStampedObject {

	public static boolean dumpPackets = false;
	public static boolean dumpPackets() {
		return dumpPackets;
	}

	public int compareTo(Object otherObject) {
		Packet otherPacket = (Packet) otherObject;
		if (equals(otherPacket)) {
			return 0;
		}
		if (src.compareToIgnoreCase(otherPacket.src) < 0) {
			return -1;
		}
		if (src.compareToIgnoreCase(otherPacket.src) > 0) {
			return 1;
		}
		if (dst.compareToIgnoreCase(otherPacket.dst) < 0) {
			return -1;
		}
		if (dst.compareToIgnoreCase(otherPacket.dst) > 0) {
			return 1;
		}
		if (data_len < otherPacket.data_len) {
			return -1;
		}
		if (data_len > otherPacket.data_len) {
			return 1;
		}
		if (type < otherPacket.type) {
			return -1;
		}
		if (type > otherPacket.type) {
			return 1;
		}
		for (int i=0; i < data_len; i++) {
			if (data[i] < otherPacket.data[i] ) {
				return -1;
			}
			if (data[i] > otherPacket.data[i] ) {
				return 1;
			}
		}
		//  this should not happen
		System.out.println("Packet.equals() == false, but no difference \n"+this+"\n"+ otherPacket);
		System.exit(10);
		return 0;
	}

	/**
	 * Compares two packtes for equality. RSSI and time are not compared.
	 * @param otherObject
	 * @return true, if all fields but RSSI and time are equal 
	 */
	public boolean equals(Object otherObject) {
		if (otherObject == null) return false;
		Packet otherPacket = (Packet) otherObject;
		if (!src.equalsIgnoreCase(otherPacket.src)) return false;
		if (!dst.equalsIgnoreCase(otherPacket.dst)) return false;
		if (data_len != otherPacket.data_len) return false;
		if (type != otherPacket.type) return false;
		if (group != otherPacket.group) return false;
		for (int i=0; i < data_len; i++) {
			if (data[i] != otherPacket.data[i] ) return false;
		}
		return true;
	}
	
	/** 
	 * Compute hashCode of packet according to the contract: equals => hashCode 
	 * TODO data.hashCode does not return the same value for two arrays with identical content
	 */
	public int hashCode() {
		return src.hashCode() ^ dst.hashCode() ^ data_len ^ type ^ group ^ data.hashCode();
	}
	
	/**
	 * 
	 * @param packetType
	 * @return
	 */
	public boolean instanceOf(String packetType) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * Human-readable representation of packet. Time is given as offset to time_base
	 * @param time_base in ms
	 * @return
	 */
	public String toString(){
		StringBuffer result = createHeader();
		result.append( time_ms);
		appendData(result); 
		return result.toString();
	}

	/**
	 * Human-readable representation of packet. Time is given as offset to time_base
	 * @param time_base in ms
	 * @return
	 */
	public String toString(long time_base){
		StringBuffer result = createHeader();
		long simulationTime = time_ms - time_base;
		result.append( (simulationTime)/1000+"." + (simulationTime % 1000));
		appendData(result); 
		return result.toString();
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
		if (data_len > 0 && data != null ) {
			while (offset < data_len) {
				result.append("    ");
				appendHex(result, 4, ""+offset);
				result.append(":");
				int count = data_len - offset;
				if (count > 16) { count = 16; };
				for (int i = 0; i<count; i++) {
					result.append(" ");
					appendHex( result, 2, Integer.toHexString(data[offset++] ));
				}
				result.append("\n");
			}
		}
	}

	/**
	 * Get 2 byte value from data
	 */
	int getWord( int pos){
		return data[pos] + (data[pos+1] << 8);
	}
	
	/**
	 * @return
	 */
	private StringBuffer createHeader() {
		StringBuffer result = new StringBuffer ("src="+src+", dst="+dst+", type=["+type+","+group+"], data_len="+data_len+", rssi="+rssi+", time=");
		return result;
	}

	
	public byte[] getTOSmsg() {
		byte rawData[] = new byte[ data_len + 7]; // dest (2), type, group, len
		int dot;
		String dst;
		dst = this.dst;
		dot = dst.lastIndexOf(".");
		String srcLow = dst.substring(dot+1);
		dst = dst.substring(0, dot);
		dot = dst.lastIndexOf(".");
		String srcHigh = dst.substring(dot+1);
		
		rawData[0] = (byte) Integer.parseInt(srcLow);
		rawData[1] = (byte) Integer.parseInt(srcHigh);
		rawData[2] = (byte) type;
		rawData[3] = (byte) group;
		rawData[4] = (byte) data_len;
		
		for (int i = 0; i < data_len; i++) {
			rawData[5+i] = (byte) data[i];
		}
		return rawData;
	}

	/** member fields */
	public String src = "";
	public String dst = "";
	public int   data_len;
	public int   type;
	public int   group;
	public int   rssi;	// -1, if unknown
	public boolean bad = false;
	public int   data[];
	public long time_ms;
	
	public long getTime() {
		return time_ms;
	}
	
}
