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
 * @todo: limit validation by post-poning validation. but have to use timer for this
 */
public class TopologyAnalyzer extends AbstractPipe<Tuple,Tuple> implements
		TimeTriggered {

	/** single downlink and its timeout */
	private class DownLink {
		NodeAddress downLink;
		long timeout;
		DownLink( NodeAddress addr, long timeout) {
			downLink = addr;
			this.timeout = timeout;
		}
	}
	
	/** node state used for evaluation of network */
	private class NodeState {
		NodeAddress nodeId;
		String partitionCause = "";
		long evaluationTime = 0;
		boolean inEvaluation = false;
		Tuple stateTuple = null; 
		HashMap<NodeAddress, DownLink> downLinks = new HashMap<NodeAddress, DownLink>();
		
		/**
		 * @param nodeId
		 * @param partitionCause
		 * @param evaluated
		 */
		public NodeState(NodeAddress nodeId ) {
			this.nodeId = nodeId;
		}
	}
	
	protected NodeAddress sink;
	
	protected HashMap<NodeAddress,NodeState> nodeStates = new HashMap<NodeAddress,NodeState>();
	
	protected int timewindow;
	
	protected int metricPeriod;
	
	public long lastEvaluation = 0;

	protected LinkedList<TimeStampedObject<Tuple>> window = new LinkedList<TimeStampedObject<Tuple>>();

	TupleChangePredicate tupleChangePredicate;

	protected int nodeStateChangeSrcID;
	
	protected int packetTracerSrcID;

	int partionedTupleID;

	TupleAttribute nodeIDID;
	
	TupleAttribute partitionedID;
	
	TupleAttribute crashedNodesIDs;

	TupleAttribute l2srcAttribute;
	
	TupleAttribute l2dstAttribute;

	
	/**
	 * @param sink
	 * @param timewindow
	 * @param metricPeriod
	 * @param window
	 * @param groupFieldID
	 */
	public TopologyAnalyzer(NodeAddress sink, int timewindow, int metricPeriod,
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
		
		// prepare sink node state
		nodeStates.put( sink, new NodeState( sink ));
	}


	/** 
	 * evaluate all nodes
	 *
	 */
	protected void validateNew(long timestamp) {
		System.out.println("Network Parition, Validate at " + timestamp / 1000);

		// clear evaluation markers
		for (NodeAddress node : nodeStates.keySet()) {
			NodeState nodeState = nodeStates.get(node);
			nodeState.inEvaluation = true;
			nodeState.partitionCause = "";
		}
		// tree traversal to mark connected nodes.
		// also get list of crashed nodes
		HashMap<NodeAddress, NodeState> crashedNodes = new HashMap<NodeAddress, NodeState>();
		markConnectedAndCrashed(timestamp, sink, crashedNodes);

		// figure out last valid uplink and report as partitioned
		reportPartitionedNodes(timestamp, crashedNodes);
	}
	
	/**
	 * Mark all reachable nodes as connected
	 * 
	 * @param connectedNode is reachable by valid links from sink
	 * @param crashedNodes to collect crashed nodes
	 */
	private void markConnectedAndCrashed(long timestamp, NodeAddress connectedNode, HashMap<NodeAddress,NodeState> crashedNodes) {
		NodeState nodeState = nodeStates.get( connectedNode );
		// of interest?
		if (!nodeState.inEvaluation) return;
		
		// check this node for NodeCrash
		Tuple nodeStateTuple = nodeState.stateTuple;
		if (nodeStateTuple != null && nodeStateTuple.getType() == "NodeCrash") {
			// remember crashed node
			crashedNodes.put(connectedNode, nodeState);
			
			// System.out.println("Network Parition: node "+node + " crashed at " + timestamp/1000);
			nodeState.evaluationTime = timestamp;
			nodeState.inEvaluation = false;
			nodeState.partitionCause = connectedNode.toString();
			return;
		}

		// node is connected. mark it and descent further
		nodeState.evaluationTime = timestamp;
		nodeState.inEvaluation = false;
		Tuple result = Tuple.createTuple(partionedTupleID);
		result.setIntAttribute( nodeIDID, connectedNode.getInt());
		result.setIntAttribute( partitionedID, 0);
		result.setStringAttribute( crashedNodesIDs, ""+sink);
		
		transfer( result, timestamp );
		
		//  check downlinks
		for (DownLink dl : nodeState.downLinks.values()) {
			if (dl.timeout > timestamp) {
				markConnectedAndCrashed( timestamp, dl.downLink, crashedNodes);
			}
		}
		
	}
	
	/**
	 * report not connected nodes as partitioned 
	 * 
	 * this is done on a level-by-level basis
	 * 
	 * @param timestamp
	 * @param crashedNodes
	 */
	private void reportPartitionedNodes(long timestamp, HashMap<NodeAddress,NodeState> crashedNodes) {
		// mark all nodes that can be reached from crashed nodes as partitioned
		HashMap<NodeAddress,NodeState> partitionedNodes = new HashMap<NodeAddress,NodeState> ();
		boolean partitioned;
		do {
			partitioned = false;
			for (NodeState crashedNode : crashedNodes.values()  ) {
				for (DownLink dl : crashedNode.downLinks.values()) {
					NodeState downLinkNode = nodeStates.get( dl.downLink);
					if (downLinkNode.inEvaluation) {
						partitioned = true;
						downLinkNode.partitionCause += crashedNode.partitionCause + " ";
						partitionedNodes.put( dl.downLink, downLinkNode);
					}
				}
			}
			// report nodes
			for (NodeState partitionedNode : partitionedNodes.values() ){
				// mark as evaluated
				partitionedNode.inEvaluation = false;
				// emit paritioned tuple
				Tuple result = Tuple.createTuple(partionedTupleID);
				result.setIntAttribute( nodeIDID, partitionedNode.nodeId.getInt());
				result.setIntAttribute( partitionedID, 1);
				result.setStringAttribute( crashedNodesIDs, partitionedNode.partitionCause);
				// System.out.println("Network Paritioned at " + timestamp/1000 + " " + result);
				transfer( result, timestamp );				
			}
			// prepare next round
			crashedNodes= partitionedNodes;
			partitionedNodes = new HashMap<NodeAddress,NodeState> ();
			
		} while (partitioned == true);
	}


	public void handleTimerEvent(long timestamp) {
		// check entries for removal
		boolean validate = false;
		while (window.size() > 0 && window.getFirst().timestamp + timewindow <= timestamp ) {
			Tuple o = window.removeFirst().object;
			// did link vanish?
			NodeState parent = nodeStates.get( new NodeAddress( o.getIntAttribute(l2dstAttribute) ));
			DownLink dl = parent.downLinks.get( new NodeAddress( o.getIntAttribute(l2srcAttribute) ));
			if (dl == null) {
				validate = true;
			} else 	if (dl.timeout <= timestamp) {
				validate = true;
				parent.downLinks.remove( dl.downLink);
			}
		}
		// check nodes
		if (validate)
			validateNew( timestamp );
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
			
			// store or update downlink timeout
			NodeState parentNode = nodeStates.get( l2dst );
			DownLink dl = parentNode.downLinks.get(l2src);
			if (dl == null) {
				parentNode.downLinks.put( l2src, new DownLink(l2src,timestamp+timewindow ));
				// new link => check nodes
				validate = true;
			} else {
				dl.timeout = timestamp+timewindow;
			}
			
			// register timeout
			Scheduler.getInstance().registerTimeout( timestamp + timewindow, this );
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
		if (validate) validateNew( timestamp );
	}
}
