package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import model.Routing.RouteState;


/**
 * Representation of an observed real wireless sensor network node
 * @author mringwal
 *
 */
public class WSNnode {
	
	public class HopItem {
		NodeAddress next;
		int counter = 1;
	}

	public enum NodeState {
		OK, NodeCrash, NodeReboot, NoNeighbors, NoRoute, BadPathToNode, BadNodeTransmit,
		BadPathToSink, NoNeighborPartition, NoRoutePartition, BadPathToSinkPending, NoRoutePending
	}
	
	private WSNnode(NodeAddress src) {
		addr = src;
	}
	
	/** 
	 * 
	 */
	public static void discardNodes() {
		nodes.clear();
	}
	
	/**
	 * Get the specified node
	 * @param src address
	 * @return existing ode object or newly created one
	 * @throws Exception 
	 */
	public static WSNnode getNode( NodeAddress addr)  {
		// 
		if (addr == null ) {
			new Exception( "WSNnode.getNode(null)!").printStackTrace();
			System.exit(10);
		}
		WSNnode node = nodes.get( addr );
		if (node == null) {
			node = new WSNnode (addr);
			nodes.put( addr, node);
		}
		return node;
	}
	
	/** Get NodeAddress of all WSNnodes */
	public static Set<NodeAddress> getNodes() {
		return nodes.keySet();
	}
	
	public void packetTransmit(Packet p) {
		if (firstPacketTx < 0) {
			firstPacketTx = p.time_ms;
		}
		lastPacketTx = p.time_ms;
		totalPacketsTx++;
	}
	
	public void hearedByNeighbor( NodeAddress addr, long time ) {
		neighborHearedMe.put( addr, new Long(time));
	}

	/**
	 * 
	 * @param newNeighborTable
	 * @param timestamp TODO
	 * @throws Exception 
	 */
	public void updateNeighborTable(HashMap<NodeAddress, Integer> newNeighborTable, long timestamp) throws Exception {
		Set<NodeAddress> oldNeighbors = neighborTable.keySet();
		for (NodeAddress address : oldNeighbors) {
				if (!newNeighborTable.containsKey(address)) {
					WSNnode lostNeighbor = WSNnode.getNode(address);
					lostNeighbor.neighborHearedMe.remove(addr);
					if ( routeUplink != null && routeUplink.equals(address)) {
						// System.out.println("Node "+addr+" lost uplink "+address+" as neighbor at "+timestamp);
						havePath = false;
						routeUplinkPrevious = routeUplink;
						routeUplink = null;
						routeTime = timestamp;
					}
			}
		}
		// keep last known neighbors
		if (newNeighborTable.size() == 0 && neighborTable.size() > 0) {
			lastNeighbors = neighborTable;
			// no neighbors => no path
			havePath = false;
			routeUplinkPrevious = routeUplink;
			routeUplink = null;
			routeTime = timestamp;
		}
		neighborTable = newNeighborTable;
		neighbors_time = timestamp;
	}

	public int getNumNeighborsHearedMeAfter(long time) {
		int count = 0;
		Set<NodeAddress> neighbors = neighborHearedMe.keySet();
		for (NodeAddress address : neighbors) {
			if ( neighborHearedMe.get(address)  >= time ) {
				count++;
			}
		};
		return count;
	}
	
	public void updatePath(long timestamp, int quality, int round) {
		havePath = true;
		routeTime = timestamp;
		routeQuality = quality;
		routeRound = round;
	}
	
	public void lostPath(long timestamp) {
		havePath = false;
		if (routeUplink != null) {
			routeUplinkPrevious = routeUplink;
		}
		routeUplink = null;
		routeTime = timestamp;
		System.out.println("Node "+getAddress()+" lost path to sink");
	}


	public int getNeighborCount() {
		return neighborTable.size();
	}
	
	public NodeAddress getAddress() {
		return addr;
	}
	
	public String getNeighbors() {
		Set<NodeAddress> neighbors = neighborHearedMe.keySet();
		StringBuffer result = new StringBuffer("{");
		for (NodeAddress address : neighbors) {
			result .append( " "+address+" ("+(neighborHearedMe.get(address)/1000)+"s),");
		};
		result.append("}");
		return result.toString();
	}
	

