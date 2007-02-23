package model;

import java.util.HashSet;

/**
 * Address of a WSN node
 *  
 * @author mringwal
 */
public class NodeAddress {
	String address;
	
	public NodeAddress(String address) {
		this.address = address;
	}
	public NodeAddress(int word) {
		this.address = convertInt(word);
	}
	public String convertInt( int word) {
		return  "0.0."+word / 256+"." + (word % 256);
	}
	public boolean equals( Object object) {
		if (object == null) return false;
		if (object instanceof Integer) {
			return address.equals( convertInt( (Integer) object ));
		}
		if (object instanceof String) {
			return address.equals( (String) object);
		}
		NodeAddress other = (NodeAddress) object;
		return address.equals( other.address);
	}
	/**
	 * get two lower bytes of node address as int
	 * 
	 * @return
	 */
	public int getInt() {
		int dot;
		String dst;
		dst = address;
		dot = dst.lastIndexOf(".");
		String srcLow = dst.substring(dot+1);
		dst = dst.substring(0, dot);
		dot = dst.lastIndexOf(".");
		String srcHigh = dst.substring(dot+1);
		return Integer.parseInt(srcHigh) * 256 + Integer.parseInt(srcLow);
	}
	
	public int hashCode() {
		int code = address.hashCode();
		return code;
	}
	
	public String toString() {
		return address;
	}
	
	public boolean isBroadcast() {
		return equals(broadcast);
	}
	
	public static final NodeAddress broadcast = new NodeAddress("255.255.255.255");
	
	public static void main(String[] args) {
		HashSet<NodeAddress> visited = new HashSet<NodeAddress>();
		NodeAddress nodeNrOne = new NodeAddress(1);
		visited.add( nodeNrOne);
		if (!visited.contains(new NodeAddress(1))) {
			System.out.println("NodeAddress:ERRROR. can not find similar element");
		}
		if (!visited.contains(nodeNrOne)) {
			System.out.println("NodeAddress:ERRROR. can not find the real element");
		}
		System.out.println("ok");
	}
}
