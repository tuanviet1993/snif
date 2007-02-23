package stream;

/**
 * generic filter 
 * 
 * an object is passed on if the unary predicate returns true
 * @author mringwal
 *
 * @param <I>
 */
public class Filter<I> extends AbstractPipe<I,I> {
	
	private Predicate<? super I> predicate;

	public Filter (Predicate<I> predicate, String name) {
		this.predicate = predicate;
		this.name = name;
	}
	
	public void process(I o, int srcID, long timestamp) {
		if (predicate.invoke(o,timestamp) ) {
			// System.out.println("Filter TRUE "+predicate + " on " + o);
			transfer( o, timestamp);
		} else {
			// System.out.println("Filter FALSE "+predicate + " on " + o);
		}
	}

}
