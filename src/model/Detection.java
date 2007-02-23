/**
 * implement all failure detection mechanisms and metric generation
 */
package model;

public class Detection {
	
	/** area not used by seq nr in case of regular seq nr overrun */
	static final int overrunBuffer = 4096;

	/**
	 * Check a single node for failures
	 * - node crash
	 * - node reboot
	 * - no neighbours
	 * - no route
	 * - incorrect route
	 * - network partition
	 * @return
	 */
	public static void checkNode(WSNnode node) {
		
		// check for "node crash"
		// -- no packets received during last epoch
		// -- no link estimator announcements receive
		node.checkNodeCrash();
		if (node.isNodeCrashed()) return;
	    
	    // check for "node reboot"
	    // -- implemented in processPacket for LinkEstimatorBeacons
		// -- skippped in    processPacket for MultiHopPackets as it does not provide better information
	    
	    // check for "no neighbors"
		// @TODO check implementation
		// -- no link estimator announcements received from node during last epoch
		// -- no link estimator announcements received with at least on neighbor during last epoch
		node.checkNoNeighbors();
		if (!node.hasNeighbors() ) return;
		
	    // check for "no route"
		// -- no path estimator announcements received from node during last epoch
		// -- no path esitmator announcements recieved with at least one route during last epoch
		node.checkNoRoute();
		if (!node.hasRoute() ) return;

    	// check for "insufficient data generation"
    	// @TODO: implement data generation measurement 
		// use linked-list for time-window counting
		node.checkDataGeneration();
		if (node.hasGeneratedSufficientData()) return;

		// check for "routing loops"
		// @TODO implement loop detetion
		// -- event: routing loop observed: hops
		// -- implemented in processPacket 
		
	    // check for "good route"
		// -- a node has a good route to the sink, if there exists a series of packet forwardings which
		//    finally reached the sink during the last epoch
		// TODO implement checkGoodRoute
		node.checkGoodRoute();
		if (! node.hasGoodRoute()) return;

		// measure latency node -> sink
		
		// measure packet loss on link ??? possible?
		
		// calculate ratio of received data at sink to data generated
		// => learning ??
		
		//
	}
	
	/**
	 * check if a new seq counter is caused by a node reboot
	 * @param lastCounter
	 * @param newCounter
	 * @param overrunBuffer node is expected to skip this area if seq. counter is overrun
	 * @return true if newCounter was caused by node reset
	 */
	public static boolean checkSeqNrReset( int lastCounter, int newCounter, int overrunBuffer){
		if (lastCounter == -1){
			return false;
		}
		if (newCounter > lastCounter) {
			return false;
		}
		if (newCounter <  overrunBuffer){
			return true;
		}
		return false;
	}
	public static boolean checkSeqNrReset( int lastCounter, int newCounter){
		return checkSeqNrReset( lastCounter, newCounter, overrunBuffer);
	}
}