	public String getNeighbors(long currentTime) {
		Set<NodeAddress> neighbors = neighborHearedMe.keySet();
		StringBuffer result = new StringBuffer("{");
		for (NodeAddress address : neighbors) {
			result .append( " "+address+" (-"+((currentTime - neighborHearedMe.get(address))/1000)+" s),");
		};
		result.append("}");
		return result.toString();
	}

	/** 
	 * node use a (new) uplink node for packet forwarding
	 * @param newNextHop
	 */
	public void routeChange(NodeAddress newNextHop){
		// If this is the first route, should it be reported as new? as changed?
		routeChanged = true;               // ? event instead of flag
		routeUplinkPrevious = routeUplink; // ? routeUplinkPrevious?
		routeUplink = newNextHop;
		if (routeUplinkPrevious != null)  {
			PacketSorter.postEvent( new Event( addr, EventType.RouteUpdate, "Route Change new: " + routeUplink + ", prev: " + routeUplinkPrevious));
		} else {			
			PacketSorter.postEvent( new Event( addr, EventType.RouteUpdate, "Route Found: " + routeUplink));
		}
	}
	
	/**
	 * checkNodeCrash
	 * 
	 * @param node
	 * @return
	 */
	public void checkNodeCrash() {
		long epochBefore = PacketSorter.getCurrentTime() - PacketSorter.epoch;
		boolean hearedFromNodeLastEpoch = lastPacketTx > epochBefore;
		boolean neighborHearedNodeLastEpoch = getNumNeighborsHearedMeAfter(epochBefore) > 0;
		boolean nodeCrashed = !hearedFromNodeLastEpoch && !neighborHearedNodeLastEpoch;
	    if (nodeState == NodeState.NodeCrash)  {
	    		if (nodeCrashed == false) {
		    		nodeState = NodeState.OK;
	    			PacketSorter.postEvent(new Event(addr, "Node Running after Crash"));
	    		}
	    } else {
			if (nodeCrashed) {
	    			nodeState = NodeState.NodeCrash;
	    			nodeStateChange = PacketSorter.getCurrentTime();
	    			PacketSorter.postEvent(new Event(addr, "Node Crash"));
	    		}
	    }
	}
	
	/**
	 * check No Neighbours
	 *
	 */
	public void checkNoNeighbors() {
	    if (nodeState == NodeState.NoNeighbors ||
	    	nodeState == NodeState.NoNeighborPartition ) {
	    		if (getNeighborCount() > 0) {
	    			nodeState = NodeState.OK;
	    			nodeStateChange = PacketSorter.getCurrentTime();
    				PacketSorter.postEvent(new Event(getAddress(), "Node found new neigbors" ));
	    		}
	    } else {
	    		if (getNeighborCount() == 0) {
	    			// check time since neighbor list update
	    			long age = PacketSorter.getCurrentTime() - neighbors_time;
	    			if ( neighbors_time != 0 && age > PacketSorter.epoch ) {
		    			// check for last node(s) beeing crashed
	    				boolean allCrashed = true;
	    				Set<NodeAddress> oldNeighbors = lastNeighbors.keySet();
		    			for (NodeAddress oldNeighbor : oldNeighbors) {
		    				WSNnode oldNeigbor = WSNnode.getNode(oldNeighbor);
		    				if (oldNeigbor.nodeState != NodeState.NodeCrash) {
		    					allCrashed = false;
		    				}
		    			}
		    			if (allCrashed) {
		    				nodeState = NodeState.NoNeighborPartition;
		    				nodeStateChange = PacketSorter.getCurrentTime();
			    			PacketSorter.postEvent(new Event(getAddress(), "Network Partition: "
			    					+lastNeighbors+" crashed => No Neighbors" ));
		    			}
		    			else {
		    				nodeState = NodeState.NoNeighbors;
		    				nodeStateChange = PacketSorter.getCurrentTime();
			    			PacketSorter.postEvent(new Event(getAddress(), "No Neighbors" ));
		    			}
	    			}
	    		}
	    }	
	}
	
