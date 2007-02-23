package model;

import java.util.ArrayList;
import java.util.HashMap;



/**
 * Trace packets under the assumption that:
 * a) each packet can be identified by layer 3: src, dst, seqNo 
 * b) the layer 2 dst is known
 * 
 * Kown issues: A missing packet leads to failures in the detection.
 * 
 * @author mringwal
 */

class TraceItem {
	/** to identify a packet */
	NodeAddress src;
	NodeAddress dst;
	int seqNo;
	
	/** meta info about packet */
	long time;
	NodeAddress l2src;
	NodeAddress l2dst;
	
	ArrayList<NodeAddress> hops = new ArrayList<NodeAddress>();
	int virtualSeqNo;
	
	/** 
	 * Compute hashCode of packet according to the contract: equals => hashCode 
	 */
	public int hashCode() {
		return (src.toString() + dst.toString() + seqNo).hashCode();
	}

	/**
	 * Test two TraceItems to be equal
	 */
	public boolean equals( Object object) {
		if (object == null) return false;
		TraceItem otherItem = (TraceItem) object;
		if (!otherItem.src.equals(src)) return false;
		if (!otherItem.dst.equals(dst)) return false;
		if ( otherItem.seqNo != seqNo ) return false;
		return true;
	}
	
	/** toString */
	public String toString() {
		return "TraceItem { "+src + ", " + dst + ", " + seqNo + ", t=" + time + " } from: "+l2src + " to: "+ l2dst;
	}
}

public class PacketTracer {
	
	
	public void tracePacket( model.PacketTracerTuple traceItem, long time) {
		
		// create example entry
		TraceItem currentPacket = new TraceItem();
		currentPacket.src = traceItem.l3src;
		currentPacket.dst = traceItem.l3dst;
		currentPacket.seqNo = traceItem.l3seqNr;
		boolean retransmission = false;
		
		// identify sender 
		TraceItem storedPacket = packets.get( currentPacket.hashCode());
		// packet new or older than itemValidTime_ms
		if (storedPacket == null || storedPacket.time + itemValidTime_ms < time) {
			// no previous infomation stored, assume it is new and use l3src
			currentPacket.l2src = traceItem.l3src;
			currentPacket.l2dst = traceItem.l2dst;
			currentPacket.virtualSeqNo = virtualSeqNo++;
			currentPacket.hops.add(traceItem.l3src);
			currentPacket.hops.add(traceItem.l2dst);
			if (dump) System.out.println("PacketTracer: New packet -- " + currentPacket);
			packets.put( currentPacket.hashCode(), currentPacket);
			// -- record trace
			// hopTraces.add(currentPacket.hops);
			// --
			storedPacket = currentPacket;
		} else if (storedPacket.l2dst.equals(traceItem.l2dst)) {
			// re-transmission 
			if (dump) System.out.println("PacketTracer: Retransmission -- " + storedPacket);
			retransmission = true;
		} else {
			// next hop reached
			storedPacket.l2src = storedPacket.l2dst;
			storedPacket.l2dst = traceItem.l2dst;
			// add hops and create traces 
			boolean first = true;
			boolean recursive = false;
			for (NodeAddress hop : storedPacket.hops ){
				if (first || !recursive){
					first = false;
				} else {
					tracePacket( traceItem.l2dst, hop, traceItem.l3dst, storedPacket.virtualSeqNo, time, false);
				}
			}
			// -- record trace
			storedPacket.hops.add(traceItem.l2dst);
			// --
			if (dump) System.out.println("PacketTracer: Forwarding " + storedPacket);
		}
		// update timestamp
		storedPacket.time  = time;

		// count packets
		garbageTimer++;
		if (garbageTimer > garbageTimeout) {
			discardOldItems(time);
			garbageTimer = 0;
		}
		// store query results
		traceItem.l2src = storedPacket.l2src;
		traceItem.retransmission = retransmission;
	}
	/**
	 * Get address of node that most likely sent this packet
	 * Store tracing information about multi-hop packet, uses only 
	 */
	public NodeAddress tracePacket( NodeAddress l2dst, NodeAddress l3src, NodeAddress l3dst,
			int l3seqNo, long time, boolean reportRetransmissions) {
		
		PacketTracerTuple traceItem = new PacketTracerTuple();
		traceItem.l2dst = l2dst;
		traceItem.l3src = l3src;
		traceItem.l3dst = l3dst;
		traceItem.l3seqNr = l3seqNo;
		
		tracePacket( traceItem, time );
		
		System.out.println("PacketTracer: src "+traceItem.l2src + " dst "+traceItem.l2dst);
		
		if (traceItem.retransmission && !reportRetransmissions) return null;
		return traceItem.l2src; 
	}
	
	public void dumpTraces(NodeAddress l3src){
		
		for (ArrayList<NodeAddress> trace : hopTraces){
			if (trace.get(0).equals(l3src)){
				for (NodeAddress hop : trace){
					System.out.print( hop + " -> ");
				}
				System.out.println();
			}
		}
	}

	public void dumpTrace(NodeAddress l3src, NodeAddress l3dst, int l3seqNo){
		// create example entry
		TraceItem currentPacket = new TraceItem();
		currentPacket.src = l3src;
		currentPacket.dst = l3dst;
		currentPacket.seqNo = l3seqNo;
		TraceItem storedPacket = packets.get( currentPacket.hashCode());
		ArrayList <NodeAddress> trace = storedPacket.hops;
		for (NodeAddress hop : trace){
			System.out.print( hop + " -> ");
		}
		System.out.println();
	}

	/**
	 *  Discard old trace items 
	 *  @TODO IMPLEMENT FAIL-SAFE
	 */
	public void discardOldItems(long time) {

		/** 
		Set<Integer> keys = packets.keySet();
		Iterator iter = keys.iterator();
		while (iter.hasNext()) {
			Integer key = (Integer) iter.next();
			TraceItem item = packets.get(key);
			if (item.time + itemValidTime_ms < time ) {
				packets.remove(key);
			}
		}
		*/
	}
	
	/** 
	 * Discard all cached packets
	 */
	public static void discardCachedPacket() {
		getPacketTracer().packets.clear();
		getPacketTracer().hopTraces.clear();
	}
	
	/**
	 * Provide global access to a single packet tracer 
	 */
	public static PacketTracer getPacketTracer() {
		if (packetTracer == null) {
			packetTracer = new PacketTracer();
		}
		return packetTracer;
	}
	
	/** singleton */
	private static PacketTracer packetTracer = null;
	
	/** map to store items */
	private HashMap<Integer, TraceItem> packets = new HashMap<Integer, TraceItem>();

	/** garbage timer */
	private int garbageTimer = 0;
	
	/** clean up after x actions */
	private static final int garbageTimeout = 1000;
	
	/** time to keep a packet */
	private static final int itemValidTime_ms = 5000;
	
	/** debug flag */
	public static boolean dump = false;
	
	/** list of traces */
	ArrayList<ArrayList<NodeAddress>> hopTraces = new ArrayList<ArrayList<NodeAddress>>();

	int virtualSeqNo = 1000;
}
