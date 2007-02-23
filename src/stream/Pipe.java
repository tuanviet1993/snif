package stream;

public interface Pipe<I,O> extends Sink<I>, Source<O> {
}
