package stream;

public interface RealTime {
	/**
	 * Query, if there is at least on tuple ready to get from this source
	 * 
	 * @return true, if packets available
	 */
	boolean ready();
}
