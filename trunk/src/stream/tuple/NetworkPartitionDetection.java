package stream.tuple;

import java.util.HashMap;
import java.util.LinkedList;

import model.NodeAddress;

import stream.AbstractPipe;
import stream.Scheduler;
import stream.TimeStampedObject;
import stream.TimeTriggered;

/**
 * Network Partition Detection
 * @author mringwal
 *
 * @todo: only send if node partition state changes
 * 
 */
public class NetworkPartitionDetection extends AbstractPipe<Tuple,Tuple> implements
		TimeTriggered {

	private class NodeState {
		NodeAddress nodeId;
		String partitionCause = "";
		ConnectionType upstreamConnection;
		long evaluationTime = 0;
		boolean inEvaluation = false;
		Tuple stateTuple = null; 
		
		/**
		 * @param nodeId
		 * @param partitionCause
		 * @param evaluated
		 */
		public NodeState(NodeAddress nodeId ) {
			this.nodeId = nodeId;
		}
	}
	
	private enum ConnectionType {
		crashed, routeToSink, cannotEvaluate, partitioned
	}
	protected NodeAddress sink = new NodeAddress( 2 );
	
	protected HashMap<NodeAddress,NodeState> nodeStates = new HashMap<NodeAddress,NodeState>();
	
	protected int timewindow;
	
	protected int metricPeriod;
	
	protected int nodeStateChangeSrcID;
	
	protected int packetTracerSrcID;
	
	protected LinkedList<TimeStampedObject<Tuple>> window = new LinkedList<TimeStampedObject<Tuple>>();

	TupleChangePredicate tupleChangePredicate;

	int partionedTupleID;

	TupleAttribute nodeIDID;
	
	TupleAttribute partitionedID;
	
	TupleAttribute crashedNodesIDs;

	TupleAttribute l2srcAttribute;
	
	TupleAttribute l2dstAttribute;

	public long timeUsedNano = 0;

	public long lastEvaluation = 0;
	
	/**
	 * @param sink
	 * @param timewindow
	 * @param metricPeriod
	 * @param window
	 * @param groupFieldID
	 */
	public NetworkPartitionDetection(NodeAddress sink, int timewindow, int metricPeriod,
			int nodeStateChangeSrcID, int packetTracerSrcID) {
		this.sink = sink;
		this.timewindow = timewindow;
		this.metricPeriod = metricPeriod;
		this.nodeStateChangeSrcID = nodeStateChangeSrcID;
		this.packetTracerSrcID = packetTracerSrcID;
		
		partionedTupleID = Tuple.registerTupleType( "NodePartitioned", "partitioned", "nodeID", "crashedNodes");
		nodeIDID = new TupleAttribute("nodeID");
		partitionedID = new TupleAttribute("partitioned");
		crashedNodesIDs = new TupleAttribute("crashedNodes");
		l2srcAttribute = new TupleAttribute("l2src");
		l2dstAttribute = new TupleAttribute("l2dst");		
		
	}

	/** 
	 * evaluate all nodes
	 *
	 */
	private boolean changed;
	protected void validate(long timestamp) {
		if (timestamp > lastEvaluation + metricPeriod){
			// System.out.println("Network Parition, Validate at " + timestamp/1000);

			// clear evaluation markers
			for (NodeAddress node : nodeStates.keySet()){
				NodeState nodeState = nodeStates.get( node );
				nodeState.inEvaluation = true;
				nodeState.partitionCause = "";
			}
			
			// mark all crashed nodes and nodes which could reach the sink
			do {
				changed = false;
				for (NodeAddress node : nodeStates.keySet()){
					markCrashedAndConnected( node, timestamp);
				}
			} while (changed);

			// identify remaining nodes: node which have at least one paritioned or crashed node as uplink are partitiones
			do {
				changed = false;
				for (NodeAddress node : nodeStates.keySet()){
					reportParitionedNodes( node, timestamp);
				}
			} while (changed);
			
			//System.out.println("time network partition "+timeUsedNano);
			lastEvaluation = timestamp;
		}
	}
	
	
	private void markCrashedAndConnected(NodeAddress node, long timestamp ) {
		NodeState nodeState = nodeStates.get( node );
		// of interest?
		if (!nodeState.inEvaluation) return;

		// check this node for NodeCrash
		Tuple nodeStateTuple = nodeState.stateTuple;
		if (nodeStateTuple == null) {
			// don't know nothing
			nodeState.evaluationTime = timestamp;
			nodeState.upstreamConnection = ConnectionType.cannotEvaluate;
			nodeState.inEvaluation = false;
			changed = true;
			return;
		}
		if (nodeStateTuple.getType() == "NodeCrash") {
			// System.out.println("Network Parition: node "+node + " crashed at " + timestamp/1000);
			nodeState.evaluationTime = timestamp;
			nodeState.upstreamConnection = ConnectionType.crashed;
			nodeState.inEvaluation = false;
			changed = true;
			return;
		}
		// check its uplinks
		for (TimeStampedObject<Tuple> routeSegment : window ) {
			NodeAddress l2src = new NodeAddress ( routeSegment.object.getIntAttribute(l2srcAttribute));
			if ( ! l2src.equals( node )) continue;
			NodeAddress l2dst = new NodeAddress ( routeSegment.object.getIntAttribute(l2dstAttribute));
			// upstream node is sink
			if ( l2dst.equals(sink)) {
				nodeState.evaluationTime = timestamp;
				nodeState.inEvaluation = false;
				if (nodeState.upstreamConnection == null || nodeState.upstreamConnection != ConnectionType.routeToSink ) {
					nodeState.upstreamConnection = ConnectionType.routeToSink; 
					Tuple result = Tuple.createTuple(partionedTupleID);
					result.setIntAttribute( nodeIDID, node.getInt());
					result.setIntAttribute( partitionedID, 0);
					result.setStringAttribute( crashedNodesIDs, ""+sink);
					// System.out.println("Network OK at " + timestamp/1000 + " " + result);
					transfer( result, timestamp );
				}
				changed = true;
				return;
			}
			// check if upstream node is evaluated and has route
			NodeState nodeUpState = nodeStates.get( l2dst );
			if (nodeUpState == null) continue;
			if (nodeUpState.inEvaluation) continue;
			if (nodeUpState.upstreamConnection == ConnectionType.routeToSink) {
				nodeState.evaluationTime = timestamp;
				nodeState.inEvaluation = false;
				if (nodeState.upstreamConnection == null || nodeState.upstreamConnection != ConnectionType.routeToSink ) {
					nodeState.upstreamConnection = ConnectionType.routeToSink; 
					Tuple result = Tuple.createTuple(partionedTupleID);
					result.setIntAttribute( nodeIDID, node.getInt());
					result.setIntAttribute( partitionedID, 0);
					result.setStringAttribute( crashedNodesIDs, ""+l2dst);
					// System.out.println("Network OK at " + timestamp/1000 + " " + result);
					transfer( result, timestamp );
				}
				changed = true;
				return;
			}
		}
	}


	
	private void reportParitionedNodes(NodeAddress node, long timestamp ) {
		NodeState nodeState = nodeStates.get( node );
		// of interest?
		if (!nodeState.inEvaluation) return;
		// check its uplinks
		int counter = 0;
		for (TimeStampedObject<Tuple> routeSegment : window ) {
			NodeAddress l2src = new NodeAddress ( routeSegment.object.getIntAttribute(l2dstAttribute));
			if ( ! l2src.equals( node )) continue;
			NodeAddress l2dst = new NodeAddress ( routeSegment.object.getIntAttribute(l2dstAttribute));

			// at least one link did exists => partitioned. collect causes
			counter++;

			// check if upstream node is evaluated and has route
			NodeState nodeUpState = nodeStates.get( l2dst );
			if (nodeUpState == null) continue;
			if (nodeUpState.inEvaluation) continue;
			// upstream node has crashed
			if (nodeUpState.upstreamConnection == ConnectionType.crashed) {
				if ( nodeState.partitionCause.indexOf( l2dst.toString()) < 0) {
					nodeState.partitionCause += l2dst + " ";
				}
			}
			// upstream node is partitioned
			// @TODO properly add multiple partition causes
			if (nodeUpState.upstreamConnection ==  ConnectionType.partitioned) {
				String cause = nodeStates.get( l2dst ).partitionCause;
				if ( nodeState.partitionCause.indexOf( cause ) < 0) {
					nodeState.partitionCause += cause + " ";
				}
			}
		}
		if (counter > 0) {
			nodeState.evaluationTime = timestamp;
			if (nodeState.upstreamConnection == null || nodeState.upstreamConnection != ConnectionType.partitioned ) {
				nodeState.upstreamConnection = ConnectionType.partitioned;
				Tuple result = Tuple.createTuple(partionedTupleID);
				result.setIntAttribute( nodeIDID, node.getInt());
				result.setIntAttribute( partitionedID, 1);
				result.setStringAttribute( crashedNodesIDs, nodeState.partitionCause);
				// System.out.println("Network Paritioned at " + timestamp/1000 + " " + result);
				transfer( result, timestamp );
			}
		}
		nodeState.inEvaluation = false;
	}

	public void handleTimerEvent(long timestamp) {
		// check entries for removal
		boolean removed = false;
		while (window.size() > 0 && window.getFirst().timestamp + timewindow <= timestamp ) {
			window.removeFirst();
			removed = true;
		}
		// check nodes
		if (removed)
			validate( timestamp );
	}

	public void process(Tuple o, int srcID, long timestamp) {
		boolean validate = false;
		
		// node state or route info
		if ( srcID == packetTracerSrcID) {
			// add to list
			window.addLast(new TimeStampedObject<Tuple>(timestamp,o));
	
			// check for node id exists
			NodeAddress l2src = new NodeAddress( o.getIntAttribute(l2srcAttribute));
			if ( !nodeStates.containsKey(l2src)) {
				nodeStates.put( l2src, new NodeState( l2src));
			}
			NodeAddress l2dst = new NodeAddress( o.getIntAttribute(l2dstAttribute));
			if ( !nodeStates.containsKey(l2dst)) {
				nodeStates.put( l2dst, new NodeState( l2dst ));
			}
			
			// register timeout
			Scheduler.getInstance().registerTimeout( timestamp + timewindow, this );

			// check nodes
			validate = true;
		}
		
		if (srcID == nodeStateChangeSrcID) {
			// store node state
			NodeAddress nodeAddr = new NodeAddress ( o.getIntAttribute( nodeIDID));
			NodeState nodeState = nodeStates.get( nodeAddr);
			if ( nodeState == null) {
				nodeState = new NodeState( nodeAddr);
				nodeStates.put( nodeAddr, nodeState);
			}
			nodeState.stateTuple = o;
			
			// check nodes
			validate = true;
		}
		// check nodes
		if (validate) validate( timestamp );
	}
}
