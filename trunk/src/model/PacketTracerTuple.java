package model;


public class PacketTracerTuple {
	public NodeAddress l2src;
	public NodeAddress l2dst;
	public NodeAddress l3src;
	public NodeAddress l3dst;
	public int l3seqNr;
	public boolean retransmission;
}