	/**
	 * Check No Route
	 *
	 */
	public  void checkNoRoute() {
		if (nodeState == NodeState.NoRoute
				|| nodeState == NodeState.NoRoutePending
				|| nodeState == NodeState.NoRoutePartition) {
			if (routeUplink != null && havePath) {
				// TODO check if uplinkNode.nodeState is ok
				nodeStateChange = PacketSorter.getCurrentTime();
				nodeState = NodeState.OK;
				// PacketSorter.postEvent(new Event(node.getAddress(), "Route fixed, new uplink "+node.routeUplink ));
			}
		} else {
			if (havePath == false) {
				// check time since neighbor list update
				long age = PacketSorter.getCurrentTime() - routeTime;
				if (routeTime != 0 && age > PacketSorter.epoch) {
					// NoRoutePending..

					// check for last node(s) beeing crashed
					boolean uplinkNodeCrashed = false;
					if (routeUplinkPrevious != null) {
						WSNnode parent = WSNnode
								.getNode(routeUplinkPrevious);
						if (parent.nodeState == NodeState.NodeCrash) {
							uplinkNodeCrashed = true;
							crashedUplinkNode = routeUplinkPrevious;
						}
						if (parent.nodeState == NodeState.NoRoutePartition) {
							uplinkNodeCrashed = true;
							crashedUplinkNode = parent.crashedUplinkNode;

						}
					}
					if (uplinkNodeCrashed) {
						nodeState = NodeState.NoRoutePartition;
						nodeStateChange = PacketSorter.getCurrentTime();
		    				PacketSorter.postEvent(new Event(getAddress(),
		    					"Network Partition: "+routeUplinkPrevious+" crashed => No Route" ));
					} else {
						nodeState = NodeState.NoRoute;
						nodeStateChange = PacketSorter.getCurrentTime();
						PacketSorter.postEvent(new Event(getAddress(), "No Route" ));
					}
				}
			}
		}
	}
	
	/**
	 * 
	 *
	 */
	public void checkDataGeneration() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Check Route itself
	 *
	 */
	public void checkGoodRoute() {
	    RouteState routeState = Routing.testRoute(this);
		long epochBefore = PacketSorter.getCurrentTime() - PacketSorter.epoch;
	    if (nodeState == NodeState.BadPathToSinkPending || nodeState == NodeState.BadPathToSink) {
	    		// recover if route gets fixed
	    		if (routeState == RouteState.OK) { 
	    			nodeStateChange = PacketSorter.getCurrentTime();
	    			nodeState = NodeState.OK;
	    			return; //  "Route fixed, new uplink "+routeUplink;
	    		}
	    }
    		if (nodeState == NodeState.BadPathToSinkPending) {
    			if (nodeStateChange < epochBefore) {
	    	    		switch ( routeState ) {
		    	    		case LOOP:
		    	    			nodeStateChange = PacketSorter.getCurrentTime();
		    	    			nodeState = NodeState.BadPathToSink;
								PacketSorter.postEvent(new Event(getAddress(),
										"Bad Path -- LOOP: "+Routing.dumpRoute(this) ));
						break;
		    	    		case BAD:
		    	    			nodeStateChange = PacketSorter.getCurrentTime();
		    	    			nodeState = NodeState.BadPathToSink;
								PacketSorter.postEvent(new Event(getAddress(),
										"Bad Path -- " + Routing.dumpRoute(this) ));
						break;
		    	    		case UPLINKPROBLEM:
		    	    			nodeStateChange = PacketSorter.getCurrentTime();
		    	    			nodeState = NodeState.BadPathToSink;
		    	    			crashedUplinkNode = Routing.getCrashedUplinkNode(this);
						PacketSorter.postEvent(new Event(getAddress(),
								"Network Partition: "+crashedUplinkNode+" crashed => Bad Path To Sink: "
								+ Routing.dumpRoute(this) ));
						return;
	        			default:
	        				break;
	    	    		}
	    	    		return;
    			}
    		}
    		if (nodeState == NodeState.OK) {
    			if (routeState != RouteState.OK) {
	    			nodeStateChange = PacketSorter.getCurrentTime();
	    			nodeState = NodeState.BadPathToSinkPending;
    			}
    		}
	}

	public String toString() {
		return "Node: "+addr+": last heared at t= " + lastPacketTx/1000 + " s. " + getNeighbors();
	}
	
