package stream;

public abstract class Function<P, R> {
	public abstract R invoke(P argument);
}
