package stream.tuple;

import packetparser.DecodedPacket;
import stream.ITimeStampedObject;


public class PacketTuple extends Tuple implements ITimeStampedObject {

	DecodedPacket packet; 
	long time_ms;
	String dsnNode;
	
	public PacketTuple(DecodedPacket decodedPacket, long timestamp) {
		this.packet = decodedPacket;
		this.time_ms = timestamp;
	}
	
	public boolean exists( String attribute) {
		return packet.exists( attribute);
	}
	
	public boolean exists( int attributeID) {
		String attributeName = attributeList.get(attributeID);
		Object result = packet.getIntAttribute( attributeName);
		if (result == null) return false;
		if (!(result instanceof String)) return true;
		return ! ((String) result).startsWith("Attribute");
	}
	
	public Object getAttribute(TupleAttribute attribute) {
		return getAttribute( attribute.getName());
	}

/*	public Object getAttribute(int attributeID) {
		return packet.getIntAttribute( attributeList.get( attributeID));
	}
*/
	public  int getIntAttribute(String attributeID) {
		return (Integer) packet.getIntAttribute( attributeID);
	}
	
	public  int getIntAttribute(int attributeID) {
		return (Integer) packet.getIntAttribute( attributeList.get( attributeID));
	}

	public  Object getAttribute(String attributeID) {
		return packet.getIntAttribute(attributeID);
	}

	public  String getStringAttribute(String attributeID) {
		return "" + packet.getIntAttribute(  attributeID);
	}
	public  String getStringAttribute(int attributeID) {
		return "" + packet.getIntAttribute( attributeList.get( attributeID));
	}

	
	public int getIntAttribute(TupleAttribute attribute) {
		return (Integer) packet.getIntAttribute(attribute.getName());
	}
	
	public String toString() {
		return packet.toString();
	}
	public boolean equals( Object obj) {
		if (! (obj instanceof PacketTuple)) return false;
		return packet.equals( ((PacketTuple) obj).packet);
	}
	public int hashCode () {
		return packet.hashCode();
	}

	public long getTime() {
		return time_ms;
	}

	public long setTime(long newTime) {
		return time_ms = newTime;
	}

	public String getDsnNode() {
		return dsnNode;
	}

	public void setDsnNode(String dsnNode) {
		this.dsnNode = dsnNode;
	}

	public DecodedPacket getPacket(){
		return packet;
	}
	
	public byte [] getRaw(){
		return packet.getRaw();
	}
}
