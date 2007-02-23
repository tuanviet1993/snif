package stream;

import java.util.LinkedList;

import model.Packet;


/** 
 * Eliminate redundant packet transmission observations
 * 
 * A packet is considered a duplicate, if the packet contents match
 * another packet which was received during the last duplicate_timeout interval
 *
 * implemenetation detail:
 * - all packets are kept in a linked list
 * - upon process(), packets older than duplicate_timeout are removed from list
 * 
 * @author mringwal
 *
 */

public class DistinctInWindow extends Predicate<Packet> {

	private LinkedList<Packet> window = new LinkedList<Packet>();
	
	private int duplicate_timeout;

	/**
	 * @param duplicate_timeout
	 */
	public DistinctInWindow(int duplicate_timeout) {
		this.duplicate_timeout = duplicate_timeout;
	}

	@Override
	public boolean invoke(Packet p, long timestamp) {
		if (p == null) return false;

		// remove outdated elements
		while (window.size()>0 && window.getFirst().time_ms < timestamp - duplicate_timeout) {
			window.removeFirst();
		}
		
		// contained in window?
		if (window.contains(p)) {
			return false;
		}
		
		// keep in window
		window.addLast(p);
		return true;
	}

}
