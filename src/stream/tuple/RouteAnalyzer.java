package stream.tuple;

import java.util.HashMap;
import java.util.Iterator;

import model.NodeAddress;
import stream.AbstractPipe;

/**
 * 
 */

public final class RouteAnalyzer extends AbstractPipe<Tuple, Tuple> {
	private final NodeAddress sink;

	private static boolean tupleRegisterd = false;

	private static boolean dump = false;

	int l2srcID;

	int l2dstID;

	int nodeIdField;

	int destIdField;

	int timeFieldID;

	int hopsFieldID;

	int routingLoopTupleID;

	int latencyTupleID;

	class Route {
		Object nodeID;

		long timestamp;

		int hops;

		public Route(Object nodeID, long timestamp, int hops) {
			this.nodeID = nodeID;
			this.timestamp = timestamp;
			this.hops = hops;
		}
	}

	// store for all nodes timestamp of last information received
	HashMap<Object, HashMap<Object, Route>> lastInformationReceived = new HashMap<Object, HashMap<Object, Route>>();

	public RouteAnalyzer(NodeAddress sink, String name) {
		this.name = name;
		if (!tupleRegisterd) {
			Tuple.registerTupleType("RoutingLoop", "nodeID", "destID");
			Tuple.registerTupleType("LatencyMeasurement", "nodeID", "time",	"hops");
			tupleRegisterd = true;
		}
		l2srcID = Tuple.getAttributeId("l2src");

		l2dstID = Tuple.getAttributeId("l2dst");

		nodeIdField = Tuple.getAttributeId("nodeID");

		destIdField = Tuple.getAttributeId("destID");

		timeFieldID = Tuple.getAttributeId("time");

		hopsFieldID = Tuple.getAttributeId("hops");

		routingLoopTupleID = Tuple.getTupleTypeID("RoutingLoop");

		latencyTupleID = Tuple.getTupleTypeID("LatencyMeasurement");

		this.sink = sink;
	}

	private void transferRoutingLoopTuple(Object l2src, Object l2dst,
			long timestamp) {
		Tuple tuple = Tuple.createTuple(routingLoopTupleID);
		tuple.setAttribute(nodeIdField, l2src);
		tuple.setAttribute(destIdField, l2dst);
		transfer(tuple, timestamp);
		if (dump)
			System.out.println("" + timestamp + " : " + tuple);
	}

	private void transferLatencyTuple(Route route, long timestamp) {
		Tuple tuple = Tuple.createTuple(latencyTupleID);
		tuple.setAttribute(nodeIdField, route.nodeID);
		tuple.setAttribute(timeFieldID, timestamp - route.timestamp);
		tuple.setAttribute(hopsFieldID, route.hops);
		transfer(tuple, timestamp);
		if (dump)
			System.out.println("" + timestamp + " : " + tuple);
	}

	public void process(Tuple o, int srcID, long timestamp) {
		// update trace information
		Object l2src = o.getAttribute(l2srcID);
		Object l2dst = o.getAttribute(l2dstID);
		
		// TODO fix this hardcoded value
		if (l2dst.equals(65535)) {
			return;
		}
		
		// assert dstNode exists
		HashMap<Object, Route> dstNode = lastInformationReceived.get(l2dst);
		if (dstNode == null) {
			dstNode = new HashMap<Object, Route>();
			lastInformationReceived.put(l2dst, dstNode);
		}
		// add information about last hop
		Route dstHop = dstNode.get(l2src);
		if (dstHop == null) {
			dstHop = new Route(l2src, timestamp, 1);
			dstNode.put(l2src, dstHop);
		} else {
			dstHop.hops = 1;
			dstHop.timestamp = timestamp;
		}

		if (sink.equals(l2dst)) {
			transferLatencyTuple(dstHop, timestamp);
		}

		// update information from info stored on srcNode
		HashMap<Object, Route> srcNode = lastInformationReceived.get(l2src);
		if (srcNode != null) {
			Iterator iterator = srcNode.keySet().iterator();
			while (iterator.hasNext()) {
				Object key = iterator.next();
				// check for routing loop: l2dst is element of srcNode
				if (l2dst.equals(key)) {
					transferRoutingLoopTuple(l2src, l2dst, timestamp);
					// clean up table by removing information about this routing loop
					iterator.remove();
					continue;
				}
				Route srcHop = srcNode.get(key);
				dstHop = dstNode.get(key);
				if (dstHop == null) {
					dstHop = new Route(srcHop.nodeID, srcHop.timestamp, srcHop.hops + 1);
					dstNode.put(srcHop.nodeID, dstHop);
					if (sink.equals(l2dst)) {
						transferLatencyTuple(dstHop, timestamp);
					}
				} else {
					if (srcHop.timestamp > dstHop.timestamp) {
						dstHop.timestamp = srcHop.timestamp;
						dstHop.hops = srcHop.hops + 1;
						if (sink.equals(l2dst)) {
							transferLatencyTuple(dstHop, timestamp);
						}
					}
				}
			}
		}
	}

	// Test Code
	static int timestamp = 0;

	public void send(int src, int dest) {
		timestamp += 10;
		Tuple tuple = Tuple.createTuple("PacketTracerTuple");
		tuple.setIntAttribute(l2srcID, src);
		tuple.setIntAttribute(l2dstID, dest);
		process(tuple, 0, timestamp);
	}

	public static void main(String[] args) throws Exception {
		Tuple.registerTupleType("PacketTracerTuple", "l2src", "l2dst", "l3src",
				"l3dst", "l3seqNr");
		RouteAnalyzer analyzer = new RouteAnalyzer(new NodeAddress(2),"RouteAnalyzer");
		dump = true;

		analyzer.send(1, 2); 
//		10 : LatencyMeasurement { nodeID = 1, time = 0, hops = 1 }

		analyzer.send(3, 4); 
		analyzer.send(4, 2); 
//		30 : LatencyMeasurement { nodeID = 4, time = 0, hops = 1 }
//		30 : LatencyMeasurement { nodeID = 3, time = 10, hops = 2 }

		analyzer.send(5, 3); 
		analyzer.send(6, 3); 
		analyzer.send(3, 2); 
//		60 : LatencyMeasurement { nodeID = 3, time = 0, hops = 1 }
//		60 : LatencyMeasurement { nodeID = 6, time = 10, hops = 2 }
//		60 : LatencyMeasurement { nodeID = 5, time = 20, hops = 2 }
 
		analyzer.send(4, 3); 
//		70 : RoutingLoop { nodeID = 4, destID = 3 }
		analyzer.send(4, 3); 
		analyzer.send(3, 2);  
//		90 : LatencyMeasurement { nodeID = 3, time = 0, hops = 1 }
//		90 : LatencyMeasurement { nodeID = 4, time = 10, hops = 2 }
	}
}