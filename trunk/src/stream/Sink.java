package stream;

/** generic sink
 * 
 * @author mringwal
 *
 * @param <I>
 */

public abstract interface Sink<I> {
	
	public abstract void process(I o, int srcID, long timestamp);
}

