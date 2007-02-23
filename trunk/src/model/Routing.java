package model;

import java.util.HashSet;

import model.WSNnode.NodeState;

public class Routing {

	public enum RouteState {
		OK, LOOP, BAD, NoSink, UPLINKPROBLEM;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	/**
	 * Get predecessor of l2dst on route from l3src to l3dst
	 */
	public static NodeAddress getRoutePredecessor(NodeAddress l2dst, NodeAddress l3src, NodeAddress l3dst) {
		HashSet<NodeAddress> visited = new HashSet<NodeAddress>();
		NodeAddress currentNodeAddr = l3src;
		while (true) {
			if (currentNodeAddr == null) {
				return null;
			}
			if (visited.contains(currentNodeAddr)) {
				return null;
			}
			visited.add(currentNodeAddr);
			WSNnode node = WSNnode.getNode( currentNodeAddr );
			// if (node == null || !node.havePath || (node.routeUplink == null) ) {
			if (node == null || node.routeUplink == null ) {
				 return null;
			}
			if (node.routeUplink.equals(l2dst)) {
				return node.getAddress();
			}
			currentNodeAddr = node.routeUplink;
		}
	}

	/*
	 * Graph=based route reconstruction 
	 */
	public static NodeAddress tracePacketAndGetSrc2(NodeAddress l2dst, NodeAddress l3src,
			NodeAddress l3dst, int l3seqNr, long time_ms, NodeAddress realSrc) {
		// if not broadcast
		// @note a MultiHop flooding packet does not contain the sender id
		if ( !l2dst.isBroadcast()) {
			// ground truth is "p.src" (not available in real WSN) 

			WSNnode srcNode = WSNnode.getNode(realSrc);
			srcNode.recordNextHop( l2dst );

			// determine src by tracing multihop packets 
			NodeAddress tracedSrc = PacketTracer.getPacketTracer().tracePacket(l2dst,
					l3src, l3dst, l3seqNr, time_ms, false);
			if (tracedSrc == null) {
				// retransmission
				return null;
			}
			
			WSNnode dstNode = WSNnode.getNode(l2dst);
			// get weight on incoming link
			float oldWeight = (float) 0.0;
			Float floatValue = dstNode.downLinks.get(tracedSrc);
			if (floatValue != null){
				oldWeight = floatValue;	
			}
			// increase and store new weight
			float newWeight = oldWeight + 2;
			if (newWeight > 10) {
				newWeight = 10; 
			}
			dstNode.downLinks.put( tracedSrc, new Float(newWeight));
			// decrease other links
			for (NodeAddress link : dstNode.downLinks.keySet()){
				oldWeight = dstNode.downLinks.get(link);
				newWeight = oldWeight - 1;
				if (newWeight < 0){
					newWeight = 0;
				}
				dstNode.downLinks.put(link, new Float(newWeight));
			}
//			System.out.print("Routing: l3src "+l3src+" to l2dst" +l2dst + " from traced "+tracedSrc + " (real: "+realSrc+")");
//			if (tracedSrc.equals(realSrc)){
//				System.out.print(" Correct ");
//			} else {
//				System.out.print(" Wrong   ");
//				if (tracedSrc.equals(l2dst)) {
//					PacketTracer.getPacketTracer().dumpTrace( l3src, l3dst, l3seqNr);
//				}
//			}
//			System.out.println( ", w= "+newWeight);
		}
		// find strongest incoming link. resp. 
		return null;
	}
	/**
	 * Processes a multihop packet without layer 2 src address.
	 * 
	 * Try to determine l2src address and validate routing table
	 * 
	 * @param l2dst
	 * @param l3src
	 * @param l3dst
	 * @param l3seqNr
	 * @param time_ms
	 * @param realSrc
	 * @return
	 */
	public static NodeAddress tracePacketAndGetSrc(NodeAddress l2dst, NodeAddress l3src, NodeAddress l3dst, int l3seqNr, long time_ms, NodeAddress realSrc) {
		// if not broadcast
		// @note a MultiHop flooding packet does not contain the sender id
		if ( !l2dst.isBroadcast()) {
			// ground truth is "p.src" (not available in real WSN) 

			// determine src by tracing multihop packets 
			NodeAddress tracedSrc = PacketTracer.getPacketTracer().tracePacket(l2dst,
					l3src, l3dst, l3seqNr, time_ms, false);
			WSNnode tracedSrcNode = WSNnode.getNode(tracedSrc);

			// determine expected sender by following the route from l3src to l3dst
			NodeAddress expectedSrc = getRoutePredecessor(l2dst, l3src, l3dst);
			WSNnode expectedSrcNode = null;
			if (expectedSrc != null ) {
				expectedSrcNode = WSNnode.getNode(expectedSrc);
			}
			// check also if route is established
			/* RouteState routeState = */ testRoute(WSNnode.getNode(l3src));
			boolean srcIdentified = false;
			boolean updateRoute = false;
			int srcIdentifiedType = 0;
			if (expectedSrc != null){
				if (expectedSrc.equals(tracedSrc)) {
					// expected sender based on l2dst and next hop from PacketTracer match
					srcIdentified = true;
					srcIdentifiedType = 1;
				} else {
					// dst is on route from l3src to sink, but different than next hop from PacketTracer
					// check, how often this happens
					// - this happens quite often if there REALLY is a route change
					// - it happens sometime due to packet loss
					if (tracedSrcNode.newRouteNode == null || tracedSrcNode.newRouteNode.equals(expectedSrc)) {
						tracedSrcNode.newRouteCounter++;
						// another heuristic: if uplinkNode is crashed, immediatly accept new route
						// System.out.println("MultihopPacket. New 2-hop. pending: (real) src =" + p.src + ", tracer = "+tracedSrc + ", nextHop = " + expectedSrc + "\nOLD route" + PacketSorter.dumpRoute( tracedSrcNode) );
						if (expectedSrcNode != null && expectedSrcNode.nodeState == NodeState.NodeCrash) {
							// System.out.println("MultihopPacket. New 2-hop. accepted, uplink node crash: (real) src =" + p.src + ", tracer = "+tracedSrc + ", route = " + expectedSrc + "\n" + PacketSorter.dumpRoute( tracedSrcNode) );
							srcIdentified = true;
							srcIdentifiedType = 2;
							updateRoute = true;
						}
						else if (tracedSrcNode.newRouteCounter > newRouteThreshold) {
							// new route accepted
							// System.out.println("MultihopPacket. New 2-hop. accepted: (real) src =" + p.src + ", tracer = "+tracedSrc + ", route = " + expectedSrc + "\n" + PacketSorter.dumpRoute( tracedSrcNode) );
							srcIdentified = true;
							srcIdentifiedType = 3;
							updateRoute = true;
						}
					}
				}
			} else {
				// if not found on path, it's a new route
				// but that's not only true, if we don't miss the first packet
				// truth: l2dst, l3src 
					srcIdentified = true;
					srcIdentifiedType = 4;
					updateRoute = true;
			}
			
			if (srcIdentified) {
				// verify (only possible in simulation) if src is correct
				if ( !tracedSrc.equals( realSrc)){
					PacketSorter.postEvent( new Event( realSrc, EventType.Error, 
						" ERROR: PacketTracer ["+srcIdentifiedType +
						"] failed to determine correct src "+realSrc +", got "+tracedSrc ));
					return null;
				}
				// update route
				if (updateRoute){
					System.out.println ( "UPDATE ROUTE: real "+ realSrc+", expected "+ expectedSrc + ", traced "+tracedSrc);
					tracedSrcNode.newRouteCounter = 0;
					tracedSrcNode.newRouteNode = null;
					tracedSrcNode.routeChange( l2dst );
				}
				return tracedSrc;
			}
		}
		return null;
	}
	
	
	
	
	/**
	 * return uplink node which is crashed
	 * precondition: crasehd node in route to sink
	 * @param node
	 * @return
	 * TODO use of uplinkNodePrevious is fucked up
	 */
	static NodeAddress getCrashedUplinkNode(WSNnode node) {
		WSNnode uplinkNode = null;
		while (!node.isSink()) {
			// System.out.println("PacketSorter:goodRoute. checking " + node.getAddress());
			if(node.routeUplink == null) {
				if (node.routeUplinkPrevious == null) {
					// new Exception("getCrashedUplinkNode: uplink == null, "+node.nodeState + ", "+ dumpRoute(firstNode) ).printStackTrace();
					//System.exit(10);
					
				} else {
					uplinkNode = WSNnode.getNode( node.routeUplinkPrevious);
				}
			} else {
				uplinkNode = WSNnode.getNode( node.routeUplink);
			}
			if (uplinkNode.nodeState == NodeState.NodeCrash) {
				return uplinkNode.getAddress();
			}
			node = uplinkNode;
		}
		// todo: should not happen
		return null;
	}

	
	/**
	 * Test, if routeUplink leads to the sink
	 * @param node
	 * @return
	 */
	static RouteState testRoute(WSNnode node) {
		HashSet<NodeAddress> visited = new HashSet<NodeAddress>();
		while (!node.isSink()) {
			// System.out.println("PacketSorter:goodRoute. checking " + node.getAddress());
			visited.add(node.getAddress());
			if (node.routeUplink == null) {
				return RouteState.NoSink;
			}
			if (visited.contains(node.routeUplink)) {
				// System.out.println("Failure: Routing loop detected");
				return RouteState.LOOP;
			}
			WSNnode uplinkNode = WSNnode.getNode( node.routeUplink);
			if (uplinkNode.nodeState != NodeState.OK) {
				return RouteState.UPLINKPROBLEM;
			}
			if (uplinkNode == null) {
				return RouteState.NoSink;
			}
			node = uplinkNode;
		}
		return RouteState.OK;
	}
	
	/**
	 * get current route as string
	 * @param node 
	 * @return route in the form "x -> y -> sink"
	 */
	public static String dumpRoute(WSNnode node) {
		StringBuffer route = new StringBuffer();
		route.append( node.getAddress() );
		HashSet<NodeAddress> visited = new HashSet<NodeAddress>();
		while (!node.isSink()) {
			visited.add(node.getAddress());
			if (node.routeUplink == null) {
				return route.append(" -> ? ").toString();
			}
			WSNnode uplinkNode = WSNnode.getNode( node.routeUplink);
			if (visited.contains(node.routeUplink)) {
				return route.append(" -> LOOP ").toString();
			}
			if (uplinkNode == null) {
				return route.append(" -> ? ").toString();
			}
			route.append(" -> "+uplinkNode.getAddress());
			node = uplinkNode;
		}
		return route.toString();
	}
		
	private static final int newRouteThreshold = 5;
}
