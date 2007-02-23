package stream;

public abstract class Predicate<P> {
	public abstract boolean invoke(P o, long timestamp );
}
