package stream.tuple;

import java.util.LinkedList;

import stream.Predicate;


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

public class DistinctInWindow extends Predicate<PacketTuple> {

	private LinkedList<PacketTuple> window = new LinkedList<PacketTuple>();
	
	private int duplicate_timeout;

	private int item_counter = 0;
	private float item_mean;
	private int item_total = 0;
	
	private int dup_history[] = new int[50];
	
	/**
	 * @param duplicate_timeout
	 */
	public DistinctInWindow(int duplicate_timeout) {
		this.duplicate_timeout = duplicate_timeout;
	}

	@Override
	public boolean invoke(PacketTuple p, long timestamp) {
		if (p == null) return false;

		// remove outdated elements
		while (window.size()>0 && window.getFirst().time_ms < timestamp - duplicate_timeout) {
			window.removeFirst();
		}
		
		item_counter++;

		// contained in window?
		if (window.contains(p)) {
			return false;
		}

		item_counter--;
		item_total++;
				
		dup_history[item_counter]++;
		item_counter = 1;
		
		// keep in window
		window.addLast(p);
		return true;
	}

	public void dump() {
		for (int i=0;i<dup_history.length;i++) {
			System.out.println("value = " + i + ", count "+dup_history[i]);
		}
		System.out.println("total = " + item_total);
		System.out.println("mean = " + getMean());
		System.out.println("variance = " + getVariance());
	}
	
	public float getMean() {
		float sum = 0;
		int total_count = 0;
		for (int i=0;i<dup_history.length;i++) {
			sum += i * dup_history[i];
			total_count += dup_history[i];
		}
		item_mean = sum / item_total;
		return item_mean;
	}
	
	public float getVariance() {
		float sum = 0;
		for (int i=0;i<dup_history.length;i++) {
			sum += ((i - item_mean) * (i - item_mean)) * dup_history[i] ;
		}
		return sum / item_total;
	}
}