	public String toString(long currentTime, long epoch) {
		return "Node: "+addr+": last heared " + (currentTime-lastPacketTx)/1000 + " seconds ago. \n"
		+getNeighbors(currentTime)+"\n"
		+ "Number neighbors heared me since " + (epoch/1000) + " seconds: " + getNumNeighborsHearedMeAfter( currentTime - epoch);
	}
	public boolean isSink() {
		return addr.equals(sink);
	}
	public void dumpDownLinks() {
		System.out.print("downLinks: ");
		for (NodeAddress link : downLinks.keySet()){
			Float oldWeight = downLinks.get(link);
			System.out.print( link.toString() +"("+oldWeight+"), " );
		}
		System.out.println();
	}

	public void recordNextHop(NodeAddress addr) {
		HopItem next = null;
		boolean newItem = true;
		
		if (nextHop.size() > 0 ) {
			next = nextHop.get( nextHop.size() -1 );
			if (next.next.equals(addr)) {
				newItem = false;
			}
		}
		if (newItem) {
			next = new HopItem();
			next.next = addr;
			nextHop.add(next);
		} else {
			next.counter++;
		}
	}
	
	public void dumpNextHops() {
		System.out.print(addr+" hops: ");
		for (HopItem hop : nextHop){
			System.out.print( hop.next.toString() +"( " + hop.counter + " x ), ");
		}
		System.out.println();
	}
	
	public boolean isNodeCrashed() {
		return nodeState == NodeState.NodeCrash;
	}
	
	public boolean hasNeighbors() {
		if (nodeState == NodeState.NoNeighborPartition) return false;
		if (nodeState == NodeState.NoNeighbors) return false;
		return true;
	}
	
	public boolean hasRoute() {
		if (nodeState == NodeState.NoRoute) return false;
		if (nodeState == NodeState.NoRoutePartition) return false;
		return true;
	}
	
	public boolean hasGoodRoute() {
		return nodeState == NodeState.BadPathToSink;
	}
	
	public boolean hasGeneratedSufficientData() {
		// TODO Auto-generated method stub
		return false;
	}

	/** class fields */
	static HashMap<NodeAddress, WSNnode> nodes = new HashMap<NodeAddress, WSNnode>();
	public static NodeAddress sink;
	
	/** instance fields */
	NodeAddress addr;
	
	/** node state and timestamp of last change */
	public NodeState nodeState = NodeState.OK;
	public long nodeStateChange = 0;
	
	/** node crash - last received packet */
	public long lastPacketTx = 0;

	/** node reboot - last received seq.nrs. */
	public int  beacon1SeqNo  = -1;  // allows for reboot detection 
	// public int  multihopSeqNo = -1;  // allows for reboot detection -- currently not used

	/** no neighbors */
	public long   neighbors_time = 0; // timestamp of last neighbor packet
	HashMap<NodeAddress,Long> neighborHearedMe = new HashMap<NodeAddress,Long>();
	// neghbor list with associated quality
	HashMap<NodeAddress,Integer> neighborTable = new HashMap<NodeAddress,Integer>();
	// last neighbors a node had
	public HashMap<NodeAddress,Integer> lastNeighbors = new HashMap<NodeAddress,Integer>();
	
	/** no route */
	public NodeAddress routeUplink = null;
	public NodeAddress routeUplinkPrevious = null;
	public boolean havePath = false;
	public long   routeTime = 0;	 // timestamp of last path announcement
	int routeQuality = 0;
	int routeRound = -1;
	public NodeAddress crashedUplinkNode = null;
	public boolean routeChanged = false;
	public int newRouteCounter = 0;
	public NodeAddress newRouteNode = null;
	
	/** bad path to node */
	
	/** bad node transmit */
	
	/** bad path to sink */
	
	/** statistics */
	public long totalPacketsTx = 0; // nr of transmitted packets
	long firstPacketTx = -1;	// allows to calculate uptime

	/** routing observations */
	HashMap<NodeAddress,Float> downLinks = new HashMap<NodeAddress,Float>();

	/** next hop analysis */
	ArrayList<HopItem> nextHop = new ArrayList<HopItem>();


	
}
