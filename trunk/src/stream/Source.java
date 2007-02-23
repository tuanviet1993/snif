package stream;

/**
 * generic source
 * @author mringwal
 *
 * @param <O>
 */
public abstract interface Source<O> {

	public abstract void transfer ( O o, long timestamp);

	public abstract boolean subscribe(Sink<? super O> sink, int sinkID);
}
